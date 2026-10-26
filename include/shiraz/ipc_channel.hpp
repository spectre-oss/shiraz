// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstdint>
#include <cstddef>
#include <vector>
namespace shiraz {
// Private socketpair transport, not Android's kernel Binder wire protocol.
// Each direction must have one serialized reader/writer. Packet owns received FDs.
struct IpcPacket {
    int pid=-1,uid=-1;
    std::vector<uint8_t> bytes;
    std::vector<int> descriptors;
    IpcPacket() = default;
    IpcPacket(const IpcPacket&) = delete;
    IpcPacket& operator=(const IpcPacket&) = delete;
    ~IpcPacket();
};
int ipc_pair(int sockets[2]);
int ipc_send(int socket,const uint8_t* bytes,size_t size,const int* descriptors,size_t count);
int ipc_receive(int socket,IpcPacket& packet);
}
