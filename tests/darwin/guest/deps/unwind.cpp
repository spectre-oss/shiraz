// SPDX-License-Identifier: GPL-3.0-only
// Freestanding guest frames: no dependency on either host or Android libc++abi.
using Callback = int (*)();
static volatile int observed;
__attribute__((noinline)) static int middle(Callback callback) {
  volatile unsigned char storage[96];
  storage[0] = 13;
  int result = callback();
  observed = result + storage[0];
  return result;
}
extern "C" __attribute__((visibility("default"), noinline))
int guest_unwind_frames(Callback callback) {
  volatile unsigned char storage[64];
  storage[0] = 17;
  int result = middle(callback);
  observed = result + storage[0];
  return result;
}
