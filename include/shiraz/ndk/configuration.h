// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstdint>

struct AConfiguration;
struct AAssetManager;

enum {
    ACONFIGURATION_ORIENTATION_ANY = 0x0000,
    ACONFIGURATION_ORIENTATION_PORT = 0x0001,
    ACONFIGURATION_ORIENTATION_LAND = 0x0002,
    ACONFIGURATION_ORIENTATION_SQUARE = 0x0003,

    ACONFIGURATION_TOUCHSCREEN_ANY = 0x0000,
    ACONFIGURATION_TOUCHSCREEN_NOTOUCH = 0x0001,
    ACONFIGURATION_TOUCHSCREEN_STYLUS = 0x0002,
    ACONFIGURATION_TOUCHSCREEN_FINGER = 0x0003,

    ACONFIGURATION_DENSITY_DEFAULT = 0,
    ACONFIGURATION_DENSITY_LOW = 120,
    ACONFIGURATION_DENSITY_MEDIUM = 160,
    ACONFIGURATION_DENSITY_TV = 213,
    ACONFIGURATION_DENSITY_HIGH = 240,
    ACONFIGURATION_DENSITY_XHIGH = 320,
    ACONFIGURATION_DENSITY_XXHIGH = 480,
    ACONFIGURATION_DENSITY_XXXHIGH = 640,
    ACONFIGURATION_DENSITY_ANY = 0xfffe,
    ACONFIGURATION_DENSITY_NONE = 0xffff,

    ACONFIGURATION_MCC = 0x0001,
    ACONFIGURATION_MNC = 0x0002,
    ACONFIGURATION_LOCALE = 0x0004,
    ACONFIGURATION_TOUCHSCREEN = 0x0008,
    ACONFIGURATION_KEYBOARD = 0x0010,
    ACONFIGURATION_KEYBOARD_HIDDEN = 0x0020,
    ACONFIGURATION_NAVIGATION = 0x0040,
    ACONFIGURATION_ORIENTATION = 0x0080,
    ACONFIGURATION_DENSITY = 0x0100,
    ACONFIGURATION_SCREEN_SIZE = 0x0200,
    ACONFIGURATION_VERSION = 0x0400,
    ACONFIGURATION_SCREEN_LAYOUT = 0x0800,
    ACONFIGURATION_UI_MODE = 0x1000,
    ACONFIGURATION_SMALLEST_SCREEN_SIZE = 0x2000,
    ACONFIGURATION_LAYOUTDIR = 0x4000,
    ACONFIGURATION_SCREEN_ROUND = 0x8000,
};

extern "C" {
AConfiguration* AConfiguration_new();
void AConfiguration_delete(AConfiguration* config);
void AConfiguration_fromAssetManager(AConfiguration* out, AAssetManager* am);
void AConfiguration_copy(AConfiguration* dest, AConfiguration* src);
int32_t AConfiguration_getMcc(AConfiguration* config);
void AConfiguration_setMcc(AConfiguration* config, int32_t mcc);
int32_t AConfiguration_getMnc(AConfiguration* config);
void AConfiguration_setMnc(AConfiguration* config, int32_t mnc);
void AConfiguration_getLanguage(AConfiguration* config, char* outLanguage);
void AConfiguration_setLanguage(AConfiguration* config, const char* language);
void AConfiguration_getCountry(AConfiguration* config, char* outCountry);
void AConfiguration_setCountry(AConfiguration* config, const char* country);
int32_t AConfiguration_getOrientation(AConfiguration* config);
void AConfiguration_setOrientation(AConfiguration* config, int32_t orientation);
int32_t AConfiguration_getTouchscreen(AConfiguration* config);
void AConfiguration_setTouchscreen(AConfiguration* config, int32_t touchscreen);
int32_t AConfiguration_getDensity(AConfiguration* config);
void AConfiguration_setDensity(AConfiguration* config, int32_t density);
int32_t AConfiguration_getKeyboard(AConfiguration* config);
void AConfiguration_setKeyboard(AConfiguration* config, int32_t keyboard);
int32_t AConfiguration_getNavigation(AConfiguration* config);
void AConfiguration_setNavigation(AConfiguration* config, int32_t navigation);
int32_t AConfiguration_getKeysHidden(AConfiguration* config);
void AConfiguration_setKeysHidden(AConfiguration* config, int32_t keysHidden);
int32_t AConfiguration_getNavHidden(AConfiguration* config);
void AConfiguration_setNavHidden(AConfiguration* config, int32_t navHidden);
int32_t AConfiguration_getSdkVersion(AConfiguration* config);
void AConfiguration_setSdkVersion(AConfiguration* config, int32_t sdkVersion);
int32_t AConfiguration_getScreenSize(AConfiguration* config);
void AConfiguration_setScreenSize(AConfiguration* config, int32_t screenSize);
int32_t AConfiguration_getScreenLong(AConfiguration* config);
void AConfiguration_setScreenLong(AConfiguration* config, int32_t screenLong);
int32_t AConfiguration_getScreenRound(AConfiguration* config);
void AConfiguration_setScreenRound(AConfiguration* config, int32_t screenRound);
int32_t AConfiguration_getUiModeType(AConfiguration* config);
void AConfiguration_setUiModeType(AConfiguration* config, int32_t uiModeType);
int32_t AConfiguration_getUiModeNight(AConfiguration* config);
void AConfiguration_setUiModeNight(AConfiguration* config, int32_t uiModeNight);
int32_t AConfiguration_getScreenWidthDp(AConfiguration* config);
void AConfiguration_setScreenWidthDp(AConfiguration* config, int32_t value);
int32_t AConfiguration_getScreenHeightDp(AConfiguration* config);
void AConfiguration_setScreenHeightDp(AConfiguration* config, int32_t value);
int32_t AConfiguration_getSmallestScreenWidthDp(AConfiguration* config);
void AConfiguration_setSmallestScreenWidthDp(AConfiguration* config, int32_t value);
int32_t AConfiguration_getLayoutDirection(AConfiguration* config);
void AConfiguration_setLayoutDirection(AConfiguration* config, int32_t value);
int32_t AConfiguration_diff(AConfiguration* config1, AConfiguration* config2);
}
