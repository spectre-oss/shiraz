# SPDX-License-Identifier: GPL-3.0-only
import io
import os
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import patch

from shiraz import cli


class CliRunTests(unittest.TestCase):
    def test_registered_run_is_pinned_supervised_and_preserves_options(self):
        report = dict(apk_sha256='a' * 64, manifest=dict(package='test.app'))
        with (
            patch.object(cli, 'inspect_apk', return_value=report),
            patch('shiraz.host_session.HostSession') as factory,
            patch.object(cli, 'run_activity') as direct,
            patch.dict(os.environ, SHIRAZ_GUEST_LOADER='1'),
            redirect_stdout(io.StringIO()),
        ):
            host = factory.return_value.__enter__.return_value
            host.wait.return_value = dict(apps=[dict(exit_status=0), dict(exit_status=3)])
            code = cli.main(
                [
                    'run',
                    'fixture.apk',
                    '--registry',
                    'build/registry',
                    '--width',
                    '720',
                    '--height',
                    '1280',
                    '--frames',
                    '10',
                    '--timeout',
                    '15',
                    '--snapshot',
                    'build/frame.ppm',
                    '--device-profile',
                    'config/device.json',
                ]
            )
            self.assertEqual(code, 1, 'Secondary failure must affect CLI exit')
            direct.assert_not_called()
            self.assertEqual(host.start.call_args.args, ('test.app',))
            self.assertEqual(host.start.call_args.kwargs['expected_sha256'], 'a' * 64)
            settings = host.start.call_args.kwargs['settings']
            self.assertTrue(settings['native_code'])
            self.assertEqual(settings['frames'], 10)
            self.assertEqual((settings['width'], settings['height']), (720, 1280))
            self.assertEqual(settings['snapshot'], str(Path('build/frame.ppm').resolve()))
            self.assertEqual(settings['device_profile'], str(Path('config/device.json').resolve()))
            host.wait.assert_called_once_with(15)

    def test_registered_run_rejects_invalid_native_mode_before_admission(self):
        with (
            patch.object(cli, 'inspect_apk', return_value={}),
            patch('shiraz.host_session.HostSession') as host,
            patch.dict(os.environ, SHIRAZ_GUEST_LOADER='invalid'),
            redirect_stderr(io.StringIO()),
        ):
            self.assertEqual(cli.main(['run', 'fixture.apk', '--registry', 'build/registry']), 1)
            host.assert_not_called()

    def test_expected_termination_is_success_for_both_supervised_commands(self):
        for command in (
            ['run', 'fixture.apk', '--registry', 'build/registry'],
            ['run-session', '--registry', 'build/registry', 'test.app'],
        ):
            for status, reason, expected in (
                (-9, 'package_request', 0),
                (-9, None, 1),
                (-11, 'package_request', 1),
                (3, None, 1),
            ):
                with (
                    self.subTest(command=command[0], status=status, reason=reason),
                    patch.object(
                        cli,
                        'inspect_apk',
                        return_value=dict(apk_sha256='a' * 64, manifest=dict(package='test.app')),
                    ),
                    patch('shiraz.host_session.HostSession') as factory,
                    redirect_stdout(io.StringIO()),
                ):
                    factory.return_value.__enter__.return_value.wait.return_value = dict(
                        apps=[
                            dict(exit_status=status, termination_reason=reason),
                            dict(exit_status=0),
                        ]
                    )
                    self.assertEqual(cli.main(command), expected)

    def test_unregistered_run_keeps_direct_execution_with_default_sandbox(self):
        with (
            patch.object(cli, 'inspect_apk', return_value={}),
            patch.object(cli, 'run_activity', return_value=7) as direct,
            patch('shiraz.host_session.HostSession') as host,
        ):
            self.assertEqual(cli.main(['run', 'fixture.apk', '--frames', '2']), 7)
            self.assertEqual(direct.call_args.kwargs['frames'], 2)
            self.assertTrue(direct.call_args.kwargs['sandbox'])
            self.assertTrue(direct.call_args.kwargs['allow_network'])
            host.assert_not_called()

    def test_cli_sandbox_opt_out_and_no_network_flags(self):
        report = dict(apk_sha256='a' * 64, manifest=dict(package='test.app'))
        with (
            patch.object(cli, 'inspect_apk', return_value=report),
            patch.object(cli, 'run_activity', return_value=0) as direct,
        ):
            self.assertEqual(cli.main(['run', 'fixture.apk', '--no-sandbox']), 0)
            self.assertFalse(direct.call_args.kwargs['sandbox'])
            self.assertTrue(direct.call_args.kwargs['allow_network'])
            self.assertEqual(cli.main(['run', 'fixture.apk', '--no-network']), 0)
            self.assertTrue(direct.call_args.kwargs['sandbox'])
            self.assertFalse(direct.call_args.kwargs['allow_network'])
        with (
            patch.object(cli, 'inspect_apk', return_value=report),
            patch.object(cli, 'run_activity', return_value=0) as direct,
            redirect_stderr(io.StringIO()),
        ):
            self.assertEqual(cli.main(['run', 'fixture.apk', '--no-sandbox', '--no-network']), 1)
            self.assertEqual(cli.main(['run', 'fixture.apk', '--no-sandbox', '--sandbox']), 1)
            direct.assert_not_called()
        with (
            patch.object(cli, 'inspect_apk', return_value=report),
            patch('shiraz.host_session.HostSession') as factory,
            redirect_stdout(io.StringIO()),
        ):
            host = factory.return_value.__enter__.return_value
            host.wait.return_value = dict(apps=[dict(exit_status=0)])
            self.assertEqual(
                cli.main(['run', 'fixture.apk', '--registry', 'build/registry', '--sandbox']), 0
            )
            settings = host.start.call_args.kwargs['settings']
            self.assertTrue(settings['sandbox'])
            self.assertTrue(settings['allow_network'])
