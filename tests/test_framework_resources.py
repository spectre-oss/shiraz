# SPDX-License-Identifier: GPL-3.0-only
import io
import importlib.util
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
import xml.etree.ElementTree as ET
from shiraz.framework_resources import ResourceIndex, parse


class FrameworkResourceTests(unittest.TestCase):
    def test_closure_preserves_parents_variants_public_ids_and_references(self):
        files = {
            'res/values/main.xml': b'''<resources>
              <public type="style" name="Base.Child" id="0x01030241"/>
              <attr name="accent" format="color"/><attr name="label" format="string"/>
              <style name="Base" parent=""><item name="accent">@color/red</item></style>
              <style name="Base.Child"><item name="label">@string/title</item></style>
              <color name="red">#ff0000</color><string name="title">Default</string>
              <string name="unused">Unused</string></resources>''',
            'res/values-fr/text.xml': b'<resources><string name="title">@string/french</string><string name="french">Bonjour</string></resources>',
        }
        index = ResourceIndex(files); selected, generated = index.closure([('style', 'Base.Child')])
        self.assertIn(('style', 'Base'), selected)
        self.assertIn(('attr', 'accent'), selected)
        self.assertIn(('color', 'red'), selected)
        self.assertIn(('string', 'french'), selected)
        self.assertNotIn(('string', 'unused'), selected)
        out, origins = index.emit(selected, generated)
        public = ET.fromstring(out['res/values/shiraz_public.xml'])
        self.assertEqual(public[0].attrib, {'type': 'style', 'name': 'Base.Child', 'id': '0x01030241'})
        self.assertIn('res/values-fr/shiraz_subset.xml', out)
        self.assertEqual(origins['string/title'], ['res/values-fr/text.xml', 'res/values/main.xml'])

    def test_excluded_public_ids_are_reserved_and_checked(self):
        index = ResourceIndex({'res/values/a.xml': b'''<resources>
          <public type="style" name="Included" id="0x01030001"/>
          <public type="style" name="Excluded" id="0x01030002"/>
          <style name="Included" parent=""/><style name="Excluded" parent=""/>
          <style name="Private" parent=""/></resources>'''})
        selected, _ = index.closure([('style', 'Included')])
        self.assertNotIn(('style', 'Excluded'), selected)
        self.assertIn('android:style/Excluded = 0x01030002\n', index.stable_ids())
        index.validate_ids({'style/Included': 0x01030001, 'style/Private': 0x01030003}, selected)
        with self.assertRaisesRegex(ValueError, 'public ID collision'):
            index.validate_ids({'style/Included': 0x01030001, 'style/Private': 0x01030002}, selected)
        with self.assertRaisesRegex(ValueError, 'public ID changed'):
            index.validate_ids({}, selected)

    def test_file_xml_namespace_attributes_ids_and_nine_patch(self):
        files = {'res/values/attrs.xml': b'<resources><attr name="background" format="reference"/><attr name="id" format="reference"/></resources>',
                 'res/layout/panel.xml': b'<View xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/panel" android:background="@drawable/background"/>',
                 'res/drawable/background.9.png': b'PNG'}
        index = ResourceIndex(files); selected, generated = index.closure([('layout', 'panel')])
        self.assertEqual(generated, {('id', 'panel')})
        out, _ = index.emit(selected, generated)
        self.assertEqual(out['res/drawable/background.9.png'], b'PNG')
        self.assertIn(('attr', 'background'), selected)
        self.assertEqual(ET.fromstring(out['res/values/shiraz_subset.xml']).find("item[@type='id']").get('name'), 'panel')

    def test_missing_references_fail_and_cycles_are_bounded(self):
        index = ResourceIndex({'res/values/x.xml': b'<resources><string name="a">@string/b</string></resources>'})
        with self.assertRaisesRegex(ValueError, 'missing AOSP resource'):
            index.closure([('string', 'a')])
        index = ResourceIndex({'res/values/x.xml': b'<resources><string name="a">@string/b</string><string name="b">@string/a</string></resources>'})
        self.assertEqual(len(index.closure([('string', 'a')])[0]), 2)

    def test_repeated_styleable_attributes_merge_but_conflicts_fail(self):
        index = ResourceIndex({'res/values/a.xml': b'''<resources>
            <declare-styleable name="One"><attr name="mode" format="integer"><enum name="one" value="1"/></attr></declare-styleable>
            <declare-styleable name="Two"><attr name="mode" format="reference"><enum name="two" value="2"/></attr></declare-styleable>
            </resources>'''})
        selected, generated = index.closure([('attr', 'mode')]); out, _ = index.emit(selected, generated)
        attr = ET.fromstring(out['res/values/shiraz_subset.xml'])[0]
        self.assertEqual(attr.get('format'), 'integer|reference')
        self.assertEqual([node.get('name') for node in attr], ['one', 'two'])
        with self.assertRaisesRegex(ValueError, 'conflicting attribute enum'):
            ResourceIndex({'res/values/a.xml': b'<resources><attr name="x"><enum name="a" value="1"/></attr><attr name="x"><enum name="a" value="2"/></attr></resources>'})

    def test_xml_and_resource_paths_reject_unsafe_inputs(self):
        with self.assertRaises(ValueError): parse(b'<!DOCTYPE resources [<!ENTITY x "payload">]><resources/>')
        with self.assertRaises(ValueError): ResourceIndex({'res/../outside.xml': b'<resources/>'})
        with self.assertRaises(ValueError):
            ResourceIndex({'res/values/a.xml': b'<resources><public type="style" name="A" id="0x01030001"/><public type="style" name="A" id="0x01030002"/></resources>'})

    def test_source_archives_reject_links_traversal_duplicates_and_large_entries(self):
        scripts = Path(__file__).resolve().parents[1] / 'scripts'
        sys.path.insert(0, str(scripts))
        try:
            spec = importlib.util.spec_from_file_location('bootstrap_framework_resources', scripts / 'bootstrap_framework_resources.py')
            module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        finally: sys.path.remove(str(scripts))
        with tempfile.TemporaryDirectory() as temporary:
            archive = Path(temporary) / 'source.tar.gz'
            for kind in ('link', 'traversal', 'duplicate', 'large'):
                with tarfile.open(archive, 'w:gz') as out:
                    entry = tarfile.TarInfo('../escape' if kind == 'traversal' else 'res/values/a.xml')
                    if kind == 'link': entry.type = tarfile.SYMTYPE; entry.linkname = '/etc/passwd'
                    if kind == 'large': entry.size = 17 * 1024 * 1024
                    out.addfile(entry, io.BytesIO(b'\0' * entry.size) if kind == 'large' else None)
                    if kind == 'duplicate': out.addfile(entry)
                with self.subTest(kind=kind), self.assertRaises(ValueError): module.source_files(archive)
        first = {'res/values/a.xml': b'<resources/>', 'NOTICE': b'license'}
        reordered = {'NOTICE': b'license', 'res/values/a.xml': b'<resources/>'}
        changed = {'NOTICE': b'license', 'res/values/a.xml': b'<resources><x/></resources>'}
        self.assertEqual(module.source_content_sha256(first), module.source_content_sha256(reordered))
        self.assertNotEqual(module.source_content_sha256(first), module.source_content_sha256(changed))
