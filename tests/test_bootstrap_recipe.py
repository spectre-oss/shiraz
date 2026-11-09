# SPDX-License-Identifier: GPL-3.0-only
"""Real patch metadata and local repository transitions; no dependency downloads."""
import hashlib
import subprocess
import tempfile
import unittest
from contextlib import contextmanager
from pathlib import Path
from unittest.mock import patch

from test_bootstrap import bootstrap


def digest(value):
    return hashlib.sha256(value).hexdigest()


class PatchRecipeTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='patch recipe ')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.repo = self.root / 'context/dependency'
        self.repo.mkdir(parents=True)
        (self.root / 'patches').mkdir()
        self.git('init', '-q')
        for name in ('chain', 'other file'):
            (self.repo / name).write_text('original\n')
        self.git('add', '.')
        self.git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid',
                 'commit', '-qm', 'fixture')
        self.revision = self.git('rev-parse', 'HEAD').decode().strip()

    def git(self, *arguments):
        return subprocess.check_output(['git', *arguments], cwd=self.repo,
                                       stderr=subprocess.PIPE)

    def forward(self, name='forward.patch', target='chain'):
        path = self.repo / target
        path.write_text('legacy\n')
        (self.root / 'patches' / name).write_bytes(self.git('diff', '--', target))
        path.write_text('original\n')
        return name, {target: digest(b'legacy\n')}

    @contextmanager
    def recipes(self, recipes, sources=None):
        with patch.object(bootstrap, 'ROOT', self.root), \
                patch.object(bootstrap, 'SOURCES', sources or {'dependency': ('unused', self.revision)}), \
                patch.object(bootstrap, 'PATCHES', {'dependency': recipes}), \
                patch.object(bootstrap, 'PATCHES_DARWIN', {}):
            yield

    def test_duplicate_recipe_rejected_before_any_clone_or_mutation(self):
        recipe = self.forward()
        sources = {'not-created': ('unused', self.revision),
                   'dependency': ('unused', self.revision)}
        with self.recipes([recipe, recipe], sources), \
                patch.object(bootstrap, 'run', side_effect=AssertionError('must preflight before mutation')), \
            self.assertRaisesRegex(SystemExit, 'duplicate dependency patch name'):
            bootstrap.sources()
        self.assertFalse((self.root / 'context/not-created').exists())
        self.assertEqual((self.repo / 'chain').read_bytes(), b'original\n')

    def test_declared_targets_cannot_hide_a_different_or_extra_file(self):
        recipe = self.forward()
        wrong = (recipe[0], {'other file': digest(b'legacy\n')})
        with self.recipes([wrong]), patch.object(bootstrap, 'run', side_effect=AssertionError('no mutation')), \
            self.assertRaisesRegex(SystemExit, 'targets differ from recipe'):
            bootstrap.sources()
        (self.repo / 'chain').write_text('legacy\n')
        (self.repo / 'other file').write_text('legacy\n')
        (self.root / 'patches/extra.patch').write_bytes(self.git('diff'))
        self.git('checkout', '--', '.')
        with self.recipes([('extra.patch', {'chain': digest(b'legacy\n')})]), \
                patch.object(bootstrap, 'run', side_effect=AssertionError('no mutation')), \
            self.assertRaisesRegex(SystemExit, 'targets differ from recipe'):
            bootstrap.sources()
        self.assertEqual((self.repo / 'chain').read_bytes(), b'original\n')
        self.assertEqual((self.repo / 'other file').read_bytes(), b'original\n')

    def test_missing_malformed_and_unsafe_patches_fail_without_source_changes(self):
        (self.root / 'patches/broken.patch').write_text('not a patch\n')
        cases = [('missing.patch', {'chain': digest(b'legacy\n')}, 'missing'),
                 ('broken.patch', {'chain': digest(b'legacy\n')}, 'malformed'),
                 ('../escape.patch', {'chain': digest(b'legacy\n')}, 'unsafe')]
        for name, files, reason in cases:
            with self.subTest(name=name), self.recipes([(name, files)]), \
                    patch.object(bootstrap, 'run', side_effect=AssertionError('no mutation')), \
                self.assertRaisesRegex(SystemExit, reason):
                bootstrap.sources()
        self.assertEqual((self.repo / 'chain').read_bytes(), b'original\n')

    def test_space_in_target_path_is_parsed_without_shell_or_quote_loss(self):
        recipe = self.forward(target='other file')
        with self.recipes([recipe]):
            bootstrap.sources()
            self.assertEqual((self.repo / 'other file').read_bytes(), b'legacy\n')
            with patch.object(bootstrap, 'run', side_effect=AssertionError('already final')):
                bootstrap.sources()

    def test_legacy_reset_skips_fresh_tree_migrates_known_tree_and_refuses_unknown(self):
        forward = self.forward()
        path = self.repo / 'chain'
        path.write_text('legacy\n')
        (self.root / 'patches/reset.patch').write_bytes(self.git('diff', '-R', '--', 'chain'))
        path.write_text('original\n')
        reset = ('reset.patch', {'chain': digest(b'original\n')})
        with self.recipes([forward, reset]):
            with patch.object(bootstrap, 'run', side_effect=AssertionError('fresh is already final')):
                bootstrap.sources()
            path.write_text('legacy\n')
            bootstrap.sources()
            self.assertEqual(path.read_bytes(), b'original\n')
            with patch.object(bootstrap, 'run', side_effect=AssertionError('final is idempotent')):
                bootstrap.sources()
            path.write_text('unknown private edit\n')
            with self.assertRaisesRegex(SystemExit, 'modified or partially applied'):
                bootstrap.sources()
            self.assertEqual(path.read_bytes(), b'unknown private edit\n')

    def test_source_archive_inside_another_checkout_has_no_git_prefix_filter(self):
        recipe = self.forward()
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        archive = self.root / 'nested source archive'
        (archive / 'patches').mkdir(parents=True)
        (archive / 'patches' / recipe[0]).write_bytes((self.root / 'patches' / recipe[0]).read_bytes())
        with patch.object(bootstrap, 'ROOT', archive):
            bootstrap.validate_patch_recipes([recipe])
        self.assertEqual((self.repo / 'chain').read_bytes(), b'original\n')
