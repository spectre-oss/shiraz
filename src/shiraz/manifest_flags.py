# SPDX-License-Identifier: GPL-3.0-only
"""Bounded static manifest flags for the API-27, normal-UI enrollment profile.

Other configuration axes are rejected rather than guessed. Runtime and enrollment
must use the same profile when consuming these security-relevant booleans.
"""

import struct

from .apk import FormatError


def select(table, ident, *, sdk=27, ui_type=1):
    candidates = []
    for _, kind, value, raw in table.values(ident, configuration=True):
        config = bytearray(raw)
        version = struct.unpack_from('<H', config, 24)[0] if len(config) >= 28 else 0
        mode = config[29] if len(config) >= 32 else 0
        if version > sdk or mode & 15 not in (0, ui_type):
            continue
        config[:4] = bytes(4)
        if len(config) >= 28:
            config[24:26] = bytes(2)
        if len(config) >= 32:
            config[29] &= 0xF0
        if any(config):
            raise FormatError('Manifest flag uses unsupported configuration qualifiers')
        candidates.append(((bool(mode & 15), version), (kind, value)))
    if not candidates:
        raise FormatError('Manifest flag has no matching resource configuration')
    rank = max(rank for rank, _ in candidates)
    values = {value for candidate, value in candidates if candidate == rank}
    if len(values) != 1:
        raise FormatError('Ambiguous manifest flag resource configuration')
    return values.pop()


def resolve_boolean(table, ident):
    kind, value = resolve_value(table, ident)
    if kind != 18:
        raise FormatError('Manifest flag resource is not boolean')
    return 'true' if value else 'false'


def resolve_value(table, ident):
    seen = set()
    for _ in range(9):
        if ident in seen:
            raise FormatError('Cyclic manifest flag resource')
        seen.add(ident)
        kind, value = select(table, ident)
        if kind == 1:
            ident = value
            continue
        return kind, value
    raise FormatError('Manifest flag reference depth exceeded')


def normalize(apk, manifest):
    """Resolve component visibility flags in a copy; never rewrite the signed APK."""
    import copy
    import re
    import zipfile

    from .app_icon import MAX_TABLE, ResourceTable

    result = copy.deepcopy(manifest)
    pending = []
    for group in ('activities', 'activity_aliases', 'services', 'receivers', 'providers'):
        for item in result.get(group, []):
            for field in ('enabled', 'exported', 'process'):
                value = item.get(field)
                if isinstance(value, str) and value.startswith('@'):
                    ident = item.get(field + 'Res')
                    if ident is None:
                        if not re.fullmatch(r'@(0x[0-9a-fA-F]+|[0-9]+)', value):
                            raise FormatError('Unresolved manifest flag resource name')
                        ident = int(value[1:], 16 if value.startswith('@0x') else 10)
                    pending.append((item, field, ident))
    if not pending:
        return result
    if len(pending) > 4096:
        raise FormatError('Manifest resource flag count limit')
    with zipfile.ZipFile(apk) as archive:
        if archive.namelist().count('resources.arsc') != 1:
            raise FormatError('Missing or duplicate manifest resource table')
        info = archive.getinfo('resources.arsc')
        if info.file_size > MAX_TABLE:
            raise FormatError('Manifest resource table exceeds limit')
        table = ResourceTable(archive.read(info))
    cache = {}
    for item, field, ident in pending:
        if type(ident) is not int or not 0 < ident <= 0xFFFFFFFF:
            raise FormatError('Invalid manifest flag resource ID')
        key = (ident, field == 'process')
        if key not in cache:
            if field == 'process':
                kind, value = resolve_value(table, ident)
                if kind != 3:
                    raise FormatError('Manifest process resource is not a string')
                value = table.string(value)
                if not 1 <= len(value) <= 1024 or not re.fullmatch(
                    r':?[A-Za-z_][A-Za-z0-9_.]*', value
                ):
                    raise FormatError('Invalid manifest process resource')
                cache[key] = value
            else:
                cache[key] = resolve_boolean(table, ident)
        item[field] = cache[key]
    return result
