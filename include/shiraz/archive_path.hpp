// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <string_view>
namespace shiraz {
inline bool validArchiveFilePath(std::string_view path) {
    if (path.empty() || path.front()=='/' || path.find('\0')!=path.npos || path.find('\\')!=path.npos) return false;
    size_t begin=0;
    while (true) {
        size_t end=path.find('/',begin);
        auto part=path.substr(begin,end==path.npos?path.size()-begin:end-begin);
        if (part.empty() || part=="." || part=="..") return false;
        if (end==path.npos) return true;
        begin=end+1;
    }
}
}
