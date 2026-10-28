// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/archive_path.hpp"
#include <cassert>
int main() {
    for (auto path : {"res/layout/main.xml", "a.xml", "a", "resources/icons/a.png"}) assert(shiraz::validArchiveFilePath(path));
    for (auto path : {"", "/etc/passwd", "../a", "./a", "a/../b", "a/./b", "a//b", "a/", "a/..", "a/.", "a\\b"}) assert(!shiraz::validArchiveFilePath(path));
    assert(!shiraz::validArchiveFilePath(std::string_view("a\0b",3)));
}
