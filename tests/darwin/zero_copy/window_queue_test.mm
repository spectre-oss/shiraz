// SPDX-License-Identifier: GPL-3.0-only
// T9 (build/macos/m4b/design/DESIGN-v2.md section 9): the M4b window queue in
// libandroid alone, no EGL. make macos-zero-copy-queue-test.
#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#include <IOSurface/IOSurfaceRef.h>
#include "shiraz/darwin/window_queue.h"
#include <atomic>
#include <cerrno>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <thread>
#include <vector>

static int failures = 0, checks = 0;
#define CHECK(name, cond) do { ++checks; bool ok_ = (cond); std::printf("%s %s\n", ok_ ? "PASS" : "FAIL", name); if (!ok_) ++failures; } while (0)

static void fill(const ShirazWindowSlot& s, uint8_t r, uint8_t g, uint8_t b, uint8_t a) {
    IOSurfaceRef surface = (IOSurfaceRef)s.surface;
    IOSurfaceLock(surface, 0, nullptr);
    uint8_t* base = (uint8_t*)IOSurfaceGetBaseAddress(surface);
    for (int y = 0; y < s.height; ++y) for (int x = 0; x < s.width; ++x) {
        uint8_t* p = base + size_t(y) * s.bytes_per_row + size_t(x) * 4;
        // Row 0 is the bottom (GL). Mark the bottom row blue for orientation.
        if (y == 0) { p[0] = 0; p[1] = 0; p[2] = 255; p[3] = 255; } else { p[0] = r; p[1] = g; p[2] = b; p[3] = a; }
    }
    IOSurfaceUnlock(surface, 0, nullptr);
}

int main() {
    @autoreleasepool {
    id<MTLDevice> device = MTLCreateSystemDefaultDevice();
    CHECK("set_device", shiraz_window_set_device((__bridge void*)device) == 0);
    CHECK("set_device same device again", shiraz_window_set_device((__bridge void*)device) == 0);
    int32_t W = 64, H = 32;
    ANativeWindow* w = shiraz_window_create(W, H);
    const uint64_t P = 11, C = 22;
    CHECK("connect producer", shiraz_window_connect_producer(w, P) == 0);
    CHECK("second producer -EBUSY", shiraz_window_connect_producer(w, P + 1) == -EBUSY);
    id<MTLSharedEvent> event = [device newSharedEvent];
    uint64_t serial = 0;
    void* owned = shiraz_window_adopt_event(w, SHIRAZ_EVENT_FRAME, (__bridge_retained void*)event, &serial);
    CHECK("adopt frame event", owned == (__bridge void*)event && serial > 0);
    id<MTLSharedEvent> other = [device newSharedEvent];
    uint64_t serial2 = 0;
    CHECK("second adopter gets the first event", shiraz_window_adopt_event(w, SHIRAZ_EVENT_FRAME, (__bridge_retained void*)other, &serial2) == owned && serial2 == serial);
    ShirazWindowSlot a{}, b{}, c{}, d{}, x{};
    CHECK("dequeue first", shiraz_window_dequeue_slot(w, P, &a) == 0 && a.width == W && a.height == H && a.texture && a.surface && a.value == 0 && a.fourcc == 'RGBA');
    uint32_t pixels[64 * 32]; uint64_t rev = 0;
    CHECK("acquire before any queue -ENODATA", shiraz_window_acquire_slot(w, C, 1, &x) == -ENODATA &&
          std::strcmp(shiraz_window_nodata_reason(w), "nodata-nothing-queued") == 0);
    uint64_t v1 = shiraz_window_next_value(w, SHIRAZ_EVENT_FRAME), v2 = shiraz_window_next_value(w, SHIRAZ_EVENT_FRAME);
    CHECK("frame values monotonic", v1 == 1 && v2 == 2);
    fill(a, 10, 20, 30, 255);
    CHECK("queue_and_dequeue a -> b", shiraz_window_queue_and_dequeue(w, P, a.index, a.generation, W, H, v1, &b) == 0 && b.index != a.index);
    CHECK("acquire a (frame 1)", shiraz_window_acquire_slot(w, C, 1, &x) == 0 && x.index == a.index && x.value == v1);
    CHECK("other consumer -EBUSY", shiraz_window_acquire_slot(w, C + 1, 1, &x) == -EBUSY);
    CHECK("same frame_serial returns same slot", shiraz_window_acquire_slot(w, C, 1, &x) == 0 && x.index == a.index);
    fill(b, 40, 50, 60, 255);
    CHECK("queue_and_dequeue b -> c", shiraz_window_queue_and_dequeue(w, P, b.index, b.generation, W, H, v2, &c) == 0 && c.index != a.index && c.index != b.index);
    CHECK("same frame_serial still a after newer queue", shiraz_window_acquire_slot(w, C, 1, &x) == 0 && x.index == a.index);
    CHECK("supersede of unmarked slot -EINPROGRESS", shiraz_window_acquire_slot(w, C, 2, &x) == -EINPROGRESS);
    CHECK("mark a sampled r=7", shiraz_window_mark_sampled(w, C, a.index, a.generation, 7) == 0);
    CHECK("acquire b (frame 2) supersedes a", shiraz_window_acquire_slot(w, C, 2, &x) == 0 && x.index == b.index);
    CHECK("mark b sampled r=8", shiraz_window_mark_sampled(w, C, b.index, b.generation, 8) == 0);
    // Producer holds c; a is FREE with release 7.
    uint64_t v3 = shiraz_window_next_value(w, SHIRAZ_EVENT_FRAME);
    CHECK("queue_and_dequeue c -> a carries release_value=7", shiraz_window_queue_and_dequeue(w, P, c.index, c.generation, W, H, v3, &d) == 0 &&
          d.index == a.index && d.value == 7);
    // Failed draw still marked at end of frame: acquire in frame 3, mark (conservative), next frame no -EINPROGRESS.
    CHECK("acquire c (frame 3)", shiraz_window_acquire_slot(w, C, 3, &x) == 0 && x.index == c.index);
    CHECK("conservative mark of failed draw", shiraz_window_mark_sampled(w, C, b.index, b.generation, 8) == -ESTALE &&
          shiraz_window_mark_sampled(w, C, c.index, c.generation, 9) == 0);
    // Mailbox: queue d (=a slot) then dequeue; then queue the next; the older QUEUED becomes FREE.
    uint64_t v4 = shiraz_window_next_value(w, SHIRAZ_EVENT_FRAME), v5 = shiraz_window_next_value(w, SHIRAZ_EVENT_FRAME);
    ShirazWindowSlot e{}, f{};
    CHECK("queue d -> e", shiraz_window_queue_and_dequeue(w, P, d.index, d.generation, W, H, v4, &e) == 0 && e.index == b.index);
    CHECK("queue e -> f (mailbox drops d, f reuses d's slot)", shiraz_window_queue_and_dequeue(w, P, e.index, e.generation, W, H, v5, &f) == 0 && f.index == d.index);
    CHECK("acquire newest (frame 4) = e", shiraz_window_acquire_slot(w, C, 4, &x) == 0 && x.index == e.index && x.value == v5);
    shiraz_window_mark_sampled(w, C, e.index, e.generation, 10);
    // CPU newer.
    std::vector<uint32_t> argb(size_t(W) * H, 0xff112233u);
    CHECK("cpu post", shiraz_window_post_argb(w, argb.data(), argb.size()) == 0);
    CHECK("acquire after CPU post -ENODATA cpu-newer", shiraz_window_acquire_slot(w, C, 5, &x) == -ENODATA &&
          std::strcmp(shiraz_window_nodata_reason(w), "nodata-cpu-newer") == 0);
    // Resize: queue of an old-size slot -EAGAIN (dropped), no revision bump.
    uint64_t before = shiraz_window_revision(w);
    CHECK("host resize", shiraz_window_resize(w, 48, 40) == 0);
    uint64_t afterResize = shiraz_window_revision(w);
    CHECK("queue old-size slot -EAGAIN", shiraz_window_queue_slot(w, P, f.index, f.generation, W, H, shiraz_window_next_value(w, SHIRAZ_EVENT_FRAME)) == -EAGAIN &&
          shiraz_window_revision(w) == afterResize && afterResize > before);
    ShirazWindowSlot g{};
    CHECK("dequeue after resize reallocates at new size", shiraz_window_dequeue_slot(w, P, &g) == 0 && g.width == 48 && g.height == 40 && g.value == 0);
    // Materialise: CPU copy of a queued slot, orientation (bottom row blue -> last output row).
    fill(g, 200, 100, 50, 255);
    uint64_t vg = shiraz_window_next_value(w, SHIRAZ_EVENT_FRAME);
    event.signaledValue = vg;
    CHECK("queue g", shiraz_window_queue_slot(w, P, g.index, g.generation, 48, 40, vg) == 0);
    CHECK("copy_argb_revision materialises", shiraz_window_copy_argb_revision(w, pixels, 48 * 40, &rev) == 0 &&
          rev == shiraz_window_revision(w) && pixels[0] == 0xffc86432u && pixels[48 * 39] == 0xff0000ffu);
    char line[4096]; shiraz_zero_copy_counters(line, sizeof line);
    CHECK("materialize counted as a CPU pixel read", std::strstr(line, "read.materialize=1") != nullptr);
    CHECK("second copy at the same revision does not re-read", shiraz_window_copy_argb(w, pixels, 48 * 40) == 0 &&
          (shiraz_zero_copy_counters(line, sizeof line), std::strstr(line, "read.materialize=1") != nullptr));
    // Pin -> 4th slot -> 16 ms -EWOULDBLOCK. The frame event is held unsignaled so a CPU reader stays pinned.
    ShirazWindowSlot h{}, j{}, k{};
    shiraz_window_dequeue_slot(w, P, &h);
    uint64_t vh = shiraz_window_next_value(w, SHIRAZ_EVENT_FRAME);
    CHECK("queue h (unsignaled frame)", shiraz_window_queue_slot(w, P, h.index, h.generation, 48, 40, vh) == 0);
    std::atomic<int> readerResult{1};
    std::thread reader([&] { std::vector<uint32_t> out(48 * 40); uint64_t r = 0; readerResult = shiraz_window_copy_argb_revision(w, out.data(), out.size(), &r); });
    std::this_thread::sleep_for(std::chrono::milliseconds(100));  // reader pins h and waits on the event
    // Slots: g QUEUED? (mailboxed FREE by h), h QUEUED+pinned. Take every free slot.
    int got = 0; ShirazWindowSlot held[4]{};
    while (got < 4 && shiraz_window_dequeue_slot(w, P, &held[got]) == 0) ++got;
    shiraz_zero_copy_counters(line, sizeof line);
    CHECK("pin forces a 4th slot", std::strstr(line, "extra_slots=1") != nullptr);
    auto t0 = std::chrono::steady_clock::now();
    int32_t blocked = shiraz_window_dequeue_slot(w, P, &k);
    double waitedMs = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
    CHECK("all slots taken: -EWOULDBLOCK after ~16 ms", blocked == -EWOULDBLOCK && waitedMs >= 14 && waitedMs < 200);
    // queue_and_dequeue with no slot available: CUR stays DEQUEUED, older QUEUED (h) untouched.
    uint64_t revBefore = shiraz_window_revision(w);
    int32_t qd = shiraz_window_queue_and_dequeue(w, P, held[0].index, held[0].generation, 48, 40, shiraz_window_next_value(w, SHIRAZ_EVENT_FRAME), &j);
    CHECK("queue_and_dequeue without a slot -EWOULDBLOCK, not queued", qd == -EWOULDBLOCK && shiraz_window_revision(w) == revBefore);
    CHECK("cur still DEQUEUED (cancel succeeds)", shiraz_window_cancel_slot(w, P, held[0].index) == 0);
    event.signaledValue = vh;
    reader.join();
    CHECK("pinned reader finished after signal", readerResult == 0);
    for (int n = 1; n < got; ++n) shiraz_window_cancel_slot(w, P, held[n].index);
    // Reconnect: values stay monotonic.
    shiraz_window_disconnect_producer(w, P);
    CHECK("reconnect another producer", shiraz_window_connect_producer(w, P + 5) == 0);
    uint64_t vr = shiraz_window_next_value(w, SHIRAZ_EVENT_FRAME);
    CHECK("values monotonic across reconnect", vr > vh);
    CHECK("old producer -EPIPE", shiraz_window_dequeue_slot(w, P, &k) == -EPIPE);
    // Offscreen / CPU-required reasons.
    const char* reason = nullptr;
    CHECK("cpu frames not required", shiraz_window_cpu_frames_required(w, &reason) == 0);
    shiraz_window_set_offscreen(w);
    CHECK("offscreen -> cpu frames required", shiraz_window_cpu_frames_required(w, &reason) == 1 && std::strcmp(reason, "offscreen") == 0);
    // Detach + abandon.
    CHECK("consumer detach", shiraz_window_consumer_detach(w, C) == 0);
    shiraz_window_abandon(w);
    CHECK("abandoned: dequeue -EPIPE", shiraz_window_dequeue_slot(w, P + 5, &k) == -EPIPE && shiraz_window_abandoned(w) == 1);
    CHECK("abandoned: acquire -EPIPE", shiraz_window_acquire_slot(w, C, 9, &x) == -EPIPE);
    ANativeWindow_release(w);
    // Materialise vs concurrent resize (ASan build): staging never lands in a resized front.
    ANativeWindow* r = shiraz_window_create(32, 32);
    shiraz_window_connect_producer(r, P);
    id<MTLSharedEvent> always = [device newSharedEvent];
    always.signaledValue = 1ull << 60;
    shiraz_window_adopt_event(r, SHIRAZ_EVENT_FRAME, (__bridge_retained void*)always, nullptr);
    std::atomic<bool> stop{false}; std::atomic<int> copies{0};
    std::thread resizer([&] { int n = 0; while (!stop) { shiraz_window_resize(r, (n & 1) ? 32 : 40, (n & 1) ? 32 : 24); ++n; } });
    for (int n = 0; n < 300; ++n) {
        ShirazWindowSlot s{};
        if (shiraz_window_dequeue_slot(r, P, &s) != 0) continue;
        fill(s, 1, 2, 3, 255);
        shiraz_window_queue_slot(r, P, s.index, s.generation, s.width, s.height, shiraz_window_next_value(r, SHIRAZ_EVENT_FRAME));
        std::vector<uint32_t> out(40 * 32); uint64_t rr = 0;
        if (shiraz_window_copy_argb_revision(r, out.data(), out.size(), &rr) == 0) ++copies;
    }
    stop = true; resizer.join();
    CHECK("materialise under concurrent resize: no crash, some copies", copies > 0);
    ANativeWindow_release(r);
    }
    std::printf("WINDOW_QUEUE_TEST checks=%d failures=%d\n", checks, failures);
    return failures ? 1 : 0;
}
