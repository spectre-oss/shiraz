// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <array>
#include <algorithm>
#include <cstdint>
#include <functional>
#include <limits>
#include <utility>
namespace shiraz {
struct KeyEvent {
    int action, key, scan, repeat, meta, flags;
    int64_t down, time;
};
/** Bounded host keyboard state; orphan repeats/releases cannot create presses. */
class KeyboardStream {
    struct Held { bool active = false, longPress = false, textOwned = false; int key = 0, scan = 0, repeat = 0; int64_t down = 0; };
    std::array<Held, 512> held_{};
    std::function<void(const KeyEvent&)> emit_;
    int64_t last_ = 0;
    int meta_ = 0;
public:
    explicit KeyboardStream(std::function<void(const KeyEvent&)> emit) : emit_(std::move(emit)) { }
    void down(int token, int key, int scan, int meta, bool repeat, int64_t time, bool textOwned = false) {
        if (token < 0 || token >= int(held_.size())) return;
        auto& held = held_[token]; if (repeat != held.active) return;
        last_ = std::max(last_, time); meta_ = meta;
        if (!repeat) { held = {}; held.active = true; held.key = key; held.scan = scan; held.down = last_; held.textOwned = textOwned; }
        else if (held.repeat < std::numeric_limits<int>::max()) ++held.repeat;
        if (held.textOwned) return; // SDL commits layout/IME text separately for this entire press.
        int flags = 8;
        if (repeat && !held.longPress && last_ - held.down >= 500) { held.longPress = true; flags |= 128; }
        emit_({0, held.key, held.scan, held.repeat, meta, flags, held.down, last_});
    }
    void up(int token, int meta, int64_t time, bool canceled = false) {
        if (token < 0 || token >= int(held_.size()) || !held_[token].active) return;
        auto copy = held_[token]; held_[token] = {}; last_ = std::max(last_, time); meta_ = meta;
        if (copy.textOwned) return;
        emit_({1, copy.key, copy.scan, 0, meta, 8 | (canceled ? 32 : 0), copy.down, last_});
    }
    int64_t textTime(int64_t time) { last_ = std::max(last_, time); return last_; }
    void cancel(int64_t time) {
        const int locks = meta_ & (0x100000 | 0x200000 | 0x400000);
        for (int token = 0; token < int(held_.size()); ++token) up(token, locks, time, true);
    }
};
}
