# SPDX-License-Identifier: GPL-3.0-only
import struct
import unittest
from shiraz.manifest import ANDROID, BinaryXml, ManifestError, inspect_manifest


def binary_fixture(utf8=True, label_reference=False, service_metadata=False, activity_config=False, launch_mode=None, themes=False, package_metadata=False):
    strings = [ANDROID, 'manifest', 'package', 'sample.app', 'application', 'activity', 'name', '.Main',
               'intent-filter', 'action', 'android.intent.action.MAIN', 'category', 'android.intent.category.LAUNCHER',
               'uses-sdk', 'minSdkVersion', 'label', '@0x7f010001',
               'service', 'meta-data', 'value', 'resource', '.Worker', 'text_true', 'bool_true', 'negative', 'float_value', 'resource_value', 'true', 'configChanges', 'screenOrientation', 'launchMode', 'theme', 'icon', 'logo', 'banner', 'sharedUserId', 'test.shared', 'sharedUserLabel', 'revisionCode', 'installLocation']
    def index(value):
        return 0xffffffff if value is None else strings.index(value)
    raw, offsets = b'', []
    for s in strings:
        offsets.append(len(raw))
        if utf8:
            b = s.encode(); raw += bytes([len(s), len(b)]) + b + b'\0'
        else:
            raw += struct.pack('<H', len(s)) + s.encode('utf-16-le') + b'\0\0'
    raw += b'\0' * (-len(raw) % 4)
    start = 28 + len(strings) * 4
    pool = struct.pack('<HHIIIIII', 1, 28, start + len(raw), len(strings), 0, 0x100 if utf8 else 0, start, 0)
    pool += struct.pack('<' + 'I' * len(offsets), *offsets) + raw
    def node(tag, attrs=(), children=b''):
        attributes = b''
        for namespace, name, value in attrs:
            dtype, data = value if isinstance(value, tuple) else (0x10, value) if isinstance(value, int) else (3, index(value))
            attributes += struct.pack('<IIIHBBI', index(namespace), index(name), 0xffffffff, 8, 0, dtype, data)
        head = struct.pack('<HHIII', 0x102, 16, 36 + len(attributes), 1, 0xffffffff)
        head += struct.pack('<IIHHHHHH', 0xffffffff, index(tag), 20, 20, len(attrs), 0, 0, 0)
        end = struct.pack('<HHIIIII', 0x103, 16, 24, 1, 0xffffffff, 0xffffffff, index(tag))
        return head + attributes + children + end
    intent = node('intent-filter', children=node('action', [(ANDROID, 'name', 'android.intent.action.MAIN')]) + node('category', [(ANDROID, 'name', 'android.intent.category.LAUNCHER')]))
    activity = node('activity', [(ANDROID, 'name', '.Main')] + ([(ANDROID, 'theme', (1, 0x7f120002))] if themes else []) + ([(ANDROID, 'configChanges', (0x10, 0x680)), (ANDROID, 'screenOrientation', (0x10, 0xffffffff))] if activity_config else []) + ([(ANDROID, 'launchMode', (0x10, launch_mode))] if launch_mode is not None else []), intent)
    service = b''
    if service_metadata:
        entries = [('text_true', 'value', 'true'), ('bool_true', 'value', (0x12, 1)),
                   ('negative', 'value', (0x10, 0xfffffff9)), ('float_value', 'value', (4, 0x3fa00000)),
                   ('resource_value', 'resource', (1, 0x7f010001))]
        children = b''.join(node('meta-data', [(ANDROID, 'name', key), (ANDROID, field, value)]) for key, field, value in entries)
        service = node('service', [(ANDROID, 'name', '.Worker')], children)
    tree = node('manifest', [(None, 'package', 'sample.app')] + ([(ANDROID, 'sharedUserId', 'test.shared'), (ANDROID, 'sharedUserLabel', (1, 0x7f010005)), (ANDROID, 'revisionCode', 3), (ANDROID, 'installLocation', 2)] if package_metadata else []), node('uses-sdk', [(ANDROID, 'minSdkVersion', 24)]) + node('application', [(ANDROID, 'label', (1, 0x7f010001) if label_reference else '@0x7f010001')] + ([(ANDROID, 'theme', (1, 0x7f120001))] if themes else []) + ([(ANDROID, 'icon', (1, 0x7f010002)), (ANDROID, 'logo', (1, 0x7f010003)), (ANDROID, 'banner', (1, 0x7f010004))] if package_metadata else []), children=activity+service))
    return struct.pack('<HHI', 3, 8, 8 + len(pool) + len(tree)) + pool + tree


class ManifestTests(unittest.TestCase):
    def test_binary_package_metadata(self):
        manifest = inspect_manifest(binary_fixture(package_metadata=True))
        self.assertEqual(manifest['shared_user_id'], 'test.shared')
        self.assertEqual(manifest['shared_user_label'], 0x7f010005)
        self.assertEqual(manifest['revision_code'], 3)
        self.assertEqual(manifest['install_location'], 2)
        self.assertEqual(manifest['application_icon'], 0x7f010002)
        self.assertEqual(manifest['application_logo'], 0x7f010003)
        self.assertEqual(manifest['application_banner'], 0x7f010004)

    def test_package_identity_and_install_location(self):
        manifest = inspect_manifest(f'<manifest xmlns:android="{ANDROID}" package="test.app" android:sharedUserId="test.shared" android:revisionCode="3" android:installLocation="preferExternal"><application/></manifest>'.encode())
        self.assertEqual(manifest['shared_user_id'], 'test.shared')
        self.assertEqual(manifest['revision_code'], 3)
        self.assertEqual(manifest['install_location'], 2)
        self.assertEqual(manifest['shared_user_label'], 0)
        plain = inspect_manifest(b'<manifest package="test.app"><application/></manifest>')
        self.assertIsNone(plain['shared_user_id'])
        self.assertEqual(plain['install_location'], -1)

    def test_binary_pools_and_launcher(self):
        for utf8 in (True, False):
            manifest = inspect_manifest(binary_fixture(utf8))
            self.assertEqual(manifest['package'], 'sample.app')
            self.assertEqual(manifest['min_sdk'], '24')
            self.assertEqual(manifest['launcher_activities'], [{'name': 'sample.app.Main', 'target': 'sample.app.Main'}])

    def test_resource_and_literal_labels_remain_distinct(self):
        for utf8 in (True, False):
            reference = inspect_manifest(binary_fixture(utf8, label_reference=True))
            literal = inspect_manifest(binary_fixture(utf8))
            self.assertEqual(reference['application_label'], literal['application_label'])
            self.assertEqual(reference['application_label_resource'], 0x7f010001)
            self.assertIsNone(literal['application_label_resource'])

    def test_manifest_themes_and_defined_permissions(self):
        manifest = inspect_manifest(binary_fixture(themes=True))
        self.assertEqual(manifest['application_theme'], 0x7f120001)
        self.assertEqual(manifest['activities'][0]['theme'], 0x7f120002)
        manifest = inspect_manifest(f'<manifest xmlns:android="{ANDROID}" package="test.app"><permission android:name="test.app.LOCAL" android:protectionLevel="signature"/><application/></manifest>'.encode())
        self.assertEqual(manifest['defined_permissions'], [{'name': 'test.app.LOCAL', 'protection': 2}])

    def test_plain_manifest_and_alias(self):
        xml = f'''<manifest xmlns:android="{ANDROID}" package="example.app">
          <application android:name="Application"><activity-alias android:name=".Alias" android:targetActivity=".Main">
          <intent-filter><action android:name="android.intent.action.MAIN"/>
          <category android:name="android.intent.category.LAUNCHER"/></intent-filter>
          </activity-alias></application></manifest>'''.encode()
        manifest = inspect_manifest(xml)
        self.assertEqual(manifest['application_class'], 'example.app.Application')
        self.assertEqual(manifest['launcher_activities'][0]['target'], 'example.app.Main')

    def test_service_declarations_and_process_inheritance(self):
        xml = f'<manifest xmlns:android="{ANDROID}" package="example.app"><application android:process=":app"><service android:name=".Worker"/><service android:name="Disabled" android:enabled="false" android:process="example.app" android:isolatedProcess="true"/></application></manifest>'.encode()
        services = inspect_manifest(xml)['services']
        self.assertEqual([{key: service[key] for key in ('name', 'enabled', 'process', 'isolated')} for service in services], [{'name': 'example.app.Worker', 'enabled': None, 'process': ':app', 'isolated': None},
                                    {'name': 'example.app.Disabled', 'enabled': 'false', 'process': 'example.app', 'isolated': 'true'}])
        for body in ('<service/>', '<service android:name=".Same"/><service android:name=".Same"/>'):
            with self.assertRaises(ManifestError):
                inspect_manifest(f'<manifest xmlns:android="{ANDROID}" package="example.app"><application>{body}</application></manifest>'.encode())

    def test_binary_metadata_preserves_types(self):
        for utf8 in (True, False):
            metadata = inspect_manifest(binary_fixture(utf8, service_metadata=True))['services'][0]['metadata']
            actual = {item['name']: (item['kind'], item['value']) for item in metadata}
            self.assertEqual(actual, {'text_true': ('string', 'true'), 'bool_true': ('boolean', 'true'),
                                     'negative': ('integer', -7), 'float_value': ('float_bits', 0x3fa00000),
                                     'resource_value': ('resource', 0x7f010001)})

    def test_metadata_requires_values_and_has_a_bound(self):
        for children in ('<meta-data android:name="missing"/>', '<meta-data android:value="true"/>',
                         '<meta-data android:name="many" android:value="true"/>' * 129):
            with self.assertRaises(ManifestError):
                inspect_manifest(f'<manifest xmlns:android="{ANDROID}" package="example.app"><application><service android:name=".Worker">{children}</service></application></manifest>'.encode())

    def test_activity_launch_modes(self):
        modes = ('standard', 'singleTop', 'singleTask', 'singleInstance', 'singleInstancePerTask')
        for number, mode in enumerate(modes):
            for utf8 in (True, False):
                self.assertEqual(inspect_manifest(binary_fixture(utf8, launch_mode=number))['activities'][0]['launch_mode'], number)
            for raw in (mode, str(number), hex(number)):
                xml = f'<manifest xmlns:android="{ANDROID}" package="sample.app"><application><activity android:name=".Main" android:launchMode="{raw}"/></application></manifest>'.encode()
                self.assertEqual(inspect_manifest(xml)['activities'][0]['launch_mode'], number)
        self.assertEqual(inspect_manifest(binary_fixture())['activities'][0]['launch_mode'], 0)
        for raw in ('-1', '5', 'unknown', '@integer/mode', 'singleTop|singleTask'):
            with self.assertRaises(ManifestError):
                inspect_manifest(f'<manifest xmlns:android="{ANDROID}" package="sample.app"><application><activity android:name=".Main" android:launchMode="{raw}"/></application></manifest>'.encode())
        for number in (5, 0xffffffff):
            with self.assertRaises(ManifestError):
                inspect_manifest(binary_fixture(launch_mode=number))

    def test_activity_configuration_policy_values(self):
        for utf8 in (True, False):
            values = inspect_manifest(binary_fixture(utf8, activity_config=True))['activities']
            self.assertEqual([{k: v[k] for k in ('name', 'config_changes', 'screen_orientation')} for v in values], [{'name': 'sample.app.Main', 'config_changes': 0x680, 'screen_orientation': -1}])
        xml = f'<manifest xmlns:android="{ANDROID}" package="example.app"><application><activity android:name=".Main" android:configChanges="orientation|screenSize|uiMode" android:screenOrientation="portrait"/><activity-alias android:name=".Alias" android:targetActivity=".Main"/></application></manifest>'.encode()
        self.assertEqual([{k: v[k] for k in ('name', 'config_changes', 'screen_orientation')} for v in inspect_manifest(xml)['activities']], [{'name': 'example.app.Main', 'config_changes': 0x680, 'screen_orientation': 1}])
        for body in ('<activity/>', '<activity android:name=".Main" android:screenOrientation="15"/>', '<activity android:name=".Main" android:configChanges="unknown"/>', '<activity android:name=".Main"/>' * 2):
            with self.assertRaises(ManifestError):
                inspect_manifest(f'<manifest xmlns:android="{ANDROID}" package="example.app"><application>{body}</application></manifest>'.encode())

    def test_activity_filter_transport(self):
        from shiraz.runtime import application_metadata
        xml = f'<manifest xmlns:android="{ANDROID}" package="sample.app"><application><activity android:name=".Main"><intent-filter android:priority="7"><action android:name="sample.OPEN"/><category android:name="android.intent.category.DEFAULT"/><data android:scheme="https" android:host="example.test" android:mimeType="text/plain"/></intent-filter></activity></application></manifest>'.encode()
        manifest = inspect_manifest(xml)
        item = manifest['activities'][0]['filters'][0]
        self.assertEqual(item['action'], ['sample.OPEN'])
        self.assertEqual(item['scheme'], ['https'])
        self.assertTrue(item['unsupported_data'])
        values = application_metadata(manifest, '/app.apk', '/data')
        self.assertEqual(values['activity.0.filter.0.mime.0'], 'text/plain')
        self.assertEqual(values['activity.0.filter.0.priority'], '7')
        self.assertEqual(values['activity.0.filter.0.unsupported'], 'true')
        bad = xml.replace(b'<action android:name="sample.OPEN"/>', b'<action/>')
        with self.assertRaises(ManifestError):
            inspect_manifest(bad)

    def test_activity_component_metadata(self):
        xml = f'<manifest xmlns:android="{ANDROID}" package="sample.app"><application android:permission="sample.PERMISSION" android:process=":app" android:directBootAware="true"><activity android:name=".Main" android:enabled="false" android:parentActivityName=".Parent" android:label="Activity café"><intent-filter/><meta-data android:name="flag" android:value="true"/></activity></application></manifest>'.encode()
        activity = inspect_manifest(xml)['activities'][0]
        self.assertEqual(activity['parentActivityName'], 'sample.app.Parent')
        self.assertEqual(activity['label'], 'Activity café')
        self.assertIsNone(activity['labelRes'])
        self.assertEqual(activity['process'], ':app')
        self.assertEqual(activity['permission'], 'sample.PERMISSION')
        self.assertEqual(activity['enabled'], 'false')
        self.assertEqual(activity['exported'], 'true')
        self.assertEqual(activity['directBootAware'], 'true')
        self.assertEqual(activity['metadata'][0]['kind'], 'boolean')
        for children in ('<meta-data android:name="missing"/>', '<meta-data android:name="many" android:value="true"/>' * 129):
            with self.assertRaises(ManifestError):
                inspect_manifest(f'<manifest xmlns:android="{ANDROID}" package="sample.app"><application><activity android:name=".Main">{children}</activity></application></manifest>'.encode())

    def test_bad_lengths_and_indices(self):
        for offset, value in [(4, 7), (8 + 4, 0xffffffff), (8 + 20, 1), (8 + 28, 0xffffffff)]:
            data = bytearray(binary_fixture())
            struct.pack_into('<I', data, offset, value)
            with self.assertRaises(ManifestError):
                inspect_manifest(data)

    def test_application_metadata(self):
        manifest = inspect_manifest(f'''<manifest xmlns:android="{ANDROID}" package="sample.app"
            android:versionName="1.2 – café" android:versionCode="4294967295" android:versionCodeMajor="2">
            <application android:label="A &amp; B" android:debuggable="true" android:enabled="false" android:hasCode="false"/>
            </manifest>'''.encode())
        self.assertEqual(manifest['version_name'], '1.2 – café')
        self.assertEqual(manifest['version_code'], '4294967295')
        self.assertEqual(manifest['version_code_major'], '2')
        self.assertEqual(manifest['application_label'], 'A & B')
        self.assertEqual(manifest['application_debuggable'], 'true')
        self.assertEqual(manifest['application_enabled'], 'false')
        self.assertEqual(manifest['application_has_code'], 'false')

    def test_invalid_and_entities(self):
        for data in [b'', b'not XML', b'<manifest/>', b'<!DOCTYPE manifest [<!ENTITY a "x">]><manifest package="&a;"/>']:
            with self.assertRaises(ManifestError):
                inspect_manifest(data)

    def test_end_tag_must_match(self):
        data = bytearray(binary_fixture())
        struct.pack_into('<I', data, len(data) - 4, 3)
        with self.assertRaises(ManifestError):
            inspect_manifest(data)
