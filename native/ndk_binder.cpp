// SPDX-License-Identifier: GPL-3.0-only
// Real in-process Android NDK libbinder_ndk.so implementation: AIBinder,
// AIBinder_Class, AIBinder_Weak, AIBinder_DeathRecipient, ABinderProcess,
// binary AParcel serializer/deserializer, and AStatus.
#include "shiraz/ndk/binder_ndk.h"

#include <algorithm>
#include <atomic>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <mutex>
#include <new>
#include <string>
#include <unistd.h>
#include <vector>

struct AStatus {
    binder_exception_t exception = EX_NONE;
    int32_t serviceSpecific = 0;
    binder_status_t status = STATUS_OK;
    std::string message;
};

struct AIBinder_Class {
    std::string descriptor;
    AIBinder_Class_onCreate onCreate = nullptr;
    AIBinder_Class_onDestroy onDestroy = nullptr;
    AIBinder_Class_onTransact onTransact = nullptr;
    AIBinder_onDump onDump = nullptr;
    bool disableHeader = false;
};

struct AIBinder_DeathRecipient {
    AIBinder_DeathRecipient_onBinderDied onDied = nullptr;
    AIBinder_DeathRecipient_onBinderUnlinked onUnlinked = nullptr;
};

struct DeathLink {
    AIBinder_DeathRecipient* recipient = nullptr;
    void* cookie = nullptr;
};

struct BinderControlBlock {
    std::mutex mu;
    AIBinder* binder = nullptr;
};

struct AIBinder {
    const AIBinder_Class* clazz = nullptr;
    void* userData = nullptr;
    std::atomic<int32_t> strongCount{1};
    std::shared_ptr<BinderControlBlock> control;
    std::mutex deathMu;
    std::vector<DeathLink> deathLinks;
};

struct AIBinder_Weak {
    std::shared_ptr<BinderControlBlock> control;
};

struct AParcel {
    std::vector<uint8_t> data;
    mutable size_t pos = 0;
    std::vector<AIBinder*> binders;
    std::vector<int> fds;

    ~AParcel() {
        for (AIBinder* b : binders) {
            if (b) {
                AIBinder_decStrong(b);
            }
        }
        for (int fd : fds) {
            if (fd >= 0) {
                ::close(fd);
            }
        }
    }

    template <typename T> binder_status_t writePod(const T& v) {
        const size_t end = pos + sizeof(T);
        if (end > data.size()) {
            data.resize(end);
        }
        std::memcpy(data.data() + pos, &v, sizeof(T));
        pos = end;
        return STATUS_OK;
    }

    template <typename T> binder_status_t readPod(T* out) const {
        if (!out) {
            return STATUS_BAD_VALUE;
        }
        if (pos + sizeof(T) > data.size()) {
            return STATUS_NOT_ENOUGH_DATA;
        }
        std::memcpy(out, data.data() + pos, sizeof(T));
        pos += sizeof(T);
        return STATUS_OK;
    }
};

namespace {

std::atomic<bool> gThreadPoolStarted{false};
std::atomic<uint32_t> gMaxThreads{15};

bool allocToStdString(void* stringData, int32_t length, char** buffer) {
    auto* s = static_cast<std::string*>(stringData);
    if (length < 0) {
        s->clear();
        *buffer = nullptr;
        return true;
    }
    s->resize(static_cast<size_t>(length));
    *buffer = s->data();
    return true;
}

} // namespace

extern "C" {

// --- AIBinder_Class ---

AIBinder_Class* AIBinder_Class_define(const char* interfaceDescriptor,
                                      AIBinder_Class_onCreate onCreate,
                                      AIBinder_Class_onDestroy onDestroy,
                                      AIBinder_Class_onTransact onTransact) {
    if (!interfaceDescriptor || !onCreate || !onDestroy || !onTransact) {
        return nullptr;
    }
    auto* c = new (std::nothrow) AIBinder_Class();
    if (!c) {
        return nullptr;
    }
    c->descriptor = interfaceDescriptor;
    c->onCreate = onCreate;
    c->onDestroy = onDestroy;
    c->onTransact = onTransact;
    return c;
}

void AIBinder_Class_setOnDump(AIBinder_Class* clazz, AIBinder_onDump onDump) {
    if (clazz) {
        clazz->onDump = onDump;
    }
}

void AIBinder_Class_disableInterfaceTokenHeader(AIBinder_Class* clazz) {
    if (clazz) {
        clazz->disableHeader = true;
    }
}

const char* AIBinder_Class_getDescriptor(const AIBinder_Class* clazz) {
    return clazz ? clazz->descriptor.c_str() : nullptr;
}

// --- AIBinder ---

AIBinder* AIBinder_new(const AIBinder_Class* clazz, void* args) {
    if (!clazz) {
        return nullptr;
    }
    auto* b = new (std::nothrow) AIBinder();
    if (!b) {
        return nullptr;
    }
    b->clazz = clazz;
    b->control = std::make_shared<BinderControlBlock>();
    b->control->binder = b;
    b->userData = clazz->onCreate(args);
    return b;
}

bool AIBinder_isRemote(const AIBinder* /*binder*/) {
    return false;
}

bool AIBinder_isAlive(const AIBinder* binder) {
    return binder != nullptr && binder->strongCount.load() > 0;
}

binder_status_t AIBinder_ping(AIBinder* binder) {
    return binder ? STATUS_OK : STATUS_UNEXPECTED_NULL;
}

binder_status_t AIBinder_dump(AIBinder* binder, int fd, const char** args, uint32_t numArgs) {
    if (!binder) {
        return STATUS_UNEXPECTED_NULL;
    }
    if (binder->clazz && binder->clazz->onDump) {
        return binder->clazz->onDump(binder, fd, args, numArgs);
    }
    return STATUS_OK;
}

binder_status_t AIBinder_linkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                     void* cookie) {
    if (!binder || !recipient) {
        return STATUS_UNEXPECTED_NULL;
    }
    std::lock_guard<std::mutex> lock(binder->deathMu);
    binder->deathLinks.push_back({recipient, cookie});
    return STATUS_OK;
}

binder_status_t AIBinder_unlinkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                       void* cookie) {
    if (!binder || !recipient) {
        return STATUS_UNEXPECTED_NULL;
    }
    bool removed = false;
    {
        std::lock_guard<std::mutex> lock(binder->deathMu);
        for (auto it = binder->deathLinks.begin(); it != binder->deathLinks.end(); ++it) {
            if (it->recipient == recipient && it->cookie == cookie) {
                binder->deathLinks.erase(it);
                removed = true;
                break;
            }
        }
    }
    if (!removed) {
        return STATUS_NAME_NOT_FOUND;
    }
    if (recipient->onUnlinked) {
        recipient->onUnlinked(cookie);
    }
    return STATUS_OK;
}

uid_t AIBinder_getCallingUid(void) {
    return ::getuid();
}

pid_t AIBinder_getCallingPid(void) {
    return ::getpid();
}

void AIBinder_incStrong(AIBinder* binder) {
    if (binder) {
        binder->strongCount.fetch_add(1);
    }
}

void AIBinder_decStrong(AIBinder* binder) {
    if (!binder) {
        return;
    }
    if (binder->strongCount.fetch_sub(1) == 1) {
        {
            std::lock_guard<std::mutex> lock(binder->control->mu);
            binder->control->binder = nullptr;
        }
        std::vector<DeathLink> links;
        {
            std::lock_guard<std::mutex> lock(binder->deathMu);
            links.swap(binder->deathLinks);
        }
        for (const auto& l : links) {
            if (l.recipient && l.recipient->onUnlinked) {
                l.recipient->onUnlinked(l.cookie);
            }
        }
        if (binder->clazz && binder->clazz->onDestroy) {
            binder->clazz->onDestroy(binder->userData);
        }
        delete binder;
    }
}

int32_t AIBinder_debugGetRefCount(AIBinder* binder) {
    return binder ? binder->strongCount.load() : 0;
}

bool AIBinder_associateClass(AIBinder* binder, const AIBinder_Class* clazz) {
    if (!binder || !clazz) {
        return false;
    }
    if (binder->clazz == clazz) {
        return true;
    }
    if (binder->clazz && binder->clazz->descriptor == clazz->descriptor) {
        return true;
    }
    return false;
}

const AIBinder_Class* AIBinder_getClass(AIBinder* binder) {
    return binder ? binder->clazz : nullptr;
}

void* AIBinder_getUserData(AIBinder* binder) {
    return binder ? binder->userData : nullptr;
}

binder_status_t AIBinder_prepareTransaction(AIBinder* binder, AParcel** in) {
    if (!binder || !in) {
        return STATUS_UNEXPECTED_NULL;
    }
    AParcel* p = AParcel_create();
    if (!p) {
        *in = nullptr;
        return STATUS_NO_MEMORY;
    }
    if (binder->clazz && !binder->clazz->disableHeader) {
        AParcel_writeString(p, binder->clazz->descriptor.c_str(),
                            static_cast<int32_t>(binder->clazz->descriptor.size()));
    }
    *in = p;
    return STATUS_OK;
}

binder_status_t AIBinder_transact(AIBinder* binder, transaction_code_t code, AParcel** in,
                                  AParcel** out, binder_flags_t /*flags*/) {
    if (!binder || !in || !*in || !out) {
        return STATUS_UNEXPECTED_NULL;
    }
    AParcel* inParcel = *in;
    *in = nullptr;
    AParcel* outParcel = AParcel_create();
    if (!outParcel) {
        AParcel_delete(inParcel);
        *out = nullptr;
        return STATUS_NO_MEMORY;
    }
    *out = outParcel;

    inParcel->pos = 0;
    if (binder->clazz && !binder->clazz->disableHeader) {
        std::string token;
        const binder_status_t hdrSt = AParcel_readString(inParcel, &token, allocToStdString);
        if (!token.empty() && token.back() == '\0') {
            token.pop_back();
        }
        if (hdrSt != STATUS_OK || token != binder->clazz->descriptor) {
            AParcel_delete(inParcel);
            return STATUS_BAD_TYPE;
        }
    }

    binder_status_t st = STATUS_UNKNOWN_TRANSACTION;
    if (binder->clazz && binder->clazz->onTransact) {
        st = binder->clazz->onTransact(binder, code, inParcel, outParcel);
    }
    outParcel->pos = 0;
    AParcel_delete(inParcel);
    return st;
}

// --- AIBinder_Weak ---

AIBinder_Weak* AIBinder_Weak_new(AIBinder* binder) {
    if (!binder) {
        return nullptr;
    }
    auto* w = new (std::nothrow) AIBinder_Weak();
    if (!w) {
        return nullptr;
    }
    w->control = binder->control;
    return w;
}

void AIBinder_Weak_delete(AIBinder_Weak* weakBinder) {
    delete weakBinder;
}

AIBinder* AIBinder_Weak_promote(AIBinder_Weak* weakBinder) {
    if (!weakBinder || !weakBinder->control) {
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(weakBinder->control->mu);
    AIBinder* b = weakBinder->control->binder;
    if (!b || b->strongCount.load() <= 0) {
        return nullptr;
    }
    b->strongCount.fetch_add(1);
    return b;
}

AIBinder_Weak* AIBinder_Weak_clone(const AIBinder_Weak* weak) {
    if (!weak) {
        return nullptr;
    }
    auto* w = new (std::nothrow) AIBinder_Weak();
    if (!w) {
        return nullptr;
    }
    w->control = weak->control;
    return w;
}

// --- AIBinder_DeathRecipient ---

AIBinder_DeathRecipient*
AIBinder_DeathRecipient_new(AIBinder_DeathRecipient_onBinderDied onBinderDied) {
    if (!onBinderDied) {
        return nullptr;
    }
    auto* r = new (std::nothrow) AIBinder_DeathRecipient();
    if (!r) {
        return nullptr;
    }
    r->onDied = onBinderDied;
    return r;
}

void AIBinder_DeathRecipient_delete(AIBinder_DeathRecipient* recipient) {
    delete recipient;
}

void AIBinder_DeathRecipient_setOnUnlinked(AIBinder_DeathRecipient* recipient,
                                           AIBinder_DeathRecipient_onBinderUnlinked onUnlinked) {
    if (recipient) {
        recipient->onUnlinked = onUnlinked;
    }
}

// --- Java interop & Process ---

AIBinder* AIBinder_fromJavaBinder(JNIEnv* /*env*/, jobject /*binder*/) {
    return nullptr;
}

jobject AIBinder_toJavaBinder(JNIEnv* /*env*/, AIBinder* /*binder*/) {
    return nullptr;
}

bool ABinderProcess_setThreadPoolMaxThreadCount(uint32_t numThreads) {
    gMaxThreads.store(numThreads);
    return true;
}

void ABinderProcess_startThreadPool(void) {
    gThreadPoolStarted.store(true);
}

void ABinderProcess_joinThreadPool(void) {
    gThreadPoolStarted.store(true);
}

bool ABinderProcess_isThreadPoolStarted(void) {
    return gThreadPoolStarted.load();
}

// --- AParcel ---

AParcel* AParcel_create(void) {
    return new (std::nothrow) AParcel();
}

void AParcel_delete(AParcel* parcel) {
    delete parcel;
}

binder_status_t AParcel_setDataPosition(const AParcel* parcel, int32_t position) {
    if (!parcel || position < 0 || static_cast<size_t>(position) > parcel->data.size()) {
        return STATUS_BAD_VALUE;
    }
    parcel->pos = static_cast<size_t>(position);
    return STATUS_OK;
}

int32_t AParcel_getDataPosition(const AParcel* parcel) {
    return parcel ? static_cast<int32_t>(parcel->pos) : 0;
}

int32_t AParcel_getDataSize(const AParcel* parcel) {
    return parcel ? static_cast<int32_t>(parcel->data.size()) : 0;
}

binder_status_t AParcel_writeInt32(AParcel* p, int32_t v) {
    return p ? p->writePod(v) : STATUS_BAD_VALUE;
}
binder_status_t AParcel_writeUint32(AParcel* p, uint32_t v) {
    return p ? p->writePod(v) : STATUS_BAD_VALUE;
}
binder_status_t AParcel_writeInt64(AParcel* p, int64_t v) {
    return p ? p->writePod(v) : STATUS_BAD_VALUE;
}
binder_status_t AParcel_writeUint64(AParcel* p, uint64_t v) {
    return p ? p->writePod(v) : STATUS_BAD_VALUE;
}
binder_status_t AParcel_writeFloat(AParcel* p, float v) {
    return p ? p->writePod(v) : STATUS_BAD_VALUE;
}
binder_status_t AParcel_writeDouble(AParcel* p, double v) {
    return p ? p->writePod(v) : STATUS_BAD_VALUE;
}
binder_status_t AParcel_writeBool(AParcel* p, bool v) {
    const uint8_t b = v ? 1u : 0u;
    return p ? p->writePod(b) : STATUS_BAD_VALUE;
}
binder_status_t AParcel_writeChar(AParcel* p, uint16_t v) {
    return p ? p->writePod(v) : STATUS_BAD_VALUE;
}
binder_status_t AParcel_writeByte(AParcel* p, int8_t v) {
    return p ? p->writePod(v) : STATUS_BAD_VALUE;
}

binder_status_t AParcel_writeString(AParcel* p, const char* string, int32_t length) {
    if (!p) {
        return STATUS_BAD_VALUE;
    }
    if (!string) {
        return p->writePod<int32_t>(-1);
    }
    if (length < 0) {
        length = static_cast<int32_t>(std::strlen(string));
    }
    p->writePod<int32_t>(length);
    const size_t n = static_cast<size_t>(length);
    const size_t end = p->pos + n;
    if (end > p->data.size()) {
        p->data.resize(end);
    }
    if (n > 0) {
        std::memcpy(p->data.data() + p->pos, string, n);
    }
    p->pos = end;
    return STATUS_OK;
}

binder_status_t AParcel_writeByteArray(AParcel* p, const int8_t* arrayData, int32_t length) {
    if (!p) {
        return STATUS_BAD_VALUE;
    }
    if (!arrayData) {
        return p->writePod<int32_t>(-1);
    }
    if (length < 0) {
        return STATUS_BAD_VALUE;
    }
    p->writePod<int32_t>(length);
    const size_t n = static_cast<size_t>(length);
    const size_t end = p->pos + n;
    if (end > p->data.size()) {
        p->data.resize(end);
    }
    if (n > 0) {
        std::memcpy(p->data.data() + p->pos, arrayData, n);
    }
    p->pos = end;
    return STATUS_OK;
}

binder_status_t AParcel_writeInt32Array(AParcel* p, const int32_t* arrayData, int32_t length) {
    if (!p) {
        return STATUS_BAD_VALUE;
    }
    if (!arrayData) {
        return p->writePod<int32_t>(-1);
    }
    if (length < 0) {
        return STATUS_BAD_VALUE;
    }
    p->writePod<int32_t>(length);
    const size_t bytes = static_cast<size_t>(length) * sizeof(int32_t);
    const size_t end = p->pos + bytes;
    if (end > p->data.size()) {
        p->data.resize(end);
    }
    if (bytes > 0) {
        std::memcpy(p->data.data() + p->pos, arrayData, bytes);
    }
    p->pos = end;
    return STATUS_OK;
}

binder_status_t AParcel_writeStrongBinder(AParcel* p, AIBinder* binder) {
    if (!p) {
        return STATUS_BAD_VALUE;
    }
    if (!binder) {
        return p->writePod<int32_t>(-1);
    }
    AIBinder_incStrong(binder);
    const int32_t idx = static_cast<int32_t>(p->binders.size());
    p->binders.push_back(binder);
    return p->writePod<int32_t>(idx);
}

binder_status_t AParcel_writeParcelFileDescriptor(AParcel* p, int fd) {
    if (!p) {
        return STATUS_BAD_VALUE;
    }
    if (fd < 0) {
        return p->writePod<int32_t>(-1);
    }
    const int dupFd = ::dup(fd);
    if (dupFd < 0) {
        return STATUS_BAD_VALUE;
    }
    const int32_t idx = static_cast<int32_t>(p->fds.size());
    p->fds.push_back(dupFd);
    return p->writePod<int32_t>(idx);
}

binder_status_t AParcel_writeStatusHeader(AParcel* p, const AStatus* status) {
    if (!p || !status) {
        return STATUS_BAD_VALUE;
    }
    p->writePod<int32_t>(status->exception);
    if (status->exception != EX_NONE) {
        AParcel_writeString(p, status->message.c_str(),
                            static_cast<int32_t>(status->message.size()));
        p->writePod<int32_t>(status->serviceSpecific);
        p->writePod<int32_t>(status->status);
    }
    return STATUS_OK;
}

binder_status_t AParcel_readInt32(const AParcel* p, int32_t* v) {
    return p ? p->readPod(v) : STATUS_BAD_VALUE;
}
binder_status_t AParcel_readUint32(const AParcel* p, uint32_t* v) {
    return p ? p->readPod(v) : STATUS_BAD_VALUE;
}
binder_status_t AParcel_readInt64(const AParcel* p, int64_t* v) {
    return p ? p->readPod(v) : STATUS_BAD_VALUE;
}
binder_status_t AParcel_readUint64(const AParcel* p, uint64_t* v) {
    return p ? p->readPod(v) : STATUS_BAD_VALUE;
}
binder_status_t AParcel_readFloat(const AParcel* p, float* v) {
    return p ? p->readPod(v) : STATUS_BAD_VALUE;
}
binder_status_t AParcel_readDouble(const AParcel* p, double* v) {
    return p ? p->readPod(v) : STATUS_BAD_VALUE;
}
binder_status_t AParcel_readBool(const AParcel* p, bool* v) {
    if (!p || !v) {
        return STATUS_BAD_VALUE;
    }
    uint8_t b = 0;
    const binder_status_t st = p->readPod(&b);
    if (st == STATUS_OK) {
        *v = (b != 0);
    }
    return st;
}
binder_status_t AParcel_readChar(const AParcel* p, uint16_t* v) {
    return p ? p->readPod(v) : STATUS_BAD_VALUE;
}
binder_status_t AParcel_readByte(const AParcel* p, int8_t* v) {
    return p ? p->readPod(v) : STATUS_BAD_VALUE;
}

binder_status_t AParcel_readString(const AParcel* p, void* stringData,
                                   AParcel_stringAllocator allocator) {
    if (!p || !allocator) {
        return STATUS_BAD_VALUE;
    }
    int32_t len = 0;
    const binder_status_t st = p->readPod(&len);
    if (st != STATUS_OK) {
        return st;
    }
    if (len < 0) {
        char* buf = nullptr;
        return allocator(stringData, -1, &buf) ? STATUS_OK : STATUS_NO_MEMORY;
    }
    const size_t n = static_cast<size_t>(len);
    if (p->pos + n > p->data.size()) {
        return STATUS_NOT_ENOUGH_DATA;
    }
    char* buf = nullptr;
    if (!allocator(stringData, len + 1, &buf)) {
        return STATUS_NO_MEMORY;
    }
    if (buf) {
        if (n > 0) {
            std::memcpy(buf, p->data.data() + p->pos, n);
        }
        buf[n] = '\0';
    }
    p->pos += n;
    return STATUS_OK;
}

binder_status_t AParcel_readByteArray(const AParcel* p, void* arrayData,
                                      AParcel_byteArrayAllocator allocator) {
    if (!p || !allocator) {
        return STATUS_BAD_VALUE;
    }
    int32_t len = 0;
    const binder_status_t st = p->readPod(&len);
    if (st != STATUS_OK) {
        return st;
    }
    if (len < 0) {
        int8_t* buf = nullptr;
        return allocator(arrayData, -1, &buf) ? STATUS_OK : STATUS_NO_MEMORY;
    }
    const size_t n = static_cast<size_t>(len);
    if (p->pos + n > p->data.size()) {
        return STATUS_NOT_ENOUGH_DATA;
    }
    int8_t* buf = nullptr;
    if (!allocator(arrayData, len, &buf)) {
        return STATUS_NO_MEMORY;
    }
    if (n > 0 && buf) {
        std::memcpy(buf, p->data.data() + p->pos, n);
    }
    p->pos += n;
    return STATUS_OK;
}

binder_status_t AParcel_readInt32Array(const AParcel* p, void* arrayData,
                                       AParcel_int32ArrayAllocator allocator) {
    if (!p || !allocator) {
        return STATUS_BAD_VALUE;
    }
    int32_t len = 0;
    const binder_status_t st = p->readPod(&len);
    if (st != STATUS_OK) {
        return st;
    }
    if (len < 0) {
        int32_t* buf = nullptr;
        return allocator(arrayData, -1, &buf) ? STATUS_OK : STATUS_NO_MEMORY;
    }
    const size_t bytes = static_cast<size_t>(len) * sizeof(int32_t);
    if (p->pos + bytes > p->data.size()) {
        return STATUS_NOT_ENOUGH_DATA;
    }
    int32_t* buf = nullptr;
    if (!allocator(arrayData, len, &buf)) {
        return STATUS_NO_MEMORY;
    }
    if (bytes > 0 && buf) {
        std::memcpy(buf, p->data.data() + p->pos, bytes);
    }
    p->pos += bytes;
    return STATUS_OK;
}

binder_status_t AParcel_readStrongBinder(const AParcel* p, AIBinder** binder) {
    if (!p || !binder) {
        return STATUS_BAD_VALUE;
    }
    int32_t idx = -1;
    const binder_status_t st = p->readPod(&idx);
    if (st != STATUS_OK) {
        return st;
    }
    if (idx < 0) {
        *binder = nullptr;
        return STATUS_OK;
    }
    if (static_cast<size_t>(idx) >= p->binders.size()) {
        return STATUS_BAD_VALUE;
    }
    AIBinder* b = p->binders[static_cast<size_t>(idx)];
    AIBinder_incStrong(b);
    *binder = b;
    return STATUS_OK;
}

binder_status_t AParcel_readParcelFileDescriptor(const AParcel* p, int* fd) {
    if (!p || !fd) {
        return STATUS_BAD_VALUE;
    }
    int32_t idx = -1;
    const binder_status_t st = p->readPod(&idx);
    if (st != STATUS_OK) {
        return st;
    }
    if (idx < 0) {
        *fd = -1;
        return STATUS_OK;
    }
    if (static_cast<size_t>(idx) >= p->fds.size()) {
        return STATUS_BAD_VALUE;
    }
    const int dupFd = ::dup(p->fds[static_cast<size_t>(idx)]);
    if (dupFd < 0) {
        return STATUS_BAD_VALUE;
    }
    *fd = dupFd;
    return STATUS_OK;
}

binder_status_t AParcel_readStatusHeader(const AParcel* p, AStatus** status) {
    if (!p || !status) {
        return STATUS_BAD_VALUE;
    }
    int32_t ex = EX_NONE;
    const binder_status_t st = p->readPod(&ex);
    if (st != STATUS_OK) {
        return st;
    }
    if (ex == EX_NONE) {
        *status = AStatus_newOk();
        return *status ? STATUS_OK : STATUS_NO_MEMORY;
    }
    std::string msg;
    if (AParcel_readString(p, &msg, allocToStdString) != STATUS_OK) {
        return STATUS_NOT_ENOUGH_DATA;
    }
    if (!msg.empty() && msg.back() == '\0') {
        msg.pop_back();
    }
    int32_t serviceSpec = 0;
    int32_t rawStatus = STATUS_OK;
    p->readPod(&serviceSpec);
    p->readPod(&rawStatus);
    auto* s = new (std::nothrow) AStatus();
    if (!s) {
        return STATUS_NO_MEMORY;
    }
    s->exception = ex;
    s->serviceSpecific = serviceSpec;
    s->status = rawStatus;
    s->message = std::move(msg);
    *status = s;
    return STATUS_OK;
}

// --- AStatus ---

AStatus* AStatus_newOk(void) {
    return new (std::nothrow) AStatus();
}

AStatus* AStatus_fromExceptionCode(binder_exception_t exception) {
    return AStatus_fromExceptionCodeWithMessage(exception, "");
}

AStatus* AStatus_fromExceptionCodeWithMessage(binder_exception_t exception, const char* message) {
    auto* s = new (std::nothrow) AStatus();
    if (!s) {
        return nullptr;
    }
    s->exception = exception;
    s->status = (exception == EX_NONE) ? STATUS_OK : STATUS_FAILED_TRANSACTION;
    s->message = message ? message : "";
    return s;
}

AStatus* AStatus_fromServiceSpecificError(int32_t serviceSpecific) {
    return AStatus_fromServiceSpecificErrorWithMessage(serviceSpecific, "");
}

AStatus* AStatus_fromServiceSpecificErrorWithMessage(int32_t serviceSpecific, const char* message) {
    auto* s = new (std::nothrow) AStatus();
    if (!s) {
        return nullptr;
    }
    s->exception = EX_SERVICE_SPECIFIC;
    s->serviceSpecific = serviceSpecific;
    s->status = STATUS_OK;
    s->message = message ? message : "";
    return s;
}

AStatus* AStatus_fromStatus(binder_status_t status) {
    auto* s = new (std::nothrow) AStatus();
    if (!s) {
        return nullptr;
    }
    s->exception = (status == STATUS_OK) ? EX_NONE : EX_TRANSACTION_FAILED;
    s->status = status;
    return s;
}

bool AStatus_isOk(const AStatus* status) {
    return status && status->exception == EX_NONE && status->status == STATUS_OK;
}

binder_exception_t AStatus_getExceptionCode(const AStatus* status) {
    return status ? status->exception : EX_NULL_POINTER;
}

int32_t AStatus_getServiceSpecificError(const AStatus* status) {
    return status ? status->serviceSpecific : 0;
}

binder_status_t AStatus_getStatus(const AStatus* status) {
    return status ? status->status : STATUS_UNEXPECTED_NULL;
}

const char* AStatus_getMessage(const AStatus* status) {
    return status ? status->message.c_str() : "";
}

const char* AStatus_getDescription(const AStatus* status) {
    std::string desc = "null status";
    if (status) {
        if (AStatus_isOk(status)) {
            desc = "OK";
        } else {
            desc = "EX=" + std::to_string(status->exception) +
                   " STATUS=" + std::to_string(status->status) +
                   " SERVICE=" + std::to_string(status->serviceSpecific) +
                   (status->message.empty() ? "" : (": " + status->message));
        }
    }
    char* copy = static_cast<char*>(std::malloc(desc.size() + 1));
    if (copy) {
        std::memcpy(copy, desc.c_str(), desc.size() + 1);
    }
    return copy;
}

void AStatus_deleteDescription(const char* description) {
    std::free(const_cast<char*>(description));
}

void AStatus_delete(AStatus* status) {
    delete status;
}

} // extern "C"
