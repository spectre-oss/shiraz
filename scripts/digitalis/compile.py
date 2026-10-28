# SPDX-License-Identifier: GPL-3.0-only
import os
import pathlib,json,subprocess,concurrent.futures,re
R=pathlib.Path(__file__).resolve().parents[2];S=R/'context/digitalis';W=R/'build/digitalis-host';A=R/'context/art_standalone';B=R/'context/digitalis-native-bridge-support'
os.environ['TMPDIR']=str(W/'tmp')
m=json.loads((W/'manifest.json').read_text());m['sources'] += [str(R/'native/digitalis_bridge.cpp'),str(R/'native/digitalis_guest_tls.cpp'),str(R/'native/digitalis_file_profile.cpp')];inc=m['includes']+[str(A/'system/core/libnativebridge/include')]+[str(A/'libbase/include'),str(A/'system/core/include'),str(R/'context/digitalis-bionic/libc'),str(R/'context/digitalis-bionic/libc/platform'),str(S/'jni'),str(S/'lite_translator'),str(S/'kernel_api'),str(A/'libnativehelper/include/nativehelper')]+[str(p) for p in B.rglob('include')]
flags=[os.environ.get('DIGITALIS_CXX', '/usr/lib/llvm21/bin/clang++'),'-std=gnu++23','-DHAVE_SYS_UIO_H','-DANDROID_DLEXT_USE_NAMESPACE=0x200','-O1','-g','-fPIC','-include','string','-DANDROID','-DBERBERIS_GUEST_LP64=1','-DNATIVE_BRIDGE_GUEST_ARCH_ARM64','-Wno-deprecated-declarations','-ferror-limit=3',*[f'-I{x}' for x in inc]]
(W/'obj').mkdir(exist_ok=True)
for old_object in (W/'obj').glob('*.o'): old_object.unlink()
def build(s):
 p=pathlib.Path(s);o=W/'obj'/('_'.join((p.relative_to(S).parts if p.is_relative_to(S) else (p.name,)))+'.o')
 if p.name=='syscall_emulation.cc' and p.parent.name=='arm64':
  text=p.read_text().replace('namespace berberis {','namespace berberis {\nlong ShirazGuestProfilePath(long, long);\nvoid ShirazTraceGuestFile(long, long, long, long);\nvoid ShirazGuestProfileStat(const char*, struct stat*);',1)
  text=text.replace('                                    state->cpu.x[1],','                                    ShirazGuestProfilePath(guest_nr,state->cpu.x[1]),')
  text=text.replace('  if (result == -1) {', '  ShirazTraceGuestFile(guest_nr, state->cpu.x[0], state->cpu.x[1], result);\n  if (result == -1) {')
  text=text.replace('    ConvertHostStatToGuestArch(host_stat,', '    ShirazGuestProfileStat(reinterpret_cast<const char*>(arg_2), &host_stat);\n    ConvertHostStatToGuestArch(host_stat,')
  patched=W/'syscall_emulation.cc';patched.write_text(text);s=str(patched)
  localflags_extra=['-I'+str(p.parent)]
 else:localflags_extra=[]
 if p.name=='frontend.cc' and p.parent.name=='arm64':
  # libstdc++ 12 lacks C++23 tuple assignment from std::array. Preserve both
  # generated registers through an ordinary tuple without changing the IR.
  text,count=re.subn(r'(std::tie\([^;]+?\)\s*=\s*)(Gen<x86_64::LockCmpXchg[^;]+);',
      r'\1[](auto values) { return std::make_tuple(std::get<0>(values), std::get<1>(values)); }(\2);',p.read_text())
  if count!=12:raise RuntimeError('Unexpected ARM64 compare-exchange frontend source')
  patched=W/'frontend.cc';patched.write_text(text);s=str(patched)
  localflags_extra += ['-I'+str(p.parent)]
 if p.name=='interpreter-main.cc':
  text=p.read_text().replace('#include "interpreter.h"', '#include "interpreter.h"\n#include <cstdlib>')
  text=text.replace('namespace berberis {', 'namespace berberis {\nvoid ShirazTraceGuestPc(unsigned long, const uint64_t*);',1)
  text=text.replace('    GuestAddr pc = state->cpu.insn_addr;', '    GuestAddr pc = state->cpu.insn_addr;\n    static const bool trace_pc = std::getenv("SHIRAZ_TRACE_GUEST_MODULE") != nullptr;\n    if (trace_pc) ShirazTraceGuestPc(pc, state->cpu.x);')
  patched=W/'interpreter-main.cc';patched.write_text(text);s=str(patched)
  localflags_extra += ['-I'+str(p.parent)]
 if p.name=='jni_trampolines.cc':
  patched=W/'jni_trampolines.cc';patched.write_text(p.read_text().replace('namespace berberis {', 'namespace berberis {\njint ShirazRegisterGuestNatives(JNIEnv*, jclass, const JNINativeMethod*, jint);',1).replace('(arg_env->functions)->RegisterNatives(arg_env, arg_clazz, arg_methods, arg_n)', 'ShirazRegisterGuestNatives(arg_env, arg_clazz, arg_methods, arg_n)'));s=str(patched)
 if p.name=='jni_trampolines.cc':
  text=pathlib.Path(s).read_text()
  text=text.replace('void RunGuestJNIOnLoad(GuestAddr pc, GuestArgumentBuffer* buf) {',"""void RunGuestJNIObjectFunction(GuestAddr pc, GuestArgumentBuffer* buf) {
  auto [env] = HostArgumentsValues<void(JNIEnv*)>(buf);
  RunGuestJNIFunction(pc, buf);
  auto [result] = GuestResultValue<jobject()>(buf);
  ShirazTraceJniObjectResult(env, result);
}
void RunGuestJNIOnLoad(GuestAddr pc, GuestArgumentBuffer* buf) {""")
  text=text.replace('jint ShirazRegisterGuestNatives(JNIEnv*, jclass, const JNINativeMethod*, jint);','jint ShirazRegisterGuestNatives(JNIEnv*, jclass, const JNINativeMethod*, jint);\nvoid ShirazTraceJniObjectResult(JNIEnv*, jobject);')
  text=text.replace('has_jnienv_and_jobject ? RunGuestJNIFunction : RunGuestCall', "has_jnienv_and_jobject ? (shorty[0]=='L' ? RunGuestJNIObjectFunction : RunGuestJNIFunction) : RunGuestCall")
  pathlib.Path(s).write_text(text)
 if p.name=='proxy_library_builder.cc':
  patched=W/'proxy_library_builder.cc';patched.write_text(p.read_text().replace('dlopen(library_name, RTLD_GLOBAL)', 'dlopen(library_name, RTLD_NOW | RTLD_GLOBAL)'));s=str(patched)
 if p.name=='guest_signal_handling.cc':
  text=p.read_text().replace('void SetHostRegIP(ucontext* ucontext,','void SetHostRegIP(ucontext_t* ucontext,')
  text=text.replace('namespace berberis {','namespace berberis {\nvoid ShirazTraceGuestFaultMaps(unsigned long, unsigned long);',1)
  text=text.replace('        uint64_t sp_val = cpu.sp;', '        ShirazTraceGuestFaultMaps(GetInsnAddr(cpu), reinterpret_cast<unsigned long>(fault_addr));\n        uint64_t sp_val = cpu.sp;')
  text=text.replace('        uint64_t sp_val = cpu.sp;', '        __android_log_print(ANDROID_LOG_ERROR, \"berberis\", \"  range x4=%p x5=%p x19=%p x20=%p\", (void*)cpu.x[4], (void*)cpu.x[5], (void*)cpu.x[19], (void*)cpu.x[20]);\n        uint64_t sp_val = cpu.sp;')
  patched=W/'guest_signal_handling.cc';patched.write_text(text);s=str(patched)
 localflags=flags+['-include','memory'] if p.suffix!='.S' else [x for i,x in enumerate(flags) if x not in ('-include','string')]
 if p.name=='guest_loader.cc':
  text=p.read_text().replace('  ret = pthread_self();','  extern void* ShirazGuestPthread();\n  ret = reinterpret_cast<pthread_t>(ShirazGuestPthread());')
  text=text.replace('namespace berberis {','namespace berberis {\nvoid* ShirazGuestPthread();',1).replace('  extern void* ShirazGuestPthread();\n','')
  patched=W/'guest_loader.cc';patched.write_text(text);s=str(patched)
  localflags += ['-I'+str(p.parent)]
 if p.name=='guest_thread_manager.cc':
  text=p.read_text().replace('namespace berberis {','namespace berberis {\nunsigned long ShirazAttachedGuestStack(unsigned long, size_t);',1)
  text=text.replace('  InsertCurrentThread(thread, register_dtor);', '  auto& cpu = GetCPUState(*thread->state());\n  SetStackRegister(cpu, ShirazAttachedGuestStack(GetStackRegister(cpu), stack_size));\n  InsertCurrentThread(thread, register_dtor);',1)
  patched=W/'guest_thread_manager.cc';patched.write_text(text);s=str(patched)
  localflags += ['-I'+str(p.parent)]
 if p.name=='guest_thread.cc':
  text=p.read_text().replace('namespace berberis {','namespace berberis {\nvoid* ShirazGuestPthread();\nvoid* ShirazGuestBionicTls();',1)
  text=text.replace('#if defined(__BIONIC__)\n  if (thread->static_tls_', '  if (thread->static_tls_').replace('  if (thread->scs_region_ != nullptr)', '#if defined(__BIONIC__)\n  if (thread->scs_region_ != nullptr)')
  start=text.index('bool GuestThread::AllocStaticTls()')
  end=text.index('void GuestThread::ConfigStaticTls(',start)
  section=text[start:end].replace('#if defined(__BIONIC__)','#if 1')
  a=section.index('  const size_t host_thread_id_slot')
  b=section.index('  SetTlsAddr(*state_, ToGuestAddr(tls));',a)
  section=section[:a]+'  tls[g_static_tls_config.tls_slot_thread_id] = ShirazGuestPthread();\n  tls[g_static_tls_config.tls_slot_bionic_tls] = ShirazGuestBionicTls();\n'+section[b:]
  text=text[:start]+section+text[end:]
  patched=W/'guest_thread.cc';patched.write_text(text);s=str(patched)
  localflags += ['-I'+str(p.parent)]
 result=subprocess.run(localflags+localflags_extra+['-c',s,'-o',str(o)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 return o,result.stdout if result.returncode else None
failed=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
 for o,err in pool.map(build,m['sources']):
  if err: print(err,flush=True);failed.append(str(o))
  else:print('BUILT',o.name,flush=True)
print('FAILED',len(failed),flush=True)
if failed:raise SystemExit(1)
lib=A/'out/host/linux-x86/lib64'
subprocess.run([flags[0],'-shared','-Wl,-z,defs','-Wl,-z,noexecstack','-o',str(W/'libberberis_arm64.so'),*[str(p) for p in (W/'obj').glob('*.o')],'-L'+str(lib),'-Wl,-rpath,'+str(lib),'-lbase','-llog','-ldl','-pthread','-latomic'],check=True)

for source, name in [('digitalis_libc_proxy.cpp', 'libberberis_proxy_libc.so'), ('digitalis_libm_proxy.cpp', 'libberberis_proxy_libm.so')]:
 extra=[]
 if source=='digitalis_libc_proxy.cpp':
  upstream=B/'android_api/libc/proxy'
  extra=['-I'+str(upstream),str(upstream/'setjmp_thunks.cc')]
 subprocess.run(flags+extra+['-shared',str(R/'native'/source),'-L'+str(W),'-lberberis_arm64','-o',str(W/name)],check=True)
