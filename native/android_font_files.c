// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

// Loaded only into the host ART process by its ELF loader. No global installation.
static pthread_once_t initialized = PTHREAD_ONCE_INIT;
static FILE *(*host_fopen)(const char *, const char *);
static FILE *(*host_fopen64)(const char *, const char *);
static void initialize(void) {
    *(void **)(&host_fopen) = dlsym(RTLD_NEXT, "fopen");
    *(void **)(&host_fopen64) = dlsym(RTLD_NEXT, "fopen64");
}
static const char *font_name(const char *path) {
    static const char *names[] = {
        "Roboto-Regular.ttf", "DroidSansMono.ttf",
        "NotoSerif-Regular.ttf", "NotoSerif-Bold.ttf", "NotoSerif-Italic.ttf", "NotoSerif-BoldItalic.ttf",
        "NotoNaskhArabic-Regular.ttf", "NotoNaskhArabic-Bold.ttf",
        "NotoSans-Regular.ttf", "NotoSans-Bold.ttf",
        "NotoSansArabic-Regular.ttf", "NotoSansArabic-Bold.ttf",
        "NotoSansHebrew-Regular.ttf", "NotoSansHebrew-Bold.ttf",
        "NotoSansLao-Regular.ttf", "NotoSansLao-Bold.ttf",
        "NotoColorEmoji.ttf", "NotoEmoji[wght].ttf"
    };
    for (size_t i = 0; i < sizeof(names) / sizeof(names[0]); ++i)
        if (!strcmp(path, names[i])) return names[i];
    return NULL;
}
static FILE *open_font(const char *path, const char *mode, int *handled) {
    *handled = 0;
    const char *directory = getenv("SHIRAZ_ANDROID_FONT_DIR");
    if (!directory || !directory[0]) return NULL;
    const char *name = NULL;
    if (!strcmp(path, "/system/etc/fonts.xml")) name = "android-fonts.xml";
    else if (!strncmp(path, "/system/fonts/", 14)) {
        *handled = 1;
        name = font_name(path + 14);
        if (!name) { errno = ENOENT; return NULL; }
    } else return NULL;
    *handled = 1;
    // Never let Android resource reads become writes to bundled font assets.
    if (strcmp(mode, "r") && strcmp(mode, "rb") && strcmp(mode, "re") && strcmp(mode, "rbe")) {
        errno = EROFS; return NULL;
    }
    if (directory[0] != '/') { errno = EINVAL; return NULL; }
    int dir = open(directory, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (dir < 0) return NULL;
    int fd = openat(dir, name, O_RDONLY | O_NOFOLLOW | O_NONBLOCK | O_CLOEXEC);
    int saved = errno; close(dir); errno = saved;
    if (fd < 0) return NULL;
    struct stat info;
    if (fstat(fd, &info)) { saved = errno; close(fd); errno = saved; return NULL; }
    if (!S_ISREG(info.st_mode) || info.st_size <= 0 || info.st_size > 64 * 1024 * 1024) {
        close(fd); errno = EINVAL; return NULL;
    }
    FILE *stream = fdopen(fd, "rb");
    if (!stream) { saved = errno; close(fd); errno = saved; }
    return stream;
}
FILE *fopen(const char *path, const char *mode) {
    int handled; FILE *stream = open_font(path, mode, &handled);
    if (handled) return stream;
    pthread_once(&initialized, initialize);
    if (!host_fopen) { errno = ENOSYS; return NULL; }
    return host_fopen(path, mode);
}
FILE *fopen64(const char *path, const char *mode) {
    int handled; FILE *stream = open_font(path, mode, &handled);
    if (handled) return stream;
    pthread_once(&initialized, initialize);
    if (!host_fopen64) { errno = ENOSYS; return NULL; }
    return host_fopen64(path, mode);
}
