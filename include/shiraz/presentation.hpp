// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/compositor.hpp"
#include "shiraz/display_geometry.hpp"
#include "shiraz/pointer.hpp"
#include "shiraz/keyboard.hpp"
#include <string>
#include <functional>
namespace shiraz {
DisplayGeometry prepare_presentation(int logicalWidth,int logicalHeight);
void discard_prepared_presentation();
float presentation_refresh_rate();
bool presentation_has_touchscreen();
void request_text_input(bool visible);
bool uses_wayland_text_input();
void request_screen_awake(bool awake);
void request_pointer_icon(int type);
void request_window_title(const std::string& title);
void request_window_size(int width,int height);
bool set_clipboard_text(const std::string& text);
std::string get_clipboard_text(bool& available);
// Linux presentation endpoint with single-pointer dispatch.
// Optional contentRevision identifies the pixels supplied by tick/resize. Callers
// must change it after every pixel mutation; without it every tick uploads.
// Unchanged content skips presentation, but events and tick still run each frame.
// Expose, output-size changes and renderer resets force a repaint.
// Positive frames bounds a test; -1 runs until close/Escape. Throws on SDL failure.
void present(const Surface& image, const std::string& title, int frames, const std::function<bool(Surface&)>& tick = {}, const std::function<void(const PointerEvent&)>& input = {}, const std::function<void(const KeyEvent&)>& keyboard = {}, const std::function<void(const std::string&, int64_t)>& text = {}, const std::function<void(Surface&, int, int)>& resize = {}, const std::function<void(bool)>& focus = {}, const std::function<void(bool)>& visibility = {}, const std::function<void(double,double,float)>& testLocation = {}, const std::function<uint64_t()>& contentRevision = {}, const std::function<void(Surface&,const DisplayGeometry&)>& displayChanged = {});
}
