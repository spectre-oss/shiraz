# SPDX-License-Identifier: GPL-3.0-only
"""Native launcher contract tests. /usr/bin/true replaces ART only in these fixtures."""
import fcntl
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[1]

@unittest.skipUnless((ROOT/'build/shiraz-app').is_file(), 'make build/shiraz-app first')
class AppImageLauncherTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='shiraz launcher ')
        self.root=Path(self.tmp.name); self.app=self.root/'App Dir'
        self.share=self.app/'usr/share/shiraz';self.share.mkdir(parents=True)
        self.bin=self.app/'usr/bin';self.bin.mkdir(parents=True)
        self.exe=self.bin/'shiraz-app';shutil.copy2(ROOT/'build/shiraz-app',self.exe)
        lib=self.app/'usr/lib';lib.mkdir()
        (lib/'ld-linux-x86-64.so.2').symlink_to('/usr/lib/ld-linux-x86-64.so.2')
        (lib/'libart-compiler.so').touch()
        for path in ['usr/art/bin/dalvikvm','usr/bin/dex2oat']:
            p=self.app/path;p.parent.mkdir(parents=True,exist_ok=True);shutil.copy2('/usr/bin/true',p)
        for name in ('core-oj-hostdex.jar','core-libart-hostdex.jar','okhttp-hostdex.jar','bouncycastle-hostdex.jar','wolfssljni-hostdex.jar'):
            p=self.app/'usr/art/framework'/name;p.parent.mkdir(parents=True,exist_ok=True);p.touch()
        for name in ('original.apk','shiraz-framework.jar','framework-res.apk','fonts.conf','cacerts.jks','espeak-ng-data/phondata'):
            p=self.share/name;p.parent.mkdir(parents=True,exist_ok=True);p.touch()
        (self.share/'application.properties').write_text('package=org.shiraz.test\n')
        self.launch=self.share/'launch.properties';self.write_launch('a'*64)
        self.data=self.root/'data #=حافظ';self.cache=self.root/'cache'
        self.env=dict(os.environ,HOME=str(self.root/'home'),XDG_CACHE_HOME=str(self.cache),SHIRAZ_ART_MODE='jit')
        self.env.pop('SHIRAZ_ART_HEAP_MB',None)

    def tearDown(self):self.tmp.cleanup()
    def write_launch(self,digest):
        self.launch.write_text('package=org.shiraz.test\napplication=android.app.Application\nactivity=org.shiraz.test.Main\nsha256='+digest+'\nversion=1.2.3\n')
    def run_launcher(self,*args):
        return subprocess.run([str(self.exe),'--data-dir',str(self.data),*args],env=self.env,capture_output=True,text=True,timeout=10)
    def test_version_has_no_data_side_effects(self):
        result=self.run_launcher('--version');self.assertEqual(result.returncode,0,result.stderr)
        self.assertIn('1.2.3',result.stdout);self.assertFalse(self.data.exists());self.assertFalse(self.cache.exists())
    def test_jni_check_is_explicit_diagnostic(self):
        shutil.copy2('/usr/bin/echo',self.app/'usr/art/bin/dalvikvm')
        for value in (None, '1', '0'):
            if value is None:self.env.pop('SHIRAZ_CHECK_JNI',None)
            else:self.env['SHIRAZ_CHECK_JNI']=value
            before=set(self.cache.rglob('stdout.log'))
            result=self.run_launcher('--frames','1')
            self.assertEqual(result.returncode,0,result.stderr)
            log=(set(self.cache.rglob('stdout.log'))-before).pop()
            self.assertEqual('-Xcheck:jni' in log.read_text().split(),value=='1')
    def test_relocation_metadata_persistence_and_updates(self):
        result=self.run_launcher('--frames','1');self.assertEqual(result.returncode,0,result.stderr)
        record=self.data/'.package-install.native';before=record.read_text()
        result=self.run_launcher('--frames','1');self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(record.read_text(),before)
        properties=list(self.cache.rglob('application.properties'))[0].read_text()
        self.assertIn('dataDir='+str(self.data).replace(' ','\\ ').replace('#','\\#').replace('=','\\='),properties)
        self.write_launch('b'*64);result=self.run_launcher('--frames','1');self.assertEqual(result.returncode,0,result.stderr)
        old=before.splitlines();new=record.read_text().splitlines()
        self.assertEqual(new[1],old[1]);self.assertGreater(int(new[2]),int(old[2]))
    def test_existing_output_readonly_payload_and_session_lock(self):
        output=self.root/'existing.ppm';output.write_bytes(b'keep')
        result=self.run_launcher('--snapshot',str(output));self.assertNotEqual(result.returncode,0)
        self.assertEqual(output.read_bytes(),b'keep')
        result=self.run_launcher('--data-dir',str(self.app/'state'));self.assertNotEqual(result.returncode,0)
        self.data.mkdir()
        with (self.data/'.session.lock').open('w') as lock:
            fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
            result=self.run_launcher('--frames','1');self.assertNotEqual(result.returncode,0);self.assertIn('running session',result.stderr)
    def test_invalid_geometry_and_modes_fail_before_data_creation(self):
        for args in [('--width','0'),('--height','NaN'),('--width','8192','--height','8192'),('--unknown',)]:
            self.assertNotEqual(self.run_launcher(*args).returncode,0)
        self.env['SHIRAZ_ART_MODE']='typo';self.assertNotEqual(self.run_launcher().returncode,0)
        self.assertFalse(self.data.exists())

    def test_heap_cap_is_bounded_and_diagnostic(self):
        result=self.run_launcher('--diagnose');self.assertEqual(result.returncode,0,result.stderr)
        self.assertIn('Java heap cap MiB: 512',result.stdout)
        self.env['SHIRAZ_ART_HEAP_MB']='512'
        result=self.run_launcher('--diagnose');self.assertEqual(result.returncode,0,result.stderr)
        self.assertIn('Java heap cap MiB: 512',result.stdout)
        for value in ('0','127','513','4097','-1','1024m',''):
            self.env['SHIRAZ_ART_HEAP_MB']=value
            self.assertNotEqual(self.run_launcher('--diagnose').returncode,0,value)
        self.assertFalse(self.data.exists())

    def test_heap_cap_reaches_art_arguments(self):
        shutil.copy2('/usr/bin/echo',self.app/'usr/art/bin/dalvikvm')
        self.env['SHIRAZ_ART_HEAP_MB']='512'
        result=self.run_launcher('--frames','1');self.assertEqual(result.returncode,0,result.stderr)
        output=next(self.cache.rglob('stdout.log')).read_text()
        self.assertIn('-Xmx512m',output.split())
        for option in ('-Xgc:CMS','-XX:BackgroundGC=CMS','-XX:DisableHSpaceCompactForOOM'):
            self.assertIn(option,output.split())

    def test_aot_payload_validation_and_runtime_arguments(self):
        with self.launch.open('a') as output: output.write('aot=true\n')
        result=self.run_launcher('--diagnose')
        self.assertNotEqual(result.returncode,0)
        self.assertFalse(self.data.exists())
        for stem in ('boot','boot-core-libart-hostdex','boot-okhttp-hostdex','boot-bouncycastle-hostdex','boot-wolfssljni-hostdex'):
            for suffix in ('.art','.oat','.vdex'):
                file=self.app/'usr/art/framework/x86_64'/(stem+suffix)
                file.parent.mkdir(parents=True,exist_ok=True);file.touch()
        for stem in ('shiraz-framework','original'):
            for suffix in ('.odex','.vdex'):
                file=self.share/'oat/x86_64'/(stem+suffix)
                file.parent.mkdir(parents=True,exist_ok=True);file.touch()
        self.assertIn('Packaged AOT: yes',self.run_launcher('--diagnose').stdout)
        shutil.copy2('/usr/bin/echo',self.app/'usr/art/bin/dalvikvm')
        result=self.run_launcher('--frames','1');self.assertEqual(result.returncode,0,result.stderr)
        output=next(self.cache.rglob('stdout.log')).read_text()
        self.assertIn('-Xbootclasspath-locations:/shiraz/boot/core-oj-hostdex.jar:',output)
        self.assertIn('-Ximage:',output);self.assertIn('-Xno-dex-file-fallback',output)
        self.env['SHIRAZ_ART_MODE']='interpreter'
        result=self.run_launcher('--frames','1');self.assertEqual(result.returncode,0,result.stderr)
        output=max(self.cache.rglob('stdout.log'),key=lambda p:p.stat().st_mtime_ns).read_text()
        self.assertIn('-Xint',output);self.assertNotIn('-Ximage:',output)

    def test_native_payload_validation_before_data_creation(self):
        with self.launch.open('a') as out: out.write('native=true\n')
        result=self.run_launcher('--diagnose')
        self.assertNotEqual(result.returncode,0);self.assertFalse(self.data.exists())
        native=self.app/'usr/android-lib';native.mkdir();(native/'libexample.so').touch()
        for name in ('fonts/android-fonts.xml','bionic_translation/cfg.d/bionic_translation.cfg','bionic_translation/cfg.d/shiraz-host.cfg'):
            target=self.share/name;target.parent.mkdir(parents=True,exist_ok=True);target.touch()
        for name in ('libshiraz-android-fonts.so','libshiraz-android-process.so','libjnigraphics.so'):(self.app/'usr/lib'/name).touch()
        result=self.run_launcher('--diagnose');self.assertEqual(result.returncode,0,result.stderr)
        # Resolve preload by basename: the relocated AppDir contains spaces.
        # A real preload is required for execution; diagnose only checks layout.
        shutil.copy2(ROOT/'build/libshiraz-android-fonts.so',self.app/'usr/lib/libshiraz-android-fonts.so')
        shutil.copy2(ROOT/'build/libshiraz-android-process.so',self.app/'usr/lib/libshiraz-android-process.so')
        shutil.copy2('/usr/bin/echo',self.app/'usr/art/bin/dalvikvm')
        result=self.run_launcher('--frames','1');self.assertEqual(result.returncode,0,result.stderr)
        output=next(self.cache.rglob('stdout.log')).read_text()
        self.assertIn(':'+str(native),output)
        self.assertIn('nativeLibraryDir=',next(self.cache.rglob('application.properties')).read_text())
        self.assertNotIn('cannot be preloaded',next(self.cache.rglob('stderr.log')).read_text())

    def test_desktop_install_extracts_relocatable_copy_and_preserves_data(self):
        # Installation is exercised without a real APK or system desktop writes.
        (self.app/'usr/lib/ld-linux-x86-64.so.2').unlink()
        (self.app/'AppRun').write_text('#!/bin/sh\nexec "$(dirname "$0")/usr/bin/shiraz-app" "$@"\n')
        (self.app/'AppRun').chmod(0o755)
        (self.app/'shiraz.desktop').write_text('[Desktop Entry]\nType=Application\nName=Test\nExec=AppRun\nIcon=shiraz-icon\nTerminal=false\n')
        (self.app/'shiraz-icon.png').write_bytes(b'\x89PNG\r\n\x1a\nfixture')
        location=self.root/'desktop space $cash %value'
        self.env['XDG_DATA_HOME']=str(location)
        userdata=location/'shiraz-apps/org.shiraz.test/keep'
        userdata.parent.mkdir(parents=True);userdata.write_text('keep')
        for _ in range(2):
            result=subprocess.run([str(self.exe),'install'],env=self.env,capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
        desktop=location/'applications/org.shiraz.test.Shiraz.desktop'
        content=desktop.read_text()
        self.assertIn('%%value',content);self.assertIn('\\\\$cash',content)
        self.assertEqual(content.count('X-Purism-FormFactor=Workstation;Mobile;'),1)
        copies=list((location/'shiraz/desktop/org.shiraz.test').glob('*/1.2.3-*'))
        self.assertEqual(len(copies),1)
        self.assertIn('Icon='+str(copies[0]/'shiraz-icon.png'),content)
        self.assertEqual((copies[0]/'shiraz-icon.png').read_bytes(),b'\x89PNG\r\n\x1a\nfixture')
        shutil.move(self.app,self.root/'original-moved')
        result=subprocess.run([str(copies[0]/'AppRun'),'--version'],env=self.env,capture_output=True,text=True)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertIn('1.2.3',result.stdout)
        self.assertEqual(userdata.read_text(),'keep')
        if shutil.which('desktop-file-validate'):
            subprocess.run(['desktop-file-validate',str(desktop)],check=True)

    def test_desktop_install_migrates_only_matching_legacy_launcher(self):
        (self.app/'usr/lib/ld-linux-x86-64.so.2').unlink()
        (self.app/'AppRun').write_text('fixture')
        (self.app/'shiraz-icon.png').write_bytes(b'icon')
        (self.app/'shiraz.desktop').write_text('[Desktop Entry]\nExec=AppRun\nIcon=shiraz-icon\n')
        self.launch.write_text(self.launch.read_text().replace('org.shiraz.test','chat.delta'))
        location=self.root/'desktop';self.env['XDG_DATA_HOME']=str(location)
        applications=location/'applications';applications.mkdir(parents=True)
        legacy=applications/'shiraz-messenger.desktop'
        unrelated=applications/'shiraz-osmand.desktop';unrelated.write_text('untouched')
        for content, migrated in [('custom launcher',False),
                ('[Desktop Entry]\nExec=/old/Messenger-Shiraz-1-x86_64.AppImage\nX-AppImage-Version=1\n',True)]:
            legacy.write_text(content)
            result=subprocess.run([str(self.exe),'install'],env=self.env,capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            self.assertEqual(legacy.exists(),not migrated)
            self.assertEqual(unrelated.read_text(),'untouched')
        archived=list((location/'shiraz/legacy-launchers').iterdir())
        self.assertEqual(len(archived),1)
        self.assertIn('Messenger-Shiraz',archived[0].read_text())

    def test_desktop_install_rejects_external_payload_symlink(self):
        (self.app/'shiraz.desktop').write_text('[Desktop Entry]\nExec=AppRun\nIcon=shiraz-icon\n')
        (self.app/'AppRun').touch();(self.app/'shiraz-icon.png').touch()
        self.env['XDG_DATA_HOME']=str(self.root/'install-data')
        result=subprocess.run([str(self.exe),'install'],env=self.env,capture_output=True,text=True)
        self.assertNotEqual(result.returncode,0)
        self.assertIn('symlink escapes',result.stderr)
        self.assertFalse((self.root/'install-data/applications').exists())
