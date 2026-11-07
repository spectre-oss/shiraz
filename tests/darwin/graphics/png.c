// SPDX-License-Identifier: GPL-3.0-only
// In-tree static libpng (from bootstrap_darwin.py) with the SDK zlib through the
// stub zlib.pc: encode and decode an RGBA image losslessly in memory.
#include <png.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <zlib.h>

int main(void) {
    enum { W = 9, H = 7 };
    unsigned char rgba[W * H * 4];
    for (int i = 0; i < W * H * 4; ++i) rgba[i] = (unsigned char) (i * 37);
    png_image image; memset(&image, 0, sizeof image);
    image.version = PNG_IMAGE_VERSION; image.width = W; image.height = H; image.format = PNG_FORMAT_RGBA;
    png_alloc_size_t size = 0;
    if (!png_image_write_to_memory(&image, NULL, &size, 0, rgba, 0, NULL)) { fprintf(stderr, "size: %s\n", image.message); return 1; }
    void* encoded = malloc(size);
    if (!png_image_write_to_memory(&image, encoded, &size, 0, rgba, 0, NULL)) { fprintf(stderr, "write: %s\n", image.message); return 1; }
    png_image read; memset(&read, 0, sizeof read); read.version = PNG_IMAGE_VERSION;
    unsigned char decoded[W * H * 4];
    int ok = png_image_begin_read_from_memory(&read, encoded, size);
    read.format = PNG_FORMAT_RGBA;
    ok = ok && read.width == W && read.height == H && png_image_finish_read(&read, NULL, decoded, 0, NULL);
    ok = ok && memcmp(decoded, rgba, sizeof rgba) == 0;
    printf("PNG_OK libpng=%s zlib=%s bytes=%lu exact=%d\n", png_get_libpng_ver(NULL), zlibVersion(), (unsigned long) size, ok);
    free(encoded);
    return ok ? 0 : 1;
}
