// SPDX-License-Identifier: GPL-3.0-only
// Partial exception gate: typed catches and guest-frame search only. It does
// not establish RAII cleanup or rethrow, whose compiler landing pads require
// _Unwind_Resume, not exported by the unchanged APK libc++.
namespace {
__attribute__((noinline)) void integer_leaf(int value) { throw value; }
__attribute__((noinline)) void integer_middle(int value) { integer_leaf(value); }
__attribute__((noinline)) void double_leaf() { throw 2.5; }
}

extern "C" __attribute__((visibility("default")))
int guest_exception_nested() {
  try {
    integer_middle(73);
  } catch (double) {
    return 1;
  } catch (int value) {
    return value == 73 ? 0 : 2;
  } catch (...) {
    return 3;
  }
  return 4;
}

extern "C" __attribute__((visibility("default")))
int guest_exception_double() {
  try {
    double_leaf();
  } catch (int) {
    return 1;
  } catch (double value) {
    return value == 2.5 ? 0 : 2;
  } catch (...) {
    return 3;
  }
  return 4;
}

extern "C" __attribute__((visibility("default")))
int guest_exception_catch_all() {
  try {
    double_leaf();
  } catch (int) {
    return 1;
  } catch (...) {
    return 0;
  }
  return 2;
}
