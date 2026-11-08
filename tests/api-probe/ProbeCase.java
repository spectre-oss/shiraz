// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

final class ProbeCase {
  final String id, category, expected, mode;
  ProbeCase(String id, String category, String expected, String mode) {
    this.id = id;
    this.category = category;
    this.expected = expected;
    this.mode = mode;
  }
}
