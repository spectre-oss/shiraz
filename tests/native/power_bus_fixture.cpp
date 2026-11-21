// SPDX-License-Identifier: GPL-3.0-only
#include <cstdarg>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>
namespace {
int mode = 0, read_fd = -1, write_fd = -1, buses = 0, messages = 0;
}
extern "C" void power_fixture_mode(int value) {
    mode = value;
}
extern "C" int power_fixture_balanced() {
    return buses == 0 && messages == 0;
}
extern "C" int power_fixture_eof() {
    char byte;
    return read(read_fd, &byte, 1) == 0;
}
extern "C" void power_fixture_disconnect() {
    if (read_fd >= 0)
        close(read_fd);
    read_fd = -1;
}
extern "C" int sd_bus_open_system(void** bus) {
    *bus = reinterpret_cast<void*>(1);
    ++buses;
    return 0;
}
extern "C" void* sd_bus_flush_close_unref(void*) {
    --buses;
    return nullptr;
}
extern "C" int sd_bus_set_method_call_timeout(void*, unsigned long long timeout) {
    return timeout == 2000000 ? 0 : -1;
}
extern "C" int sd_bus_call_method(void*, const char* dest, const char* path, const char* iface,
                                  const char* member, void*, void** reply, const char* types, ...) {
    if (mode == 1)
        return -1;
    if (std::strcmp(dest, "org.freedesktop.login1") ||
        std::strcmp(path, "/org/freedesktop/login1") ||
        std::strcmp(iface, "org.freedesktop.login1.Manager") || std::strcmp(member, "Inhibit") ||
        std::strcmp(types, "ssss"))
        return -1;
    va_list args;
    va_start(args, types);
    bool valid = std::strcmp(va_arg(args, const char*), "idle") == 0;
    valid = std::strcmp(va_arg(args, const char*), "Shiraz") == 0 && valid;
    valid = std::strcmp(va_arg(args, const char*), "Android background work") == 0 && valid;
    valid = std::strcmp(va_arg(args, const char*), "block") == 0 && valid;
    va_end(args);
    if (!valid)
        return -1;
    if (read_fd >= 0)
        close(read_fd);
    int pair[2];
    if (pipe(pair))
        return -1;
    read_fd = pair[0];
    write_fd = pair[1];
    fcntl(read_fd, F_SETFL, O_NONBLOCK);
    *reply = reinterpret_cast<void*>(2);
    ++messages;
    return 1;
}
extern "C" int sd_bus_message_read(void*, const char* types, ...) {
    va_list args;
    va_start(args, types);
    int* value = va_arg(args, int*);
    va_end(args);
    if (std::strcmp(types, "h"))
        return -1;
    *value = mode == 2 ? -1 : write_fd;
    return 1;
}
extern "C" void* sd_bus_message_unref(void*) {
    --messages;
    close(write_fd);
    write_fd = -1;
    return nullptr;
}
