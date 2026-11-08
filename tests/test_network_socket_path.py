# SPDX-License-Identifier: GPL-3.0-only
"""Darwin abstract socket placement must work in deep and non-ASCII worktrees."""
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from shiraz.host_layout import darwin_socket_directory


class DarwinSocketDirectory(unittest.TestCase):
    def bounded(self, directory):
        self.assertLess(len(os.fsencode(directory / ('a' * 32))), 104)
        self.assertTrue(directory.is_absolute())

    def test_short_checkout_preserves_existing_namespace(self):
        directory = darwin_socket_directory('/shiraz')
        self.assertEqual(directory, Path('/shiraz/.local/sockets'))
        self.bounded(directory)

    @patch('shiraz.host_layout.os.getuid', return_value=501)
    def test_long_checkout_uses_short_private_namespace(self, _uid):
        directory = darwin_socket_directory('/' + 'x' * 200)
        self.assertEqual(directory.parent, Path('/tmp'))
        self.assertTrue(directory.name.startswith('shiraz-local-501-'))
        self.bounded(directory)

    def test_byte_limit_includes_filename_and_terminator(self):
        fits = Path('/' + 'x' * 54)
        too_long = Path('/' + 'x' * 55)
        self.assertEqual(darwin_socket_directory(fits), fits / '.local/sockets')
        self.assertEqual(darwin_socket_directory(too_long).parent, Path('/tmp'))
        self.bounded(darwin_socket_directory(fits))
        self.bounded(darwin_socket_directory(too_long))

    def test_non_ascii_paths_count_bytes(self):
        directory = darwin_socket_directory('/tmp/' + 'د' * 40)
        self.assertEqual(directory.parent, Path('/tmp'))
        self.assertTrue(directory.name.startswith('shiraz-local-'))
        self.bounded(directory)

    def test_namespaces_are_stable_and_separate_users_and_checkouts(self):
        root = '/' + 'a' * 100
        with patch('shiraz.host_layout.os.getuid', return_value=501):
            first = darwin_socket_directory(root)
            self.assertEqual(first, darwin_socket_directory(root))
            second = darwin_socket_directory('/' + 'b' * 100)
        with patch('shiraz.host_layout.os.getuid', return_value=502):
            other_user = darwin_socket_directory(root)
        self.assertEqual(len({first, second, other_user}), 3)

    def test_symlink_aliases_share_the_canonical_checkout(self):
        build = Path(__file__).resolve().parents[1] / 'build'
        build.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=build) as temporary:
            root = Path(temporary) / ('checkout' * 16)
            root.mkdir()
            alias = Path(temporary) / 'alias'
            alias.symlink_to(root, target_is_directory=True)
            self.assertEqual(darwin_socket_directory(root), darwin_socket_directory(alias))


if __name__ == '__main__':
    unittest.main()
