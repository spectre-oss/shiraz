# SPDX-License-Identifier: GPL-3.0-only
"""Bounded app launch settings shared by the host and its private worker."""

import os
import stat

from .apk import FormatError


def _embed_paths(embed):
    """Launcher-embedding files: two existing owner-only regular files in one private directory."""
    if embed is None:
        return None
    if not isinstance(embed, dict) or set(embed) != {'input', 'frame'}:
        raise FormatError('Invalid embed setting')
    paths = [embed['input'], embed['frame']]
    if not all(
        type(path) is str
        and os.path.isabs(path)
        and os.path.normpath(path) == path
        and '\0' not in path
        for path in paths
    ):
        raise FormatError('Embed paths must be absolute')
    parent = os.path.dirname(paths[0])
    if paths[0] == paths[1] or os.path.dirname(paths[1]) != parent:
        raise FormatError('Embed paths must be distinct files in one directory')
    try:
        directory = os.lstat(parent)
        files = [os.lstat(path) for path in paths]
    except OSError:
        raise FormatError('Embed paths must exist') from None
    uid = os.getuid()
    if (
        not stat.S_ISDIR(directory.st_mode)
        or directory.st_uid != uid
        or stat.S_IMODE(directory.st_mode) != 0o700
        or os.path.realpath(parent) != parent
    ):
        raise FormatError('Embed directory must be a private real directory')
    if any(
        not stat.S_ISREG(info.st_mode)
        or info.st_uid != uid
        or info.st_mode & 0o077
        or info.st_nlink != 1
        for info in files
    ):
        raise FormatError('Embed files must be private regular files')
    return dict(input=paths[0], frame=paths[1])


def normalize_settings(settings=None):
    result = dict(
        width=360,
        height=640,
        log_level='INFO',
        timeout=None,
        native_code=False,
        sandbox=False,
        allow_network=True,
        embed=None,
        frames=None,
        snapshot=None,
        device_profile=None,
    )
    if settings is not None:
        if not isinstance(settings, dict) or not set(settings) <= result.keys():
            raise FormatError('Invalid launch settings')
        result.update(settings)
    width, height = result['width'], result['height']
    if (
        type(width) is not int
        or type(height) is not int
        or not 1 <= width <= 8192
        or not 1 <= height <= 8192
        or width * height > 16 * 1024 * 1024
    ):
        raise FormatError('Invalid launch dimensions')
    timeout = result['timeout']
    if timeout is not None and (type(timeout) is not int or not 1 <= timeout <= 0x7FFFFFFF):
        raise FormatError('Invalid launch timeout')
    if result['log_level'] not in ('VERBOSE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'ASSERT', 'SILENT'):
        raise FormatError('Invalid launch log level')
    if type(result['native_code']) is not bool:
        raise FormatError('Invalid native code setting')
    if type(result['sandbox']) is not bool:
        raise FormatError('Invalid sandbox setting')
    if type(result['allow_network']) is not bool:
        raise FormatError('Invalid allow_network setting')
    if not result['allow_network'] and not result['sandbox']:
        raise FormatError('Disabling network requires sandbox=True')
    frames = result['frames']
    if frames is not None and (type(frames) is not int or not 1 <= frames <= 600):
        raise FormatError('Invalid launch frame limit')
    for key in ('snapshot', 'device_profile'):
        path = result[key]
        if path is not None and (
            type(path) is not str
            or not os.path.isabs(path)
            or os.path.normpath(path) != path
            or '\0' in path
        ):
            raise FormatError('Invalid launch ' + key + ' path')
    result['embed'] = _embed_paths(result['embed'])
    return result
