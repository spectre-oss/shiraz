// SPDX-License-Identifier: GPL-3.0-only
// Static in-tree HarfBuzz through hb-ft: shape "Hello" with a bundled TTF.
#include <ft2build.h>
#include FT_FREETYPE_H
#include <hb-ft.h>
#include <stdio.h>
#include <string.h>

int main(int argc, char** argv) {
    if (argc != 2) { fprintf(stderr, "usage: %s font.ttf\n", argv[0]); return 2; }
    FT_Library library; FT_Face face;
    if (FT_Init_FreeType(&library) || FT_New_Face(library, argv[1], 0, &face)) { fprintf(stderr, "cannot open font\n"); return 1; }
    FT_Set_Char_Size(face, 0, 32 * 64, 72, 72);
    hb_font_t* font = hb_ft_font_create_referenced(face);
    hb_buffer_t* buffer = hb_buffer_create();
    const char* text = "Hello";
    hb_buffer_add_utf8(buffer, text, -1, 0, -1);
    hb_buffer_guess_segment_properties(buffer);
    hb_shape(font, buffer, NULL, 0);
    unsigned count = 0;
    hb_glyph_info_t* info = hb_buffer_get_glyph_infos(buffer, &count);
    hb_glyph_position_t* pos = hb_buffer_get_glyph_positions(buffer, NULL);
    int advance = 0, ok = count == strlen(text) && hb_buffer_allocation_successful(buffer);
    printf("HARFBUZZ_OK version=%s glyphs=", hb_version_string());
    for (unsigned i = 0; i < count; ++i) {
        printf("%s%u", i ? "," : "", info[i].codepoint);
        advance += pos[i].x_advance;
        ok = ok && info[i].codepoint != 0 && pos[i].x_advance > 0 && info[i].cluster == i;
    }
    // 'l' 'l' must map to the same glyph.
    ok = ok && info[2].codepoint == info[3].codepoint;
    printf(" advance=%d direction=%s\n", advance, hb_direction_to_string(hb_buffer_get_direction(buffer)));
    hb_buffer_destroy(buffer); hb_font_destroy(font);
    FT_Done_Face(face); FT_Done_FreeType(library);
    return ok && advance > 0 ? 0 : 1;
}
