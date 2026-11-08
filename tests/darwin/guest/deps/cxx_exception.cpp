// SPDX-License-Identifier: GPL-3.0-only
// Header-free first-party exception fixture. All C++ exception ABI functions,
// fundamental typeinfo and the personality resolve to the unchanged APK libc++.
// Exceptions are always caught within guest frames; host ABI crossing is not
// claimed by this fixture.
namespace {
volatile unsigned trace;
volatile unsigned destructors;
struct Mark {
  unsigned digit;
  explicit Mark(unsigned value) : digit(value) {}
  __attribute__((noinline)) ~Mark() {
    trace = trace * 10 + digit;
    destructors = destructors + 1;
  }
};
__attribute__((noinline)) void integer_leaf(int value, unsigned mark) {
  Mark scope(mark);
  throw value;
}
__attribute__((noinline)) void integer_middle(int value) {
  Mark scope(2);
  integer_leaf(value, 3);
}
__attribute__((noinline)) void rethrow_middle(int value) {
  Mark scope(5);
  try {
    integer_leaf(value, 6);
  } catch (int &caught) {
    // The exception object must remain the same object through rethrow.
    caught += 1;
    trace = trace * 10 + 7;
    throw;
  }
}
__attribute__((noinline)) void double_leaf() {
  Mark scope(8);
  throw 2.5;
}
}

extern "C" __attribute__((visibility("default")))
int guest_exception_nested() {
  trace = 0;
  destructors = 0;
  try {
    Mark scope(1);
    integer_middle(73);
  } catch (double) {
    return 1;
  } catch (int value) {
    return value == 73 && trace == 321 && destructors == 3 ? 0 : 2;
  } catch (...) {
    return 3;
  }
  return 4;
}

extern "C" __attribute__((visibility("default")))
int guest_exception_rethrow() {
  trace = 0;
  destructors = 0;
  try {
    rethrow_middle(90);
  } catch (double) {
    return 1;
  } catch (const int &value) {
    return value == 91 && trace == 675 && destructors == 2 ? 0 : 2;
  } catch (...) {
    return 3;
  }
  return 4;
}

extern "C" __attribute__((visibility("default")))
int guest_exception_double() {
  trace = 0;
  destructors = 0;
  try {
    double_leaf();
  } catch (int) {
    return 1;
  } catch (double value) {
    return value == 2.5 && trace == 8 && destructors == 1 ? 0 : 2;
  } catch (...) {
    return 3;
  }
  return 4;
}

extern "C" __attribute__((visibility("default")))
int guest_exception_catch_all() {
  trace = 0;
  destructors = 0;
  try {
    double_leaf();
  } catch (int) {
    return 1;
  } catch (...) {
    return trace == 8 && destructors == 1 ? 0 : 2;
  }
  return 3;
}

// Negative gate only: no guest catch frame exists above this entry. The host
// probe runs it in its own disposable process and verifies explicit abort;
// guest exceptions crossing the host ABI remain unsupported.
extern "C" __attribute__((visibility("default"), noinline, noreturn))
void guest_exception_escape() {
  throw 73;
}
