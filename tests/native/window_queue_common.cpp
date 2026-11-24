// SPDX-License-Identifier: GPL-3.0-only
// Unit test verifying include/shiraz/window_queue_common.hpp against both
// Linux-shaped and macOS-shaped WindowQueue structs (ensuring cross-platform
// compile and state-machine parity).
#include "shiraz/window_queue_common.hpp"
#include <cassert>
#include <condition_variable>
#include <cstdio>
#include <cstring>
#include <memory>
#include <mutex>

namespace {

using shiraz::zc_common::SLOT_ACQUIRED;
using shiraz::zc_common::SLOT_DEQUEUED;
using shiraz::zc_common::SLOT_FREE;
using shiraz::zc_common::SLOT_QUEUED;
using shiraz::zc_common::SlotState;

struct MockSlot {
    uint64_t generation = 0;
    SlotState state = SLOT_FREE;
    int pins = 0;
    uint64_t revision = 0;
};

struct MockQueue {
    MockSlot slots[6];
    uint32_t count = 0;
    uint64_t producer = 0;
    uint64_t consumer = 0;
    bool abandoned = false;
    bool offscreen = false;
    bool ever_queued = false;
    std::condition_variable cv;
};

struct MockWindow {
    std::mutex mutex;
    void* shared = nullptr;
    bool frameSink = false;
    std::unique_ptr<MockQueue> queue;
};

MockQueue* ensureMockQueue(MockWindow* w) {
    if (!w->queue)
        w->queue = std::make_unique<MockQueue>();
    return w->queue.get();
}

} // namespace

int main() {
    MockWindow w;
    assert(shiraz::zc_common::abandoned(&w) == 0);
    assert(std::strcmp(shiraz::zc_common::nodataReason(&w), "nodata-nothing-queued") == 0);

    // Producer connection lifecycle.
    assert(shiraz::zc_common::connectProducer(&w, 100, ensureMockQueue) == 0);
    assert(shiraz::zc_common::connectProducer(&w, 100, ensureMockQueue) == 0);
    assert(shiraz::zc_common::connectProducer(&w, 200, ensureMockQueue) == -EBUSY);
    assert(shiraz::zc_common::checkProducer(&w, 100) == 0);
    assert(shiraz::zc_common::checkProducer(&w, 200) == -EPIPE);

    // Slot selection: allocate -> free reuse -> mailbox drop -> pin skip -> exhaustion.
    bool alloc = false;
    assert(shiraz::zc_common::pickSlot(w.queue.get(), 6, -1, false, &alloc) == 0 && alloc);
    w.queue->count = 2;
    w.queue->slots[0] = {1, SLOT_DEQUEUED, 0, 1};
    w.queue->slots[1] = {2, SLOT_QUEUED, 0, 2};
    assert(shiraz::zc_common::checkHeld(&w, 0, 1) == 0);
    assert(shiraz::zc_common::checkHeld(&w, 0, 99) == -EINVAL);
    assert(shiraz::zc_common::checkHeld(&w, 1, 2) == -EINVAL);
    // Drop-queued picks slot 1 when excluding slot 0.
    assert(shiraz::zc_common::pickSlot(w.queue.get(), 6, 0, true, &alloc) == 1 && !alloc);
    // Pinning slot 1 prevents dropping it and triggers allocating slot 2.
    w.queue->slots[1].pins = 1;
    assert(shiraz::zc_common::pickSlot(w.queue.get(), 6, 0, true, &alloc) == 2 && alloc);

    // Newest readable slot picks highest revision among QUEUED / ACQUIRED.
    w.queue->slots[0] = {1, SLOT_ACQUIRED, 0, 5};
    w.queue->slots[1] = {2, SLOT_QUEUED, 0, 7};
    assert(shiraz::zc_common::findNewestReadableSlot(w.queue.get()) == 1);

    // Cancel & disconnect restore DEQUEUED -> FREE.
    w.queue->slots[0].state = SLOT_DEQUEUED;
    assert(shiraz::zc_common::cancelSlot(&w, 100, 0) == 0);
    assert(w.queue->slots[0].state == SLOT_FREE);
    w.queue->slots[0].state = SLOT_DEQUEUED;
    shiraz::zc_common::disconnectProducer(&w, 100);
    assert(w.queue->producer == 0 && w.queue->slots[0].state == SLOT_FREE);

    // CPU-frames-required precedence & offscreen / abandon flags.
    const char* why = nullptr;
    assert(shiraz::zc_common::cpuFramesRequired(&w, &why) == 0 && why == nullptr);
    shiraz::zc_common::setOffscreen(&w, ensureMockQueue);
    assert(shiraz::zc_common::cpuFramesRequired(&w, &why) == 1 &&
           std::strcmp(why, "offscreen") == 0);
    w.queue->ever_queued = true;
    assert(std::strcmp(shiraz::zc_common::nodataReason(&w), "nodata-cpu-newer") == 0);
    shiraz::zc_common::abandon(&w, ensureMockQueue);
    assert(shiraz::zc_common::abandoned(&w) == 1);
    assert(shiraz::zc_common::connectProducer(&w, 300, ensureMockQueue) == -EPIPE);

    // RGBA -> ARGB conversion & telemetry counters.
    const uint8_t rgba[8] = {0x11, 0x22, 0x33, 0xAA, 0x44, 0x55, 0x66, 0xFF};
    uint32_t argb[2] = {};
    shiraz::zc_common::copyRgbaToArgb(rgba, argb, 2);
    assert(argb[0] == 0xAA112233u && argb[1] == 0xFF445566u);

    const uint64_t before = shiraz::zc_common::cpuPixelReadsTotal();
    shiraz::zc_common::addCpuPixelReads("unit", 16);
    shiraz::zc_common::addZeroCopyCount("queued", 3);
    shiraz::zc_common::recordZeroCopySample("frame_ms", 1.25);
    assert(shiraz::zc_common::cpuPixelReadsTotal() == before + 1);
    char buf[1024];
    shiraz::zc_common::formatCounters(buf, sizeof buf);
    assert(std::strstr(buf, "SHIRAZ_ZC_COUNTERS") != nullptr);
    assert(std::strstr(buf, "queued=3") != nullptr);
    assert(std::strstr(buf, "frame_ms_n=1") != nullptr);

    std::puts("WINDOW_QUEUE_COMMON_OK");
    return 0;
}
