# SPDX-License-Identifier: GPL-3.0-only
import hashlib
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('bootstrap_runtime', Path(__file__).resolve().parents[1] / 'scripts/bootstrap_runtime.py')
bootstrap = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bootstrap)


class DependencyPatchTests(unittest.TestCase):
    def test_incremental_patches_and_tamper_rejection(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            repo = root / 'context/dependency'
            repo.mkdir(parents=True)
            patches = root / 'patches'
            patches.mkdir()
            def git(*args):
                return subprocess.check_output(['git', *args], cwd=repo, stderr=subprocess.PIPE)
            git('init', '-q')
            for name in ('first', 'second', 'unrelated'):
                (repo / name).write_text('original\n')
            git('add', '.')
            git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@localhost', 'commit', '-qm', 'fixture')
            revision = git('rev-parse', 'HEAD').decode().strip()
            recipes = []
            for name in ('first', 'second'):
                path = repo / name
                path.write_text('patched\n')
                (patches / (name + '.patch')).write_bytes(git('diff', '--', name))
                recipes.append((name + '.patch', {name: hashlib.sha256(path.read_bytes()).hexdigest()}))
                path.write_text('original\n')
            with patch.object(bootstrap, 'ROOT', root), patch.object(bootstrap, 'SOURCES', {'dependency': ('unused', revision)}), patch.object(bootstrap, 'PATCHES', {'dependency': recipes[:1]}):
                bootstrap.sources()
                self.assertEqual((repo / 'first').read_text(), 'patched\n')
                # A newly prepended independent patch must also upgrade an old tree.
                with patch.object(bootstrap, 'PATCHES', {'dependency': list(reversed(recipes))}):
                    bootstrap.sources()
                    bootstrap.sources()  # Rebuilding known patched sources is allowed.
                    self.assertEqual((repo / 'second').read_text(), 'patched\n')
                    (repo / 'second').write_text('tampered\n')
                    with self.assertRaisesRegex(SystemExit, 'partially applied'):
                        bootstrap.sources()
                    (repo / 'second').write_text('patched\n')
                    (repo / 'unrelated').write_text('modified\n')
                    with self.assertRaisesRegex(SystemExit, 'unrecognized tracked changes'):
                        bootstrap.sources()

    def test_overlapping_patch_snapshots(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            repo = root / 'context/dependency'
            repo.mkdir(parents=True)
            (root / 'patches').mkdir()
            def git(*args):
                return subprocess.check_output(['git', *args], cwd=repo, stderr=subprocess.PIPE)
            git('init', '-q')
            for name in ('shared', 'header', 'independent'):
                (repo / name).write_text('original\n')
            git('add', '.')
            git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@localhost', 'commit', '-qm', 'base')
            revision = git('rev-parse', 'HEAD').decode().strip()
            recipes = []
            for step in ('one', 'two'):
                for name in ('shared', 'header'):
                    (repo / name).write_text(step + '\n')
                (root / 'patches' / (step + '.patch')).write_bytes(git('diff'))
                recipes.append((step + '.patch', {name: hashlib.sha256((repo / name).read_bytes()).hexdigest()
                                                 for name in ('shared', 'header')}))
                git('add', '.')
                git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@localhost', 'commit', '-qm', step)
            git('reset', '--hard', revision)
            with patch.object(bootstrap, 'ROOT', root), patch.object(bootstrap, 'SOURCES', {'dependency': ('unused', revision)}), patch.object(bootstrap, 'PATCHES', {'dependency': recipes}):
                bootstrap.sources()  # Fresh base applies both overlapping deltas.
                self.assertEqual((repo / 'shared').read_text(), 'two\n')
                with patch.object(bootstrap, 'run', side_effect=AssertionError('Final tree must not apply patches')):
                    bootstrap.sources()
                git('reset', '--hard', revision)
                with patch.object(bootstrap, 'PATCHES', {'dependency': recipes[:1]}):
                    bootstrap.sources()
                bootstrap.sources()  # Known earlier complete snapshot upgrades.
                self.assertEqual((repo / 'header').read_text(), 'two\n')
                for invalid in ('tampered\n', 'one\n', 'original\n'):
                    (repo / 'shared').write_text(invalid)
                    with self.assertRaisesRegex(SystemExit, 'partially applied'):
                        bootstrap.sources()
                    self.assertEqual((repo / 'header').read_text(), 'two\n')
                (repo / 'shared').write_text('two\n')
                (repo / 'independent').write_text('tampered\n')
                with self.assertRaisesRegex(SystemExit, 'unrecognized tracked changes'):
                    bootstrap.sources()
                git('reset', '--hard', revision)
                git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@localhost', 'commit', '--allow-empty', '-qm', 'wrong pin')
                with self.assertRaisesRegex(SystemExit, 'revision differs'):
                    bootstrap.sources()
