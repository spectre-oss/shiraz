// SPDX-License-Identifier: GPL-3.0-only
// Native Windows app isolation layer: Job Object memory/process/UI restrictions
// and CreateRestrictedToken privilege stripping for sandboxed child processes.
#include "shiraz/platform.hpp"

#include <cerrno>
#include <cstdint>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>

extern "C" {

SHIRAZ_EXPORT void* shiraz_win32_sandbox_create_job(std::uint64_t memoryLimitBytes,
                                                    std::uint32_t activeProcessLimit,
                                                    int uiRestrictions) {
    HANDLE job = CreateJobObjectW(nullptr, nullptr);
    if (!job)
        return nullptr;

    JOBOBJECT_EXTENDED_LIMIT_INFORMATION ext{};
    ext.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    if (memoryLimitBytes > 0) {
        ext.BasicLimitInformation.LimitFlags |= JOB_OBJECT_LIMIT_JOB_MEMORY;
        ext.JobMemoryLimit = static_cast<SIZE_T>(memoryLimitBytes);
    }
    if (activeProcessLimit > 0) {
        ext.BasicLimitInformation.LimitFlags |= JOB_OBJECT_LIMIT_ACTIVE_PROCESS;
        ext.BasicLimitInformation.ActiveProcessLimit = static_cast<DWORD>(activeProcessLimit);
    }
    if (!SetInformationJobObject(job, JobObjectExtendedLimitInformation, &ext, sizeof(ext))) {
        CloseHandle(job);
        return nullptr;
    }

    if (uiRestrictions) {
        JOBOBJECT_BASIC_UI_RESTRICTIONS ui{};
        ui.UIRestrictionsClass = JOB_OBJECT_UILIMIT_DESKTOP | JOB_OBJECT_UILIMIT_EXITWINDOWS |
                                 JOB_OBJECT_UILIMIT_GLOBALATOMS |
                                 JOB_OBJECT_UILIMIT_SYSTEMPARAMETERS |
                                 JOB_OBJECT_UILIMIT_WRITECLIPBOARD;
        if (!SetInformationJobObject(job, JobObjectBasicUIRestrictions, &ui, sizeof(ui))) {
            CloseHandle(job);
            return nullptr;
        }
    }
    return static_cast<void*>(job);
}

SHIRAZ_EXPORT int shiraz_win32_sandbox_create_restricted_token(void** outToken) {
    if (!outToken)
        return -EINVAL;
    *outToken = nullptr;

    HANDLE currentToken = nullptr;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_DUPLICATE | TOKEN_ASSIGN_PRIMARY | TOKEN_QUERY,
                          &currentToken)) {
        return -EPERM;
    }
    HANDLE restricted = nullptr;
    const BOOL ok = CreateRestrictedToken(currentToken, DISABLE_MAX_PRIVILEGE, 0, nullptr, 0,
                                          nullptr, 0, nullptr, &restricted);
    CloseHandle(currentToken);
    if (!ok || !restricted)
        return -EPERM;
    *outToken = static_cast<void*>(restricted);
    return 0;
}

SHIRAZ_EXPORT void shiraz_win32_sandbox_close(void* handle) {
    if (handle)
        CloseHandle(static_cast<HANDLE>(handle));
}

} // extern "C"
#endif
