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
    resolve_sandbox_backend,
    sandbox_plan,
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
            data_dir.mkdir()
            cache_dir.mkdir()
            outside_dir.mkdir()

            policy = SandboxPolicy(
                package_name='org.shiraz.sbtest',
                data_dir=str(data_dir),
                cache_dir=str(cache_dir),
                read_only_paths=('/bin', '/usr/bin'),
                allow_network=False,
            )
            profile = build_macos_seatbelt_profile(policy)
            profile_path = root / 'policy.sb'
            profile_path.write_text(profile, encoding='utf-8')

            allowed_file = data_dir / 'ok.txt'
            ok = subprocess.run(
                wrap_macos_seatbelt_command(
                    ['/bin/sh', '-c', f'echo ok > "{allowed_file}"'], str(profile_path)
                ),
                capture_output=True,
                text=True,
            )
            self.assertEqual(ok.returncode, 0, ok.stderr)
            self.assertEqual(allowed_file.read_text(encoding='utf-8').strip(), 'ok')

            blocked_file = (
                Path('/private/var/tmp/shiraz-sb-blocked.txt')
                if str(outside_dir).startswith(('/tmp/', '/private/tmp/'))
                else outside_dir / 'nope.txt'
            )
            blocked = subprocess.run(
                wrap_macos_seatbelt_command(
                    ['/bin/sh', '-c', f'echo nope > "{blocked_file}"'], str(profile_path)
                ),
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(blocked.returncode, 0)
            self.assertFalse(blocked_file.exists())


if __name__ == '__main__':
    unittest.main()
