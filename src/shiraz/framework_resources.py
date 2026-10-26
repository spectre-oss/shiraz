# SPDX-License-Identifier: GPL-3.0-only
"""Conservative named-resource closure over pinned AOSP XML and resource files."""
from collections import defaultdict
import copy
from pathlib import PurePosixPath
import re
import xml.etree.ElementTree as ET

ANDROID = 'http://schemas.android.com/apk/res/android'
REFERENCE = re.compile(r'(?<!\\)[@?](?:\+|\*)?(?:(android):)?([A-Za-z_][\w-]*)/([A-Za-z_][\w.-]*)')


def parse(data):
    if len(data) > 16 * 1024 * 1024 or b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper():
        raise ValueError('unsafe or oversized resource XML')
    return ET.fromstring(data)


class ResourceIndex:
    def __init__(self, files):
        self.files = files
        self.nodes = defaultdict(list)
        self.origins = defaultdict(set)
        self.public = {}
        for path, data in sorted(files.items()):
            parts = PurePosixPath(path).parts
            if PurePosixPath(path).is_absolute() or '..' in parts or '\\' in path:
                raise ValueError('unsafe resource path')
            if len(parts) != 3 or parts[0] != 'res':
                continue
            folder, filename = parts[1:]
            kind = folder.split('-')[0]
            if kind == 'values':
                for element in parse(data):
                    if element.tag == 'public' and element.get('id'):
                        key = (element.get('type'), element.get('name'))
                        ident = int(element.get('id'), 16)
                        if key in self.public and self.public[key] != ident:
                            raise ValueError('conflicting public resource IDs')
                        self.public[key] = ident
                    elif element.tag == 'declare-styleable':
                        for attr in element:
                            if attr.tag == 'attr' and (attr.get('format') or len(attr)):
                                self.add(folder, path, attr)
                    elif element.tag not in ('public', 'public-padding', 'java-symbol', 'eat-comment', 'skip'):
                        self.add(folder, path, element)
            else:
                name = filename[:-6] if filename.endswith('.9.png') else filename.rsplit('.', 1)[0]
                self.nodes[(kind, name)].append((folder, path, None))
                self.origins[(kind, name)].add(path)

    def add(self, folder, path, element):
        name = element.get('name')
        if not name or ':' in name:
            return
        kind = element.get('type') if element.tag == 'item' else element.tag
        if kind in ('string-array', 'integer-array'):
            kind = 'array'
        key = (kind, name)
        self.origins[key].add(path)
        if kind == 'attr':
            # An attr nested in declare-styleable can be a reference to a full definition.
            old = [n for n in self.nodes[key] if n[0] == folder]
            if old:
                if not element.get('format') and not len(element):
                    return
                if any(n[2].get('format') or len(n[2]) for n in old):
                    # AOSP repeats attr declarations in different styleables. Merge their
                    # type/enum constraints, while rejecting contradictory enum values.
                    current = old[0][2]
                    formats = set(filter(None, (current.get('format', '') + '|' + element.get('format', '')).split('|')))
                    if formats: current.set('format', '|'.join(sorted(formats)))
                    children = {(child.tag, child.get('name')): child for child in current}
                    for child in element:
                        item = (child.tag, child.get('name'))
                        if item in children and children[item].attrib != child.attrib:
                            raise ValueError('conflicting attribute enum: ' + name)
                        if item not in children: current.append(copy.deepcopy(child))
                    return
                self.nodes[key] = [n for n in self.nodes[key] if n[0] != folder]
        self.nodes[key].append((folder, path, element))

    def dependencies(self, key):
        found = set()
        for _, path, element in self.nodes[key]:
            if element is None:
                if not path.endswith('.xml') or key[0] == 'raw':
                    continue
                element = parse(self.files[path])
            if key[0] == 'style':
                parent = element.get('parent')
                if parent is None and '.' in key[1]:
                    found.add(('style', key[1].rsplit('.', 1)[0]))
                elif parent and not parent.startswith('@'):
                    found.add(('style', parent.removeprefix('android:style/').removeprefix('style/')))
                for item in element:
                    if item.tag == 'item' and item.get('name'):
                        found.add(('attr', item.get('name').removeprefix('android:')))
            for node in element.iter():
                values = [node.text or '', node.tail or '', *node.attrib.values()]
                for attr in node.attrib:
                    if attr.startswith('{' + ANDROID + '}'):
                        found.add(('attr', attr.split('}', 1)[1]))
                for value in values:
                    for _, kind, name in REFERENCE.findall(value):
                        found.add((kind, name))
        return found

    def closure(self, seeds):
        selected, todo, generated_ids = set(), list(seeds), set()
        while todo:
            key = todo.pop()
            if key in selected:
                continue
            if len(selected) >= 65536:
                raise ValueError('resource closure limit exceeded')
            if key not in self.nodes:
                if key[0] == 'id':
                    generated_ids.add(key)
                else:
                    raise ValueError('missing AOSP resource: ' + '/'.join(key))
            selected.add(key)
            todo.extend(sorted(self.dependencies(key) - selected))
        return selected, generated_ids

    def stable_ids(self):
        """Reserve the full public ABI, including resources outside the closure."""
        return ''.join(f'android:{kind}/{name} = 0x{ident:08x}\n'
                       for (kind, name), ident in sorted(self.public.items()))

    def validate_ids(self, actual, selected):
        expected = {ident: '/'.join(key) for key, ident in self.public.items()}
        for name, ident in actual.items():
            if ident in expected and expected[ident] != name:
                raise ValueError('compiled framework public ID collision: ' + name + ' replaces ' + expected[ident])
        for key in selected:
            if key in self.public and actual.get('/'.join(key)) != self.public[key]:
                raise ValueError('compiled framework public ID changed: ' + '/'.join(key))

    def emit(self, selected, generated_ids):
        values = defaultdict(list)
        result, provenance = {}, {}
        for key in sorted(selected):
            for folder, path, element in self.nodes[key]:
                if element is None:
                    result[path] = self.files[path]
                else:
                    values[folder].append(copy.deepcopy(element))
                provenance.setdefault('/'.join(key), set()).update(self.origins[key])
        for kind, name in sorted(generated_ids):
            values['values'].append(ET.Element('item', {'type': kind, 'name': name}))
        for folder, elements in sorted(values.items()):
            root = ET.Element('resources')
            root.extend(elements)
            result['res/' + folder + '/shiraz_subset.xml'] = ET.tostring(root, encoding='utf-8', xml_declaration=True)
        public = ET.Element('resources')
        for key in sorted(selected):
            if key in self.public:
                ET.SubElement(public, 'public', {'type': key[0], 'name': key[1], 'id': f'0x{self.public[key]:08x}'})
        result['res/values/shiraz_public.xml'] = ET.tostring(public, encoding='utf-8', xml_declaration=True)
        return result, {key: sorted(paths) for key, paths in sorted(provenance.items())}
