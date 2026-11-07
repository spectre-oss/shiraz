// SPDX-License-Identifier: GPL-3.0-only
// Static in-tree libwebp: lossless round trip must be exact; lossy must decode
// to the same size and close colours.
#include <webp/decode.h>
#include <webp/encode.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void) {
    enum { W = 24, H = 16 };
    uint8_t rgba[W * H * 4];
    for (int y = 0; y < H; ++y)
        for (int x = 0; x < W; ++x) {
            uint8_t* p = rgba + (y * W + x) * 4;
            p[0] = (uint8_t) (x * 10); p[1] = (uint8_t) (y * 15); p[2] = 0x56; p[3] = x < W / 2 ? 0xff : 0x80;
        }
    uint8_t* encoded = NULL;
    size_t size = WebPEncodeLosslessRGBA(rgba, W, H, W * 4, &encoded);
    int width = 0, height = 0;
    uint8_t* decoded = size ? WebPDecodeRGBA(encoded, size, &width, &height) : NULL;
    int lossless = decoded && width == W && height == H && memcmp(decoded, rgba, sizeof rgba) == 0;
    printf("WEBP lossless bytes=%zu decoded=%dx%d exact=%d\n", size, width, height, lossless);
    WebPFree(decoded); WebPFree(encoded);
    size = WebPEncodeRGBA(rgba, W, H, W * 4, 95, &encoded);
    decoded = size ? WebPDecodeRGBA(encoded, size, &width, &height) : NULL;
    int worst = 255;
    if (decoded && width == W && height == H) {
        worst = 0;
        for (int i = 0; i < W * H * 4; ++i) { int d = abs(decoded[i] - rgba[i]); if (d > worst) worst = d; }
    }
    printf("WEBP_OK version=%06x lossy bytes=%zu decoded=%dx%d max_delta=%d\n", WebPGetDecoderVersion(), size, width,
           height, worst);
    WebPFree(decoded); WebPFree(encoded);
    return lossless && worst <= 40 ? 0 : 1;
}
