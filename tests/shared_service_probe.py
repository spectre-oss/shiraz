# SPDX-License-Identifier: GPL-3.0-only
"""Two separately admitted ART clients share one registered service instance."""

import os
import selectors
import signal
import subprocess
import sys
import tempfile
import time
import zipfile
from contextlib import ExitStack
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from art_smoke import HOST, JARS, NATIVE, environment  # noqa: E402
from build_macos_fixture import append_aligned_dex  # noqa: E402

from shiraz.apk import FormatError  # noqa: E402
from shiraz.package_registry import PackageRegistry  # noqa: E402
from shiraz.service_broker import ServiceBroker  # noqa: E402


def line(child):
    deadline = time.monotonic() + 30
    buffered = getattr(child, '_probe_buffer', b'')
    with selectors.DefaultSelector() as selector:
        selector.register(child.stdout, selectors.EVENT_READ)
        while True:
            while b'\n' in buffered:
                raw, buffered = buffered.split(b'\n', 1)
                result = raw.decode('utf-8', errors='replace').strip()
                child._probe_lines.append(result)
                if result in (
                    'READY',
                    'UNBOUND',
                    'REBOUND',
                    'STARTED',
                    'STOPPED',
                    'STOP_TRUE',
                    'STOP_FALSE',
                    'RETIRED',
                    'DISCONNECTED',
                ) or result.startswith('RESULT '):
                    child._probe_buffer = buffered
                    return result
            remaining = deadline - time.monotonic()
            assert remaining > 0 and selector.select(remaining), 'Client response timed out'
            chunk = os.read(child.stdout.fileno(), 65536)
            assert chunk, 'Client exited; inspect shared-service-smoke logs'
            buffered += chunk
            assert len(buffered) <= 65536, 'Client output line limit'


def call(child, command="call"):
    child.stdin.write((command + "\n").encode())
    child.stdin.flush()
    result = line(child).split()
    assert result[0] == 'RESULT', result
    return [int(value) for value in result[1:]]


def group_alive(pid):
    try:
        os.killpg(pid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        # Some macOS execution contexts deny signal probes even after exit.
        # Verify membership independently; permission denial is not exit proof.
        groups = subprocess.run(
            ['ps', '-axo', 'pgid='], capture_output=True, text=True, check=True, timeout=5
        )
        return pid in [int(value) for value in groups.stdout.split()]


def main():
    work = ROOT / 'build/shared-service-smoke'
    work.mkdir(parents=True, exist_ok=True)
    registry = PackageRegistry(sys.argv[1])
    records = [registry.identity(package) for package in sys.argv[2:4]]
    assert len(records) == 2 and records[0]['uid'] != records[1]['uid']
    children = []
    with tempfile.TemporaryDirectory(dir=work) as folder, ExitStack() as stack:
        temp = Path(folder)
        classes = temp / 'classes'
        classes.mkdir()
        core = (
            ROOT
            / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
        )
        subprocess.run(
            [
                'javac',
                '--release',
                '8',
                '-cp',
                str(ROOT / 'build/framework/classes') + ':' + str(core),
                '-d',
                str(classes),
                str(ROOT / 'tests/java/SharedServiceClientSmoke.java'),
                str(ROOT / 'tests/java/ServiceReconnectClientSmoke.java'),
                str(ROOT / 'tests/java/RemoteServiceFilterSmoke.java'),
            ],
            check=True,
            capture_output=True,
            timeout=30,
        )
        env = environment(temp)
        dex = temp / 'classes.dex'
        subprocess.run(
            [
                str(HOST / 'bin/dx'),
                '--dex',
                '--min-sdk-version=27',
                '--output=' + str(dex),
                str(classes),
            ],
            env=env,
            check=True,
            capture_output=True,
            timeout=30,
        )
        jar = temp / 'smoke.jar'
        with zipfile.ZipFile(jar, 'w'):
            pass
        append_aligned_dex(jar, dex)
        vm = HOST / 'bin/dalvikvm64'
        if not vm.is_file():
            vm = HOST / 'bin/dalvikvm'
        with ServiceBroker(registry) as broker:
            try:
                for index, record in enumerate(records):
                    with broker.open_session(record['package'], record['sha256']) as channel:
                        command = [
                            str(vm),
                            '-Xbootclasspath:'
                            + ':'.join(str(HOST / 'framework' / name) for name in JARS),
                            '-Xcheck:jni',
                            '-Xusejit:false',
                            '-Xint',
                            '-Xnoimage-dex2oat',
                            '-Djava.library.path=' + str(NATIVE) + ':' + str(HOST / 'lib64'),
                            '-cp',
                            str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar),
                            'SharedServiceClientSmoke',
                            str(channel.fileno()),
                        ]
                        errors = stack.enter_context(
                            (work / f'client-{index}-stderr.log').open('wb')
                        )
                        child = subprocess.Popen(
                            command,
                            env=dict(env, SHIRAZ_PACKAGE_UID=str(record['uid'])),
                            pass_fds=(channel.fileno(),),
                            stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE,
                            stderr=errors,
                            bufsize=0,
                        )
                        child._probe_lines = []
                        children.append(child)
                        ready = line(child)
                        assert ready == 'READY', repr(ready)
                        assert 'ACTION_DISCOVERY_OK' in child._probe_lines
                with broker.lock:
                    processes = list(broker.processes)
                    assert len(processes) == 1 and processes[0].references == 2
                try:
                    with registry._generation_lock('org.shiraz.servicefixture', exclusive=True):
                        raise AssertionError('Live service did not hold its generation lease')
                except FormatError:
                    pass
                processes[0].restart_sticky()
                deadline = time.monotonic() + 5
                while True:
                    with broker.lock:
                        reported = (
                            processes[0].last_start_id == 1 and processes[0].restart_mode == 2
                        )
                    if reported:
                        break
                    assert time.monotonic() < deadline, 'Null-intent sticky start result missing'
                    time.sleep(0.01)
                assert (
                    'SERVICE_ONLY_STICKY_NULL' in (processes[0].evidence / 'stdout.log').read_text()
                )
                try:
                    processes[0].restart_sticky()
                    raise AssertionError('Repeated sticky restart accepted')
                except ValueError:
                    pass
                children[1].stdin.write(b'start\n')
                children[1].stdin.flush()
                assert line(children[1]) == 'STARTED'
                with broker.lock:
                    assert not processes[0].restart_suppressed
                    assert processes[0].last_start_ticket > 0
                    assert not broker.start_tickets
                children[1].stdin.write(b'stop\n')
                children[1].stdin.flush()
                assert line(children[1]) == 'STOP_TRUE'
                with broker.lock:
                    assert not processes[0].started and processes[0].references == 2
                    assert processes[0].restart_suppressed
                assert processes[0].process.poll() is None, 'Stop destroyed a bound service'
                first, second = call(children[0]), call(children[1])
                assert first[:3] == second[:3] and first[2] == 1, (
                    'Different instance or repeated onBind'
                )
                assert (
                    first[3] == 1
                    and second[3] == 2
                    and first[4] == records[0]['uid']
                    and second[4] == records[1]['uid']
                )
                children[1].stdin.write(b'hold-leaf\n')
                children[1].stdin.flush()
                assert line(children[1]) == 'READY'
                nested_first, nested_second = (
                    call(children[0], 'nested'),
                    call(children[1], 'nested'),
                )
                direct = call(children[1], 'leaf')
                target_uid = registry.identity('org.shiraz.servicefixture')['uid']
                assert nested_first[0] == 1 and nested_second[0] == 2 and direct[0] == 3
                assert nested_first[1] == nested_second[1] == direct[1]
                assert nested_first[2:] == nested_second[2:] == [target_uid, records[0]['uid']]
                assert direct[2:] == [records[1]['uid'], records[0]['uid']]
                with broker.lock:
                    all_processes = list(broker.processes)
                    assert len(all_processes) == 2, 'Nested bind escaped the root broker pool'
                (work / 'nested-results.txt').write_text(
                    repr([nested_first, nested_second, direct]) + '\n'
                )
                children[0].stdin.write(b'unbind\n')
                children[0].stdin.flush()
                assert line(children[0]) == 'UNBOUND'
                assert children[0].poll() is None, 'Unbind must not require app exit'
                children[0].stdin.write(b'close\n')
                children[0].stdin.flush()
                assert children[0].wait(timeout=15) == 0
                remaining = call(children[1])
                assert (
                    remaining[:3] == first[:3]
                    and remaining[3] == 3
                    and remaining[4] == records[1]['uid']
                    and remaining[5] == 0
                )
                children[1].stdin.write(b'unbind\n')
                children[1].stdin.flush()
                assert line(children[1]) == 'UNBOUND'
                assert children[1].poll() is None
                assert all(process.process.poll() == 0 for process in all_processes), (
                    'Final unbind retained service'
                )
                with broker.lock:
                    assert not broker.targets and not broker.processes
                for _ in range(6):
                    children[1].stdin.write(b'rebind\n')
                    children[1].stdin.flush()
                    assert line(children[1]) == 'REBOUND'
                for _ in range(2):
                    children[1].stdin.write(b'start\n')
                    children[1].stdin.flush()
                    assert line(children[1]) == 'STARTED'
                with broker.lock:
                    started = list(broker.processes)
                    assert len(started) == 1 and started[0].started and started[0].references == 0
                assert started[0].process.poll() is None, (
                    'Started service lost with temporary command connection'
                )
                deadline = time.monotonic() + 5
                while True:
                    with broker.lock:
                        result_reported = (
                            started[0].last_start_id == 2 and started[0].restart_mode == 2
                        )
                    if result_reported:
                        break
                    assert time.monotonic() < deadline, (
                        'ART service start result did not reach broker'
                    )
                    time.sleep(0.01)
                children[1].stdin.write(b'stop-self\n')
                children[1].stdin.flush()
                assert line(children[1]) == 'STOPPED'
                assert started[0].process.wait(timeout=15) == 0
                started_log = (started[0].evidence / 'stdout.log').read_text()
                assert (
                    started_log.count('SERVICE_ONLY_STARTED ') == 2
                    and started_log.count('SERVICE_ONLY_CREATED') == 1
                )
                assert started_log.count('SERVICE_ONLY_DESTROYED') == 1
                with broker.lock:
                    assert not broker.targets and not broker.processes
                (work / 'started-results.txt').write_text(started_log)
                children[1].stdin.write(b'stop\n')
                children[1].stdin.flush()
                assert line(children[1]) == 'STOP_FALSE'
                with broker.lock:
                    assert not broker.targets and not broker.processes
                children[1].stdin.write(b'foreground\n')
                children[1].stdin.flush()
                assert line(children[1]) == 'STARTED'
                with broker.lock:
                    foreground = list(broker.processes)
                    assert (
                        len(foreground) == 1
                        and foreground[0].started
                        and foreground[0].references == 0
                    )
                children[1].stdin.write(b'stop\n')
                children[1].stdin.flush()
                assert line(children[1]) == 'STOP_TRUE'
                assert foreground[0].process.wait(timeout=15) == 0
                foreground_log = (foreground[0].evidence / 'stdout.log').read_text()
                assert (
                    'SERVICE_ONLY_FOREGROUND' in foreground_log
                    and foreground_log.count('SERVICE_ONLY_DESTROYED') == 1
                )
                (work / 'foreground-results.txt').write_text(foreground_log)
                children[1].stdin.write(b'stop\n')
                children[1].stdin.flush()
                assert line(children[1]) == 'STOP_FALSE'
                with broker.lock:
                    assert not broker.targets and not broker.processes
                children[1].stdin.write(b'start\n')
                children[1].stdin.flush()
                assert line(children[1]) == 'STARTED'
                with broker.lock:
                    orphan = next(iter(broker.processes))
                    assert orphan.started and orphan.references == 0
                children[1].stdin.write(b'close\n')
                children[1].stdin.flush()
                assert children[1].wait(timeout=15) == 0
                assert orphan.process.poll() is None, 'Caller exit stopped its started service'
                try:
                    with registry._generation_lock('org.shiraz.servicefixture', exclusive=True):
                        raise AssertionError(
                            'Caller exit released a running background service generation'
                        )
                except FormatError:
                    pass
                # A distinct package/process stops the retained instance without binding it.
                for mode, marker in [
                    ('stop-once', 'STOPPED_AFTER_CALLER_EXIT'),
                    ('start-once', 'STARTED_BEFORE_CALLER_EXIT'),
                ]:
                    with broker.open_session(
                        records[0]['package'], records[0]['sha256']
                    ) as channel:
                        one_shot = command[:-1] + [str(channel.fileno()), mode]
                        result = subprocess.run(
                            one_shot,
                            env=dict(env, SHIRAZ_PACKAGE_UID=str(records[0]['uid'])),
                            pass_fds=(channel.fileno(),),
                            capture_output=True,
                            timeout=30,
                        )
                    (work / (mode + '-stdout.log')).write_bytes(result.stdout)
                    (work / (mode + '-stderr.log')).write_bytes(result.stderr)
                    assert result.returncode == 0 and marker in result.stdout.decode(), mode
                    if mode == 'stop-once':
                        assert orphan.process.wait(timeout=15) == 0
                        assert (orphan.evidence / 'stdout.log').read_text().count(
                            'SERVICE_ONLY_CREATED'
                        ) == 1
                        with broker.lock:
                            assert not broker.targets and not broker.processes
                with broker.lock:
                    teardown_orphan = next(iter(broker.processes))
                    assert teardown_orphan.started and teardown_orphan.references == 0
                assert teardown_orphan.process.poll() is None
                crashed = teardown_orphan
                crashed.process.kill()
                assert crashed.process.wait(timeout=15) == -signal.SIGKILL
                crashed.reaper.join(timeout=10)
                assert not crashed.reaper.is_alive(), 'Worker reaper stalled'
                deadline = time.monotonic() + 10
                while True:
                    alive = group_alive(crashed.process.pid)
                    with broker.lock:
                        retired = not broker.targets and not broker.processes
                    if retired and not alive:
                        break
                    assert time.monotonic() < deadline, (
                        'Crashed worker left process group or broker ownership'
                    )
                    time.sleep(0.01)
                # Explicit start must create a new worker, not reuse the dead target.
                with broker.open_session(records[0]['package'], records[0]['sha256']) as channel:
                    replacement = command[:-1] + [str(channel.fileno()), 'start-once']
                    result = subprocess.run(
                        replacement,
                        env=dict(env, SHIRAZ_PACKAGE_UID=str(records[0]['uid'])),
                        pass_fds=(channel.fileno(),),
                        capture_output=True,
                        timeout=30,
                    )
                (work / 'replacement-stdout.log').write_bytes(result.stdout)
                (work / 'replacement-stderr.log').write_bytes(result.stderr)
                assert result.returncode == 0 and b'STARTED_BEFORE_CALLER_EXIT' in result.stdout
                with broker.lock:
                    teardown_orphan = next(iter(broker.processes))
                    assert (
                        teardown_orphan is not crashed
                        and teardown_orphan.started
                        and teardown_orphan.references == 0
                    )
                assert teardown_orphan.process.poll() is None
                (work / 'crash-recovery.txt').write_text(
                    f'crashed_worker={crashed.process.pid} exit={crashed.process.returncode} group_removed=true\n'
                    f'explicit_replacement={teardown_orphan.process.pid} started=true references=0\n'
                )
                (work / 'results.txt').write_text(repr([first, second, remaining]) + '\n')

                # Real sticky service: unexpected wrapper death must replace the
                # process and deliver null without another caller start request.
                def sticky_command(mode):
                    with broker.open_session(
                        records[0]['package'], records[0]['sha256']
                    ) as channel:
                        result = subprocess.run(
                            command[:-1] + [str(channel.fileno()), mode],
                            env=dict(env, SHIRAZ_PACKAGE_UID=str(records[0]['uid'])),
                            pass_fds=(channel.fileno(),),
                            capture_output=True,
                            timeout=30,
                        )
                    assert result.returncode == 0, result.stderr.decode()

                sticky_command('intent-first')
                queue_key = (
                    'org.shiraz.servicefixture',
                    registry.identity('org.shiraz.servicefixture')['sha256'],
                    'org.shiraz.servicefixture.IntentQueue',
                )
                with broker.lock:
                    queued = broker.targets[queue_key]
                    assert queued.started and queued.references == 0
                deadline = time.monotonic() + 10
                while True:
                    with broker.lock:
                        retained = queued.restart_mode == 3 and set(queued.start_state.intents) == {
                            1
                        }
                        original = queued.start_state.intents.get(1, b'')
                    if retained:
                        break
                    assert time.monotonic() < deadline, 'Pending service intent was not retained'
                    time.sleep(0.01)
                assert 'first'.encode('utf-16le') in original, (
                    'Retained payload lost original action'
                )
                sticky_command('intent-second')
                assert queued.process.wait(timeout=20) == 0, 'IntentService worker failed'
                assert queued.last_finished_start_id == 2, (
                    'IntentService work was not acknowledged to broker'
                )
                assert not queued.start_state.intents, 'Completed service retained intent payloads'
                output = (queued.evidence / 'stdout.log').read_text()
                markers = [
                    'INTENT_QUEUE_HANDLED first',
                    'INTENT_QUEUE_HANDLED second',
                    'INTENT_QUEUE_DESTROYED',
                ]
                assert all(output.count(marker) == 1 for marker in markers), output
                positions = [output.index(marker) for marker in markers]
                assert positions == sorted(positions), output
                deadline = time.monotonic() + 10
                while True:
                    with broker.lock:
                        retired = queue_key not in broker.targets and queued not in broker.processes
                    if retired and not group_alive(queued.process.pid):
                        break
                    assert time.monotonic() < deadline, (
                        'IntentService left broker ownership or process group'
                    )
                    time.sleep(0.01)
                print('SHARED_INTENT_SERVICE_QUEUE_OK')
                sticky_command('redelivery-first')
                redelivery_key = (
                    queue_key[0],
                    queue_key[1],
                    'org.shiraz.servicefixture.Redelivery',
                )
                with broker.lock:
                    interrupted = broker.targets[redelivery_key]
                sticky_command('redelivery-second')
                deadline = time.monotonic() + 10
                while True:
                    with broker.lock:
                        ready = (
                            interrupted.last_start_id == 2
                            and interrupted.last_finished_start_id == 1
                            and set(interrupted.start_state.intents) == {2}
                        )
                    if ready:
                        break
                    assert time.monotonic() < deadline, 'First work did not finish before crash'
                    time.sleep(0.01)
                interrupted.process.kill()
                assert interrupted.process.wait(timeout=15) == -signal.SIGKILL
                deadline = time.monotonic() + 20
                while True:
                    with broker.lock:
                        restored = broker.targets.get(redelivery_key)
                        ready = (
                            restored is not None
                            and restored is not interrupted
                            and restored.restart_mode == 3
                            and restored.last_start_id == 2
                            and set(restored.start_state.intents) == {2}
                        )
                    if ready:
                        break
                    assert time.monotonic() < deadline, 'Unfinished intent was not redelivered'
                    time.sleep(0.01)
                assert restored.restart_attempt == 1
                assert not group_alive(interrupted.process.pid)
                sticky_command('redelivery-finish')
                assert restored.process.wait(timeout=20) == 0
                assert restored.last_finished_start_id == 3 and not restored.start_state.intents
                output = (restored.evidence / 'stdout.log').read_text()
                assert output.count('REDELIVERY_RESTORED_ID_2') == 1, output
                assert 'REDELIVERY_HANDLED first' not in output, output
                assert output.count('REDELIVERY_HANDLED second') == 1, output
                assert output.count('REDELIVERY_HANDLED finish') == 1, output
                assert output.count('REDELIVERY_DESTROYED') == 1, output
                print('SHARED_SERVICE_CRASH_REDELIVERY_OK')
                sticky_command('sticky-once')
                sticky_key = (
                    'org.shiraz.servicefixture',
                    registry.identity('org.shiraz.servicefixture')['sha256'],
                    'org.shiraz.servicefixture.Sticky',
                )
                deadline = time.monotonic() + 10
                while True:
                    with broker.lock:
                        sticky = broker.targets.get(sticky_key)
                        complete = sticky is not None and sticky.restart_mode == 1
                    if complete:
                        break
                    assert time.monotonic() < deadline, 'Initial sticky start did not complete'
                    time.sleep(0.01)
                sticky.process.kill()
                assert sticky.process.wait(timeout=15) == -signal.SIGKILL
                deadline = time.monotonic() + 20
                while True:
                    with broker.lock:
                        automatic = broker.targets.get(sticky_key)
                        complete = (
                            automatic is not None
                            and automatic is not sticky
                            and automatic.restart_mode == 1
                        )
                    if complete:
                        break
                    assert time.monotonic() < deadline, (
                        'Sticky worker was not automatically replaced'
                    )
                    time.sleep(0.01)
                assert not group_alive(sticky.process.pid), 'Replacement preceded old group cleanup'
                assert automatic.restart_attempt == 1 and automatic.last_start_id == 1
                assert 'STICKY_AUTOMATIC_NULL' in (automatic.evidence / 'stdout.log').read_text()
                sticky_command('sticky-stop')
                assert automatic.process.wait(timeout=15) == 0
                with broker.lock:
                    assert (
                        sticky_key not in broker.service_restarts
                        and sticky_key not in broker.targets
                    )
                (work / 'sticky-restart.txt').write_text(
                    f'old={sticky.process.pid} replacement={automatic.process.pid} null_intent=true stop_exit=0\n'
                )
                broker.restart_delays = (60, 60, 60)
                sticky_command('sticky-once')
                deadline = time.monotonic() + 10
                while True:
                    with broker.lock:
                        stopped_pending = broker.targets.get(sticky_key)
                        complete = stopped_pending is not None and stopped_pending.restart_mode == 1
                    if complete:
                        break
                    assert time.monotonic() < deadline, 'Second sticky start did not complete'
                    time.sleep(0.01)
                stopped_pending.process.kill()
                assert stopped_pending.process.wait(timeout=15) == -signal.SIGKILL
                deadline = time.monotonic() + 10
                while True:
                    with broker.lock:
                        pending = (
                            sticky_key in broker.service_restarts
                            and sticky_key not in broker.targets
                        )
                    if pending:
                        break
                    assert time.monotonic() < deadline, 'Sticky backoff was not entered'
                    time.sleep(0.01)
                sticky_command('sticky-stop')  # Must return true despite no live endpoint.
                sticky_command('sticky-absent')  # A second stop must return false.
                with broker.lock:
                    assert (
                        sticky_key not in broker.service_restarts
                        and sticky_key not in broker.targets
                    )
                print('SHARED_SERVICE_PENDING_STICKY_STOP_RESULT_OK')
                print('SHARED_SERVICE_AUTOMATIC_STICKY_RESTART_OK')
                # Exercise Context.bindService, not the low-level broker client:
                # one unchanged ServiceConnection receives a replacement binder.
                record = records[0]
                with broker.open_session(record['package'], record['sha256']) as channel:
                    reconnect_command = command[:-2] + [
                        'shiraz.runtime.ServiceReconnectClientSmoke',
                        record['package'],
                    ]
                    errors = stack.enter_context((work / 'reconnect-client-stderr.log').open('wb'))
                    reconnect_client = subprocess.Popen(
                        reconnect_command,
                        env=dict(
                            env,
                            SHIRAZ_PACKAGE_UID=str(record['uid']),
                            SHIRAZ_BROKER_FD=str(channel.fileno()),
                        ),
                        pass_fds=(channel.fileno(),),
                        stdin=subprocess.PIPE,
                        stdout=subprocess.PIPE,
                        stderr=errors,
                        bufsize=0,
                    )
                reconnect_client._probe_lines = []
                children.append(reconnect_client)
                initial = line(reconnect_client).split()
                assert initial[0] == 'RESULT' and initial[2] == '1', initial
                teardown_orphan.process.kill()
                assert teardown_orphan.process.wait(timeout=15) == -signal.SIGKILL
                assert line(reconnect_client) == 'DISCONNECTED'
                rebound = line(reconnect_client).split()
                assert rebound[0] == 'RESULT' and rebound[2] == '2' and rebound[1] != initial[1], (
                    rebound
                )
                with broker.lock:
                    recovered = next(
                        item
                        for item in broker.targets.values()
                        if item.key[2] == 'org.shiraz.servicefixture.Worker'
                    )
                reconnect_client.stdin.write(b'unbind-on-death\n')
                reconnect_client.stdin.flush()
                assert line(reconnect_client) == 'READY'
                recovered.process.kill()
                assert recovered.process.wait(timeout=15) == -signal.SIGKILL
                assert line(reconnect_client) == 'UNBOUND'
                reconnect_client.stdin.write(b'close\n')
                reconnect_client.stdin.flush()
                assert reconnect_client.wait(timeout=15) == 0
                (work / 'reconnect-results.txt').write_text(
                    f'initial={initial[1]} replacement={rebound[1]} same_connection=true unbound_on_death=true\n'
                )
                with broker.open_session(record['package'], record['sha256']) as channel:
                    filter_errors = stack.enter_context(
                        (work / 'filter-client-stderr.log').open('wb')
                    )
                    filters = subprocess.Popen(
                        command[:-2]
                        + ['shiraz.runtime.RemoteServiceFilterSmoke', record['package']],
                        env=dict(
                            env,
                            SHIRAZ_PACKAGE_UID=str(record['uid']),
                            SHIRAZ_BROKER_FD=str(channel.fileno()),
                        ),
                        pass_fds=(channel.fileno(),),
                        stdin=subprocess.PIPE,
                        stdout=subprocess.PIPE,
                        stderr=filter_errors,
                        bufsize=0,
                    )
                filters._probe_lines = []
                children.append(filters)
                filter_ready = line(filters).split()
                assert filter_ready[0] == 'RESULT', filter_ready
                with broker.lock:
                    filter_target = next(
                        item
                        for item in broker.targets.values()
                        if item.key[2] == 'org.shiraz.servicefixture.Worker'
                    )
                assert filter_target.references == 2, 'Expected one broker endpoint per filter'
                filter_target.process.kill()
                assert filter_target.process.wait(timeout=15) == -signal.SIGKILL
                filter_output, _ = filters.communicate(b'restart\n', timeout=45)
                (work / 'filter-client-stdout.log').write_bytes(
                    ('\n'.join(filters._probe_lines) + '\n').encode() + filter_output
                )
                assert filters.returncode == 0 and b'REMOTE_SERVICE_FILTER_OK' in filter_output, (
                    'Remote filter regression failed; inspect filter-client logs'
                )
                assert b'REMOTE_SERVICE_FILTER_RECONNECTED_OK' in filter_output
                print('SHARED_SERVICE_FILTER_ISOLATION_OK')
                # Restore the started fixture used by the remaining retirement checks.
                with broker.open_session(records[0]['package'], records[0]['sha256']) as channel:
                    restored = subprocess.run(
                        command[:-1] + [str(channel.fileno()), 'start-once'],
                        env=dict(env, SHIRAZ_PACKAGE_UID=str(records[0]['uid'])),
                        pass_fds=(channel.fileno(),),
                        capture_output=True,
                        timeout=30,
                    )
                assert restored.returncode == 0, restored.stderr.decode()
                with broker.lock:
                    teardown_orphan = next(
                        item
                        for item in broker.targets.values()
                        if item.key[2] == 'org.shiraz.servicefixture.Worker'
                    )
                print('SHARED_SERVICE_CONNECTION_RECONNECTED_OK')
                # Keep a real ART binding open while retiring its service owner.
                target = registry.identity('org.shiraz.servicefixture')
                assert broker.quiesce_package(
                    target['package'], expected_sha256=target['sha256'], expected_uid=target['uid']
                )
                assert teardown_orphan.process.poll() == 0 and not group_alive(
                    teardown_orphan.process.pid
                )
                assert registry.identity(target['package']) == target, (
                    'Quiescence changed package identity'
                )
                with registry._generation_lock(target['package'], exclusive=True):
                    pass
                assert broker.resume_package(
                    target['package'], expected_sha256=target['sha256'], expected_uid=target['uid']
                )
                with broker.open_session(records[0]['package'], records[0]['sha256']) as channel:
                    replacement = command[:-1] + [str(channel.fileno()), 'start-once']
                    resumed = subprocess.run(
                        replacement,
                        env=dict(env, SHIRAZ_PACKAGE_UID=str(records[0]['uid'])),
                        pass_fds=(channel.fileno(),),
                        capture_output=True,
                        timeout=30,
                    )
                assert resumed.returncode == 0 and b'STARTED_BEFORE_CALLER_EXIT' in resumed.stdout
                with broker.lock:
                    teardown_orphan = next(iter(broker.processes))
                    assert teardown_orphan.started and teardown_orphan.references == 0
                print('SHARED_SERVICE_QUIESCE_RESUME_OK')
                record = records[0]
                with broker.open_session(record['package'], record['sha256']) as channel:
                    bound_command = command[:-1] + [str(channel.fileno())]
                    errors = stack.enter_context((work / 'retirement-client-stderr.log').open('wb'))
                    child = subprocess.Popen(
                        bound_command,
                        env=dict(env, SHIRAZ_PACKAGE_UID=str(record['uid'])),
                        pass_fds=(channel.fileno(),),
                        stdin=subprocess.PIPE,
                        stdout=subprocess.PIPE,
                        stderr=errors,
                        bufsize=0,
                    )
                child._probe_lines = []
                children.append(child)
                assert line(child) == 'READY'
                assert call(child)[2] >= 1
                target = registry.identity('org.shiraz.servicefixture')
                assert broker.retire_package(
                    target['package'], expected_sha256=target['sha256'], expected_uid=target['uid']
                )
                assert teardown_orphan.process.poll() is not None, (
                    'Retired service worker remained alive'
                )
                assert not group_alive(teardown_orphan.process.pid), (
                    'Retired service process group remained alive'
                )
                with registry._generation_lock(target['package'], exclusive=True):
                    pass  # Cleanup must release generation ownership too.
                child.stdin.write(b'retired\n')
                child.stdin.flush()
                assert line(child) == 'RETIRED'
                assert child.poll() is None, 'Retirement killed unrelated client'
                child.stdin.write(b'close\n')
                child.stdin.flush()
                assert child.wait(timeout=15) == 0
                (work / 'retirement-results.txt').write_text(
                    f'worker={teardown_orphan.process.pid} exit={teardown_orphan.process.returncode} '
                    'group_removed=true old_endpoint_dead=true rebind_denied=true caller_survived=true\n'
                )
            finally:
                for index, child in enumerate(children):
                    (work / f'client-{index}-stdout.log').write_text(
                        '\n'.join(child._probe_lines) + '\n'
                    )
                    if child.poll() is None:
                        child.kill()
                    child.communicate(timeout=10)
        assert teardown_orphan.process.poll() == 0, 'Broker teardown retained a started worker'
        assert all(process.process.poll() == 0 for process in all_processes), (
            'Service survived broker teardown'
        )
        leaf = next(process for process in all_processes if process.key[2].endswith('.LeafService'))
        assert 'LEAF_SERVICE_DESTROYED' in (leaf.evidence / 'stdout.log').read_text()
        log = (processes[0].evidence / 'stdout.log').read_text()
        assert log.count('SERVICE_ONLY_BOUND') == 1 and log.count('SERVICE_ONLY_UNBOUND') == 1
        assert 'SERVICE_ONLY_DESTROYED' in log
        assert not broker.processes and not broker.sessions and not broker.targets
        print('SHARED_SERVICE_CRASH_CLEANUP_AND_EXPLICIT_RESTART_OK')
        print('SHARED_SERVICE_RETIREMENT_REVOKES_LIVE_BINDING_OK')
        print('SHARED_SERVICE_CALLER_EXIT_RETENTION_OK')
        print('SHARED_SERVICE_STARTED_RETENTION_OK')
        print('SHARED_SERVICE_TWO_CALLERS_OK')
        print('ROOT_BROKER_NESTED_SERVICE_OK')


if __name__ == '__main__':
    main()
