// SPDX-License-Identifier: GPL-3.0-only
extern "C" {
__thread int cross_module_tls = 41;
int peer_tls_value() { return cross_module_tls; }
}
