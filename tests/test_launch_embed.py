# SPDX-License-Identifier: GPL-3.0-only
import json
import os
import socket
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from shiraz import app_worker
from shiraz.apk import FormatError
from shiraz.launch_settings import normalize_settings


class LaunchEmbedTests(unittest.TestCase):
    def setUp(self):
        self.root = Path(os.path.realpath(tempfile.mkdtemp()))
        self.directory = self.root / 'embed'
        self.directory.mkdir(mode=0o700)
        self.input = self.directory / 'input'
        self.frame = self.directory / 'frame'
        for path in (self.input, self.frame):
            path.touch(mode=0o600)

    def tearDown(self):
        for path in sorted(self.root.rglob('*'), reverse=True):
            path.unlink() if not path.is_dir() or path.is_symlink() else path.rmdir()
        self.root.rmdir()

    def embed(self, **changes):
        return dict(input=str(self.input), frame=str(self.frame), **changes)

    def test_default_is_not_embedded_and_private_files_are_accepted(self):
        self.assertIsNone(normalize_settings()['embed'])
        self.assertEqual(normalize_settings(dict(embed=self.embed()))['embed'], self.embed())

    def test_rejects_malformed_or_unsafe_paths(self):
        other = self.root / 'other'
        other.mkdir(mode=0o700)
        (other / 'frame').touch(mode=0o600)
        link = self.root / 'link'
        link.symlink_to(self.directory)
        cases = [
            'path',
            {'input': str(self.input)},
            self.embed(extra='x'),
            dict(input='input', frame=str(self.frame)),
            dict(input=str(self.input), frame=str(self.input)),
            dict(input=str(self.input), frame=str(other / 'frame')),
            dict(input=str(self.directory / 'missing'), frame=str(self.frame)),
            dict(input=str(link / 'input'), frame=str(link / 'frame')),
            dict(input=str(self.directory / '..' / 'embed' / 'input'), frame=str(self.frame)),
        ]
        for embed in cases:
            with self.subTest(embed=embed), self.assertRaises(FormatError):
                normalize_settings(dict(embed=embed))

    def test_rejects_shared_directory_or_files(self):
        self.directory.chmod(0o755)
        with self.assertRaises(FormatError):
            normalize_settings(dict(embed=self.embed()))
        self.directory.chmod(0o700)
        self.frame.chmod(0o644)
        with self.assertRaises(FormatError):
            normalize_settings(dict(embed=self.embed()))
        self.frame.chmod(0o600)
        os.link(self.frame, self.root / 'hardlink')
        with self.assertRaises(FormatError):
            normalize_settings(dict(embed=self.embed()))

    def test_worker_sets_embed_environment_for_its_launch_only(self):
        report = dict(apk_sha256='a' * 64, manifest=dict(package='test.app'))
        for embed in (self.embed(), None):
            left, right = socket.socketpair()
            with left, right, patch.dict(os.environ, {}, clear=False):
                os.environ.pop('SHIRAZ_LIVE_INPUT', None)
                os.environ.pop('SHIRAZ_EMBED_FRAME', None)
                arguments = [
                    'worker',
                    '/registry',
                    'test.app',
                    'a' * 64,
                    str(os.dup(left.fileno())),
                    json.dumps(dict(embed=embed)),
                ]
                with (
                    patch.object(app_worker.sys, 'argv', arguments),
                    patch.object(app_worker, 'PackageRegistry') as registry,
                    patch.object(app_worker, 'inspect_apk', return_value=report),
                    patch.object(app_worker, 'run_activity', return_value=0) as run,
                ):
                    registry.return_value.launch_apk.return_value = Path('/verified.apk')
                    self.assertEqual(app_worker.main(), 0)
                    self.assertNotIn('embed', run.call_args.kwargs)
                    if embed is None:
                        self.assertNotIn('SHIRAZ_LIVE_INPUT', os.environ)
                        self.assertNotIn('SHIRAZ_EMBED_FRAME', os.environ)
                    else:
                        self.assertEqual(os.environ['SHIRAZ_LIVE_INPUT'], str(self.input))
                        self.assertEqual(os.environ['SHIRAZ_EMBED_FRAME'], str(self.frame))


if __name__ == '__main__':
    unittest.main()
