// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <optional>
#include <stdexcept>
#include <vector>

namespace shiraz {
struct Point { double x, y; };
struct Viewport { double x, y, width, height; };
inline Viewport fit(int app_width, int app_height, int window_width, int window_height) {
    if (app_width <= 0 || app_height <= 0 || window_width <= 0 || window_height <= 0)
        throw std::invalid_argument("dimensions must be positive");
    double scale = std::min(double(window_width) / app_width, double(window_height) / app_height);
    double w = app_width * scale, h = app_height * scale;
    return {(window_width - w) / 2, (window_height - h) / 2, w, h};
}
inline std::optional<Point> map_pointer(Point p, Viewport v, int width, int height) {
    if (width <= 0 || height <= 0 || v.width <= 0 || v.height <= 0 ||
        p.x < v.x || p.y < v.y || p.x >= v.x + v.width || p.y >= v.y + v.height)
        return std::nullopt;
    return Point{(p.x - v.x) * width / v.width, (p.y - v.y) * height / v.height};
}
struct Surface {
    int width, height;
    std::vector<uint32_t> pixels; // Straight alpha ARGB8888, top-left origin.
    Surface(int w, int h, uint32_t color = 0) : width(w), height(h) {
        if (w <= 0 || h <= 0 || w > 8192 || h > 8192 || uint64_t(w) * h > 16 * 1024 * 1024)
            throw std::invalid_argument("invalid surface dimensions");
        pixels.assign(size_t(w) * h, color);
    }
};
struct Layer { const Surface* surface; int x, y, z; uint8_t opacity = 255; bool visible = true; };
inline uint32_t over(uint32_t src, uint32_t dst, unsigned opacity) {
    unsigned sa = ((src >> 24) * opacity + 127) / 255, da = dst >> 24;
    if (!sa) return da ? dst : 0;
    if (sa == 255) return src;
    if (da == 255) {
        // An opaque destination keeps output alpha opaque; use a constant
        // divisor instead of three per-pixel divisions by computed alpha.
        uint32_t out = 0xff000000u;
        for (unsigned shift : {0u, 8u, 16u})
            out |= ((((src >> shift) & 255) * sa + ((dst >> shift) & 255) * (255 - sa) + 127) / 255) << shift;
        return out;
    }
    unsigned remainder = (da * (255 - sa) + 127) / 255, alpha = sa + remainder;
    if (!alpha) return 0;
    uint32_t out = alpha << 24;
    for (unsigned shift : {0u, 8u, 16u}) {
        unsigned s = (src >> shift) & 255, d = (dst >> shift) & 255;
        out |= ((s * sa + d * remainder + alpha / 2) / alpha) << shift;
    }
    return out;
}
inline Surface compose(int width, int height, std::vector<Layer> layers, uint32_t background = 0xff000000) {
    Surface result(width, height, background);
    std::stable_sort(layers.begin(), layers.end(), [](const Layer& a, const Layer& b) { return a.z < b.z; });
    for (const auto& layer : layers) {
        if (!layer.visible || !layer.surface || !layer.opacity) continue;
        const auto& source = *layer.surface;
        if (source.pixels.size() != size_t(source.width) * source.height)
            throw std::invalid_argument("surface storage size mismatch");
        // 64-bit bounds keep clipping safe even for extreme layer coordinates.
        int64_t left = std::max<int64_t>(0, layer.x), top = std::max<int64_t>(0, layer.y);
        int64_t right = std::min<int64_t>(width, int64_t(layer.x) + source.width);
        int64_t bottom = std::min<int64_t>(height, int64_t(layer.y) + source.height);
        for (auto y = top; y < bottom; ++y)
            for (auto x = left; x < right; ++x) {
                auto& dst = result.pixels[size_t(y) * width + x];
                dst = over(source.pixels[size_t(y - layer.y) * source.width + x - layer.x], dst, layer.opacity);
            }
    }
    return result;
}
} // namespace shiraz
