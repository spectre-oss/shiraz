// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE
#include <assert.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
static void readable(FILE *stream) {
    char data[5] = {0}; assert(stream);
    assert(fread(data,1,4,stream)==4 && !strcmp(data,"FONT")); assert(!fclose(stream));
}
int main(int argc, char **argv) {
    assert(argc==2);
    readable(fopen("/system/etc/fonts.xml","rb"));
    readable(fopen64("/system/fonts/NotoSans-Regular.ttf","r"));
    readable(fopen("/system/fonts/Roboto-Regular.ttf","rb"));
    readable(fopen("/system/fonts/DroidSansMono.ttf","rb"));
    readable(fopen("/system/fonts/NotoSerif-Regular.ttf","rb"));
    readable(fopen("/system/fonts/NotoNaskhArabic-Regular.ttf","rb"));
    readable(fopen(argv[1],"rb")); // Unrelated host paths retain normal stdio behavior.
    const char *modes[]={"w","wb","a","r+","r+b"};
    for(unsigned i=0;i<sizeof(modes)/sizeof(modes[0]);i++) {
        errno=0; assert(!fopen("/system/fonts/NotoSans-Regular.ttf",modes[i]) && errno==EROFS);
    }
    errno=0; assert(!fopen("/system/fonts/../android-fonts.xml","rb") && errno==ENOENT);
    errno=0; assert(!fopen("/system/fonts/unknown.ttf","rb") && errno==ENOENT);
    errno=0; assert(!fopen("/system/fonts/NotoSans-Bold.ttf","rb") && errno==ELOOP);
    errno=0; assert(!fopen("/system/fonts/NotoSansArabic-Regular.ttf","rb") && errno==EINVAL);
    errno=0; assert(!fopen("/system/fonts/NotoSansHebrew-Regular.ttf","rb") && errno==EINVAL);
    errno=0; assert(!fopen("/system/fonts/NotoEmoji[wght].ttf","rb") && errno==EINVAL);
    puts("ANDROID_FONT_PATHS_READONLY_BOUNDS_OK");
}
