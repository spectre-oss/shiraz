// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/math.hpp"
#include <cmath>
namespace shiraz::guest {
namespace {
void guest_sincos(double value, double *sine, double *cosine) {
  *sine = std::sin(value);
  *cosine = std::cos(value);
}
void guest_sincosf(float value, float *sine, float *cosine) {
  *sine = std::sin(value);
  *cosine = std::cos(value);
}
}
Provider math_provider() {
  Provider provider{"libm.so", {"LIBC"}, {}, false};
  auto add = [&](const char *name, auto function) {
    provider.exports.push_back(fixed_export(name, "LIBC", function, ErrnoPolicy::Observe));
  };
#define UNARY(NAME) add(#NAME, static_cast<double (*)(double)>(&::NAME))
#define FLOAT_UNARY(NAME) add(#NAME, static_cast<float (*)(float)>(&::NAME))
#define BINARY(NAME) add(#NAME, static_cast<double (*)(double, double)>(&::NAME))
#define FLOAT_BINARY(NAME) add(#NAME, static_cast<float (*)(float, float)>(&::NAME))
  UNARY(acos); FLOAT_UNARY(acosf); UNARY(acosh);
  UNARY(asin); UNARY(asinh); UNARY(atan); UNARY(atanh);
  BINARY(atan2); FLOAT_BINARY(atan2f);
  UNARY(cbrt); FLOAT_UNARY(cbrtf); UNARY(cos); FLOAT_UNARY(cosf);
  UNARY(cosh); FLOAT_UNARY(erff); UNARY(exp); FLOAT_UNARY(expf);
  UNARY(exp2); FLOAT_UNARY(exp2f); BINARY(fmod); FLOAT_BINARY(fmodf);
  BINARY(hypot); FLOAT_BINARY(hypotf);
  UNARY(log); FLOAT_UNARY(logf); UNARY(log10); UNARY(log2); FLOAT_UNARY(log2f);
  FLOAT_BINARY(nextafterf); BINARY(pow); FLOAT_BINARY(powf); BINARY(remainder);
  UNARY(sin); FLOAT_UNARY(sinf); UNARY(sinh); UNARY(tan); FLOAT_UNARY(tanf); UNARY(tanh);
  UNARY(expm1); UNARY(log1p); FLOAT_UNARY(atanf); FLOAT_UNARY(sqrtf);
  UNARY(floor); FLOAT_UNARY(floorf); UNARY(ceil); FLOAT_UNARY(ceilf);
  UNARY(trunc); FLOAT_UNARY(truncf); UNARY(rint); FLOAT_UNARY(rintf);
  FLOAT_UNARY(roundf); FLOAT_UNARY(tanhf); UNARY(fabs); FLOAT_UNARY(log10f);
  BINARY(fmin); BINARY(fmax); FLOAT_BINARY(fminf); FLOAT_BINARY(fmaxf);
#undef UNARY
#undef FLOAT_UNARY
#undef BINARY
#undef FLOAT_BINARY
  add("frexp", static_cast<double (*)(double, int *)>(&::frexp)); add("frexpf", &::frexpf);
  add("scalbn", static_cast<double (*)(double,int)>(&::scalbn));
  add("modf", static_cast<double (*)(double, double *)>(&::modf)); add("modff", &::modff);
  add("remquo", static_cast<double (*)(double, double, int *)>(&::remquo)); add("ilogbf", &::ilogbf); add("nan", &::nan);
  // These have no result; the explicit adapter preserves host errno and maps
  // an observed domain/range error to the guest errno slot.
  auto combined = [&](const char *name, auto function) {
    void *entry = closure([function](Capture &capture) {
      CallCursor cursor(capture, Convention::Aapcs64);
      using Function = decltype(function);
      HostErrno preserve;
      if constexpr (std::is_same_v<Function, decltype(&guest_sincos)>) {
        double value = cursor.read<double>();
        double *sine = cursor.read<double *>();
        double *cosine = cursor.read<double *>();
        function(value, sine, cosine);
      } else {
        float value = cursor.read<float>();
        float *sine = cursor.read<float *>();
        float *cosine = cursor.read<float *>();
        function(value, sine, cosine);
      }
      if (errno) *guest_errno_location() = errno_to_guest(errno);
    });
    provider.exports.push_back({name, "LIBC", reinterpret_cast<uintptr_t>(entry),
                                SymbolKind::Function, "math/scalar-sincos", false});
  };
  combined("sincos", &guest_sincos); combined("sincosf", &guest_sincosf);
  return provider;
}
}
