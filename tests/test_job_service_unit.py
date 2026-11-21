# SPDX-License-Identifier: GPL-3.0-only
import subprocess
import sys
import unittest

from shiraz.apk import FormatError
from shiraz.job_service_unit import render


class JobServiceUnitTests(unittest.TestCase):
    def test_paths_are_literal_and_no_nested_supervisor(self):
        text = render(
            '/opt/Shiraz %i $HOME',
            '/opt/python "special"/bin/python',
            '/var/tmp/jobs %n ${OTHER}\\state',
        )
        self.assertIn('Environment="PYTHONPATH=/opt/Shiraz %%i $HOME/src"', text)
        self.assertIn(
            'ExecStart=:"/opt/python \\"special\\"/bin/python" -m shiraz serve-jobs --registry "/var/tmp/jobs %%n ${OTHER}\\\\state"',
            text,
        )
        self.assertNotIn('supervise-jobs', text)
        self.assertIn('KillMode=mixed\n', text)
        self.assertIn('WantedBy=default.target\n', text)

    def test_rejects_directive_injection_and_ambiguous_paths(self):
        for value in (
            'relative',
            '/tmp/../other',
            '/tmp/a\nExecStart=evil',
            '/tmp/\x00bad',
            '/tmp/\tbad',
        ):
            for index in range(3):
                paths = ['/checkout', '/bin/python', '/registry']
                paths[index] = value
                with self.assertRaises(FormatError):
                    render(*paths)
        with self.assertRaises(FormatError):
            render('/checkout:other', '/bin/python', '/registry')

    def test_cli_only_emits_unit(self):
        result = subprocess.run(
            [
                sys.executable,
                '-m',
                'shiraz',
                'job-service-unit',
                '--checkout',
                '/opt/shiraz',
                '--python',
                '/opt/shiraz/.venv/bin/python',
                '--registry',
                '/var/tmp/shiraz-registry',
            ],
            capture_output=True,
            text=True,
            check=True,
            timeout=10,
        )
        self.assertEqual(
            result.stdout,
            render('/opt/shiraz', '/opt/shiraz/.venv/bin/python', '/var/tmp/shiraz-registry'),
        )
        self.assertEqual(result.stderr, '')
