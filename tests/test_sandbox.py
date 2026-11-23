# SPDX-License-Identifier: GPL-3.0-only
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from shiraz.sandbox import (
    SandboxPolicy,
    build_linux_landlock_policy,
    build_macos_entitlements,
    build_macos_seatbelt_profile,
    build_windows_sandbox_config,
    is_process_in_windows_job,
    prepare_sandbox_launch,
    resolve_sandbox_backend,
    sandbox_plan,
    windows_sandbox_observer,
    wrap_macos_seatbelt_command,
)


class SandboxPolicyTest(unittest.TestCase):
    def _sample_policy(self, *, allow_network=True, allow_jit=True):
        return SandboxPolicy(
            package_name='org.shiraz.sample',
            data_dir='/tmp/shiraz/data/org.shiraz.sample',
            cache_dir='/tmp/shiraz/cache/org.shiraz.sample',
            read_only_paths=('/opt/shiraz/runtime',),
            allow_network=allow_network,
            allow_jit=allow_jit,
            memory_limit_mb=1024,
            max_processes=16,
        )

    def test_backend_resolution_all_three_oses(self):
        self.assertEqual(resolve_sandbox_backend('Linux'), 'landlock-seccomp')
        self.assertEqual(resolve_sandbox_backend('Darwin'), 'seatbelt-app-sandbox')
        self.assertEqual(resolve_sandbox_backend('Windows'), 'job-object-restricted-token')

    def test_policy_validation(self):
        with self.assertRaises(ValueError):
            SandboxPolicy(package_name='invalid', data_dir='/a', cache_dir='/b')
        with self.assertRaises(ValueError):
            SandboxPolicy(package_name='org.shiraz.app', data_dir='/a\nb', cache_dir='/b')
        with self.assertRaises(ValueError):
            SandboxPolicy(
                package_name='org.shiraz.app', data_dir='/a', cache_dir='/b', memory_limit_mb=0
            )
        with self.assertRaises(ValueError):
            SandboxPolicy(
                package_name='org.shiraz.app', data_dir='/a', cache_dir='/b', max_processes=0
            )

    def test_linux_landlock_plan(self):
        plan = sandbox_plan(self._sample_policy(allow_network=False), system='Linux')
        self.assertEqual(plan['backend'], 'landlock-seccomp')
        self.assertTrue(plan['no_new_privs'])
        self.assertTrue(plan['seccomp_deny_network'])
        self.assertIn('/opt/shiraz/runtime', plan['read_only_paths'])
        self.assertIn('/tmp/shiraz/data/org.shiraz.sample', plan['read_write_paths'])
        self.assertEqual(plan['rlimit_as_bytes'], 1024 * 1024 * 1024)
        self.assertEqual(plan['rlimit_nproc'], 16)
        self.assertEqual(
            build_linux_landlock_policy(self._sample_policy(allow_network=True))[
                'seccomp_deny_network'
            ],
            False,
        )

    def test_macos_seatbelt_and_entitlements_plan(self):
        policy = self._sample_policy(allow_network=False, allow_jit=True)
        plan = sandbox_plan(policy, system='Darwin')
        self.assertEqual(plan['backend'], 'seatbelt-app-sandbox')
        entitlements = plan['entitlements']
        self.assertTrue(entitlements['com.apple.security.app-sandbox'])
        self.assertTrue(entitlements['com.apple.security.cs.allow-jit'])
        self.assertNotIn('com.apple.security.network.client', entitlements)

        net_entitlements = build_macos_entitlements(
            self._sample_policy(allow_network=True), disable_library_validation=True
        )
        self.assertTrue(net_entitlements['com.apple.security.network.client'])
        self.assertTrue(net_entitlements['com.apple.security.cs.disable-library-validation'])

        profile = build_macos_seatbelt_profile(policy)
        self.assertIn('(version 1)', profile)
        self.assertIn('(deny default)', profile)
        self.assertIn('(deny network*)', profile)
        self.assertIn('(subpath "/tmp/shiraz/data/org.shiraz.sample")', profile)
        self.assertIn('(subpath "/opt/shiraz/runtime")', profile)

        wrapped = wrap_macos_seatbelt_command(['/bin/echo', 'hi'], '/tmp/app.sb')
        self.assertEqual(wrapped, ['/usr/bin/sandbox-exec', '-f', '/tmp/app.sb', '/bin/echo', 'hi'])

    def test_windows_job_object_and_restricted_token_plan(self):
        policy = self._sample_policy(allow_network=True)
        plan = sandbox_plan(policy, system='Windows')
        self.assertEqual(plan['backend'], 'job-object-restricted-token')
        self.assertTrue(plan['job_object']['kill_on_job_close'])
        self.assertEqual(plan['job_object']['process_memory_limit_bytes'], 1024 * 1024 * 1024)
        self.assertEqual(plan['job_object']['active_process_limit'], 16)
        self.assertTrue(plan['restricted_token']['disable_max_privilege'])
        self.assertEqual(plan, build_windows_sandbox_config(policy))

    def test_prepare_sandbox_launch_all_three_oses(self):
        policy = self._sample_policy(allow_network=False)
        with tempfile.TemporaryDirectory(prefix='shiraz-sb-prep-') as tmp:
            root = Path(tmp)
            linux = prepare_sandbox_launch(
                policy, ['/bin/echo', 'ok'], root / 'linux', system='Linux'
            )
            self.assertEqual(linux.backend, 'landlock-seccomp')
            self.assertTrue(linux.plan_path.is_file())
            self.assertIn('sandbox_exec.py', linux.command[1])
            self.assertEqual(linux.command[-3:], ['--', '/bin/echo', 'ok'])
            self.assertEqual(linux.env_updates['SHIRAZ_SANDBOX_NETWORK'], '0')

            darwin = prepare_sandbox_launch(
                policy, ['/bin/echo', 'ok'], root / 'darwin', system='Darwin'
            )
            self.assertEqual(darwin.backend, 'seatbelt-app-sandbox')
            self.assertTrue(darwin.profile_path and darwin.profile_path.is_file())
            self.assertEqual(
                darwin.command,
                ['/usr/bin/sandbox-exec', '-f', str(darwin.profile_path), '/bin/echo', 'ok'],
            )

            windows = prepare_sandbox_launch(
                policy, ['cmd.exe', '/c', 'echo', 'ok'], root / 'windows', system='Windows'
            )
            self.assertEqual(windows.backend, 'job-object-restricted-token')
            self.assertEqual(windows.command, ['cmd.exe', '/c', 'echo', 'ok'])
            self.assertIsNotNone(windows.process_observer)

    @unittest.skipUnless(
        sys.platform == 'darwin' and shutil.which('sandbox-exec'),
        'macOS sandbox-exec enforcement test',
    )
    def test_macos_sandbox_exec_enforces_write_isolation(self):
        with tempfile.TemporaryDirectory(prefix='shiraz-sb-') as tmp:
            root = Path(tmp).resolve()
            data_dir = root / 'data'
            cache_dir = root / 'cache'
            outside_dir = root / 'outside'
            work_dir = root / 'work'
            data_dir.mkdir()
            cache_dir.mkdir()
            outside_dir.mkdir()
            work_dir.mkdir()

            policy = SandboxPolicy(
                package_name='org.shiraz.sbtest',
                data_dir=str(data_dir),
                cache_dir=str(cache_dir),
                read_only_paths=('/bin', '/usr/bin'),
                allow_network=False,
            )
            allowed_file = data_dir / 'ok.txt'
            launch_ok = prepare_sandbox_launch(
                policy, ['/bin/sh', '-c', f'echo ok > "{allowed_file}"'], work_dir
            )
            ok = subprocess.run(launch_ok.command, capture_output=True, text=True)
            self.assertEqual(ok.returncode, 0, ok.stderr)
            self.assertEqual(allowed_file.read_text(encoding='utf-8').strip(), 'ok')

            blocked_file = (
                Path('/private/var/tmp/shiraz-sb-blocked.txt')
                if str(outside_dir).startswith(('/tmp/', '/private/tmp/'))
                else outside_dir / 'nope.txt'
            )
            launch_blocked = prepare_sandbox_launch(
                policy, ['/bin/sh', '-c', f'echo nope > "{blocked_file}"'], work_dir
            )
            blocked = subprocess.run(launch_blocked.command, capture_output=True, text=True)
            self.assertNotEqual(blocked.returncode, 0)
            self.assertFalse(blocked_file.exists())

    @unittest.skipUnless(sys.platform == 'linux', 'Linux Landlock & seccomp enforcement test')
    def test_linux_sandbox_exec_enforces_landlock_and_no_network(self):
        from shiraz import sandbox_exec

        if sandbox_exec._landlock_abi_version(sandbox_exec._libc()) <= 0:
            self.skipTest('Kernel does not expose Landlock LSM')

        with tempfile.TemporaryDirectory(prefix='shiraz-sb-linux-') as tmp:
            root = Path(tmp).resolve()
            data_dir = root / 'data'
            cache_dir = root / 'cache'
            work_dir = root / 'work'
            outside_dir = root / 'outside'
            for d in (data_dir, cache_dir, work_dir, outside_dir):
                d.mkdir()

            # Build a policy whose writable paths exclude outside_dir and /tmp.
            ro_paths = tuple(
                dict.fromkeys(
                    [
                        '/bin',
                        '/etc',
                        sys.prefix,
                        sys.base_prefix,
                        sys.exec_prefix,
                        str(Path(sys.executable).parent),
                        str(Path(sys.executable).resolve().parent),
                    ]
                )
            )
            policy = SandboxPolicy(
                package_name='org.shiraz.linuxsb',
                data_dir=str(data_dir),
                cache_dir=str(cache_dir),
                read_only_paths=ro_paths,
                allow_network=False,
            )
            launch = prepare_sandbox_launch(policy, [sys.executable, '-c', 'pass'], work_dir)
            # Strip '/tmp' from read_write_paths so outside_dir under /tmp is strictly blocked.
            plan_data = sandbox_plan(policy, system='Linux')
            plan_data['read_write_paths'] = [str(data_dir), str(cache_dir)]
            launch.plan_path.write_text(
                __import__('json').dumps(plan_data, indent=2) + '\n', encoding='utf-8'
            )

            probe = (
                'import errno, socket, sys\n'
                'from pathlib import Path\n'
                f'Path({str(data_dir / "ok.txt")!r}).write_text("ok")\n'
                'try:\n'
                f'    Path({str(outside_dir / "blocked.txt")!r}).write_text("no")\n'
                '    sys.exit(10)\n'
                'except PermissionError:\n'
                '    pass\n'
                'try:\n'
                '    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)\n'
                '    s.close()\n'
                '    sys.exit(11)\n'
                'except OSError as exc:\n'
                '    if exc.errno != errno.ENETUNREACH:\n'
                '        sys.exit(12)\n'
            )
            res = subprocess.run(
                [
                    sys.executable,
                    str(Path(sandbox_exec.__file__)),
                    str(launch.plan_path),
                    '--',
                    sys.executable,
                    '-c',
                    probe,
                ],
                capture_output=True,
                text=True,
            )
            self.assertEqual(res.returncode, 0, res.stderr)
            self.assertEqual((data_dir / 'ok.txt').read_text(encoding='utf-8'), 'ok')
            self.assertFalse((outside_dir / 'blocked.txt').exists())

    @unittest.skipUnless(sys.platform == 'win32', 'Windows Win32 Job Object enforcement test')
    def test_windows_job_object_attaches_live_child_process(self):
        policy = self._sample_policy(allow_network=True)
        config = build_windows_sandbox_config(policy)
        observer = windows_sandbox_observer(config)
        proc = subprocess.Popen(
            [sys.executable, '-c', 'import time; time.sleep(0.5)'],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        try:
            with observer(proc) as job_handle:
                self.assertNotEqual(job_handle, 0)
                self.assertTrue(is_process_in_windows_job(proc, job_handle))
                proc.wait(timeout=10)
        finally:
            if proc.poll() is None:
                proc.kill()
                proc.wait()


if __name__ == '__main__':
    unittest.main()
