// SPDX-License-Identifier: GPL-3.0-only
#include <setjmp.h>
#include <stdint.h>
_Static_assert(sizeof(jmp_buf) == 256 && _Alignof(jmp_buf) == 8 && sizeof(sigjmp_buf) == 264,
               "pinned AArch64 bionic jump buffer capacity/alignment");
__attribute__((noinline,noreturn)) static void nested(jmp_buf buffer, int value, int depth) {
  volatile int stack[64];
  stack[depth] = depth + 19;
  __asm__ volatile("" : : "r"(stack) : "memory");
  if (depth) nested(buffer, value, depth - 1);
  longjmp(buffer, value);
}
int jump_value(int value) {
  jmp_buf buffer;
  volatile uint64_t stack[8];
  stack[0] = 0x12345678;
  int result = setjmp(buffer);
  if (!result) nested(buffer, value, 7);
  return stack[0] == 0x12345678 ? result : -1000;
}
int jump_mask(void (*change)(void), int mode) {
  sigjmp_buf buffer;
  int result;
  if (mode == 0) result = _setjmp(buffer);
  else if (mode == 1) result = setjmp(buffer);
  else result = sigsetjmp(buffer, mode == 3);
  if (!result) {
    change();
    siglongjmp(buffer, 41);
  }
  return result;
}
int jump_store(void *destination) { return _setjmp(destination); }
__attribute__((noreturn)) void jump_target(void *buffer, int value) { longjmp(buffer, value); }
int jump_cross(void (*bounce)(void *)) {
  jmp_buf buffer;
  int result = setjmp(buffer);
  if (!result) bounce(buffer);
  return result;
}

// This first-party assembly fixture really clobbers every AAPCS64 preserved
// GPR and low D register between save and jump, and grows the stack below it.
__asm__(
".text\n.p2align 2\n.global jump_registers\n.type jump_registers,%function\n"
"jump_registers:\n"
" sub sp,sp,#512\n"
" stp x19,x20,[sp,#0]\n stp x21,x22,[sp,#16]\n stp x23,x24,[sp,#32]\n"
" stp x25,x26,[sp,#48]\n stp x27,x28,[sp,#64]\n stp x29,x30,[sp,#80]\n"
" stp d8,d9,[sp,#96]\n stp d10,d11,[sp,#112]\n stp d12,d13,[sp,#128]\n stp d14,d15,[sp,#144]\n"
" mov x19,#19\n mov x20,#20\n mov x21,#21\n mov x22,#22\n mov x23,#23\n"
" mov x24,#24\n mov x25,#25\n mov x26,#26\n mov x27,#27\n mov x28,#28\n mov x29,#29\n"
" fmov d8,x19\n fmov d9,x20\n fmov d10,x21\n fmov d11,x22\n"
" fmov d12,x23\n fmov d13,x24\n fmov d14,x25\n fmov d15,x26\n"
" add x0,sp,#192\n bl _setjmp\n cbnz w0,1f\n"
" mov x19,#0\n mov x20,#0\n mov x21,#0\n mov x22,#0\n mov x23,#0\n"
" mov x24,#0\n mov x25,#0\n mov x26,#0\n mov x27,#0\n mov x28,#0\n mov x29,#0\n"
" fmov d8,xzr\n fmov d9,xzr\n fmov d10,xzr\n fmov d11,xzr\n"
" fmov d12,xzr\n fmov d13,xzr\n fmov d14,xzr\n fmov d15,xzr\n"
" sub sp,sp,#64\n add x0,sp,#256\n mov w1,#37\n bl longjmp\n brk #0\n"
"1:\n cmp w0,#37\n b.ne 2f\n"
" cmp x19,#19\n b.ne 2f\n cmp x20,#20\n b.ne 2f\n cmp x21,#21\n b.ne 2f\n"
" cmp x22,#22\n b.ne 2f\n cmp x23,#23\n b.ne 2f\n cmp x24,#24\n b.ne 2f\n"
" cmp x25,#25\n b.ne 2f\n cmp x26,#26\n b.ne 2f\n cmp x27,#27\n b.ne 2f\n"
" cmp x28,#28\n b.ne 2f\n cmp x29,#29\n b.ne 2f\n"
" fmov x9,d8\n cmp x9,#19\n b.ne 2f\n fmov x9,d9\n cmp x9,#20\n b.ne 2f\n"
" fmov x9,d10\n cmp x9,#21\n b.ne 2f\n fmov x9,d11\n cmp x9,#22\n b.ne 2f\n"
" fmov x9,d12\n cmp x9,#23\n b.ne 2f\n fmov x9,d13\n cmp x9,#24\n b.ne 2f\n"
" fmov x9,d14\n cmp x9,#25\n b.ne 2f\n fmov x9,d15\n cmp x9,#26\n b.ne 2f\n"
" ldr x9,[sp,#216]\n mov x10,sp\n cmp x9,x10\n b.ne 2f\n mov w0,#1\n b 3f\n"
"2:\n mov w0,#0\n3:\n"
" ldp d8,d9,[sp,#96]\n ldp d10,d11,[sp,#112]\n ldp d12,d13,[sp,#128]\n ldp d14,d15,[sp,#144]\n"
" ldp x19,x20,[sp,#0]\n ldp x21,x22,[sp,#16]\n ldp x23,x24,[sp,#32]\n"
" ldp x25,x26,[sp,#48]\n ldp x27,x28,[sp,#64]\n ldp x29,x30,[sp,#80]\n"
" add sp,sp,#512\n ret\n.size jump_registers,.-jump_registers\n"
);
