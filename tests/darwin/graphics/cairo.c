// SPDX-License-Identifier: GPL-3.0-only
// Static in-tree cairo/pixman: fill a rectangle and an antialiased circle into
// an ARGB32 image surface and read the pixels back.
#include <cairo.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>

static uint32_t pixel(cairo_surface_t* surface, int x, int y) {
    unsigned char* data = cairo_image_surface_get_data(surface);
    return *(uint32_t*) (data + y * cairo_image_surface_get_stride(surface) + x * 4);
}

int main(void) {
    cairo_surface_t* surface = cairo_image_surface_create(CAIRO_FORMAT_ARGB32, 64, 64);
    cairo_t* cr = cairo_create(surface);
    cairo_set_source_rgba(cr, 0x12 / 255.0, 0x34 / 255.0, 0x56 / 255.0, 1);
    cairo_rectangle(cr, 8, 8, 16, 16);
    cairo_fill(cr);
    cairo_set_source_rgba(cr, 1, 0, 0, 1);
    cairo_arc(cr, 44, 44, 10, 0, 2 * M_PI);
    cairo_fill(cr);
    cairo_surface_flush(surface);
    int status = cairo_status(cr);
    uint32_t inside = pixel(surface, 16, 16), outside = pixel(surface, 2, 2), circle = pixel(surface, 44, 44);
    // A pixel on the circle's edge is partially covered (antialiased, premultiplied).
    uint32_t edge = pixel(surface, 51, 51);
    unsigned edge_alpha = edge >> 24;
    printf("CAIRO_OK version=%s status=%s inside=%08x outside=%08x circle=%08x edge=%08x\n", cairo_version_string(),
           cairo_status_to_string(status), inside, outside, circle, edge);
    cairo_destroy(cr); cairo_surface_destroy(surface);
    return status == CAIRO_STATUS_SUCCESS && inside == 0xff123456u && outside == 0 && circle == 0xffff0000u
        && edge_alpha > 0 && edge_alpha < 255 ? 0 : 1;
}
