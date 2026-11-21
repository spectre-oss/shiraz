// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/ndk/system_services.h"
#include <algorithm>
#include <cerrno>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <limits>
#include <map>
#include <mutex>
#include <new>
#include <string>
#include <sys/stat.h>
#include <vector>

struct APerformanceHintManager {
    int64_t preferred_rate_ns = 16666666;
};

struct AWorkDuration {
    int64_t start_ts_ns = 0;
    int64_t total_ns = 0;
    int64_t cpu_ns = 0;
    int64_t gpu_ns = 0;
};

struct ASessionCreationConfig {
    std::vector<pid_t> tids;
    int64_t target_ns = 0;
    bool prefer_power_efficiency = false;
};

struct APerformanceHintSession {
    std::mutex mutex;
    std::vector<pid_t> tids;
    int64_t target_ns = 0;
    int64_t last_actual_ns = 0;
    bool prefer_power_efficiency = false;
};

struct AThermalManager {
    std::mutex mutex;
    std::vector<std::pair<AThermal_StatusCallback, void*>> listeners;
};

struct AStorageManager {
    std::mutex mutex;
    std::map<std::string, std::string> mounted;
};

struct AObbInfo {
    std::string package_name;
    int32_t version = 0;
    int32_t flags = 0;
};

namespace {
uint32_t read_le32(const uint8_t* p) {
    return static_cast<uint32_t>(p[0]) | (static_cast<uint32_t>(p[1]) << 8) |
           (static_cast<uint32_t>(p[2]) << 16) | (static_cast<uint32_t>(p[3]) << 24);
}

const AThermalHeadroomThreshold kDefaultThresholds[] = {
    {ATHERMAL_STATUS_LIGHT, 0.7f},     {ATHERMAL_STATUS_MODERATE, 0.85f},
    {ATHERMAL_STATUS_SEVERE, 1.0f},    {ATHERMAL_STATUS_CRITICAL, 1.15f},
    {ATHERMAL_STATUS_EMERGENCY, 1.3f},
};
} // namespace

extern "C" {

APerformanceHintManager* APerformanceHint_getManager() {
    static APerformanceHintManager mgr;
    return &mgr;
}

APerformanceHintSession* APerformanceHint_createSession(APerformanceHintManager* manager,
                                                        const int32_t* threadIds, size_t size,
                                                        int64_t initialTargetWorkDurationNanos) {
    if (!manager || !threadIds || size == 0 || initialTargetWorkDurationNanos <= 0)
        return nullptr;
    auto* s = new (std::nothrow) APerformanceHintSession();
    if (!s)
        return nullptr;
    s->tids.assign(threadIds, threadIds + size);
    s->target_ns = initialTargetWorkDurationNanos;
    return s;
}

int APerformanceHint_createSessionUsingConfig(APerformanceHintManager* manager,
                                              ASessionCreationConfig* config,
                                              APerformanceHintSession** sessionOut) {
    if (!manager || !config || !sessionOut || config->tids.empty() || config->target_ns <= 0)
        return EINVAL;
    auto* s = new (std::nothrow) APerformanceHintSession();
    if (!s)
        return ENOMEM;
    s->tids = config->tids;
    s->target_ns = config->target_ns;
    s->prefer_power_efficiency = config->prefer_power_efficiency;
    *sessionOut = s;
    return 0;
}

int64_t APerformanceHint_getPreferredUpdateRateNanos(APerformanceHintManager* manager) {
    return manager ? manager->preferred_rate_ns : -1;
}

int APerformanceHint_updateTargetWorkDuration(APerformanceHintSession* session,
                                              int64_t targetDurationNanos) {
    if (!session || targetDurationNanos <= 0)
        return EINVAL;
    std::lock_guard<std::mutex> lock(session->mutex);
    session->target_ns = targetDurationNanos;
    return 0;
}

int APerformanceHint_reportActualWorkDuration(APerformanceHintSession* session,
                                              int64_t actualDurationNanos) {
    if (!session || actualDurationNanos <= 0)
        return EINVAL;
    std::lock_guard<std::mutex> lock(session->mutex);
    session->last_actual_ns = actualDurationNanos;
    return 0;
}

int APerformanceHint_reportActualWorkDuration2(APerformanceHintSession* session,
                                               AWorkDuration* wd) {
    if (!session || !wd || wd->total_ns <= 0)
        return EINVAL;
    std::lock_guard<std::mutex> lock(session->mutex);
    session->last_actual_ns = wd->total_ns;
    return 0;
}

void APerformanceHint_closeSession(APerformanceHintSession* session) {
    delete session;
}

int APerformanceHint_setThreads(APerformanceHintSession* session, const pid_t* threadIds,
                                size_t size) {
    if (!session || !threadIds || size == 0)
        return EINVAL;
    std::lock_guard<std::mutex> lock(session->mutex);
    session->tids.assign(threadIds, threadIds + size);
    return 0;
}

int APerformanceHint_setPreferPowerEfficiency(APerformanceHintSession* session, bool enabled) {
    if (!session)
        return EINVAL;
    std::lock_guard<std::mutex> lock(session->mutex);
    session->prefer_power_efficiency = enabled;
    return 0;
}

int APerformanceHint_notifyWorkloadReset(APerformanceHintSession* session, bool, bool,
                                         const char*) {
    return session ? 0 : EINVAL;
}

AWorkDuration* AWorkDuration_create() {
    return new (std::nothrow) AWorkDuration();
}
void AWorkDuration_release(AWorkDuration* wd) {
    delete wd;
}
void AWorkDuration_setWorkPeriodStartTimestampNanos(AWorkDuration* wd, int64_t v) {
    if (wd)
        wd->start_ts_ns = v;
}
void AWorkDuration_setActualTotalDurationNanos(AWorkDuration* wd, int64_t v) {
    if (wd)
        wd->total_ns = v;
}
void AWorkDuration_setActualCpuDurationNanos(AWorkDuration* wd, int64_t v) {
    if (wd)
        wd->cpu_ns = v;
}
void AWorkDuration_setActualGpuDurationNanos(AWorkDuration* wd, int64_t v) {
    if (wd)
        wd->gpu_ns = v;
}

ASessionCreationConfig* ASessionCreationConfig_create() {
    return new (std::nothrow) ASessionCreationConfig();
}
void ASessionCreationConfig_release(ASessionCreationConfig* config) {
    delete config;
}
int ASessionCreationConfig_setTids(ASessionCreationConfig* config, const pid_t* tids, size_t size) {
    if (!config || !tids || size == 0)
        return EINVAL;
    config->tids.assign(tids, tids + size);
    return 0;
}
int ASessionCreationConfig_setTargetWorkDurationNanos(ASessionCreationConfig* config, int64_t ns) {
    if (!config || ns <= 0)
        return EINVAL;
    config->target_ns = ns;
    return 0;
}
int ASessionCreationConfig_setPreferPowerEfficiency(ASessionCreationConfig* config, bool enabled) {
    if (!config)
        return EINVAL;
    config->prefer_power_efficiency = enabled;
    return 0;
}

AThermalManager* AThermal_acquireManager() {
    return new (std::nothrow) AThermalManager();
}
void AThermal_releaseManager(AThermalManager* manager) {
    delete manager;
}
AThermalStatus AThermal_getCurrentThermalStatus(AThermalManager* manager) {
    return manager ? ATHERMAL_STATUS_NONE : ATHERMAL_STATUS_ERROR;
}
int AThermal_registerThermalStatusListener(AThermalManager* manager,
                                           AThermal_StatusCallback callback, void* data) {
    if (!manager || !callback)
        return EINVAL;
    std::lock_guard<std::mutex> lock(manager->mutex);
    for (const auto& entry : manager->listeners)
        if (entry.first == callback && entry.second == data)
            return EINVAL;
    manager->listeners.emplace_back(callback, data);
    return 0;
}
int AThermal_unregisterThermalStatusListener(AThermalManager* manager,
                                             AThermal_StatusCallback callback, void* data) {
    if (!manager || !callback)
        return EINVAL;
    std::lock_guard<std::mutex> lock(manager->mutex);
    auto it = std::find_if(manager->listeners.begin(), manager->listeners.end(),
                           [&](const auto& e) { return e.first == callback && e.second == data; });
    if (it == manager->listeners.end())
        return EINVAL;
    manager->listeners.erase(it);
    return 0;
}
float AThermal_getThermalHeadroom(AThermalManager* manager, int forecastSeconds) {
    if (!manager || forecastSeconds < 0 || forecastSeconds > 60)
        return std::numeric_limits<float>::quiet_NaN();
    return 0.0f;
}
int AThermal_getThermalHeadroomThresholds(AThermalManager* manager,
                                          const AThermalHeadroomThreshold** outThresholds,
                                          size_t* size) {
    if (!manager || !outThresholds || !size)
        return EINVAL;
    *outThresholds = kDefaultThresholds;
    *size = sizeof(kDefaultThresholds) / sizeof(kDefaultThresholds[0]);
    return 0;
}

AStorageManager* AStorageManager_new() {
    return new (std::nothrow) AStorageManager();
}
void AStorageManager_delete(AStorageManager* mgr) {
    delete mgr;
}

void AStorageManager_mountObb(AStorageManager* mgr, const char* rawPath, const char*,
                              AStorageManager_obbCallbackFunc cb, void* data) {
    if (!mgr || !rawPath) {
        if (cb)
            cb(rawPath, AOBB_STATE_ERROR_INTERNAL, data);
        return;
    }
    struct stat st{};
    if (stat(rawPath, &st) != 0) {
        if (cb)
            cb(rawPath, AOBB_STATE_ERROR_COULD_NOT_MOUNT, data);
        return;
    }
    std::lock_guard<std::mutex> lock(mgr->mutex);
    if (mgr->mounted.count(rawPath)) {
        if (cb)
            cb(rawPath, AOBB_STATE_ERROR_ALREADY_MOUNTED, data);
        return;
    }
    // Host desktop hosts do not mount encrypted loop block devices unprivileged;
    // directory-backed OBB roots can be tracked directly, while raw images fail explicitly.
    if (S_ISDIR(st.st_mode)) {
        mgr->mounted[rawPath] = rawPath;
        if (cb)
            cb(rawPath, AOBB_STATE_MOUNTED, data);
        return;
    }
    if (cb)
        cb(rawPath, AOBB_STATE_ERROR_COULD_NOT_MOUNT, data);
}

void AStorageManager_unmountObb(AStorageManager* mgr, const char* rawPath, const int,
                                AStorageManager_obbCallbackFunc cb, void* data) {
    if (!mgr || !rawPath) {
        if (cb)
            cb(rawPath, AOBB_STATE_ERROR_INTERNAL, data);
        return;
    }
    std::lock_guard<std::mutex> lock(mgr->mutex);
    auto it = mgr->mounted.find(rawPath);
    if (it == mgr->mounted.end()) {
        if (cb)
            cb(rawPath, AOBB_STATE_ERROR_NOT_MOUNTED, data);
        return;
    }
    mgr->mounted.erase(it);
    if (cb)
        cb(rawPath, AOBB_STATE_UNMOUNTED, data);
}

int AStorageManager_isObbMounted(AStorageManager* mgr, const char* rawPath) {
    if (!mgr || !rawPath)
        return 0;
    std::lock_guard<std::mutex> lock(mgr->mutex);
    return mgr->mounted.count(rawPath) ? 1 : 0;
}

const char* AStorageManager_getMountedObbPath(AStorageManager* mgr, const char* rawPath) {
    if (!mgr || !rawPath)
        return nullptr;
    std::lock_guard<std::mutex> lock(mgr->mutex);
    auto it = mgr->mounted.find(rawPath);
    return it != mgr->mounted.end() ? it->second.c_str() : nullptr;
}

// Parses Android OBB v1 footer format:
//   [footer_payload (footer_size bytes)] [uint32 footer_size] [uint32 magic 0x01039983]
// Where footer_payload starts with:
//   uint32 sig_version (1), uint32 package_version, uint32 flags, uint32 pkg_len, char pkg[pkg_len]
AObbInfo* AObbScanner_getObbInfo(const char* filename) {
    if (!filename)
        return nullptr;
    FILE* f = std::fopen(filename, "rb");
    if (!f)
        return nullptr;
    if (std::fseek(f, 0, SEEK_END) != 0) {
        std::fclose(f);
        return nullptr;
    }
    long file_size = std::ftell(f);
    if (file_size < 24) {
        std::fclose(f);
        return nullptr;
    }
    uint8_t tail[8];
    if (std::fseek(f, file_size - 8, SEEK_SET) != 0 || std::fread(tail, 1, 8, f) != 8) {
        std::fclose(f);
        return nullptr;
    }
    uint32_t footer_size = read_le32(tail);
    uint32_t magic = read_le32(tail + 4);
    if (magic != 0x01039983u || footer_size < 16 || footer_size > 32768 ||
        static_cast<long>(footer_size) + 8 > file_size) {
        std::fclose(f);
        return nullptr;
    }
    std::vector<uint8_t> footer(footer_size);
    if (std::fseek(f, file_size - 8 - static_cast<long>(footer_size), SEEK_SET) != 0 ||
        std::fread(footer.data(), 1, footer_size, f) != footer_size) {
        std::fclose(f);
        return nullptr;
    }
    std::fclose(f);
    uint32_t sig_version = read_le32(footer.data());
    uint32_t pkg_version = read_le32(footer.data() + 4);
    uint32_t flags = read_le32(footer.data() + 8);
    uint32_t pkg_len = read_le32(footer.data() + 12);
    if (sig_version != 1 || 16u + pkg_len > footer_size)
        return nullptr;
    auto* info = new (std::nothrow) AObbInfo();
    if (!info)
        return nullptr;
    info->version = static_cast<int32_t>(pkg_version);
    info->flags = static_cast<int32_t>(flags);
    info->package_name.assign(reinterpret_cast<const char*>(footer.data() + 16), pkg_len);
    return info;
}

void AObbInfo_delete(AObbInfo* obbInfo) {
    delete obbInfo;
}
const char* AObbInfo_getPackageName(AObbInfo* obbInfo) {
    return obbInfo ? obbInfo->package_name.c_str() : nullptr;
}
int32_t AObbInfo_getVersion(AObbInfo* obbInfo) {
    return obbInfo ? obbInfo->version : 0;
}
int32_t AObbInfo_getFlags(AObbInfo* obbInfo) {
    return obbInfo ? obbInfo->flags : 0;
}

} // extern "C"
