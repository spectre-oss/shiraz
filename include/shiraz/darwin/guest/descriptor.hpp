// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <string_view>

namespace shiraz::guest {
constexpr bool valid_method_shorty(std::string_view text) {
  if (text.empty() || text.size() > 256) return false;
  unsigned slots = 0;
  for (std::size_t i = 0; i < text.size(); ++i) {
    char kind = text[i];
    if (kind == 'V') {
      if (i) return false;
      continue;
    }
    if (kind != 'Z' && kind != 'B' && kind != 'C' && kind != 'S' &&
        kind != 'I' && kind != 'J' && kind != 'F' && kind != 'D' && kind != 'L')
      return false;
    if (i) {
      slots += kind == 'J' || kind == 'D' ? 2 : 1;
      if (slots > 255) return false;
    }
  }
  return true;
}

// JVMS 4.2.1, 4.2.2 and 4.3. Bounds are checked before generating an ABI
// shorty. The host VM still validates method existence and the receiver's
// contribution to an instance method's parameter-slot limit.
constexpr bool valid_method_descriptor(std::string_view text) {
  if (text.empty() || text.size() >= 4096 || text[0] != '(') return false;
  std::size_t cursor = 1;
  unsigned slots = 0;
  auto type = [&](bool result) constexpr -> bool {
    unsigned dimensions = 0;
    while (cursor < text.size() && text[cursor] == '[') {
      ++cursor;
      if (++dimensions > 255) return false;
    }
    if (cursor == text.size()) return false;
    char kind = text[cursor++];
    if (kind == 'V') return result && !dimensions;
    if (kind == 'L') {
      bool component = false;
      while (cursor < text.size() && text[cursor] != ';') {
        char ch = text[cursor++];
        if (!ch || ch == '.' || ch == '[') return false;
        if (ch == '/') {
          if (!component) return false;
          component = false;
        } else component = true;
      }
      if (!component || cursor == text.size()) return false;
      ++cursor;
    } else if (kind != 'Z' && kind != 'B' && kind != 'C' && kind != 'S' &&
               kind != 'I' && kind != 'J' && kind != 'F' && kind != 'D') {
      return false;
    }
    if (!result) {
      slots += !dimensions && (kind == 'J' || kind == 'D') ? 2 : 1;
      if (slots > 255) return false;
    }
    return true;
  };
  while (cursor < text.size() && text[cursor] != ')')
    if (!type(false)) return false;
  if (cursor == text.size()) return false;
  ++cursor;
  return type(true) && cursor == text.size();
}
} // namespace shiraz::guest
