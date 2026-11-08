// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/call.hpp"
#include <functional>
#include <string>
#include <stdexcept>
namespace shiraz::guest {
// AAPCS64 promoted arguments are consumed separately from Darwin variadics.
// Positional formats, %n and binary128 are explicitly unsupported.
class GuestFormatError : public std::invalid_argument {
public:
  int error;
  GuestFormatError(const char *message, int code) : std::invalid_argument(message), error(code) {}
};
std::string format_guest(const char *format,
                         const std::function<uint64_t(ArgumentType)> &next);
}
