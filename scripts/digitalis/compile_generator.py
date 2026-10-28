# SPDX-License-Identifier: GPL-3.0-only
import os
import pathlib,json,subprocess
R=pathlib.Path(__file__).resolve().parents[2];S=R/'context/digitalis';W=R/'build/digitalis-host';A=R/'context/art_standalone'
os.environ['TMPDIR']=str(W/'tmp')
m=json.loads((W/'manifest.json').read_text());inc=m['includes']+[str(S/'intrinsics/riscv64_to_x86_64'),str(A/'system/core/include'),str(A/'libbase/include'),str(A/'system/core/liblog/include')]
cmd=[os.environ.get('DIGITALIS_GENERATOR_CXX', '/usr/lib/llvm22/bin/clang++'),'-std=gnu++23','-nostdinc++','-isystem',str(R/'.deps/digitalis-cxx12/usr/include/c++/12'),'-isystem',str(R/'.deps/digitalis-cxx12/usr/include/x86_64-linux-gnu/c++/12'),'-DHAVE_SYS_UIO_H','-O0','-DANDROID','-DBERBERIS_GUEST_LP64=1','-DNATIVE_BRIDGE_GUEST_ARCH_RISCV64',*[f'-I{x}' for x in inc],str(S/'intrinsics/gen_text_asm_intrinsics.cc'),str(S/'intrinsics/riscv64_to_x86_64/constants_pool.cc'),str(S/'base/mmap_posix.cc'),'-Wl,-rpath,'+str(R/'.local/lib'),'-L'+str(A/'out/host/linux-x86/lib64'),'-Wl,-rpath,'+str(A/'out/host/linux-x86/lib64'),'-lbase','-llog','-o',str(W/'gen-intrinsics')]
subprocess.run(cmd,check=True)
os.environ['LD_LIBRARY_PATH']=str(R/'.local/lib')+':'+str(A/'out/host/linux-x86/lib64')
subprocess.run([str(W/'gen-intrinsics'),str(W/'gen/berberis/intrinsics/intrinsics.h')],check=True)
