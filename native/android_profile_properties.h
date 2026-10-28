// SPDX-License-Identifier: GPL-3.0-only
#ifndef SHIRAZ_ANDROID_PROFILE_PROPERTIES_H
#define SHIRAZ_ANDROID_PROFILE_PROPERTIES_H
// Explicit per-process values shared by host libcore and translated native code.
static const struct { const char *name; const char *environment; } shiraz_profile_properties[] = {
    {"ro.product.model", "SHIRAZ_ANDROID_MODEL"},
    {"ro.product.manufacturer", "SHIRAZ_ANDROID_MANUFACTURER"},
    {"ro.product.brand", "SHIRAZ_ANDROID_BRAND"},
    {"ro.product.device", "SHIRAZ_ANDROID_DEVICE"},
    {"ro.product.name", "SHIRAZ_ANDROID_PRODUCT"},
    {"ro.product.board", "SHIRAZ_ANDROID_BOARD"},
    {"ro.hardware", "SHIRAZ_ANDROID_HARDWARE"},
    {"ro.bootloader", "SHIRAZ_ANDROID_BOOTLOADER"},
    {"ro.serialno", "SHIRAZ_ANDROID_SERIAL"},
    {"ro.build.host", "SHIRAZ_ANDROID_HOST"},
    {"ro.build.user", "SHIRAZ_ANDROID_USER"},
    {"ro.build.id", "SHIRAZ_ANDROID_ID"},
    {"ro.build.type", "SHIRAZ_ANDROID_TYPE"},
    {"ro.build.tags", "SHIRAZ_ANDROID_TAGS"},
    {"ro.build.fingerprint", "SHIRAZ_ANDROID_FINGERPRINT"},
    {"ro.build.version.incremental", "SHIRAZ_ANDROID_INCREMENTAL"},
    {"ro.build.version.security_patch", "SHIRAZ_ANDROID_SECURITY_PATCH"},
};
#endif
