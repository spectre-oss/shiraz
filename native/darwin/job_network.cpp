// SPDX-License-Identifier: GPL-3.0-only
#include <Network/Network.h>
#include <dispatch/dispatch.h>
#include <cstdint>
#include <memory>
#include <mutex>

namespace {
struct Snapshot {
    uint32_t version, status, transports;
};
struct State {
    std::mutex lock;
    Snapshot value{1, 0, 0};
    bool received = false;
    dispatch_semaphore_t ready = dispatch_semaphore_create(0);
    ~State() {
        if (ready)
            dispatch_release(ready);
    }
};
} // namespace

// Borrowed output, fixed ABI, no callbacks into Python. A timeout is unknown,
// never disconnected. Asynchronous blocks own State beyond cancellation/return.
extern "C" int shiraz_job_network_snapshot(uint32_t* output, uint32_t count) noexcept {
    if (!output || count != 3)
        return 0;
    try {
        auto state = std::make_shared<State>();
        if (!state->ready)
            return 0;
        auto queue = dispatch_queue_create("shiraz.job-network", DISPATCH_QUEUE_SERIAL);
        if (!queue)
            return 0;
        auto monitor = nw_path_monitor_create();
        if (!monitor) {
            dispatch_release(queue);
            return 0;
        }
        nw_path_monitor_set_queue(monitor, queue);
        nw_path_monitor_set_update_handler(monitor, ^(nw_path_t path) {
          std::lock_guard<std::mutex> guard(state->lock);
          if (state->received)
              return;
          state->value.status = static_cast<uint32_t>(nw_path_get_status(path));
          if (nw_path_uses_interface_type(path, nw_interface_type_wifi))
              state->value.transports |= 1;
          if (nw_path_uses_interface_type(path, nw_interface_type_wired))
              state->value.transports |= 2;
          if (nw_path_uses_interface_type(path, nw_interface_type_cellular))
              state->value.transports |= 4;
          if (nw_path_uses_interface_type(path, nw_interface_type_other))
              state->value.transports |= 8;
          state->received = true;
          dispatch_semaphore_signal(state->ready);
        });
        nw_path_monitor_start(monitor);
        bool ready = dispatch_semaphore_wait(state->ready,
                                             dispatch_time(DISPATCH_TIME_NOW, NSEC_PER_SEC)) == 0;
        nw_path_monitor_cancel(monitor);
        nw_release(monitor);
        dispatch_release(queue);
        if (!ready)
            return 0;
        std::lock_guard<std::mutex> guard(state->lock);
        output[0] = state->value.version;
        output[1] = state->value.status;
        output[2] = state->value.transports;
        return 1;
    } catch (...) {
        return 0;
    }
}
