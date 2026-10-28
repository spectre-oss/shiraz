#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build individual ABI proxies against an existing Digitalis build."""
import argparse
import json
import os
import re
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--kind', choices=['nativewindow', 'egl', 'gles1', 'gles2', 'gles3', 'libc', 'opensles', 'android_runtime', 'android'], default='nativewindow')
    parser.add_argument('--library-dir', type=Path, default=ROOT/'build/digitalis-host')
    parser.add_argument('--android-library-dir', type=Path, default=ROOT/'build')
    parser.add_argument('--java-home', type=Path, default=Path(os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-17-openjdk-amd64')))
    args = parser.parse_args()
    work = ROOT/'build/digitalis-host'
    manifest = json.loads((work/'manifest.json').read_text())
    original = Path(manifest['includes'][0]).parents[2]
    includes = [ROOT/Path(p).relative_to(original) for p in manifest['includes']]
    includes += [ROOT/'include', ROOT/'context/art_standalone/libbase/include', ROOT/'context/art_standalone/system/core/include', ROOT/'context/art_standalone/system/core/liblog/include', args.java_home/'include', args.java_home/'include/linux']
    includes += list((ROOT/'context/digitalis-native-bridge-support').rglob('include'))
    command = [os.environ.get('DIGITALIS_CXX', 'clang++'), '-std=gnu++23', '-shared', '-fPIC', '-O1', '-include', 'string', '-DANDROID', '-DBERBERIS_GUEST_LP64=1', '-DNATIVE_BRIDGE_GUEST_ARCH_ARM64']
    if args.kind in ('android_runtime', 'android'):
        command += ['-I'+str(ROOT/'context/art_standalone/libnativehelper/include/nativehelper')]
    command += ['-I'+str(p) for p in includes]
    names = {'nativewindow': 'nativewindow', 'egl': 'EGL', 'gles1': 'GLESv1_CM', 'gles2': 'GLESv2', 'gles3': 'GLESv3', 'libc': 'c', 'opensles': 'OpenSLES', 'android_runtime': 'android_runtime', 'android': 'android'}
    source = ROOT/('native/digitalis_'+args.kind+'_proxy.cpp')
    if args.kind in ('gles2', 'gles3'):
        upstream = ROOT/('context/digitalis-native-bridge-support/android_api/lib'+names[args.kind]+'/proxy')
        text = (upstream/(args.kind+'_trampolines.cc')).read_text()
        text = text.replace('#define GL_GLEXT_PROTOTYPES', '#include <EGL/egl.h>\n#include <dlfcn.h>\n#include <vector>\n#define GL_GLEXT_PROTOTYPES')
        text = text.replace('HostCode /* callee */', 'HostCode callee')
        table = (upstream/'trampolines_arm64_to_x86_64-inl.h').read_text()
        for name in ('glDebugMessageCallbackKHR', 'glGetPointervKHR'):
            text, count = re.subn(r'(?m)^(\s*)'+name+r'\(',
                r'\1reinterpret_cast<decltype(&'+name+r')>(const_cast<void*>(callee))(', text)
            if count != 1:
                raise SystemExit('Unexpected GLES callback: '+name)
            table = table.replace('{"'+name+'", DoCustomTrampoline_'+name+', reinterpret_cast<void*>(DoBadThunk)}',
                                  '{"'+name+'", DoCustomTrampoline_'+name+', nullptr}')
        text = text.replace('DEFINE_INIT_PROXY_LIBRARY("lib'+names[args.kind]+'.so")', '''extern "C" void InitProxyLibrary(ProxyLibraryBuilder* builder) {
  static std::vector<KnownTrampoline> functions(std::begin(kKnownTrampolines), std::end(kKnownTrampolines));
  void* library = dlopen("libEGL_shiraz.so.1", RTLD_NOW | RTLD_GLOBAL);
  for (auto& entry : functions) if (!entry.thunk) {
    entry.thunk = library ? dlsym(library, entry.name) : nullptr;
    if (!entry.thunk) entry.thunk = reinterpret_cast<void*>(eglGetProcAddress(entry.name));
  }
  builder->Build("libEGL_shiraz.so.1", functions.size(), functions.data(), sizeof(kKnownVariables)/sizeof(KnownVariable), kKnownVariables);
}''')
        overlay = work/(args.kind+'-proxy')
        overlay.mkdir(exist_ok=True)
        source = overlay/(args.kind+'_trampolines.cc')
        source.write_text(text)
        (overlay/'trampolines_arm64_to_x86_64-inl.h').write_text(table)
    if args.kind == 'android':
        from android_headers import prepare
        command += ['-I'+str(prepare(work/'android-headers'))]
        upstream = ROOT/'context/digitalis-native-bridge-support/android_api/libandroid/proxy'
        text = (upstream/'libandroid_trampolines.cc').read_text()
        # Resolve optional host APIs at invocation, using the proxy builder's
        # normal missing-symbol handling rather than requiring every API at load.
        optional = ('AInputQueue_attachLooper', 'ANativeActivity_finish',
                    'ANativeActivity_hideSoftInput', 'ANativeActivity_setWindowFlags',
                    'ANativeActivity_setWindowFormat', 'ANativeActivity_showSoftInput',
                    'AStorageManager_mountObb', 'AStorageManager_unmountObb')
        table = (upstream/'trampolines_arm64_to_x86_64-inl.h').read_text()
        for name in optional:
            text, count = re.subn(r'(?m)^(\s*(?:ret = )?)'+name+r'\(',
                r'\1reinterpret_cast<decltype(&'+name+r')>(const_cast<void*>(callee))(', text)
            if count != 1:
                raise SystemExit('Unexpected Android proxy call: '+name)
            before = '{"'+name+'", DoCustomTrampoline_'+name+', reinterpret_cast<void*>(DoBadThunk)}'
            if table.count(before) != 1:
                raise SystemExit('Unexpected Android proxy entry: '+name)
            table = table.replace(before, before.replace('reinterpret_cast<void*>(DoBadThunk)', 'nullptr'))
        text = text.replace('HostCode /* callee */', 'HostCode callee')
        text = text.replace('#include <stdint.h>', '#include <stdint.h>\n#include <dlfcn.h>\n#include <cstring>\n#include <vector>')
        text = text.replace('DEFINE_INIT_PROXY_LIBRARY("libandroid.so")', '''extern "C" void InitProxyLibrary(ProxyLibraryBuilder* builder) {
  static std::vector<KnownTrampoline> functions(std::begin(kKnownTrampolines), std::end(kKnownTrampolines));
  void* resources = dlopen("libshiraz-resources.so", RTLD_NOW | RTLD_GLOBAL);
  for (auto& entry : functions)
    if (resources && std::strncmp(entry.name, "AAsset", 6) == 0)
      entry.thunk = dlsym(resources, entry.name);
  builder->Build("libandroid.so", functions.size(), functions.data(), sizeof(kKnownVariables)/sizeof(KnownVariable), kKnownVariables);
}''')
        overlay = work/'android-proxy'
        overlay.mkdir(exist_ok=True)
        source = overlay/'libandroid_trampolines.cc'
        source.write_text(text)
        (overlay/'trampolines_arm64_to_x86_64-inl.h').write_text(table)
        command += ['-I'+str(ROOT/'context/art_standalone/libandroidfw/include'),
                    '-I'+str(ROOT/'context/digitalis/native_activity/include'),
                    '-D__INTRODUCED_IN(x)=', '-D__INTRODUCED_IN_64(x)=',
                    '-D__INTRODUCED_IN_32(x)=', '-D__DEPRECATED_IN(x)=',
                    '-D__REMOVED_IN(x)=', '-D__ANDROID_API__=35']
    if args.kind == 'opensles':
        # Retain the pinned AOSP proxy's license and interface/callback wrappers.
        # Shiraz exports only supported interface IDs, so absent optional IDs
        # must not become mandatory host dynamic-link dependencies.
        upstream = ROOT/'context/digitalis-native-bridge-support/android_api/libOpenSLES/proxy'
        text = (upstream/'sles_trampolines.cc').read_text()
        text, count = re.subn(r'isSLID\(id, (SL_IID_\w+)\)',
                             r'isSLID(id, ShirazInterfaceId("\1"))', text)
        if count < 40:
            raise SystemExit('Unexpected pinned OpenSL ES interface registry')
        before = 'bool isSLID(SLInterfaceID id, SLInterfaceID id2) {\n  return memcmp(id, id2, sizeof(*id)) == 0;\n}'
        after = '''SLInterfaceID ShirazInterfaceId(const char* name) {
  auto* value = reinterpret_cast<SLInterfaceID*>(dlsym(RTLD_DEFAULT, name));
  return value ? *value : nullptr;
}
bool isSLID(SLInterfaceID id, SLInterfaceID id2) {
  return id && id2 && memcmp(id, id2, sizeof(*id)) == 0;
}'''
        if text.count(before) != 1:
            raise SystemExit('Unexpected pinned OpenSL ES interface comparison')
        text = text.replace(before, after)
        text = text.replace('*reinterpret_cast<SLInterfaceID*>(dlsym(RTLD_DEFAULT, name))',
                            'ShirazInterfaceId(name)')
        source = work/'sles_trampolines.cc'
        source.write_text(text)
        command += ['-I'+str(upstream), '-isystem', str(ROOT/'third_party/opensles')]
    if args.kind == 'gles1':
        header = Path('/usr/include/GLES/gl.h').read_text()
        functions = sorted(set(re.findall(r'GL_APIENTRY\s+(gl\w+)\s*\(', header)))
        if len(functions) < 100:
            raise SystemExit('Incomplete GLES1 declarations')
        (work/'digitalis-gles1.inc').write_text(''.join('ENTRY('+name+')\n' for name in functions))
    command += ['-I'+str(work), str(source), '-L'+str(args.library_dir), '-L'+str(args.android_library_dir), '-lberberis_arm64']
    if args.kind == 'nativewindow':
        command += ['-landroid']
    if args.kind == 'opensles':
        command += ['-l:libOpenSLES.so.1', '-llog', '-Wl,-z,defs']
    if args.kind == 'android_runtime':
        command += ['-I'+str(ROOT/'context/art_standalone/libnativehelper/include'), '-lnativehelper', '-llog', '-Wl,-z,defs']
    if args.kind == 'android':
        command += ['-landroid', '-llog', '-Wl,-z,defs']
    if args.kind in ('gles2', 'gles3'):
        command += ['-l:libEGL_shiraz.so.1', '-llog', '-Wl,-z,defs']
    command += ['-o', str(work/('libberberis_proxy_lib'+names[args.kind]+'.so'))]
    subprocess.run(command, check=True)
if __name__ == '__main__':
    main()
