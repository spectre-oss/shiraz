// SPDX-License-Identifier: GPL-3.0-only
// Static in-tree FreeType: open a bundled TTF and rasterize a glyph.
#include <ft2build.h>
#include FT_FREETYPE_H
#include <stdio.h>

int main(int argc, char** argv) {
    if (argc != 2) { fprintf(stderr, "usage: %s font.ttf\n", argv[0]); return 2; }
    FT_Library library; FT_Face face;
    if (FT_Init_FreeType(&library)) { fprintf(stderr, "FT_Init_FreeType failed\n"); return 1; }
    if (FT_New_Face(library, argv[1], 0, &face)) { fprintf(stderr, "cannot open %s\n", argv[1]); return 1; }
    if (FT_Set_Pixel_Sizes(face, 0, 48) || FT_Load_Char(face, 'A', FT_LOAD_RENDER)) { fprintf(stderr, "cannot render A\n"); return 1; }
    FT_Bitmap* bitmap = &face->glyph->bitmap;
    unsigned ink = 0;
    for (unsigned y = 0; y < bitmap->rows; ++y)
        for (unsigned x = 0; x < bitmap->width; ++x) ink += bitmap->buffer[y * bitmap->pitch + x] > 127;
    FT_Int major, minor, patch; FT_Library_Version(library, &major, &minor, &patch);
    printf("FREETYPE_OK version=%d.%d.%d family=%s glyph=%ux%u ink=%u\n", major, minor, patch, face->family_name,
           bitmap->width, bitmap->rows, ink);
    int ok = bitmap->width > 10 && bitmap->rows > 10 && ink > 50;
    FT_Done_Face(face); FT_Done_FreeType(library);
    return ok ? 0 : 1;
}
