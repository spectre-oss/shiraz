// SPDX-License-Identifier: GPL-3.0-only
// Static in-tree fontconfig with no FONTCONFIG_* environment: the compiled-in
// configuration must be the in-tree fonts.conf, every cache directory must be
// inside the repository, and "sans-serif" must match an existing font file.
// Requested the way native/text.cpp requests a fallback ("sans-serif", regular,
// roman, plus the text's charset), Latin, Persian and Hebrew must each match a
// bundled font that covers every code point, never a host font such as Arial.
#include <fontconfig/fontconfig.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>

static int inside(const char* path, const char* root) {
    size_t length = strlen(root);
    return strncmp(path, root, length) == 0 && path[length] == '/';
}

static int bundled_match(FcConfig* config, const char* fonts, const char* name, const FcChar32* points, int count) {
    FcPattern* pattern = FcPatternCreate();
    FcCharSet* chars = FcCharSetCreate();
    for (int i = 0; i < count; ++i) FcCharSetAddChar(chars, points[i]);
    FcPatternAddCharSet(pattern, FC_CHARSET, chars);
    FcPatternAddString(pattern, FC_FAMILY, (const FcChar8*) "sans-serif");
    FcPatternAddInteger(pattern, FC_WEIGHT, FC_WEIGHT_REGULAR);
    FcPatternAddInteger(pattern, FC_SLANT, FC_SLANT_ROMAN);
    FcConfigSubstitute(config, pattern, FcMatchPattern);
    FcDefaultSubstitute(pattern);
    FcResult result;
    FcPattern* match = FcFontMatch(config, pattern, &result);
    FcChar8* path = NULL; FcCharSet* covered = NULL;
    int ok = match && FcPatternGetString(match, FC_FILE, 0, &path) == FcResultMatch
             && FcPatternGetCharSet(match, FC_CHARSET, 0, &covered) == FcResultMatch
             && inside((const char*) path, fonts);
    for (int i = 0; ok && i < count; ++i) ok = FcCharSetHasChar(covered, points[i]);
    printf("FONTCONFIG_SCRIPT %s=%s %s\n", name, path ? (const char*) path : "(none)", ok ? "bundled" : "NOT-BUNDLED");
    if (match) FcPatternDestroy(match);
    FcCharSetDestroy(chars); FcPatternDestroy(pattern);
    return ok;
}

int main(int argc, char** argv) {
    if (argc != 3) { fprintf(stderr, "usage: %s repo-root bundled-font-dir\n", argv[0]); return 2; }
    const char* root = argv[1];
    FcConfig* config = FcInitLoadConfigAndFonts();
    if (!config) { fprintf(stderr, "FcInitLoadConfigAndFonts failed\n"); return 1; }
    if (!FcConfigAppFontAddDir(config, (const FcChar8*) argv[2])) { fprintf(stderr, "FcConfigAppFontAddDir failed\n"); return 1; }
    int ok = 1;
    FcChar8* file = FcConfigGetFilename(config, NULL);
    printf("FONTCONFIG config=%s\n", file ? (const char*) file : "(none)");
    ok = ok && file && inside((const char*) file, root);
    FcStrFree(file);
    FcStrList* caches = FcConfigGetCacheDirs(config);
    FcChar8* directory; int count = 0;
    while ((directory = FcStrListNext(caches))) {
        printf("FONTCONFIG cachedir=%s\n", directory);
        ok = ok && inside((const char*) directory, root);
        ++count;
    }
    FcStrListDone(caches);
    ok = ok && count > 0;
    FcStrList* dirs = FcConfigGetFontDirs(config);
    while ((directory = FcStrListNext(dirs))) printf("FONTCONFIG fontdir=%s\n", directory);
    FcStrListDone(dirs);
    FcPattern* pattern = FcNameParse((const FcChar8*) "sans-serif");
    FcConfigSubstitute(config, pattern, FcMatchPattern);
    FcDefaultSubstitute(pattern);
    FcResult result;
    FcPattern* match = FcFontMatch(config, pattern, &result);
    FcChar8* path = NULL; FcChar8* family = NULL;
    struct stat info;
    if (!match || FcPatternGetString(match, FC_FILE, 0, &path) != FcResultMatch || stat((const char*) path, &info) != 0) {
        fprintf(stderr, "sans-serif did not match an existing file\n");
        return 1;
    }
    FcPatternGetString(match, FC_FAMILY, 0, &family);
    // tests/macos-apk-graphics: "Shiraz", Persian "Shiraz" and Hebrew "shalom".
    static const FcChar32 latin[] = {'S', 'h', 'i', 'r', 'a', 'z'};
    static const FcChar32 persian[] = {0x0634, 0x06cc, 0x0631, 0x0627, 0x0632};
    static const FcChar32 hebrew[] = {0x05e9, 0x05dc, 0x05d5, 0x05dd};
    ok = bundled_match(config, argv[2], "latin", latin, 6) && ok;
    ok = bundled_match(config, argv[2], "persian", persian, 5) && ok;
    ok = bundled_match(config, argv[2], "hebrew", hebrew, 4) && ok;
    printf("FONTCONFIG_OK version=%d sans-serif=%s family=%s\n", FcGetVersion(), path, family ? (const char*) family : "?");
    FcPatternDestroy(match); FcPatternDestroy(pattern); FcConfigDestroy(config);
    return ok ? 0 : 1;
}
