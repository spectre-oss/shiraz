# SPDX-License-Identifier: GPL-3.0-only
"""Read manifest identity and launcher metadata from Android binary XML."""
import struct
import re
import xml.etree.ElementTree as ET

ANDROID = 'http://schemas.android.com/apk/res/android'
NO_INDEX = 0xffffffff


class ManifestError(ValueError):
    pass


class BinaryXml:
    def __init__(self, data):
        self.data = data
        self.strings = []
        self.references = {}
        self.typed_values = {}

    def need(self, start, length, end=None):
        if start < 0 or length < 0 or start + length > (len(self.data) if end is None else end):
            raise ManifestError('binary XML offset outside chunk')

    def unpack(self, fmt, pos, end=None):
        self.need(pos, struct.calcsize(fmt), end)
        return struct.unpack_from(fmt, self.data, pos)

    def string(self, index):
        if index == NO_INDEX:
            return None
        if index >= len(self.strings):
            raise ManifestError('binary XML string index outside pool')
        return self.strings[index]

    def pool(self, start, header, end):
        if header < 28:
            raise ManifestError('short string-pool header')
        count, styles, flags, string_start, style_start = self.unpack('<IIIII', start + 8, end)
        self.need(start + header, (count + styles) * 4, end)
        if count > 1_000_000 or string_start < header + (count + styles) * 4:
            raise ManifestError('invalid string-pool offsets')
        string_end = start + style_start if style_start else end
        if string_end > end or string_end < start + string_start:
            raise ManifestError('invalid string-pool data range')
        result = []
        for i in range(count):
            offset, = self.unpack('<I', start + header + i * 4, end)
            p = start + string_start + offset
            def length8(pos):
                self.need(pos, 1, string_end)
                value = self.data[pos]; pos += 1
                if value & 128:
                    self.need(pos, 1, string_end)
                    value = ((value & 127) << 8) | self.data[pos]; pos += 1
                return value, pos
            if flags & 0x100:
                _, p = length8(p)
                length, p = length8(p)
                self.need(p, length + 1, string_end)
                if self.data[p + length] != 0:
                    raise ManifestError('unterminated UTF-8 string')
                result.append(self.data[p:p + length].decode('utf-8'))
            else:
                length, = self.unpack('<H', p, string_end); p += 2
                if length & 0x8000:
                    low, = self.unpack('<H', p, string_end); p += 2
                    length = ((length & 0x7fff) << 16) | low
                self.need(p, length * 2 + 2, string_end)
                if self.data[p + length * 2:p + length * 2 + 2] != b'\0\0':
                    raise ManifestError('unterminated UTF-16 string')
                result.append(self.data[p:p + length * 2].decode('utf-16-le'))
        self.strings = result

    def parse(self):
        self.references.clear()
        kind, header, total = self.unpack('<HHI', 0)
        if kind != 3 or header != 8 or total != len(self.data):
            raise ManifestError('invalid binary XML header')
        pos = header
        stack, root = [], None
        while pos < total:
            kind, size, length = self.unpack('<HHI', pos)
            if size < 8 or length < size:
                raise ManifestError('invalid binary XML chunk size')
            end = pos + length
            self.need(pos, length)
            if kind == 1:
                self.pool(pos, size, end)
            elif kind == 0x102:
                if size != 16:
                    raise ManifestError('unsupported XML node header')
                ns, name, attr_start, attr_size, count = self.unpack('<IIHHH', pos + 16, end)
                if attr_size < 20 or attr_start < 20:
                    raise ManifestError('invalid attribute layout')
                namespace, local = self.string(ns), self.string(name)
                if local is None:
                    raise ManifestError('element without name')
                tag = '{' + namespace + '}' + local if namespace else local
                element = ET.Element(tag)
                first = pos + 16 + attr_start
                self.need(first, attr_size * count, end)
                for i in range(count):
                    a = first + i * attr_size
                    ans, aname, raw, value_size, reserved, dtype, value = self.unpack('<IIIHBBI', a, end)
                    if value_size != 8 or reserved:
                        raise ManifestError('invalid typed attribute value')
                    namespace, name = self.string(ans), self.string(aname)
                    if name is None:
                        raise ManifestError('attribute without name')
                    key = '{' + namespace + '}' + name if namespace else name
                    if key in element.attrib:
                        raise ManifestError('duplicate XML attribute')
                    self.typed_values[(id(element), key)] = (dtype, value, self.string(value) if dtype == 3 else None)
                    if dtype in (1, 7):
                        self.references[(id(element), key)] = value
                    if raw != NO_INDEX:
                        text = self.string(raw)
                    elif dtype == 3:
                        text = self.string(value)
                    elif dtype == 0x12:
                        text = 'true' if value else 'false'
                    elif dtype in (0x10, 0x11):
                        text = str(value)
                    else:
                        text = f'@0x{value:08x}'
                    element.set(key, text or '')
                if stack:
                    stack[-1].append(element)
                elif root is None:
                    root = element
                else:
                    raise ManifestError('multiple XML roots')
                stack.append(element)
                if len(stack) > 256:
                    raise ManifestError('manifest nesting exceeds limit')
            elif kind == 0x103:
                ns, name = self.unpack('<II', pos + 16, end)
                namespace, local = self.string(ns), self.string(name)
                tag = '{' + namespace + '}' + local if namespace else local
                if not stack or stack.pop().tag != tag:
                    raise ManifestError('unbalanced XML elements')
            pos = end
        if root is None or stack:
            raise ManifestError('incomplete XML document')
        return root


def inspect_manifest(data):
    references = {}
    typed_values = {}
    try:
        if data.lstrip().startswith(b'<'):
            if b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper():
                raise ManifestError('XML entities are not accepted')
            root = ET.fromstring(data)
        else:
            parser = BinaryXml(data)
            root = parser.parse()
            references = parser.references
            typed_values = parser.typed_values
    except (UnicodeError, ET.ParseError) as exc:
        raise ManifestError('invalid manifest text') from exc
    if root.tag != 'manifest' or not root.get('package'):
        raise ManifestError('manifest requires a package name')
    package = root.get('package')
    def attr(element, name):
        return element.get('{' + ANDROID + '}' + name)
    def qualify(name):
        if not name:
            return None
        return package + name if name.startswith('.') else package + '.' + name if '.' not in name else name
    sdk = root.find('uses-sdk')
    app = root.find('application')
    launchers = []
    if app is not None:
        for activity in app:
            if activity.tag not in ('activity', 'activity-alias'):
                continue
            for intent in activity.findall('intent-filter'):
                actions = {attr(x, 'name') for x in intent.findall('action')}
                categories = {attr(x, 'name') for x in intent.findall('category')}
                if 'android.intent.action.MAIN' in actions and 'android.intent.category.LAUNCHER' in categories:
                    launchers.append({'name': qualify(attr(activity, 'name')), 'target': qualify(attr(activity, 'targetActivity') or attr(activity, 'name'))})
    activities = []
    seen_activities = set()
    config_bits = {'mcc': 1, 'mnc': 2, 'locale': 4, 'touchscreen': 8, 'keyboard': 16,
                   'keyboardHidden': 32, 'navigation': 64, 'orientation': 128, 'screenLayout': 256,
                   'uiMode': 512, 'screenSize': 1024, 'smallestScreenSize': 2048, 'density': 4096,
                   'layoutDirection': 8192, 'colorMode': 16384, 'fontScale': 0x40000000}
    orientations = dict(zip(('unspecified', 'landscape', 'portrait', 'user', 'behind', 'sensor', 'nosensor',
                             'sensorLandscape', 'sensorPortrait', 'reverseLandscape', 'reversePortrait',
                             'fullSensor', 'userLandscape', 'userPortrait', 'fullUser', 'locked'), range(-1, 15)))
    def enum_value(element, field, names, default, flags=False):
        raw = attr(element, field)
        if raw is None: return default
        typed = typed_values.get((id(element), '{' + ANDROID + '}' + field))
        if typed:
            if typed[0] not in (0x10, 0x11): raise ManifestError('unsupported activity ' + field + ' type')
            value = typed[1]
        elif re.fullmatch(r'-?(0x[0-9a-fA-F]+|[0-9]+)', raw):
            value = int(raw, 16 if '0x' in raw else 10)
        else:
            parts = raw.split('|') if flags else [raw]
            if any(part not in names for part in parts): raise ManifestError('unknown activity ' + field)
            value = 0
            for part in parts: value |= names[part]
        if not -0x80000000 <= value <= 0xffffffff: raise ManifestError('activity enum range')
        return value if value < 0x80000000 else value - 0x100000000
    if app is not None:
        for activity in app.findall('activity'):
            name = qualify(attr(activity, 'name'))
            if not name or name in seen_activities or len(activities) >= 256:
                raise ManifestError('invalid, duplicate or excessive activity declarations')
            seen_activities.add(name)
            orientation = enum_value(activity, 'screenOrientation', orientations, -1)
            if not -1 <= orientation <= 14: raise ManifestError('invalid activity orientation')
            launch_mode = enum_value(activity, 'launchMode', {'standard': 0, 'singleTop': 1, 'singleTask': 2, 'singleInstance': 3, 'singleInstancePerTask': 4}, 0)
            if not 0 <= launch_mode <= 4: raise ManifestError('invalid activity launch mode')
            activities.append({'name': name, 'launch_mode': launch_mode, 'config_changes': enum_value(activity, 'configChanges', config_bits, 0, True),
                               'screen_orientation': orientation, 'theme': references.get((id(activity), '{' + ANDROID + '}theme'), 0)})
    def metadata_value(element):
        resource = attr(element, 'resource')
        value = attr(element, 'value')
        field = 'resource' if resource is not None else 'value'
        raw = resource if resource is not None else value
        if raw is None:
            raise ManifestError('metadata requires resource or value')
        typed = typed_values.get((id(element), '{' + ANDROID + '}' + field))
        if typed:
            kind, bits, text = typed
            if field == 'resource':
                return ('resource', bits) if kind == 1 else ('unsupported', kind)
            if kind == 1: return 'reference', bits
            if kind == 3: return 'string', text
            if kind == 4: return 'float_bits', bits
            if kind == 0x12: return 'boolean', 'true' if bits else 'false'
            if 0x10 <= kind <= 0x1f: return 'integer', bits if bits < 0x80000000 else bits - 0x100000000
            return 'unsupported', kind
        if raw.startswith('@'):
            target = raw[1:]
            kind = 'resource' if field == 'resource' else 'reference'
            if re.fullmatch(r'0x[0-9a-fA-F]+|[0-9]+', target): return kind, int(target, 16 if target.startswith('0x') else 10)
            return kind + '_name', target
        if field == 'resource': return 'unsupported', 'non-reference resource'
        if raw in ('true', 'false'): return 'boolean', raw
        if re.fullmatch(r'-?(0x[0-9a-fA-F]+|[0-9]+)', raw):
            value = int(raw, 16 if '0x' in raw else 10)
            if not -0x80000000 <= value <= 0xffffffff: raise ManifestError('metadata integer range')
            return 'integer', value if value < 0x80000000 else value - 0x100000000
        if re.fullmatch(r'[-+]?[0-9]+\.[0-9]+([eE][-+]?[0-9]+)?', raw): return 'float', raw
        return 'string', raw
    metadata_entries = metadata_bytes = 0
    def component_metadata(element):
        nonlocal metadata_entries, metadata_bytes
        metadata = []
        for item in element.findall('meta-data'):
            key = attr(item, 'name')
            if not key or len(key) > 4096 or len(metadata) >= 128:
                raise ManifestError('invalid or excessive component metadata')
            kind, value = metadata_value(item)
            metadata_entries += 1
            metadata_bytes += len(key.encode('utf-8')) + len(str(value).encode('utf-8'))
            if metadata_entries > 4096 or metadata_bytes > 4 * 1024 * 1024:
                raise ManifestError('component metadata budget exceeded')
            metadata.append({'name': key, 'name_resource': references.get((id(item), '{' + ANDROID + '}name')),
                             'kind': kind, 'value': value})
        return metadata
    def activity_filters(element):
        result = []
        for node in element.findall('intent-filter'):
            if len(result) >= 128:
                raise ManifestError('activity filter limit')
            record = {'priority': attr(node, 'priority') or '0'}
            for tag in ('action', 'category'):
                names = [attr(item, 'name') for item in node.findall(tag)]
                if len(names) > 128 or any(not name or len(name) > 4096 for name in names):
                    raise ManifestError('activity filter names')
                record[tag] = names
            data = node.findall('data')
            if len(data) > 4096:
                raise ManifestError('activity filter data limit')
            record['scheme'] = list(dict.fromkeys(attr(item, 'scheme') for item in data if attr(item, 'scheme') is not None))
            record['mime'] = list(dict.fromkeys(attr(item, 'mimeType') for item in data if attr(item, 'mimeType') is not None))
            record['unsupported_data'] = any(key not in ('{' + ANDROID + '}scheme', '{' + ANDROID + '}mimeType') for item in data for key in item.attrib)
            result.append(record)
        return result
    if app is not None:
        for activity, record in zip(app.findall('activity'), activities):
            record.update({'filters': activity_filters(activity), 'label': attr(activity, 'label'),
                           'labelRes': references.get((id(activity), '{' + ANDROID + '}label')),
                           'enabled': attr(activity, 'enabled'),
                           'enabledRes': references.get((id(activity), '{' + ANDROID + '}enabled')),
                           'exportedRes': references.get((id(activity), '{' + ANDROID + '}exported')),
                           'exported': attr(activity, 'exported') if attr(activity, 'exported') is not None else ('true' if activity.findall('intent-filter') else 'false'),
                           'directBootAware': attr(activity, 'directBootAware') or attr(app, 'directBootAware'),
                           'process': attr(activity, 'process') or attr(app, 'process') or package,
                           'permission': attr(activity, 'permission') if attr(activity, 'permission') is not None else attr(app, 'permission'),
                           'parentActivityName': qualify(attr(activity, 'parentActivityName')),
                           'metadata': component_metadata(activity)})
    services = []
    seen_services = set()
    if app is not None:
        for service in app.findall('service'):
            name = qualify(attr(service, 'name'))
            if not name or name in seen_services or len(services) >= 256:
                raise ManifestError('invalid, duplicate or excessive service declarations')
            seen_services.add(name)
            metadata = component_metadata(service)
            services.append({'name': name, 'enabled': attr(service, 'enabled'),
                             'enabledRes': references.get((id(service), '{' + ANDROID + '}enabled')),
                             'exportedRes': references.get((id(service), '{' + ANDROID + '}exported')),
                             'process': attr(service, 'process') or attr(app, 'process') or package,
                             'isolated': attr(service, 'isolatedProcess'),
                             'exported': attr(service, 'exported') if attr(service, 'exported') is not None else ('true' if service.findall('intent-filter') else 'false'),
                             'permission': attr(service, 'permission') if attr(service, 'permission') is not None else attr(app, 'permission'),
                             'directBootAware': attr(service, 'directBootAware'),
                             'stopWithTask': attr(service, 'stopWithTask'), 'externalService': attr(service, 'externalService'),
                             'singleUser': attr(service, 'singleUser'), 'useAppZygote': attr(service, 'useAppZygote'),
                             'allowSharedIsolatedProcess': attr(service, 'allowSharedIsolatedProcess'),
                             'filters': activity_filters(service),
                             'metadata': metadata})
    receivers = []
    if app is not None:
        for receiver in app.findall('receiver'):
            name = qualify(attr(receiver, 'name'))
            if not name or len(receivers) >= 256:
                raise ManifestError('invalid or excessive receiver declarations')
            record = {field: attr(receiver, field) for field in ('enabled', 'exported', 'permission', 'directBootAware')}
            record.update({'name': name, 'process': attr(receiver, 'process') or attr(app, 'process') or package,
                           'enabledRes': references.get((id(receiver), '{' + ANDROID + '}enabled')),
                           'exportedRes': references.get((id(receiver), '{' + ANDROID + '}exported')),
                           'metadata': component_metadata(receiver), 'filters': activity_filters(receiver)})
            receivers.append(record)
    providers = []
    if app is not None:
        for provider in app.findall('provider'):
            name = qualify(attr(provider, 'name'))
            if not name or len(providers) >= 256:
                raise ManifestError('invalid or excessive provider declarations')
            providers.append({'name': name, 'filters': activity_filters(provider), 'authorities': attr(provider, 'authorities'), 'enabled': attr(provider, 'enabled'),
                              'exported': attr(provider, 'exported'), 'grantUriPermissions': attr(provider, 'grantUriPermissions'), 'multiprocess': attr(provider, 'multiprocess'), 'initOrder': attr(provider, 'initOrder'), 'metadata': component_metadata(provider),
                              'process': attr(provider, 'process') or attr(app, 'process') or package})
    return {'package': package, 'version_code': attr(root, 'versionCode'), 'version_code_major': attr(root, 'versionCodeMajor'), 'version_name': attr(root, 'versionName'),
            'min_sdk': attr(sdk, 'minSdkVersion') if sdk is not None else None,
            'target_sdk': attr(sdk, 'targetSdkVersion') if sdk is not None else None,
            'application_class': qualify(attr(app, 'name')) if app is not None else None,
            'application_metadata': component_metadata(app) if app is not None else [],
            'application_label': attr(app, 'label') if app is not None else None,
            'application_theme': references.get((id(app), '{' + ANDROID + '}theme'), 0),
            'application_label_resource': references.get((id(app), '{' + ANDROID + '}label')),
            'version_name_resource': references.get((id(root), '{' + ANDROID + '}versionName')),
            'application_enabled': attr(app, 'enabled') if app is not None else None,
            'application_debuggable': attr(app, 'debuggable') if app is not None else None,
            'application_has_code': attr(app, 'hasCode') if app is not None else None,
            'launcher_activities': launchers, 'services': services, 'receivers': receivers, 'providers': providers, 'activities': activities,
            'defined_permissions': [{'name': attr(x, 'name'), 'protection': enum_value(x, 'protectionLevel', {'normal': 0, 'dangerous': 1, 'signature': 2, 'signatureOrSystem': 3, 'privileged': 16}, 0, True)} for x in root.findall('permission')],
            'permissions': sorted({attr(x, 'name') for x in root.findall('uses-permission') if attr(x, 'name')})}
