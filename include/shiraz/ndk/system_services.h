// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstddef>
#include <cstdint>
#include <sys/types.h>

struct APerformanceHintManager;
struct APerformanceHintSession;
struct AWorkDuration;
struct ASessionCreationConfig;
struct AThermalManager;
struct AStorageManager;
struct AObbInfo;

enum AThermalStatus {
    ATHERMAL_STATUS_ERROR = -1,
    ATHERMAL_STATUS_NONE = 0,
    ATHERMAL_STATUS_LIGHT = 1,
    ATHERMAL_STATUS_MODERATE = 2,
    ATHERMAL_STATUS_SEVERE = 3,
    ATHERMAL_STATUS_CRITICAL = 4,
    ATHERMAL_STATUS_EMERGENCY = 5,
    ATHERMAL_STATUS_SHUTDOWN = 6,
};

struct AThermalHeadroomThreshold {
    AThermalStatus thermalStatus;
    float headroom;
};

using AThermal_StatusCallback = void (*)(void* data, AThermalStatus status);
using AStorageManager_obbCallbackFunc = void (*)(const char* filename, int32_t state, void* data);

enum {
    AOBB_STATE_MOUNTED = 1,
    AOBB_STATE_UNMOUNTED = 2,
    AOBB_STATE_ERROR_INTERNAL = 20,
    AOBB_STATE_ERROR_COULD_NOT_MOUNT = 21,
    AOBB_STATE_ERROR_COULD_NOT_UNMOUNT = 22,
    AOBB_STATE_ERROR_NOT_MOUNTED = 23,
    AOBB_STATE_ERROR_ALREADY_MOUNTED = 24,
    AOBB_STATE_ERROR_PERMISSION_DENIED = 25,
    AOBBINFO_OVERLAY = 0x0001,
};

extern "C" {
APerformanceHintManager* APerformanceHint_getManager();
APerformanceHintSession* APerformanceHint_createSession(APerformanceHintManager* manager,
                                                        const int32_t* threadIds, size_t size,
                                                        int64_t initialTargetWorkDurationNanos);
int APerformanceHint_createSessionUsingConfig(APerformanceHintManager* manager,
                                              ASessionCreationConfig* config,
                                              APerformanceHintSession** sessionOut);
int64_t APerformanceHint_getPreferredUpdateRateNanos(APerformanceHintManager* manager);
int APerformanceHint_updateTargetWorkDuration(APerformanceHintSession* session,
                                              int64_t targetDurationNanos);
int APerformanceHint_reportActualWorkDuration(APerformanceHintSession* session,
                                              int64_t actualDurationNanos);
int APerformanceHint_reportActualWorkDuration2(APerformanceHintSession* session,
                                               AWorkDuration* workDuration);
void APerformanceHint_closeSession(APerformanceHintSession* session);
int APerformanceHint_setThreads(APerformanceHintSession* session, const pid_t* threadIds,
                                size_t size);
int APerformanceHint_setPreferPowerEfficiency(APerformanceHintSession* session, bool enabled);
int APerformanceHint_notifyWorkloadReset(APerformanceHintSession* session, bool cpu, bool gpu,
                                         const char* debugName);

AWorkDuration* AWorkDuration_create();
void AWorkDuration_release(AWorkDuration* aWorkDuration);
void AWorkDuration_setWorkPeriodStartTimestampNanos(AWorkDuration* aWorkDuration,
                                                    int64_t workPeriodStartTimestampNanos);
void AWorkDuration_setActualTotalDurationNanos(AWorkDuration* aWorkDuration,
                                               int64_t actualTotalDurationNanos);
void AWorkDuration_setActualCpuDurationNanos(AWorkDuration* aWorkDuration,
                                             int64_t actualCpuDurationNanos);
void AWorkDuration_setActualGpuDurationNanos(AWorkDuration* aWorkDuration,
                                             int64_t actualGpuDurationNanos);

ASessionCreationConfig* ASessionCreationConfig_create();
void ASessionCreationConfig_release(ASessionCreationConfig* config);
int ASessionCreationConfig_setTids(ASessionCreationConfig* config, const pid_t* tids, size_t size);
int ASessionCreationConfig_setTargetWorkDurationNanos(ASessionCreationConfig* config,
                                                      int64_t targetWorkDurationNanos);
int ASessionCreationConfig_setPreferPowerEfficiency(ASessionCreationConfig* config, bool enabled);

AThermalManager* AThermal_acquireManager();
void AThermal_releaseManager(AThermalManager* manager);
AThermalStatus AThermal_getCurrentThermalStatus(AThermalManager* manager);
int AThermal_registerThermalStatusListener(AThermalManager* manager,
                                           AThermal_StatusCallback callback, void* data);
int AThermal_unregisterThermalStatusListener(AThermalManager* manager,
                                             AThermal_StatusCallback callback, void* data);
float AThermal_getThermalHeadroom(AThermalManager* manager, int forecastSeconds);
int AThermal_getThermalHeadroomThresholds(AThermalManager* manager,
                                          const AThermalHeadroomThreshold** outThresholds,
                                          size_t* size);

AStorageManager* AStorageManager_new();
void AStorageManager_delete(AStorageManager* mgr);
void AStorageManager_mountObb(AStorageManager* mgr, const char* rawPath, const char* key,
                              AStorageManager_obbCallbackFunc cb, void* data);
void AStorageManager_unmountObb(AStorageManager* mgr, const char* rawPath, const int force,
                                AStorageManager_obbCallbackFunc cb, void* data);
int AStorageManager_isObbMounted(AStorageManager* mgr, const char* rawPath);
const char* AStorageManager_getMountedObbPath(AStorageManager* mgr, const char* rawPath);

AObbInfo* AObbScanner_getObbInfo(const char* filename);
void AObbInfo_delete(AObbInfo* obbInfo);
const char* AObbInfo_getPackageName(AObbInfo* obbInfo);
int32_t AObbInfo_getVersion(AObbInfo* obbInfo);
int32_t AObbInfo_getFlags(AObbInfo* obbInfo);
}
