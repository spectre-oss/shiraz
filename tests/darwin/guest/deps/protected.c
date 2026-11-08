// SPDX-License-Identifier: GPL-3.0-only
static volatile int constructor_value;
__attribute__((constructor)) static void initialize(void) {
  volatile char buffer[24];
  buffer[0] = 41;
  constructor_value = buffer[0];
}
__attribute__((noinline)) static int helper(int input) {
  volatile char buffer[32];
  buffer[0] = (char)input;
  return input + buffer[0];
}
int protected_sum(int input) { return helper(input) + constructor_value; }
__attribute__((noinline)) static int callback(int input) {
  volatile int buffer[8];
  buffer[0] = input;
  return input + buffer[0] + 17;
}
int (*protected_callback(void))(int) { return callback; }
