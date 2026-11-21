// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/events.hpp"
#include <cstdio>

#include "shiraz/darwin/guest/maps_stream.hpp"
#include <cassert>
#include <sys/stat.h>
#include <fcntl.h>
static void maps_tests() {
    using namespace shiraz::guest;
    std::string current = "1000-2000 r--p 00000000 00:00 0 \n";
    unsigned calls = 0;
    auto factory = [&](std::string& out) {
        ++calls;
        out = current;
        return 0;
    };
    int a = process_maps_open(factory, O_CLOEXEC), other = process_maps_open(factory, 0);
    assert(a >= 0 && other >= 0 && calls == 0);
    assert(fcntl(a, F_GETFD) & FD_CLOEXEC);
    char buffer[256]{};
    assert(event_descriptor_read(a, buffer, 5) == 5);
    assert(std::string(buffer, 5) == current.substr(0, 5));
    assert(calls == 1);
    int alias = event_descriptor_dup(a);
    assert(alias >= 0);
    assert(event_descriptor_read(alias, buffer, 3) == 3);
    assert(std::string(buffer, 3) == current.substr(5, 3));
    ssize_t got = -1;
    assert(process_file_pread(alias, buffer, 4, 0, got) && got == 4);
    int64_t pos = -1;
    assert(process_file_seek(a, 0, SEEK_CUR, pos) && pos == 8);
    assert(process_file_seek(a, 0, SEEK_END, pos) && pos == -1 && errno == EINVAL);
    assert(process_file_seek(a, 0, SEEK_CUR, pos) && pos == 8);
    struct stat x{}, y{};
    int status = -1;
    assert(process_file_stat(a, x, status) && status == 0);
    assert(process_file_stat(alias, y, status) && status == 0);
    assert(x.st_ino == y.st_ino && x.st_size == 0 && (x.st_mode & S_IFMT) == S_IFREG);
    assert(event_descriptor_write(a, "x", 1) == -1 && errno == EBADF);
    assert(process_maps_open(factory, O_WRONLY) == -1 && errno == EINVAL);
    assert(event_descriptor_read(other, buffer, 2) == 2 &&
           std::string(buffer, 2) == current.substr(0, 2));
    assert(calls == 2);
    current = "3000-4000 rw-p 00000000 00:00 0 \n";
    assert(process_file_seek(alias, 0, SEEK_SET, pos) && pos == 0);
    assert(event_descriptor_read(a, buffer, sizeof buffer) == (ssize_t)current.size());
    assert(std::string(buffer, current.size()) == current && calls == 3);
    assert(event_descriptor_read(alias, buffer, 1) == 0);
    assert(event_descriptor_close(a) == 0);
    assert(process_file_seek(alias, 0, SEEK_SET, pos) && pos == 0);
    assert(event_descriptor_read(alias, buffer, 1) == 1);
    assert(event_descriptor_close(alias) == 0);
    assert(event_descriptor_close(other) == 0);
    MapsStream errors([](std::string& out) {
        out = "partial";
        return EIO;
    });
    size_t n = 99;
    assert(errors.read(buffer, 1, &n) == EIO && n == 99);
    assert(errors.seek(0, SEEK_CUR, &pos) == 0 && pos == 0);
    MapsStream huge([](std::string& out) {
        out.assign(8 * 1024 * 1024 + 1, 'x');
        return 0;
    });
    assert(huge.read(buffer, 1, &n) == EFBIG);
    MapsStream invalid([](std::string& out) {
        out = "missing newline";
        return 0;
    });
    assert(invalid.read(buffer, 1, &n) == EINVAL);
    assert(errors.read(nullptr, 0, &n) == 0 && n == 0);
    assert(errors.pread(-1, buffer, 1, &n) == EINVAL);
    assert(errors.seek(INT64_MAX, SEEK_SET, &pos) == 0);
    assert(errors.seek(1, SEEK_CUR, &pos) == EINVAL);
    std::puts(
        "MAPS_DESCRIPTOR_PASS dup_position=1 independent_open=1 pread=1 rewind_refresh=1 "
        "seek_reject=1 readonly=1 stat_zero=1 close_alias=1 snapshot_error_atomic=1 size_bound=1");
}
