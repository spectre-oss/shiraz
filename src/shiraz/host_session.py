# SPDX-License-Identifier: GPL-3.0-only
"""Explicit multi-app host session with one shared service broker."""

import json
import os
import shutil
import signal
import socket
import subprocess
import sys
import threading
import time
import uuid
from contextlib import ExitStack
from pathlib import Path

from .apk import FormatError, inspect_apk
from .launch_settings import normalize_settings
from .package_registry import PackageRegistry
from .process_control import ProcessControl
from .service_broker import QuiescenceCleanupError, RetirementCleanupError, ServiceBroker

ROOT = Path(__file__).resolve().parents[2]


def session_exit_status(result):
    """Keep raw process status; only authenticated package-requested kills are expected."""
    return int(
        any(
            app['exit_status'] != 0
            and not (
                app['exit_status'] == -signal.SIGKILL
                and app.get('termination_reason') == 'package_request'
            )
            for app in result['apps']
        )
    )


class HostSession:
    def __init__(self, registry, library=None):
        self.library = Path(library).resolve() if library is not None else None
        if self.library is not None and not (self.library / 'apps').is_dir():
            raise FormatError('Host library requires an apps directory')
        self.registry = PackageRegistry(registry)
        self.broker = ServiceBroker(
            self.registry,
            activity_launcher=self._admit_activity,
            process_terminator=self._terminate_process,
        )
        self.work = ROOT / 'build/host-sessions' / uuid.uuid4().hex
        self.work.mkdir(parents=True, mode=0o700)
        self.apps = {}
        self.activity_processes = {}
        self.process_names = {}
        self.launch_settings = {}
        self.process_controls = {}
        self.changed = threading.Event()
        self.completed = []
        self.cleanup_errors = {}
        self.reapers = {}
        self.launch_ids = {}
        self.launch_identities = {}
        self.inboxes = {}
        self.warm_pool = None
        self.lock = threading.RLock()
        self.closed = False

    def _launches(self):
        """Called under the host lock; preserve primary-app indexing for operators."""
        return [
            (package, self.process_names.get(app[0], package), app)
            for package, app in self.apps.items()
        ] + [(package, name, app) for (package, name), app in self.activity_processes.items()]

    def _find_launch(self, package, *, launch_id=None, process_name=None):
        for owner, name, app in self._launches():
            if (
                owner == package
                and (launch_id is None or self.launch_ids.get(app[0]) == launch_id)
                and (process_name is None or name == process_name)
            ):
                return app
        return None

    def _terminate_process(self, identity, pid):
        with self.lock:
            if self.closed or type(pid) is not int or not 1 <= pid <= 0x7FFFFFFF:
                raise PermissionError('Invalid process termination')
            source = self._find_launch(identity.package, process_name=identity.process_name)
            if source is None or source[0].poll() is not None:
                raise PermissionError('Process caller is no longer live')
            caller = self.launch_identities.get(source[0])
            if (
                caller is None
                or caller['uid'] != identity.uid
                or caller['sha256'] != identity.sha256
            ):
                raise PermissionError('Process caller generation changed')
            with self.broker.lock:
                if self.broker.closed or identity.package in self.broker.quiesced:
                    raise PermissionError('Process package unavailable')
            with self.registry.lease(identity.package, identity.sha256):
                for package, _, (process, _) in self._launches():
                    target = self.launch_identities.get(process)
                    if (
                        package != identity.package
                        or process is source[0]
                        or process.poll() is not None
                        or target is None
                        or target['uid'] != identity.uid
                        or target['sha256'] != identity.sha256
                    ):
                        continue
                    control = self.process_controls.get(process)
                    if control is not None and control.reported_pid() == pid:
                        control.terminate(pid)
                        try:
                            process.wait(timeout=3)
                        except subprocess.TimeoutExpired as error:
                            raise RuntimeError("Owned process termination timed out") from error
                        self._join_reaper(process)
                        return
            raise PermissionError('Target is not a live owned package process')

    def _admit_activity(self, identity, target):
        """Broker-authenticated caller; no guest launch IDs or socket descriptors."""
        with self.lock:
            if self.closed:
                raise RuntimeError('Host session closed')
            source = self._find_launch(
                identity.package, process_name=identity.process_name or identity.package
            )
            if source is None or source[0].poll() is not None:
                raise PermissionError('Activity caller has no live host application')
            launch_identity = self.launch_identities.get(source[0])
            if (
                launch_identity is None
                or launch_identity['uid'] != identity.uid
                or launch_identity['sha256'] != identity.sha256
            ):
                raise PermissionError('Activity caller launch identity changed')
            requester = dict(package=identity.package, launch_id=self.launch_ids[source[0]])
            # Only runtime presentation/code settings propagate. Startup snapshots,
            # frame limits and embedding channels belong to the original activity.
            settings = {
                key: self.launch_settings[source[0]][key]
                for key in ('width', 'height', 'log_level', 'native_code', 'device_profile')
            }
        caller, worker = socket.socketpair()
        try:
            self.start(
                target['package'],
                expected_sha256=target['apk_sha256'],
                requester=requester,
                component=target['requested_component'],
                launch_channel=worker,
                settings=settings,
            )
            return caller
        except BaseException:
            caller.close()
            raise
        finally:
            worker.close()

    def start(
        self,
        package,
        expected_sha256=None,
        settings=None,
        requester=None,
        component=None,
        launch_channel=None,
    ):
        if launch_channel is not None and (component is None or launch_channel.fileno() < 3):
            raise FormatError('Launch channel requires an explicit activity')
        if component is not None and (requester is None or expected_sha256 is None):
            raise PermissionError('Explicit activity requires a pinned target and active requester')
        with self.lock:
            if self.closed:
                raise RuntimeError('Host session closed')
            if requester is not None:
                source = self._find_launch(requester['package'], launch_id=requester['launch_id'])
                if (
                    source is None
                    or source[0].poll() is not None
                    or self.launch_ids.get(source[0]) != requester['launch_id']
                ):
                    raise PermissionError('Requester launch is no longer active')
                caller = self.launch_identities.get(source[0])
                if caller is None:
                    raise PermissionError('Requester launch identity is unavailable')
                with self.registry.lease(requester['package'], caller['sha256']):
                    if component is None:
                        target = self.registry.identity(package)
                        self.registry.authorize_launcher(
                            caller['uid'],
                            package,
                            caller_sha256=caller['sha256'],
                            target_sha256=target['sha256'],
                        )
                    else:
                        if requester['package'] in self.broker.quiesced:
                            raise PermissionError('Activity requester unavailable')
                        self.registry.resolve_activity(
                            caller['uid'], package, component, caller_sha256=caller['sha256']
                        )
        from .foreground_admission import reserve
        from .runtime import activity_process_name

        identity = self.registry.identity(package)
        if expected_sha256 is not None and identity['sha256'] != expected_sha256:
            raise PermissionError('Launch APK does not match expected content')
        apk = self.registry.launch_apk(package, identity['sha256'])
        report = inspect_apk(apk)
        if report['apk_sha256'] != identity['sha256']:
            raise FormatError('Registered launch identity changed')
        if component is None and not report['manifest'].get('launcher_activities'):
            raise FormatError('Host session app requires a launcher')
        entry = component or report['manifest']['launcher_activities'][0]['target']
        process = activity_process_name(report['manifest'], entry)
        with reserve(self.broker, package, identity['sha256'], process):
            return self._start(
                package,
                expected_sha256=identity['sha256'],
                settings=settings,
                requester=requester,
                component=component,
                launch_channel=launch_channel,
            )

    def _start(
        self,
        package,
        expected_sha256=None,
        settings=None,
        requester=None,
        component=None,
        launch_channel=None,
    ):
        """Trusted host API: launch only the exact enrolled content, never a caller-selected classpath."""
        settings = normalize_settings(settings)
        if launch_channel is not None and (component is None or launch_channel.fileno() < 3):
            raise FormatError('Launch channel requires an explicit activity')
        if component is not None and (requester is None or expected_sha256 is None):
            raise PermissionError("Explicit activity requires a pinned target and active requester")
        with self.lock:
            if self.closed:
                raise RuntimeError('Host session closed')
            caller = None
            if requester is not None:
                source = self._find_launch(requester['package'], launch_id=requester['launch_id'])
                if (
                    source is None
                    or source[0].poll() is not None
                    or self.launch_ids.get(source[0]) != requester['launch_id']
                ):
                    raise PermissionError('Requester launch is no longer active')
                caller = self.launch_identities.get(source[0])
                if caller is None:
                    raise PermissionError('Requester launch identity is unavailable')
            identity = self.registry.identity(package)
            if expected_sha256 is not None and identity['sha256'] != expected_sha256:
                raise FormatError('Registered launch identity changed')
            apk = self.registry.launch_apk(package, identity['sha256'])
            report = inspect_apk(apk)
            if (
                report['apk_sha256'] != identity['sha256']
                or component is None
                and not report['manifest'].get('launcher_activities')
            ):
                raise FormatError('Host session app requires verified content and a launcher')
            launch_id = uuid.uuid4().hex
            work = self.work / (package + '-' + launch_id)
            work.mkdir(mode=0o700)
            env = dict(os.environ, PYTHONPATH=str(ROOT / 'src'))
            for key in (
                'SHIRAZ_BROKER_FD',
                'SHIRAZ_PACKAGE_UID',
                'SHIRAZ_SERVICE_CALLER_UID',
                'SHIRAZ_LAUNCHER_APPS',
                'SHIRAZ_LAUNCHER_INBOX',
                'SHIRAZ_LIVE_INPUT',
                'SHIRAZ_EMBED_FRAME',
                'SHIRAZ_PROCESS_CONTROL_FD',
                'SHIRAZ_ACTIVITY_FD',
                'SHIRAZ_ACTIVITY_CALLER_UID',
            ):
                env.pop(key, None)
            inbox = None
            if self.library is not None:
                sessions = self.library / 'sessions'
                sessions.mkdir(mode=0o700, exist_ok=True)
                if sessions.is_symlink():
                    raise FormatError('Host inbox directory cannot be a symlink')
                inbox = sessions / launch_id
                inbox.mkdir(mode=0o700)
                env.update(
                    SHIRAZ_LAUNCHER_APPS=str(self.library / 'apps'),
                    SHIRAZ_LAUNCHER_INBOX=str(inbox),
                )
            lease = ExitStack()
            try:
                lease.enter_context(self.registry.lease(package, identity['sha256']))
                if component is not None:
                    with self.broker.lock:
                        if self.broker.closed or requester['package'] in self.broker.quiesced:
                            raise PermissionError('Activity requester unavailable')
                    lease.enter_context(self.registry.lease(requester['package'], caller['sha256']))
                    resolved = self.registry.resolve_activity(
                        caller['uid'], package, component, caller_sha256=caller['sha256']
                    )
                    if (
                        resolved['uid'] != identity['uid']
                        or resolved['apk_sha256'] != identity['sha256']
                    ):
                        raise PermissionError('Activity generation changed during admission')

                elif caller is not None:
                    self.registry.authorize_launcher(
                        caller['uid'],
                        package,
                        caller_sha256=caller['sha256'],
                        target_sha256=identity['sha256'],
                    )
                from .runtime import activity_process_name, normalize_process_name

                entry = component or report['manifest']['launcher_activities'][0]['target']
                process_name = activity_process_name(report['manifest'], entry)
                if component is not None and process_name != normalize_process_name(
                    package, resolved['process']
                ):
                    raise PermissionError('Activity process identity changed during admission')
                self._reap_finished(package, process_name, admission=True)
                if (component is None and package in self.apps) or len(self._launches()) >= 16:
                    raise FormatError('Duplicate package or host session app limit')
                if self._find_launch(package, process_name=process_name) is not None:
                    raise FormatError(
                        'Activity process already owned; collect completed launches first'
                    )
                with self.broker.open_session(
                    package, identity['sha256'], process_name=process_name
                ) as channel:
                    direct_launch = (
                        sys.platform == 'linux'
                        and os.environ.get('SHIRAZ_DIRECT_LAUNCH') == '1'
                        and component is None
                        and launch_channel is None
                        and not settings.get('sandbox')
                        and settings.get('embed') is None
                        and settings.get('timeout') is None
                    )
                    control_host = None
                    direct_control = None
                    if direct_launch:
                        from .runtime import prepare_direct_activity
                        from .warm_pool import DirectProcessControl

                        env['SHIRAZ_GUEST_LOADER'] = '1' if settings.get('native_code') else '0'
                        (
                            session_ctx,
                            art_cmd,
                            art_env,
                            data_dir,
                            run_work,
                            cp_apk,
                            app_lib,
                            prop_pairs,
                            activity_args,
                        ) = prepare_direct_activity(
                            apk,
                            report,
                            registry=self.registry.root,
                            broker_channel=channel,
                            width=settings['width'],
                            height=settings['height'],
                            frames=settings.get('frames'),
                            snapshot=settings.get('snapshot'),
                            log_level=settings.get('log_level', 'INFO'),
                            device_profile=settings.get('device_profile'),
                            environ=env,
                        )
                        lease.enter_context(session_ctx)
                        run_stdout = run_work / 'stdout.log'
                        run_stderr = run_work / 'stderr.log'
                        with run_stdout.open('wb') as out, run_stderr.open('wb') as err:
                            for link_name, target in (
                                ('stdout.log', run_stdout),
                                ('stderr.log', run_stderr),
                            ):
                                link_path = work / link_name
                                link_path.unlink(missing_ok=True)
                                os.symlink(target, link_path)
                            process = None
                            if (
                                self.warm_pool is not None
                                and os.environ.get('SHIRAZ_WARM_POOL', '1') == '1'
                            ):
                                env_pairs = [
                                    (k, v)
                                    for k, v in art_env.items()
                                    if k != 'SHIRAZ_BROKER_FD'
                                    and (
                                        k.startswith(
                                            (
                                                'SHIRAZ_',
                                                'SDL_',
                                                'WAYLAND_',
                                                'DBUS_',
                                                'ANDROID_',
                                                'XDG_',
                                                'BIONIC_',
                                            )
                                        )
                                        or k in ('HOME', 'TMPDIR')
                                    )
                                ]
                                process = self.warm_pool.claim_and_dispatch(
                                    data_dir=data_dir,
                                    app_lib_dir=app_lib,
                                    cp_apk=cp_apk,
                                    env_pairs=env_pairs,
                                    prop_pairs=prop_pairs,
                                    activity_args=activity_args,
                                    broker_fd=channel.fileno(),
                                    stdout_fd=out.fileno(),
                                    stderr_fd=err.fileno(),
                                )
                            if process is None:
                                process = subprocess.Popen(
                                    art_cmd,
                                    cwd=data_dir,
                                    env=art_env,
                                    stdin=subprocess.DEVNULL,
                                    stdout=out,
                                    stderr=err,
                                    pass_fds=(channel.fileno(),),
                                    start_new_session=True,
                                )
                        direct_control = DirectProcessControl(process.pid)
                    else:
                        command = [
                            sys.executable,
                            '-m',
                            'shiraz.app_worker',
                            str(self.registry.root),
                            package,
                            identity['sha256'],
                            str(channel.fileno()),
                            json.dumps(settings),
                        ]
                        if component is not None:
                            command.append(component)
                        if launch_channel is not None:
                            command.extend((str(launch_channel.fileno()), str(caller['uid'])))
                        control_host, control_worker = socket.socketpair()
                        env['SHIRAZ_PROCESS_CONTROL_FD'] = str(control_worker.fileno())
                        lease.callback(control_host.close)
                        with (
                            control_worker,
                            (work / 'stdout.log').open('wb') as out,
                            (work / 'stderr.log').open('wb') as err,
                        ):
                            process = subprocess.Popen(
                                command,
                                cwd=ROOT,
                                env=env,
                                stdin=subprocess.DEVNULL,
                                stdout=out,
                                stderr=err,
                                pass_fds=(
                                    channel.fileno(),
                                    control_worker.fileno(),
                                    *(
                                        (launch_channel.fileno(),)
                                        if launch_channel is not None
                                        else ()
                                    ),
                                ),
                                start_new_session=True,
                            )
            except BaseException:
                lease.close()
                if inbox is not None:
                    shutil.rmtree(inbox)
                raise
            if package not in self.apps and (component is None or process_name == package):
                self.apps[package] = (process, work)
            else:
                self.activity_processes[(package, process_name)] = (process, work)
            self.process_names[process] = process_name
            self.launch_settings[process] = settings
            self.process_controls[process] = (
                direct_control if direct_control is not None else ProcessControl(control_host)
            )
            self.changed.set()
            self.launch_ids[process] = launch_id
            self.launch_identities[process] = dict(identity)
            if inbox is not None:
                self.inboxes[process] = inbox

            def reap():
                try:
                    status = process.wait()
                    group_killed = (
                        status == -signal.SIGKILL
                        and self.process_controls[process].group_termination_requested
                    )
                    if status and not group_killed:
                        try:
                            os.killpg(process.pid, signal.SIGKILL)
                        except ProcessLookupError:
                            pass
                except BaseException as error:
                    self.cleanup_errors[process] = error
                finally:
                    try:
                        lease.close()
                    except BaseException as error:
                        self.cleanup_errors.setdefault(process, error)
                    self.changed.set()

            reaper = threading.Thread(target=reap, name='shiraz-app-reaper', daemon=True)
            self.reapers[process] = reaper
            reaper.start()
            return process

    def _join_reaper(self, process):
        reaper = self.reapers[process]
        reaper.join(timeout=3)
        if reaper.is_alive():
            raise RuntimeError('Application reaper did not stop')
        if process in self.cleanup_errors:
            raise RuntimeError('Application process cleanup failed') from self.cleanup_errors[
                process
            ]

    def _completion(self, package, name, process, work):
        status = process.returncode
        result = dict(
            package=package,
            process_name=name,
            launch_id=self.launch_ids.get(process),
            exit_status=status,
            stdout=str(work / 'stdout.log'),
            stderr=str(work / 'stderr.log'),
        )
        control = self.process_controls.get(process)
        if (
            status == -signal.SIGKILL
            and control is not None
            and control.group_termination_requested is True
        ):
            result['termination_reason'] = 'package_request'
        return result

    def _reap_finished(self, package=None, process_name=None, *, admission=False):
        """Host lock held. Keep completion events until an operator collects them."""
        for owner, name, (process, work) in self._launches():
            if (
                package is not None
                and owner != package
                or process_name is not None
                and name != process_name
            ):
                continue
            status = process.poll()
            if status is None:
                continue
            if admission and len(self.completed) >= 256:
                raise FormatError('Activity completion backlog requires collection')
            self._join_reaper(process)
            self.completed.append(self._completion(owner, name, process, work))
            if self.apps.get(owner, (None,))[0] is process:
                del self.apps[owner]
            else:
                del self.activity_processes[(owner, name)]
            self.process_names.pop(process, None)
            self.launch_settings.pop(process, None)
            control = self.process_controls.pop(process, None)
            if control is not None:
                control.close()
            del self.reapers[process]
            del self.launch_ids[process]
            self.launch_identities.pop(process, None)
            self._remove_inbox(process)

    def collect(self):
        """Return completed launches once, including slots reclaimed by admission."""
        with self.lock:
            if self.closed:
                raise RuntimeError('Host session closed')
            self._reap_finished()
            finished = self.completed
            self.completed = []
            return finished

    def _remove_inbox(self, process):
        inbox = self.inboxes.pop(process, None)
        if inbox is not None:
            shutil.rmtree(inbox, ignore_errors=True)

    def stop(self, package, expected_launch):
        """Stop one launch, preserving the broker and other apps despite PID reuse."""
        with self.lock:
            if self.closed:
                raise RuntimeError('Host session closed')
            app = self._find_launch(package, launch_id=expected_launch)
            if app is None:
                if self._find_launch(package) is not None:
                    raise FormatError('Application launch changed since stop was prepared')
                return False
            process, _ = app
            if self.launch_ids[process] != expected_launch:
                raise FormatError('Application launch changed since stop was prepared')
            if process.poll() is not None:
                return False
            try:
                os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait(timeout=3)
            self._join_reaper(process)
            return True

    def retire(self, package, *, expected_sha256, expected_uid):
        """Retire enrollment and stop package processes, preserving other apps.

        Completion remains available through collect(). Library files and app
        data are not removed by this operation.
        """
        with self.lock:
            if self.closed:
                raise RuntimeError('Host session closed')
            failure = None
            try:
                retired = self.broker.retire_package(
                    package, expected_sha256=expected_sha256, expected_uid=expected_uid
                )
            except RetirementCleanupError as error:
                failure = error
            for owner, _, app in self._launches():
                if owner == package:
                    self.stop(package, self.launch_ids[app[0]])
            if failure is not None:
                raise failure
            return retired

    def quiesce(self, package, *, expected_sha256, expected_uid):
        """Block service admission and drain package processes without retiring UID."""
        with self.lock:
            if self.closed:
                raise RuntimeError('Host session closed')
            failure = None
            try:
                self.broker.quiesce_package(
                    package, expected_sha256=expected_sha256, expected_uid=expected_uid
                )
            except QuiescenceCleanupError as error:
                failure = error
            for owner, _, app in self._launches():
                if owner == package:
                    self.stop(package, self.launch_ids[app[0]])
            if failure is not None:
                raise failure
            return True

    def resume(self, package, *, expected_sha256, expected_uid):
        with self.lock:
            if self.closed:
                raise RuntimeError('Host session closed')
            for owner, _, app in self._launches():
                if owner == package and (app[0].poll() is None or self.reapers[app[0]].is_alive()):
                    raise RuntimeError('Package app cleanup is incomplete')
            return self.broker.resume_package(
                package, expected_sha256=expected_sha256, expected_uid=expected_uid
            )

    def wait(self, timeout=None):
        if timeout is not None and timeout <= 0:
            raise ValueError('Host session timeout must be positive')
        deadline = None if timeout is None else time.monotonic() + timeout
        try:
            while True:
                with self.lock:
                    self.changed.clear()
                    apps = self._launches()
                    if all(process.poll() is not None for _, _, (process, _) in apps):
                        return {
                            'work': str(self.work),
                            'apps': list(self.completed)
                            + [
                                self._completion(package, name, process, work)
                                for package, name, (process, work) in apps
                            ],
                        }
                remaining = None if deadline is None else deadline - time.monotonic()
                if remaining is not None and remaining <= 0:
                    self.close()
                    raise FormatError(f'Host session timed out; inspect {self.work}')
                self.changed.wait(remaining)
        except BaseException:
            self.close()
            raise

    def close(self):
        with self.lock:
            if self.closed:
                return
            self.closed = True
            processes = [app[0] for _, _, app in self._launches()]
        try:
            # Each worker and its ART descendant occupy their own process group.
            # Signal every group before waiting so shutdown time is not per app.
            for process in processes:
                if process.poll() is None:
                    try:
                        os.killpg(process.pid, signal.SIGTERM)
                    except ProcessLookupError:
                        pass
            for process in processes:
                try:
                    process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    try:
                        os.killpg(process.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                    process.wait(timeout=3)
                self._join_reaper(process)
        finally:
            if self.warm_pool is not None:
                try:
                    self.warm_pool.close()
                except Exception:
                    pass
            self.broker.close()
            for process in processes:
                if process.poll() is not None:
                    self._remove_inbox(process)

    def __enter__(self):
        # The shared host outlives individual app windows. Its scheduler must
        # also run while the launcher has no foreground applications open.
        self.broker.start_job_scheduler()
        self.broker.start_sync_scheduler()
        return self

    def __exit__(self, *_):
        self.close()
