# SPDX-License-Identifier: GPL-3.0-only
import importlib.util
from pathlib import Path
import sys
import unittest


def load(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).resolve().parents[1] / 'scripts' / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


version = load('version')
guard = load('commit_guard')
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
ci_version = load('ci_version')


class WorkflowTests(unittest.TestCase):
    def test_conventional_versions(self):
        for message, expected in [('feat(ui): add surfaces', '1.3.0'), ('fix: repair input', '1.2.4'),
                                  ('docs: clarify setup', '1.2.4'), ('feat!: break ABI', '2.0.0'),
                                  ('refactor: change ABI\n\nBREAKING CHANGE: new ABI', '2.0.0')]:
            self.assertEqual(version.bump('1.2.3', message), expected)
        with self.assertRaises(ValueError):
            version.bump('1.2.3', 'random commit')

    def test_ci_version_plan(self):
        self.assertEqual(ci_version.planned_version('1.2.3', '1.2.3', 'feat(web): update builder'), ('1.3.0', True))
        self.assertEqual(ci_version.planned_version('1.2.3', '1.2.4', 'docs: clarify setup'), ('1.2.4', False))
        self.assertEqual(ci_version.planned_version('1.2.3', '1.2.3', 'docs: clarify setup'), ('1.2.4', True))
        self.assertEqual(ci_version.planned_version(None, '1.2.3', 'chore: initial import'), ('1.2.3', False))
        with self.assertRaises(ValueError):
            ci_version.planned_version('1.2.3', '1.2.9', 'fix: mismatch')

    def test_guards(self):
        for path in ['context/osmand/file.cpp', 'build/a', 'app.apk', 'key.pem', 'secrets/value', '.env.local', 'docs/private.md']:
            self.assertIsNotNone(guard.unsafe(path, b'ordinary text'))
        self.assertIsNotNone(guard.unsafe('native/main.cpp', b'\x7fELF\0'))
        self.assertIsNotNone(guard.unsafe('config.txt', b'password=' + b'a' * 32))
        self.assertIsNotNone(guard.unsafe('link', b'target', '120000'))
        self.assertIsNone(guard.unsafe('native/main.cpp', b'int main() { return 0; }'))

    def test_public_docs_keep_content_protection(self):
        for path in guard.PUBLIC_DOCS:
            self.assertIsNone(guard.unsafe(path, b'# Public guide'))
            self.assertIsNotNone(guard.unsafe(path, b'password=' + b'a' * 32))
            self.assertIsNotNone(guard.unsafe(path, b'\x00'))
            self.assertIsNotNone(guard.unsafe(path, b'target', '120000'))
        for path in ['docs/jobs/1.md', 'docs/others/private.md', 'docs/new-guide.md']:
            self.assertIsNotNone(guard.unsafe(path, b'private notes'))

class GitHookTests(unittest.TestCase):
    def test_real_commit_versions_and_artifact_rejection(self):
        import os
        import shutil
        import subprocess
        import tempfile
        root = Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            shutil.copytree(root / 'scripts', repo / 'scripts', ignore=shutil.ignore_patterns('__pycache__'))
            shutil.copytree(root / '.githooks', repo / '.githooks')
            (repo / 'VERSION').write_text('0.0.0\n')
            env = {**os.environ, 'GIT_CONFIG_GLOBAL': os.devnull, 'GIT_CONFIG_SYSTEM': os.devnull}
            def git(*args, check=True):
                return subprocess.run(['git', *args], cwd=repo, env=env, text=True, capture_output=True, check=check)
            git('init', '-q')
            git('config', 'user.name', 'Test')
            git('config', 'user.email', 'test@localhost')
            git('config', 'core.hooksPath', '.githooks')
            git('add', 'scripts', '.githooks', 'VERSION')
            git('commit', '-m', 'feat: add runtime foundation')
            self.assertEqual(git('show', 'HEAD:VERSION').stdout.strip(), '0.0.0')
            (repo / 'source.cpp').write_text('int main() { return 0; }\n')
            git('add', 'source.cpp')
            result = git('commit', '-m', 'invalid message', check=False)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual((repo / 'VERSION').read_text().strip(), '0.0.0')
            git('commit', '-m', 'fix: repair input')
            self.assertEqual(git('show', 'HEAD:VERSION').stdout.strip(), '0.0.0')
            (repo / 'source.cpp').write_text('int main() { return 1; }\n')
            git('add', 'source.cpp')
            git('commit', '-m', 'refactor!: change ABI')
            self.assertEqual(git('show', 'HEAD:VERSION').stdout.strip(), '0.0.0')
            (repo / 'bad.apk').write_text('do not commit')
            git('add', 'bad.apk')
            result = git('commit', '-m', 'chore: add forbidden artifact', check=False)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('Blocked: bad.apk', result.stdout + result.stderr)
            self.assertEqual(git('show', 'HEAD:VERSION').stdout.strip(), '0.0.0')
