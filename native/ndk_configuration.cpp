// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/ndk/configuration.h"
#include <cstdlib>
#include <cstring>
#include <new>

struct AConfiguration {
    int32_t mcc = 0;
    int32_t mnc = 0;
    char language[2] = {0, 0};
    char country[2] = {0, 0};
    int32_t orientation = ACONFIGURATION_ORIENTATION_ANY;
    int32_t touchscreen = ACONFIGURATION_TOUCHSCREEN_ANY;
    int32_t density = ACONFIGURATION_DENSITY_DEFAULT;
    int32_t keyboard = 0;
    int32_t navigation = 0;
    int32_t keysHidden = 0;
    int32_t navHidden = 0;
    int32_t sdkVersion = 35;
    int32_t screenSize = 0;
    int32_t screenLong = 0;
    int32_t screenRound = 0;
    int32_t uiModeType = 0;
    int32_t uiModeNight = 0;
    int32_t screenWidthDp = 0;
    int32_t screenHeightDp = 0;
    int32_t smallestScreenWidthDp = 0;
    int32_t layoutDirection = 0;
};

extern "C" {

AConfiguration* AConfiguration_new() {
    return new (std::nothrow) AConfiguration();
}

void AConfiguration_delete(AConfiguration* config) {
    delete config;
}

void AConfiguration_fromAssetManager(AConfiguration* out, AAssetManager*) {
    if (!out)
        return;
    *out = AConfiguration{};
    out->sdkVersion = 35;
    out->density = ACONFIGURATION_DENSITY_XHIGH;
    out->orientation = ACONFIGURATION_ORIENTATION_PORT;
    out->touchscreen = ACONFIGURATION_TOUCHSCREEN_FINGER;
    const char* lang = std::getenv("LANG");
    if (lang && std::strlen(lang) >= 2 && lang[0] >= 'a' && lang[0] <= 'z' && lang[1] >= 'a' &&
        lang[1] <= 'z') {
        out->language[0] = lang[0];
        out->language[1] = lang[1];
        if (std::strlen(lang) >= 5 && (lang[2] == '_' || lang[2] == '-') && lang[3] >= 'A' &&
            lang[3] <= 'Z' && lang[4] >= 'A' && lang[4] <= 'Z') {
            out->country[0] = lang[3];
            out->country[1] = lang[4];
        }
    } else {
        out->language[0] = 'e';
        out->language[1] = 'n';
        out->country[0] = 'U';
        out->country[1] = 'S';
    }
}

void AConfiguration_copy(AConfiguration* dest, AConfiguration* src) {
    if (!dest || !src)
        return;
    *dest = *src;
}

int32_t AConfiguration_getMcc(AConfiguration* config) {
    return config ? config->mcc : 0;
}
void AConfiguration_setMcc(AConfiguration* config, int32_t mcc) {
    if (config)
        config->mcc = mcc;
}

int32_t AConfiguration_getMnc(AConfiguration* config) {
    return config ? config->mnc : 0;
}
void AConfiguration_setMnc(AConfiguration* config, int32_t mnc) {
    if (config)
        config->mnc = mnc;
}

void AConfiguration_getLanguage(AConfiguration* config, char* outLanguage) {
    if (!outLanguage)
        return;
    if (!config) {
        outLanguage[0] = 0;
        outLanguage[1] = 0;
        return;
    }
    outLanguage[0] = config->language[0];
    outLanguage[1] = config->language[1];
}
void AConfiguration_setLanguage(AConfiguration* config, const char* language) {
    if (!config)
        return;
    config->language[0] = language && language[0] ? language[0] : 0;
    config->language[1] = language && language[0] && language[1] ? language[1] : 0;
}

void AConfiguration_getCountry(AConfiguration* config, char* outCountry) {
    if (!outCountry)
        return;
    if (!config) {
        outCountry[0] = 0;
        outCountry[1] = 0;
        return;
    }
    outCountry[0] = config->country[0];
    outCountry[1] = config->country[1];
}
void AConfiguration_setCountry(AConfiguration* config, const char* country) {
    if (!config)
        return;
    config->country[0] = country && country[0] ? country[0] : 0;
    config->country[1] = country && country[0] && country[1] ? country[1] : 0;
}

int32_t AConfiguration_getOrientation(AConfiguration* config) {
    return config ? config->orientation : 0;
}
void AConfiguration_setOrientation(AConfiguration* config, int32_t orientation) {
    if (config)
        config->orientation = orientation;
}

int32_t AConfiguration_getTouchscreen(AConfiguration* config) {
    return config ? config->touchscreen : 0;
}
void AConfiguration_setTouchscreen(AConfiguration* config, int32_t touchscreen) {
    if (config)
        config->touchscreen = touchscreen;
}

int32_t AConfiguration_getDensity(AConfiguration* config) {
    return config ? config->density : 0;
}
void AConfiguration_setDensity(AConfiguration* config, int32_t density) {
    if (config)
        config->density = density;
}

int32_t AConfiguration_getKeyboard(AConfiguration* config) {
    return config ? config->keyboard : 0;
}
void AConfiguration_setKeyboard(AConfiguration* config, int32_t keyboard) {
    if (config)
        config->keyboard = keyboard;
}

int32_t AConfiguration_getNavigation(AConfiguration* config) {
    return config ? config->navigation : 0;
}
void AConfiguration_setNavigation(AConfiguration* config, int32_t navigation) {
    if (config)
        config->navigation = navigation;
}

int32_t AConfiguration_getKeysHidden(AConfiguration* config) {
    return config ? config->keysHidden : 0;
}
void AConfiguration_setKeysHidden(AConfiguration* config, int32_t keysHidden) {
    if (config)
        config->keysHidden = keysHidden;
}

int32_t AConfiguration_getNavHidden(AConfiguration* config) {
    return config ? config->navHidden : 0;
}
void AConfiguration_setNavHidden(AConfiguration* config, int32_t navHidden) {
    if (config)
        config->navHidden = navHidden;
}

int32_t AConfiguration_getSdkVersion(AConfiguration* config) {
    return config ? config->sdkVersion : 0;
}
void AConfiguration_setSdkVersion(AConfiguration* config, int32_t sdkVersion) {
    if (config)
        config->sdkVersion = sdkVersion;
}

int32_t AConfiguration_getScreenSize(AConfiguration* config) {
    return config ? config->screenSize : 0;
}
void AConfiguration_setScreenSize(AConfiguration* config, int32_t screenSize) {
    if (config)
        config->screenSize = screenSize;
}

int32_t AConfiguration_getScreenLong(AConfiguration* config) {
    return config ? config->screenLong : 0;
}
void AConfiguration_setScreenLong(AConfiguration* config, int32_t screenLong) {
    if (config)
        config->screenLong = screenLong;
}

int32_t AConfiguration_getScreenRound(AConfiguration* config) {
    return config ? config->screenRound : 0;
}
void AConfiguration_setScreenRound(AConfiguration* config, int32_t screenRound) {
    if (config)
        config->screenRound = screenRound;
}

int32_t AConfiguration_getUiModeType(AConfiguration* config) {
    return config ? config->uiModeType : 0;
}
void AConfiguration_setUiModeType(AConfiguration* config, int32_t uiModeType) {
    if (config)
        config->uiModeType = uiModeType;
}

int32_t AConfiguration_getUiModeNight(AConfiguration* config) {
    return config ? config->uiModeNight : 0;
}
void AConfiguration_setUiModeNight(AConfiguration* config, int32_t uiModeNight) {
    if (config)
        config->uiModeNight = uiModeNight;
}

int32_t AConfiguration_getScreenWidthDp(AConfiguration* config) {
    return config ? config->screenWidthDp : 0;
}
void AConfiguration_setScreenWidthDp(AConfiguration* config, int32_t value) {
    if (config)
        config->screenWidthDp = value;
}

int32_t AConfiguration_getScreenHeightDp(AConfiguration* config) {
    return config ? config->screenHeightDp : 0;
}
void AConfiguration_setScreenHeightDp(AConfiguration* config, int32_t value) {
    if (config)
        config->screenHeightDp = value;
}

int32_t AConfiguration_getSmallestScreenWidthDp(AConfiguration* config) {
    return config ? config->smallestScreenWidthDp : 0;
}
void AConfiguration_setSmallestScreenWidthDp(AConfiguration* config, int32_t value) {
    if (config)
        config->smallestScreenWidthDp = value;
}

int32_t AConfiguration_getLayoutDirection(AConfiguration* config) {
    return config ? config->layoutDirection : 0;
}
void AConfiguration_setLayoutDirection(AConfiguration* config, int32_t value) {
    if (config)
        config->layoutDirection = value;
}

int32_t AConfiguration_diff(AConfiguration* a, AConfiguration* b) {
    if (!a || !b)
        return 0;
    int32_t mask = 0;
    if (a->mcc != b->mcc)
        mask |= ACONFIGURATION_MCC;
    if (a->mnc != b->mnc)
        mask |= ACONFIGURATION_MNC;
    if (a->language[0] != b->language[0] || a->language[1] != b->language[1] ||
        a->country[0] != b->country[0] || a->country[1] != b->country[1])
        mask |= ACONFIGURATION_LOCALE;
    if (a->touchscreen != b->touchscreen)
        mask |= ACONFIGURATION_TOUCHSCREEN;
    if (a->keyboard != b->keyboard)
        mask |= ACONFIGURATION_KEYBOARD;
    if (a->keysHidden != b->keysHidden || a->navHidden != b->navHidden)
        mask |= ACONFIGURATION_KEYBOARD_HIDDEN;
    if (a->navigation != b->navigation)
        mask |= ACONFIGURATION_NAVIGATION;
    if (a->orientation != b->orientation)
        mask |= ACONFIGURATION_ORIENTATION;
    if (a->density != b->density)
        mask |= ACONFIGURATION_DENSITY;
    if (a->screenWidthDp != b->screenWidthDp || a->screenHeightDp != b->screenHeightDp)
        mask |= ACONFIGURATION_SCREEN_SIZE;
    if (a->sdkVersion != b->sdkVersion)
        mask |= ACONFIGURATION_VERSION;
    if (a->screenSize != b->screenSize || a->screenLong != b->screenLong)
        mask |= ACONFIGURATION_SCREEN_LAYOUT;
    if (a->uiModeType != b->uiModeType || a->uiModeNight != b->uiModeNight)
        mask |= ACONFIGURATION_UI_MODE;
    if (a->smallestScreenWidthDp != b->smallestScreenWidthDp)
        mask |= ACONFIGURATION_SMALLEST_SCREEN_SIZE;
    if (a->layoutDirection != b->layoutDirection)
        mask |= ACONFIGURATION_LAYOUTDIR;
    if (a->screenRound != b->screenRound)
        mask |= ACONFIGURATION_SCREEN_ROUND;
    return mask;
}

} // extern "C"
