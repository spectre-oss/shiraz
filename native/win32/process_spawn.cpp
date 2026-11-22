// SPDX-License-Identifier: GPL-3.0-only
// Native Windows child process spawning with CreateProcessW, MSDN argument
// quoting, and Win32 Job Object lifecycle isolation (KILL_ON_JOB_CLOSE).
#include "shiraz/platform.hpp"

#include <cerrno>
#include <cstdint>
#include <string>
#include <vector>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>

namespace {

std::wstring utf8_to_wide(const char* src) {
    if (!src || !*src)
        return {};
    const int needed = MultiByteToWideChar(CP_UTF8, 0, src, -1, nullptr, 0);
    if (needed <= 1)
        return {};
    std::wstring out(static_cast<std::size_t>(needed - 1), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, src, -1, out.data(), needed);
    return out;
}

// Standard Windows CreateProcessW / CommandLineToArgvW argument quoting.
std::wstring quote_arg(const std::wstring& arg) {
    if (!arg.empty() && arg.find_first_of(L" \t\n\v\"") == std::wstring::npos) {
        return arg;
    }
    std::wstring quoted;
    quoted.push_back(L'"');
    for (auto it = arg.begin();; ++it) {
        std::size_t backslashes = 0;
        while (it != arg.end() && *it == L'\\') {
            ++it;
            ++backslashes;
        }
        if (it == arg.end()) {
            quoted.append(backslashes * 2, L'\\');
            break;
        }
        if (*it == L'"') {
            quoted.append(backslashes * 2 + 1, L'\\');
            quoted.push_back(L'"');
        } else {
            quoted.append(backslashes, L'\\');
            quoted.push_back(*it);
        }
    }
    quoted.push_back(L'"');
    return quoted;
}

} // namespace

extern "C" {

SHIRAZ_EXPORT int shiraz_win32_spawn_process(const char* const* argv, int argc,
                                             std::uint32_t* outPid, void** outProcessHandle,
                                             void** outJobHandle) {
    if (!argv || argc <= 0 || !argv[0] || !argv[0][0])
        return -EINVAL;
    if (outPid)
        *outPid = 0;
    if (outProcessHandle)
        *outProcessHandle = nullptr;
    if (outJobHandle)
        *outJobHandle = nullptr;

    std::wstring cmdline;
    for (int i = 0; i < argc; ++i) {
        if (!argv[i])
            return -EINVAL;
        if (i > 0)
            cmdline.push_back(L' ');
        cmdline += quote_arg(utf8_to_wide(argv[i]));
    }
    std::vector<wchar_t> mutableCmd(cmdline.begin(), cmdline.end());
    mutableCmd.push_back(L'\0');

    HANDLE job = CreateJobObjectW(nullptr, nullptr);
    if (!job)
        return -ENOMEM;
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits{};
    limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    if (!SetInformationJobObject(job, JobObjectExtendedLimitInformation, &limits, sizeof(limits))) {
        CloseHandle(job);
        return -EPERM;
    }

    STARTUPINFOW si{};
    si.cb = sizeof(si);
    PROCESS_INFORMATION pi{};
    const DWORD flags = CREATE_SUSPENDED | CREATE_NO_WINDOW;
    if (!CreateProcessW(nullptr, mutableCmd.data(), nullptr, nullptr, FALSE, flags, nullptr,
                        nullptr, &si, &pi)) {
        CloseHandle(job);
        return -ENOENT;
    }

    if (!AssignProcessToJobObject(job, pi.hProcess)) {
        TerminateProcess(pi.hProcess, 1);
        CloseHandle(pi.hThread);
        CloseHandle(pi.hProcess);
        CloseHandle(job);
        return -EPERM;
    }

    ResumeThread(pi.hThread);
    CloseHandle(pi.hThread);

    if (outPid)
        *outPid = static_cast<std::uint32_t>(pi.dwProcessId);
    if (outProcessHandle)
        *outProcessHandle = static_cast<void*>(pi.hProcess);
    else
        CloseHandle(pi.hProcess);
    if (outJobHandle)
        *outJobHandle = static_cast<void*>(job);
    else
        CloseHandle(job);
    return 0;
}

SHIRAZ_EXPORT int shiraz_win32_wait_process(void* processHandle, int timeoutMs, int* outExitCode) {
    if (!processHandle)
        return -EINVAL;
    HANDLE proc = static_cast<HANDLE>(processHandle);
    const DWORD waitMs = timeoutMs < 0 ? INFINITE : static_cast<DWORD>(timeoutMs);
    const DWORD rc = WaitForSingleObject(proc, waitMs);
    if (rc == WAIT_TIMEOUT)
        return -ETIMEDOUT;
    if (rc != WAIT_OBJECT_0)
        return -EIO;
    DWORD code = 0;
    if (!GetExitCodeProcess(proc, &code))
        return -EIO;
    if (outExitCode)
        *outExitCode = static_cast<int>(code);
    return 0;
}

SHIRAZ_EXPORT void shiraz_win32_close_process(void* processHandle, void* jobHandle) {
    if (processHandle)
        CloseHandle(static_cast<HANDLE>(processHandle));
    if (jobHandle)
        CloseHandle(static_cast<HANDLE>(jobHandle));
}

} // extern "C"
#endif
