// SPDX-License-Identifier: GPL-3.0-only
// Static in-tree libjpeg-turbo: encode an RGB image to memory and decode it.
#include <stdio.h>
#include <stdlib.h>
#include <jpeglib.h>

int main(void) {
    enum { W = 32, H = 16 };
    unsigned char rgb[W * H * 3];
    for (int i = 0; i < W * H; ++i) {
        int x = i % W;
        rgb[i * 3] = x < W / 2 ? 0x12 : 0xc0; rgb[i * 3 + 1] = 0x34; rgb[i * 3 + 2] = 0x56;
    }
    struct jpeg_compress_struct c; struct jpeg_error_mgr error;
    c.err = jpeg_std_error(&error);
    jpeg_create_compress(&c);
    unsigned char* encoded = NULL; unsigned long size = 0;
    jpeg_mem_dest(&c, &encoded, &size);
    c.image_width = W; c.image_height = H; c.input_components = 3; c.in_color_space = JCS_RGB;
    jpeg_set_defaults(&c);
    jpeg_set_quality(&c, 95, TRUE);
    jpeg_start_compress(&c, TRUE);
    while (c.next_scanline < c.image_height) { JSAMPROW row = rgb + c.next_scanline * W * 3; jpeg_write_scanlines(&c, &row, 1); }
    jpeg_finish_compress(&c); jpeg_destroy_compress(&c);
    struct jpeg_decompress_struct d;
    d.err = jpeg_std_error(&error);
    jpeg_create_decompress(&d);
    jpeg_mem_src(&d, encoded, size);
    jpeg_read_header(&d, TRUE);
    d.out_color_space = JCS_RGB;
    jpeg_start_decompress(&d);
    unsigned char decoded[W * H * 3];
    int ok = d.output_width == W && d.output_height == H && d.output_components == 3;
    while (ok && d.output_scanline < d.output_height) { JSAMPROW row = decoded + d.output_scanline * W * 3; jpeg_read_scanlines(&d, &row, 1); }
    int worst = 0;
    // Compare away from the vertical colour edge, where JPEG ringing is expected.
    for (int i = 0; ok && i < W * H; ++i) {
        int x = i % W;
        if (x >= W / 2 - 2 && x < W / 2 + 2) continue;
        for (int k = 0; k < 3; ++k) { int delta = abs(decoded[i * 3 + k] - rgb[i * 3 + k]); if (delta > worst) worst = delta; }
    }
    printf("JPEG_OK lib_version=%d bytes=%lu decoded=%ux%u max_delta=%d\n", JPEG_LIB_VERSION, size, d.output_width,
           d.output_height, worst);
    jpeg_finish_decompress(&d); jpeg_destroy_decompress(&d); free(encoded);
    return ok && worst <= 8 ? 0 : 1;
}
