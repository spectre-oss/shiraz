# SPDX-License-Identifier: GPL-3.0-only
"""Bounded XML service descriptors from APK resources."""

import re
import xml.etree.ElementTree as ET
import zipfile
from pathlib import PurePosixPath

from .apk import FormatError
from .app_icon import MAX_TABLE, ResourceTable
from .manifest import ANDROID, BinaryXml, ManifestError


def inspect_service_descriptors(apk, manifest, *, action, meta_key, root_tag, decode):
    services = [
        service
        for service in manifest.get('services', [])
        if any(action in rule.get('action', []) for rule in service.get('filters', []))
    ]
    if not services:
        return []
    if len(services) > 128:
        raise FormatError('Service descriptor service count limit')
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        table = None
        visits = 0

        def read(name, limit):
            if names.count(name) != 1:
                raise FormatError('Missing or duplicate service descriptor resource')
            info = archive.getinfo(name)
            if info.file_size > limit:
                raise FormatError('Service descriptor resource size limit')
            return archive.read(info)

        def resolve(ident, depth=0):
            nonlocal table, visits
            visits += 1
            if depth > 8 or visits > 4096:
                raise FormatError('Service descriptor reference budget exceeded')
            if table is None:
                table = ResourceTable(read('resources.arsc', MAX_TABLE))
            values = {(kind, value) for _, kind, value in table.values(ident)}
            if len(values) != 1:
                raise FormatError('Missing or configuration-dependent service descriptor resource')
            kind, value = next(iter(values))
            if kind == 1:
                return resolve(value, depth + 1)
            return (kind, table.string(value)) if kind == 3 else (kind, value)

        def resource_id(value):
            if not isinstance(value, str) or not re.fullmatch(r'@(0x[0-9a-fA-F]+|[0-9]+)', value):
                raise FormatError('Unresolved service descriptor resource name')
            result = int(value[1:], 16 if value.startswith('@0x') else 10)
            if not 0 < result <= 0xFFFFFFFF:
                raise FormatError('Invalid service descriptor resource ID')
            return result

        result = []
        for service in services:
            metadata = [item for item in service.get('metadata', []) if item['name'] == meta_key]
            if len(metadata) != 1 or metadata[0].get('name_resource'):
                raise FormatError('Service descriptor requires one literal metadata key')
            meta = metadata[0]
            if meta['kind'] == 'resource':
                kind, path = resolve(meta['value'])
                if kind != 3:
                    raise FormatError('Service descriptor XML resource is not a file')
            elif meta['kind'] == 'resource_name' and re.fullmatch(
                r'xml/[A-Za-z0-9_]+', meta['value']
            ):
                path = 'res/' + meta['value'] + '.xml'
            else:
                raise FormatError('Service descriptor metadata must reference XML')
            if (
                not isinstance(path, str)
                or not path.startswith('res/')
                or '..' in PurePosixPath(path).parts
                or '\\' in path
            ):
                raise FormatError('Unsafe service descriptor XML resource path')
            data = read(path, 1024 * 1024)
            references = {}
            try:
                if data.lstrip().startswith(b'<'):
                    if b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper():
                        raise FormatError('Service descriptor XML entities are not accepted')
                    root = ET.fromstring(data)
                else:
                    parser = BinaryXml(data)
                    root = parser.parse()
                    references = parser.references
            except (ET.ParseError, ManifestError, UnicodeError) as error:
                raise FormatError('Malformed service descriptor XML') from error
            if root.tag != root_tag or len(root):
                raise FormatError('Invalid service descriptor XML root')

            def attr(name):
                key = '{' + ANDROID + '}' + name
                ident = references.get((id(root), key))
                return f'@0x{ident:08x}' if ident is not None else root.get(key)

            result.append(decode(service, attr, resolve, resource_id))
        return result
