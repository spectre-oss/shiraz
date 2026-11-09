# SPDX-License-Identifier: GPL-3.0-only
"""Pinned-source replay is independent of mutable dependency working files."""
import hashlib
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import check_bootstrap_patches as checker


class BootstrapReplayTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='bootstrap replay ')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.repo = self.root / 'context' / 'fixture'
        self.repo.mkdir(parents=True)
        self.patches = self.root / 'patches'
        self.patches.mkdir()
        self.work = self.root / 'build'
        self.work.mkdir()
        self.git('init', '-q')
        self.file = self.repo / 'source'
        self.file.write_bytes(b'original\n')
        self.git('add', '.')
        self.git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid',
                 '-c', 'commit.gpgsign=false', 'commit', '-qm', 'fixture')
        self.revision = self.git('rev-parse', 'HEAD').decode().strip()
        self.file.write_bytes(b'legacy\n')
        (self.patches / 'forward.patch').write_bytes(self.git('diff'))
        (self.patches / 'reset.patch').write_bytes(self.git('diff', '-R'))
        self.file.write_bytes(b'original\n')
        self.forward = ('forward.patch', {'source': hashlib.sha256(b'legacy\n').hexdigest()})
        self.reset = ('reset.patch', {'source': hashlib.sha256(b'original\n').hexdigest()})

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.repo, stderr=subprocess.PIPE)

    def replay(self, recipes):
        with patch.object(checker.bootstrap, 'ROOT', self.root):
            return checker.replay(self.repo, self.revision, recipes, self.patches, self.work)

    def test_uses_pinned_blob_without_touching_private_working_changes(self):
        self.file.write_bytes(b'private working edit\n')
        before = self.git('diff')
        self.assertEqual(self.replay([self.forward]), (1, 1))
        self.assertEqual(self.git('diff'), before)
        self.assertEqual(self.file.read_bytes(), b'private working edit\n')

    def test_original_hash_cannot_masquerade_as_a_patch_postimage(self):
        wrong = (self.forward[0], self.reset[1])
        with self.assertRaisesRegex(ValueError, 'postimage differs'):
            self.replay([wrong])
        self.assertEqual(self.file.read_bytes(), b'original\n')

    def test_legacy_reset_replays_and_checks_both_intermediate_snapshots(self):
        self.assertEqual(self.replay([self.forward, self.reset]), (2, 1))
        with self.assertRaisesRegex(ValueError, 'postimage differs'):
            self.replay([self.forward, (self.reset[0], self.forward[1])])
        self.assertEqual(self.file.read_bytes(), b'original\n')
