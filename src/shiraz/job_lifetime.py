# SPDX-License-Identifier: GPL-3.0-only
"""Private inherited locks for proving all job execution holders have exited.

A free lock is only a lifetime observation. Callers must also authenticate the
claim, check its recovery metadata, and retain the lock during any transition.
Missing or unsafe files are never interpreted as a stopped worker.
"""

import fcntl
import os
import stat
from pathlib import Path

from .job_store import token_value


class JobLifetime:
    def __init__(self, descriptor):
        self.descriptor = descriptor

    def fileno(self):
        if self.descriptor is None:
            raise ValueError('Job lifetime lock is closed')
        return self.descriptor

    def close(self):
        if self.descriptor is not None:
            descriptor, self.descriptor = self.descriptor, None
            # Never LOCK_UN: inherited descriptors share the locked open file.
            # Close only this holder; descendants retain the lifetime lock.
            os.close(descriptor)

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()


def _directory(root):
    path = Path(root) / 'job-lifetimes'
    path.mkdir(mode=0o700, exist_ok=True)
    info = path.lstat()
    if not stat.S_ISDIR(info.st_mode) or info.st_mode & 0o077 or info.st_uid != os.getuid():
        raise PermissionError('Unsafe job lifetime directory')
    return path


def _open(root, token, *, create):
    token_value(token)
    directory = _directory(root)
    # Open the directory itself without following a symlink, then use openat.
    directory_fd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC)
    try:
        info = os.fstat(directory_fd)
        if info.st_mode & 0o077 or info.st_uid != os.getuid():
            raise PermissionError('Unsafe opened job lifetime directory')
        flags = os.O_RDWR | os.O_NOFOLLOW | os.O_CLOEXEC
        if create:
            flags |= os.O_CREAT | os.O_EXCL
        descriptor = os.open(token + '.lock', flags, 0o600, dir_fd=directory_fd)
    finally:
        os.close(directory_fd)
    try:
        info = os.fstat(descriptor)
        if info.st_nlink == 0:
            raise FileNotFoundError('Job lifetime lock was retired concurrently')
        if (
            not stat.S_ISREG(info.st_mode)
            or info.st_mode & 0o077
            or info.st_uid != os.getuid()
            or info.st_nlink != 1
        ):
            raise PermissionError('Unsafe job lifetime lock')
        try:
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            os.close(descriptor)
            return None
        return JobLifetime(descriptor)
    except BaseException:
        os.close(descriptor)
        raise


def create(root, token):
    """Acquire a new claim's lock before publishing recoverable launch metadata."""
    return _open(root, token, create=True)


def probe(root, token):
    """Return an acquired lock, or None while any inherited holder still owns it."""
    return _open(root, token, create=False)


def unlink_owned(root, token, lifetime):
    """Unlink only the exact locked inode; caller must exclude active claims."""
    token_value(token)
    directory_fd = os.open(
        _directory(root), os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC
    )
    try:
        current = os.stat(token + '.lock', dir_fd=directory_fd, follow_symlinks=False)
        held = os.fstat(lifetime.fileno())
        if (current.st_dev, current.st_ino) != (held.st_dev, held.st_ino) or not stat.S_ISREG(
            current.st_mode
        ):
            raise RuntimeError('Job lifetime path changed during retirement')
        os.unlink(token + '.lock', dir_fd=directory_fd)
    finally:
        os.close(directory_fd)
