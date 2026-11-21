// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/ndk/native_activity.h"
#include <mutex>
#include <new>
#include <string>
#include <unordered_map>

namespace {
struct ActivityHostState {
    ANativeActivityCallbacks callbacks{};
    std::string internal_path;
    std::string external_path;
    std::string obb_path;
    bool finished = false;
    int32_t window_format = 1;
    uint32_t window_flags = 0;
    bool soft_input_visible = false;
    uint32_t soft_input_flags = 0;
};

std::mutex g_mutex;
std::unordered_map<const ANativeActivity*, ActivityHostState> g_states;
} // namespace

extern "C" {

ANativeActivity* shiraz_native_activity_create(const char* internal_path, const char* external_path,
                                               const char* obb_path, AAssetManager* assets) {
    auto* act = new (std::nothrow) ANativeActivity{};
    if (!act)
        return nullptr;
    std::lock_guard<std::mutex> lock(g_mutex);
    auto& state = g_states[act];
    state.internal_path = internal_path ? internal_path : "";
    state.external_path = external_path ? external_path : "";
    state.obb_path = obb_path ? obb_path : "";
    act->callbacks = &state.callbacks;
    act->internalDataPath = state.internal_path.c_str();
    act->externalDataPath = state.external_path.c_str();
    act->obbPath = state.obb_path.c_str();
    act->sdkVersion = 35;
    act->assetManager = assets;
    return act;
}

void shiraz_native_activity_destroy(ANativeActivity* activity) {
    if (!activity)
        return;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_states.erase(activity);
    }
    delete activity;
}

void ANativeActivity_finish(ANativeActivity* activity) {
    if (!activity)
        return;
    std::lock_guard<std::mutex> lock(g_mutex);
    g_states[activity].finished = true;
}

void ANativeActivity_setWindowFormat(ANativeActivity* activity, int32_t format) {
    if (!activity)
        return;
    std::lock_guard<std::mutex> lock(g_mutex);
    g_states[activity].window_format = format;
}

void ANativeActivity_setWindowFlags(ANativeActivity* activity, uint32_t addFlags,
                                    uint32_t removeFlags) {
    if (!activity)
        return;
    std::lock_guard<std::mutex> lock(g_mutex);
    auto& state = g_states[activity];
    state.window_flags = (state.window_flags | addFlags) & ~removeFlags;
}

void ANativeActivity_showSoftInput(ANativeActivity* activity, uint32_t flags) {
    if (!activity)
        return;
    std::lock_guard<std::mutex> lock(g_mutex);
    auto& state = g_states[activity];
    state.soft_input_visible = true;
    state.soft_input_flags = flags;
}

void ANativeActivity_hideSoftInput(ANativeActivity* activity, uint32_t flags) {
    if (!activity)
        return;
    std::lock_guard<std::mutex> lock(g_mutex);
    auto& state = g_states[activity];
    state.soft_input_visible = false;
    state.soft_input_flags = flags;
}

int shiraz_native_activity_is_finished(const ANativeActivity* activity) {
    if (!activity)
        return 0;
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_states.find(activity);
    return it != g_states.end() && it->second.finished ? 1 : 0;
}

int32_t shiraz_native_activity_window_format(const ANativeActivity* activity) {
    if (!activity)
        return 0;
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_states.find(activity);
    return it != g_states.end() ? it->second.window_format : 0;
}

uint32_t shiraz_native_activity_window_flags(const ANativeActivity* activity) {
    if (!activity)
        return 0;
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_states.find(activity);
    return it != g_states.end() ? it->second.window_flags : 0;
}

int shiraz_native_activity_soft_input_visible(const ANativeActivity* activity) {
    if (!activity)
        return 0;
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_states.find(activity);
    return it != g_states.end() && it->second.soft_input_visible ? 1 : 0;
}

} // extern "C"
