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


_MEM_FLAGS_CACHE: dict[tuple[str, int, int, int, int, int], dict[tuple[int, bool], str]] = {}


def _fast_flags_cache_path(apk_resolved):
    import hashlib
    import os
    from pathlib import Path

    if os.environ.get('SHIRAZ_FAST_INSPECT') != '1':
        return None
    base = os.environ.get('XDG_CACHE_HOME', '')
    cache_root = Path(base) if base and Path(base).is_absolute() else Path.home() / '.cache'
    key = str(apk_resolved).encode('utf-8')
    return cache_root / 'shiraz' / 'manifest_flags' / (hashlib.sha256(key).hexdigest() + '.marshal')


def normalize(apk, manifest):
    """Resolve component visibility flags in a copy; never rewrite the signed APK."""
    import copy
    import marshal
    import os
    import re
    import zipfile
    from pathlib import Path

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
    for _, _, ident in pending:
        if type(ident) is not int or not 0 < ident <= 0xFFFFFFFF:
            raise FormatError('Invalid manifest flag resource ID')

    apk_path = Path(apk)
    apk_resolved = apk_path.resolve()
    cache_path = _fast_flags_cache_path(apk_resolved)
    st = None
    mem_key = None
    cached_values = None
    if cache_path is not None:
        try:
            st = os.lstat(apk_resolved)
            mem_key = (
                str(apk_resolved),
                st.st_dev,
                st.st_ino,
                st.st_size,
                st.st_mtime_ns,
                st.st_ctime_ns,
            )
            hit = _MEM_FLAGS_CACHE.get(mem_key)
            if hit is not None:
                cached_values = hit
            elif cache_path.is_file() and not cache_path.is_symlink():
                loaded = marshal.loads(cache_path.read_bytes())
                if (
                    isinstance(loaded, dict)
                    and loaded.get('_stamp') == list(mem_key[1:])
                    and isinstance(loaded.get('values'), dict)
                ):
                    cached_values = loaded['values']
                    _MEM_FLAGS_CACHE[mem_key] = cached_values
        except (OSError, ValueError, EOFError, TypeError):
            cached_values = None

    needed_keys = {(ident, field == 'process') for _, field, ident in pending}
    if cached_values is not None and needed_keys.issubset(cached_values.keys()):
        for item, field, ident in pending:
            item[field] = cached_values[(ident, field == 'process')]
        return result

    with zipfile.ZipFile(apk) as archive:
        if archive.namelist().count('resources.arsc') != 1:
            raise FormatError('Missing or duplicate manifest resource table')
        info = archive.getinfo('resources.arsc')
        if info.file_size > MAX_TABLE:
            raise FormatError('Manifest resource table exceeds limit')
        table = ResourceTable(archive.read(info))
    cache = dict(cached_values) if cached_values is not None else {}
    for item, field, ident in pending:
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
    if cache_path is not None and mem_key is not None:
        _MEM_FLAGS_CACHE[mem_key] = cache
        try:
            cache_path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            tmp = cache_path.with_name(f'.{cache_path.name}.tmp.{os.getpid()}')
            tmp.write_bytes(marshal.dumps({'_stamp': list(mem_key[1:]), 'values': cache}))
            os.replace(tmp, cache_path)
        except (OSError, ValueError, TypeError):
            pass
    return result
