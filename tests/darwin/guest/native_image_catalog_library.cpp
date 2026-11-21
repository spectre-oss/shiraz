// SPDX-License-Identifier: GPL-3.0-only
extern "C" {
int shiraz_native_catalog_value = 7;
int* shiraz_native_catalog_mutate() {
    ++shiraz_native_catalog_value;
    return &shiraz_native_catalog_value;
}
}
