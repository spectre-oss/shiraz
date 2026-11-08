// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/math.hpp"
#include "shiraz/darwin/guest/call.hpp"
#include <cmath>
#include <cstdio>
#include <cstring>
#include <stdexcept>
using namespace shiraz::guest;
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
int main() {
  try {
    auto provider = math_provider();
    auto call = [&](const char *name, std::vector<Argument> arguments) {
      for (const auto &symbol : provider.exports)
        if (symbol.name == name) return invoke(reinterpret_cast<void *>(symbol.address), arguments);
      throw std::runtime_error("missing math export");
    };
    require(call("hypot", {Argument::real(3.), Argument::real(4.)}).double_result() == 5., "double registers");
    auto frame = call("powf", {Argument::real(2.f), Argument::real(5.f)});
    float single; std::memcpy(&single, frame.result_vectors[0], sizeof(single));
    require(single == 32.f, "float registers");
    int exponent = 0;
    require(call("frexp", {Argument::real(20.), Argument::integer(reinterpret_cast<intptr_t>(&exponent), ArgumentType::Int64)}).double_result() == .625 && exponent == 5, "mixed scalar and output pointer");
    double integral = 0;
    require(call("modf", {Argument::real(-3.25), Argument::integer(reinterpret_cast<intptr_t>(&integral), ArgumentType::Int64)}).double_result() == -.25 && integral == -3., "signed fractional output");
    int quotient = 0;
    require(call("remquo", {Argument::real(7.), Argument::real(2.), Argument::integer(reinterpret_cast<intptr_t>(&quotient), ArgumentType::Int64)}).double_result() == -1. && (quotient & 7) == 4, "remainder output");
    double sine = 0, cosine = 0;
    call("sincos", {Argument::real(0.), Argument::integer(reinterpret_cast<intptr_t>(&sine), ArgumentType::Int64), Argument::integer(reinterpret_cast<intptr_t>(&cosine), ArgumentType::Int64)});
    require(sine == 0. && cosine == 1., "combined trig outputs");
    require(std::signbit(call("sin", {Argument::real(-0.)}).double_result()), "signed zero");
    errno = EBUSY; *guest_errno_location() = 77;
    require(std::isnan(call("log", {Argument::real(-1.)}).double_result()), "domain NaN");
    require(errno == EBUSY, "host errno preservation");
    // Darwin libm reports floating exceptions; errno is translated if supplied.
    // Do not invent an errno value when the host implementation leaves it zero.
    call("cos", {Argument::real(0.)});
    require(errno == EBUSY, "success host errno preservation");
    for (const auto &symbol : provider.exports) require(symbol.name != "log2l", "binary128 must not bind Darwin double");
    std::printf("M5_MATH_PASS exports=%zu scalar=1 float=1 outputs=1 signed_zero=1 domain=1 host_errno_preserved=1 binary128=unsupported\n", provider.exports.size());
  } catch (const std::exception &error) {
    std::fprintf(stderr, "math-probe: %s\n", error.what()); return 1;
  }
}
