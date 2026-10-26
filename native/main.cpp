// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/compositor.hpp"
#include "shiraz/native_window.h"
#include <memory>
#include "version.hpp"
#include "shiraz/presentation.hpp"
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <string>

int main(int argc, char** argv) {
    int frames = -1;
    std::string snapshot;
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--version") { std::cout << "Shiraz " << SHIRAZ_VERSION << '\n'; return 0; }
        if (arg == "--frames" && i + 1 < argc) {
            try { size_t end; std::string value = argv[++i]; frames = std::stoi(value, &end);
                if (end != value.size() || frames < 1) throw std::invalid_argument("frames");
            } catch (...) { std::cerr << "--frames requires a positive integer\n"; return 1; }
        } else if (arg == "--snapshot" && i + 1 < argc) { snapshot = argv[++i]; }
        else { std::cerr << "Usage: shiraz-preview [--version] [--frames N] [--snapshot file.ppm]\n"; return 1; }
    }
    shiraz::Surface base(360, 640, 0xff17212b), panel(320, 180, 0xdd4f83cc), bar(360, 40, 0xff263544);
    std::unique_ptr<ANativeWindow, decltype(&ANativeWindow_release)> native(
        shiraz_window_create(360, 640), ANativeWindow_release);
    ANativeWindow_Buffer buffer{};
    if (!native || ANativeWindow_lock(native.get(), &buffer, nullptr) != 0) {
        std::cerr << "Native buffer allocation failed\n"; return 1;
    }
    auto* rgba = static_cast<uint8_t*>(buffer.bits);
    for (size_t i = 0; i < base.pixels.size(); ++i) {
        auto pixel = base.pixels[i];
        rgba[i * 4] = uint8_t(pixel >> 16); rgba[i * 4 + 1] = uint8_t(pixel >> 8);
        rgba[i * 4 + 2] = uint8_t(pixel); rgba[i * 4 + 3] = uint8_t(pixel >> 24);
    }
    ANativeWindow_unlockAndPost(native.get());
    shiraz_window_copy_argb(native.get(), base.pixels.data(), base.pixels.size());
    auto image = shiraz::compose(360, 640, {{&base, 0, 0, 0}, {&panel, 20, 100, 1}, {&bar, 0, 0, 2}});
    try {
        shiraz::present(image, std::string("Shiraz ") + SHIRAZ_VERSION + " | compositor preview (not Android execution)", frames);
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n'; return 1;
    }
    int result = 0;
    if (!snapshot.empty()) {
        std::ofstream file(snapshot, std::ios::binary);
        file << "P6\n360 640\n255\n";
        for (auto pixel : image.pixels) { const char rgb[] = {char(pixel >> 16), char(pixel >> 8), char(pixel)}; file.write(rgb, 3); }
        if (!file) { std::cerr << "Cannot write snapshot\n"; result = 1; }
    }
    return result;
}
