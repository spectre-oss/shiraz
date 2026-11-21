# SPDX-License-Identifier: GPL-3.0-only
"""Bounded service intent matching, mirroring the supported Java IntentFilter subset."""


def validate_query(mime=None, scheme=None, has_data=False, categories=None):
    for value in (mime, scheme):
        if value is not None and (not isinstance(value, str) or len(value) > 4096):
            raise ValueError('Invalid service intent data')
    if type(has_data) is not bool or scheme is not None and not has_data:
        raise ValueError('Invalid service intent data presence')
    if categories is None:
        categories = []
    if (
        not isinstance(categories, list)
        or len(categories) > 128
        or any(not isinstance(value, str) or not 1 <= len(value) <= 4096 for value in categories)
    ):
        raise ValueError('Invalid service intent categories')
    return mime, scheme, has_data, categories


def matches(rule, mime, scheme, has_data, categories):
    if not set(categories).issubset(rule.get('category', [])):
        return False
    types, schemes = rule.get('mime', []), rule.get('scheme', [])
    if not types and not schemes:
        return mime is None and not has_data
    if schemes:
        if (scheme or '') not in schemes:
            return False
    elif scheme not in (None, '', 'content', 'file'):
        return False
    if not types:
        return mime is None
    if mime is None:
        return False
    for candidate in types:
        if candidate == mime or candidate == '*/*' or mime == '*/*':
            return True
        major, slash, minor = mime.partition('/')
        other_major, _, other_minor = candidate.partition('/')
        if slash and major and major == other_major and (minor == '*' or other_minor == '*'):
            return True
    return False
