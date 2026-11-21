// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/ndk/asset_manager.h"
#include <algorithm>
#include <cerrno>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <new>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

struct AAssetManager {
    std::string root_dir;
};

struct AAssetDir {
    std::vector<std::string> entries;
    size_t index = 0;
};

struct AAsset {
    std::string file_path;
    std::vector<uint8_t> data;
    size_t pos = 0;
};

namespace {
bool safe_relative_path(const char* rel, std::string& out) {
    if (!rel)
        return false;
    std::string s(rel);
    while (!s.empty() && s.front() == '/')
        s.erase(s.begin());
    size_t i = 0;
    while (i < s.size()) {
        size_t slash = s.find('/', i);
        std::string part = slash == std::string::npos ? s.substr(i) : s.substr(i, slash - i);
        if (part == "..")
            return false;
        if (slash == std::string::npos)
            break;
        i = slash + 1;
    }
    out = std::move(s);
    return true;
}

std::string join_root(const std::string& root, const std::string& rel) {
    if (rel.empty())
        return root;
    if (!root.empty() && root.back() == '/')
        return root + rel;
    return root + "/" + rel;
}
} // namespace

extern "C" {

AAssetManager* shiraz_asset_manager_from_dir(const char* root_dir) {
    if (!root_dir || !root_dir[0])
        return nullptr;
    struct stat st{};
    if (stat(root_dir, &st) != 0 || !S_ISDIR(st.st_mode))
        return nullptr;
    auto* mgr = new (std::nothrow) AAssetManager();
    if (!mgr)
        return nullptr;
    mgr->root_dir = root_dir;
    return mgr;
}

void shiraz_asset_manager_destroy(AAssetManager* mgr) {
    delete mgr;
}

AAssetManager* AAssetManager_fromJava(JNIEnv* env, jobject assetManager) {
    if (!env || !assetManager)
        return nullptr;
    jclass cls = env->GetObjectClass(assetManager);
    if (!cls)
        return nullptr;
    jmethodID get_root = env->GetMethodID(cls, "shirazAssetDirectory", "()Ljava/lang/String;");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        get_root = nullptr;
    }
    env->DeleteLocalRef(cls);
    if (!get_root)
        return nullptr;
    auto path = static_cast<jstring>(env->CallObjectMethod(assetManager, get_root));
    if (env->ExceptionCheck() || !path) {
        if (env->ExceptionCheck())
            env->ExceptionClear();
        return nullptr;
    }
    const char* utf = env->GetStringUTFChars(path, nullptr);
    AAssetManager* mgr = utf ? shiraz_asset_manager_from_dir(utf) : nullptr;
    if (utf)
        env->ReleaseStringUTFChars(path, utf);
    env->DeleteLocalRef(path);
    return mgr;
}

AAssetDir* AAssetManager_openDir(AAssetManager* mgr, const char* dirName) {
    if (!mgr)
        return nullptr;
    std::string rel;
    if (!safe_relative_path(dirName ? dirName : "", rel))
        return nullptr;
    std::string full = join_root(mgr->root_dir, rel);
    DIR* dir = opendir(full.c_str());
    if (!dir)
        return nullptr;
    auto* out = new (std::nothrow) AAssetDir();
    if (!out) {
        closedir(dir);
        return nullptr;
    }
    while (struct dirent* ent = readdir(dir)) {
        if (!std::strcmp(ent->d_name, ".") || !std::strcmp(ent->d_name, ".."))
            continue;
        std::string item = join_root(full, ent->d_name);
        struct stat st{};
        if (stat(item.c_str(), &st) == 0 && S_ISREG(st.st_mode))
            out->entries.emplace_back(ent->d_name);
    }
    closedir(dir);
    std::sort(out->entries.begin(), out->entries.end());
    return out;
}

const char* AAssetDir_getNextFileName(AAssetDir* assetDir) {
    if (!assetDir || assetDir->index >= assetDir->entries.size())
        return nullptr;
    return assetDir->entries[assetDir->index++].c_str();
}

void AAssetDir_rewind(AAssetDir* assetDir) {
    if (assetDir)
        assetDir->index = 0;
}

void AAssetDir_close(AAssetDir* assetDir) {
    delete assetDir;
}

AAsset* AAssetManager_open(AAssetManager* mgr, const char* filename, int) {
    if (!mgr || !filename || !filename[0])
        return nullptr;
    std::string rel;
    if (!safe_relative_path(filename, rel) || rel.empty())
        return nullptr;
    std::string full = join_root(mgr->root_dir, rel);
    int fd = open(full.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0)
        return nullptr;
    struct stat st{};
    if (fstat(fd, &st) != 0 || !S_ISREG(st.st_mode) || st.st_size < 0 ||
        st.st_size > 256 * 1024 * 1024) {
        close(fd);
        return nullptr;
    }
    auto* asset = new (std::nothrow) AAsset();
    if (!asset) {
        close(fd);
        return nullptr;
    }
    asset->file_path = std::move(full);
    try {
        asset->data.resize(static_cast<size_t>(st.st_size));
    } catch (const std::bad_alloc&) {
        close(fd);
        delete asset;
        return nullptr;
    }
    size_t done = 0;
    while (done < asset->data.size()) {
        ssize_t n = read(fd, asset->data.data() + done, asset->data.size() - done);
        if (n < 0) {
            if (errno == EINTR)
                continue;
            close(fd);
            delete asset;
            return nullptr;
        }
        if (n == 0)
            break;
        done += static_cast<size_t>(n);
    }
    asset->data.resize(done);
    close(fd);
    return asset;
}

int AAsset_read(AAsset* asset, void* buf, size_t count) {
    if (!asset || (!buf && count > 0))
        return -1;
    if (asset->pos >= asset->data.size() || count == 0)
        return 0;
    size_t avail = asset->data.size() - asset->pos;
    size_t take = std::min(avail, count);
    std::memcpy(buf, asset->data.data() + asset->pos, take);
    asset->pos += take;
    return static_cast<int>(take);
}

shiraz_off64_t AAsset_seek64(AAsset* asset, shiraz_off64_t offset, int whence) {
    if (!asset)
        return -1;
    int64_t base = 0;
    if (whence == SEEK_SET)
        base = 0;
    else if (whence == SEEK_CUR)
        base = static_cast<int64_t>(asset->pos);
    else if (whence == SEEK_END)
        base = static_cast<int64_t>(asset->data.size());
    else
        return -1;
    int64_t next = base + static_cast<int64_t>(offset);
    if (next < 0 || next > static_cast<int64_t>(asset->data.size()))
        return -1;
    asset->pos = static_cast<size_t>(next);
    return static_cast<shiraz_off64_t>(asset->pos);
}

off_t AAsset_seek(AAsset* asset, off_t offset, int whence) {
    return static_cast<off_t>(AAsset_seek64(asset, static_cast<shiraz_off64_t>(offset), whence));
}

void AAsset_close(AAsset* asset) {
    delete asset;
}

const void* AAsset_getBuffer(AAsset* asset) {
    if (!asset)
        return nullptr;
    return asset->data.empty() ? static_cast<const void*>("") : asset->data.data();
}

shiraz_off64_t AAsset_getLength64(AAsset* asset) {
    return asset ? static_cast<shiraz_off64_t>(asset->data.size()) : 0;
}

off_t AAsset_getLength(AAsset* asset) {
    return static_cast<off_t>(AAsset_getLength64(asset));
}

shiraz_off64_t AAsset_getRemainingLength64(AAsset* asset) {
    if (!asset || asset->pos >= asset->data.size())
        return 0;
    return static_cast<shiraz_off64_t>(asset->data.size() - asset->pos);
}

off_t AAsset_getRemainingLength(AAsset* asset) {
    return static_cast<off_t>(AAsset_getRemainingLength64(asset));
}

int AAsset_openFileDescriptor64(AAsset* asset, shiraz_off64_t* outStart,
                                shiraz_off64_t* outLength) {
    if (!asset || !outStart || !outLength || asset->file_path.empty())
        return -1;
    int fd = open(asset->file_path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0)
        return -1;
    *outStart = 0;
    *outLength = static_cast<shiraz_off64_t>(asset->data.size());
    return fd;
}

int AAsset_openFileDescriptor(AAsset* asset, off_t* outStart, off_t* outLength) {
    if (!outStart || !outLength)
        return -1;
    shiraz_off64_t start = 0;
    shiraz_off64_t length = 0;
    int fd = AAsset_openFileDescriptor64(asset, &start, &length);
    if (fd >= 0) {
        *outStart = static_cast<off_t>(start);
        *outLength = static_cast<off_t>(length);
    }
    return fd;
}

int AAsset_isAllocated(AAsset* asset) {
    return asset ? 1 : 0;
}

} // extern "C"
