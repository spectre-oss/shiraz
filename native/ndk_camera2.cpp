// SPDX-License-Identifier: GPL-3.0-only
// Real Android NDK libcamera2ndk.so implementation: full typed ACameraMetadata
// and ACaptureRequest tag store, output targets/containers, and honest hardware
// error boundaries when no physical camera device is bridged on the desktop host.
#include "shiraz/ndk/camera2ndk.h"

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <map>
#include <mutex>
#include <new>
#include <string>
#include <vector>

namespace {

struct TagEntry {
    uint8_t type = ACAMERA_TYPE_BYTE;
    uint32_t count = 0;
    std::vector<uint8_t> u8;
    std::vector<int32_t> i32;
    std::vector<float> f;
    std::vector<int64_t> i64;
    std::vector<double> d;
    std::vector<ACameraMetadata_rational> r;
};

struct MetadataStore {
    std::map<uint32_t, TagEntry> entries;
    mutable std::vector<uint32_t> cachedTags;

    camera_status_t getConstEntry(uint32_t tag, ACameraMetadata_const_entry* out) const {
        if (!out) {
            return ACAMERA_ERROR_INVALID_PARAMETER;
        }
        auto it = entries.find(tag);
        if (it == entries.end()) {
            return ACAMERA_ERROR_METADATA_NOT_FOUND;
        }
        const TagEntry& e = it->second;
        out->tag = tag;
        out->type = e.type;
        out->count = e.count;
        switch (e.type) {
        case ACAMERA_TYPE_BYTE:
            out->data.u8 = e.u8.data();
            break;
        case ACAMERA_TYPE_INT32:
            out->data.i32 = e.i32.data();
            break;
        case ACAMERA_TYPE_FLOAT:
            out->data.f = e.f.data();
            break;
        case ACAMERA_TYPE_INT64:
            out->data.i64 = e.i64.data();
            break;
        case ACAMERA_TYPE_DOUBLE:
            out->data.d = e.d.data();
            break;
        case ACAMERA_TYPE_RATIONAL:
            out->data.r = e.r.data();
            break;
        default:
            return ACAMERA_ERROR_METADATA_NOT_FOUND;
        }
        return ACAMERA_OK;
    }

    camera_status_t getAllTags(int32_t* numEntries, const uint32_t** tags) const {
        if (!numEntries || !tags) {
            return ACAMERA_ERROR_INVALID_PARAMETER;
        }
        cachedTags.clear();
        cachedTags.reserve(entries.size());
        for (const auto& kv : entries) {
            cachedTags.push_back(kv.first);
        }
        *numEntries = static_cast<int32_t>(cachedTags.size());
        *tags = cachedTags.empty() ? nullptr : cachedTags.data();
        return ACAMERA_OK;
    }
};

} // namespace

struct ACameraManager {
    std::mutex mu;
    std::vector<ACameraManager_AvailabilityCallbacks> callbacks;
};

struct ACameraMetadata {
    MetadataStore store;
};

struct ACameraDevice {
    std::string id;
    bool closed = false;
};

struct ACameraOutputTarget {
    ANativeWindow* window = nullptr;
};

struct ACaptureSessionOutput {
    ANativeWindow* window = nullptr;
};

struct ACaptureSessionOutputContainer {
    std::vector<ANativeWindow*> outputs;
};

struct ACaptureRequest {
    MetadataStore store;
    std::vector<ANativeWindow*> targets;
};

struct ACameraCaptureSession {
    ACameraDevice* device = nullptr;
    bool closed = false;
};

extern "C" {

// --- ACameraManager ---

ACameraManager* ACameraManager_create(void) {
    return new (std::nothrow) ACameraManager();
}

void ACameraManager_delete(ACameraManager* manager) {
    delete manager;
}

camera_status_t ACameraManager_getCameraIdList(ACameraManager* manager,
                                               ACameraIdList** cameraIdList) {
    if (!manager || !cameraIdList) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    auto* list = new (std::nothrow) ACameraIdList();
    if (!list) {
        return ACAMERA_ERROR_NOT_ENOUGH_MEMORY;
    }
    // No physical camera hardware is bridged on desktop hosts.
    list->numCameras = 0;
    list->cameraIds = nullptr;
    *cameraIdList = list;
    return ACAMERA_OK;
}

void ACameraManager_deleteCameraIdList(ACameraIdList* cameraIdList) {
    if (!cameraIdList) {
        return;
    }
    delete[] cameraIdList->cameraIds;
    delete cameraIdList;
}

camera_status_t
ACameraManager_registerAvailabilityCallback(ACameraManager* manager,
                                            const ACameraManager_AvailabilityCallbacks* callback) {
    if (!manager || !callback) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    std::lock_guard<std::mutex> lock(manager->mu);
    manager->callbacks.push_back(*callback);
    return ACAMERA_OK;
}

camera_status_t ACameraManager_unregisterAvailabilityCallback(
    ACameraManager* manager, const ACameraManager_AvailabilityCallbacks* callback) {
    if (!manager || !callback) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    std::lock_guard<std::mutex> lock(manager->mu);
    manager->callbacks.erase(
        std::remove_if(manager->callbacks.begin(), manager->callbacks.end(),
                       [&](const ACameraManager_AvailabilityCallbacks& c) {
                           return c.context == callback->context &&
                                  c.onCameraAvailable == callback->onCameraAvailable &&
                                  c.onCameraUnavailable == callback->onCameraUnavailable;
                       }),
        manager->callbacks.end());
    return ACAMERA_OK;
}

camera_status_t ACameraManager_getCameraCharacteristics(ACameraManager* manager,
                                                        const char* cameraId,
                                                        ACameraMetadata** characteristics) {
    if (!manager || !cameraId || !characteristics) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    *characteristics = nullptr;
    return ACAMERA_ERROR_CAMERA_DISCONNECTED;
}

camera_status_t ACameraManager_openCamera(ACameraManager* manager, const char* cameraId,
                                          ACameraDevice_StateCallbacks* callback,
                                          ACameraDevice** device) {
    if (!manager || !cameraId || !callback || !device) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    *device = nullptr;
    return ACAMERA_ERROR_CAMERA_DISCONNECTED;
}

// --- ACameraMetadata ---

camera_status_t ACameraMetadata_getConstEntry(const ACameraMetadata* metadata, uint32_t tag,
                                              ACameraMetadata_const_entry* entry) {
    if (!metadata || !entry) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    return metadata->store.getConstEntry(tag, entry);
}

camera_status_t ACameraMetadata_getAllTags(const ACameraMetadata* metadata, int32_t* numEntries,
                                           const uint32_t** tags) {
    if (!metadata || !numEntries || !tags) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    return metadata->store.getAllTags(numEntries, tags);
}

ACameraMetadata* ACameraMetadata_copy(const ACameraMetadata* src) {
    if (!src) {
        return nullptr;
    }
    auto* dst = new (std::nothrow) ACameraMetadata();
    if (!dst) {
        return nullptr;
    }
    dst->store.entries = src->store.entries;
    return dst;
}

void ACameraMetadata_free(ACameraMetadata* metadata) {
    delete metadata;
}

bool ACameraMetadata_isLogicalMultiCamera(const ACameraMetadata* staticMetadata,
                                          size_t* numPhysicalCameras,
                                          const char* const** physicalCameraIds) {
    if (numPhysicalCameras) {
        *numPhysicalCameras = 0;
    }
    if (physicalCameraIds) {
        *physicalCameraIds = nullptr;
    }
    (void)staticMetadata;
    return false;
}

// --- ACameraDevice ---

camera_status_t ACameraDevice_close(ACameraDevice* device) {
    if (!device) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    device->closed = true;
    delete device;
    return ACAMERA_OK;
}

const char* ACameraDevice_getId(const ACameraDevice* device) {
    return device ? device->id.c_str() : nullptr;
}

camera_status_t ACameraDevice_createCaptureRequest(const ACameraDevice* device,
                                                   ACameraDevice_request_template /*templateId*/,
                                                   ACaptureRequest** request) {
    if (!device || !request) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    if (device->closed) {
        *request = nullptr;
        return ACAMERA_ERROR_CAMERA_DISCONNECTED;
    }
    *request = new (std::nothrow) ACaptureRequest();
    return *request ? ACAMERA_OK : ACAMERA_ERROR_NOT_ENOUGH_MEMORY;
}

camera_status_t ACameraDevice_createCaptureSession(
    ACameraDevice* device, const ACaptureSessionOutputContainer* outputs,
    const ACameraCaptureSession_stateCallbacks* callbacks, ACameraCaptureSession** session) {
    if (!device || !outputs || !callbacks || !session) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    *session = nullptr;
    return ACAMERA_ERROR_CAMERA_DISCONNECTED;
}

camera_status_t ACameraDevice_createCaptureSessionWithSessionParameters(
    ACameraDevice* device, const ACaptureSessionOutputContainer* outputs,
    const ACaptureRequest* /*sessionParameters*/,
    const ACameraCaptureSession_stateCallbacks* callbacks, ACameraCaptureSession** session) {
    return ACameraDevice_createCaptureSession(device, outputs, callbacks, session);
}

camera_status_t ACameraDevice_isSessionConfigurationSupported(
    const ACameraDevice* device, const ACaptureSessionOutputContainer* sessionOutputContainer) {
    if (!device || !sessionOutputContainer) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    return ACAMERA_ERROR_UNSUPPORTED_OPERATION;
}

// --- ACaptureRequest ---

camera_status_t ACaptureRequest_addTarget(ACaptureRequest* request,
                                          const ACameraOutputTarget* output) {
    if (!request || !output || !output->window) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    if (std::find(request->targets.begin(), request->targets.end(), output->window) ==
        request->targets.end()) {
        request->targets.push_back(output->window);
    }
    return ACAMERA_OK;
}

camera_status_t ACaptureRequest_removeTarget(ACaptureRequest* request,
                                             const ACameraOutputTarget* output) {
    if (!request || !output) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    request->targets.erase(
        std::remove(request->targets.begin(), request->targets.end(), output->window),
        request->targets.end());
    return ACAMERA_OK;
}

camera_status_t ACaptureRequest_getConstEntry(const ACaptureRequest* request, uint32_t tag,
                                              ACameraMetadata_const_entry* entry) {
    if (!request || !entry) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    return request->store.getConstEntry(tag, entry);
}

camera_status_t ACaptureRequest_getAllTags(const ACaptureRequest* request, int32_t* numEntries,
                                           const uint32_t** tags) {
    if (!request || !numEntries || !tags) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    return request->store.getAllTags(numEntries, tags);
}

camera_status_t ACaptureRequest_setEntry_u8(ACaptureRequest* request, uint32_t tag, uint32_t count,
                                            const uint8_t* data) {
    if (!request || (count > 0 && !data)) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    TagEntry e;
    e.type = ACAMERA_TYPE_BYTE;
    e.count = count;
    if (count > 0) {
        e.u8.assign(data, data + count);
    }
    request->store.entries[tag] = std::move(e);
    return ACAMERA_OK;
}

camera_status_t ACaptureRequest_setEntry_i32(ACaptureRequest* request, uint32_t tag, uint32_t count,
                                             const int32_t* data) {
    if (!request || (count > 0 && !data)) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    TagEntry e;
    e.type = ACAMERA_TYPE_INT32;
    e.count = count;
    if (count > 0) {
        e.i32.assign(data, data + count);
    }
    request->store.entries[tag] = std::move(e);
    return ACAMERA_OK;
}

camera_status_t ACaptureRequest_setEntry_float(ACaptureRequest* request, uint32_t tag,
                                               uint32_t count, const float* data) {
    if (!request || (count > 0 && !data)) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    TagEntry e;
    e.type = ACAMERA_TYPE_FLOAT;
    e.count = count;
    if (count > 0) {
        e.f.assign(data, data + count);
    }
    request->store.entries[tag] = std::move(e);
    return ACAMERA_OK;
}

camera_status_t ACaptureRequest_setEntry_i64(ACaptureRequest* request, uint32_t tag, uint32_t count,
                                             const int64_t* data) {
    if (!request || (count > 0 && !data)) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    TagEntry e;
    e.type = ACAMERA_TYPE_INT64;
    e.count = count;
    if (count > 0) {
        e.i64.assign(data, data + count);
    }
    request->store.entries[tag] = std::move(e);
    return ACAMERA_OK;
}

camera_status_t ACaptureRequest_setEntry_double(ACaptureRequest* request, uint32_t tag,
                                                uint32_t count, const double* data) {
    if (!request || (count > 0 && !data)) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    TagEntry e;
    e.type = ACAMERA_TYPE_DOUBLE;
    e.count = count;
    if (count > 0) {
        e.d.assign(data, data + count);
    }
    request->store.entries[tag] = std::move(e);
    return ACAMERA_OK;
}

camera_status_t ACaptureRequest_setEntry_rational(ACaptureRequest* request, uint32_t tag,
                                                  uint32_t count,
                                                  const ACameraMetadata_rational* data) {
    if (!request || (count > 0 && !data)) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    TagEntry e;
    e.type = ACAMERA_TYPE_RATIONAL;
    e.count = count;
    if (count > 0) {
        e.r.assign(data, data + count);
    }
    request->store.entries[tag] = std::move(e);
    return ACAMERA_OK;
}

ACaptureRequest* ACaptureRequest_copy(const ACaptureRequest* src) {
    if (!src) {
        return nullptr;
    }
    auto* dst = new (std::nothrow) ACaptureRequest();
    if (!dst) {
        return nullptr;
    }
    dst->store.entries = src->store.entries;
    dst->targets = src->targets;
    return dst;
}

void ACaptureRequest_free(ACaptureRequest* request) {
    delete request;
}

// --- Targets & Session Outputs ---

camera_status_t ACameraOutputTarget_create(ANativeWindow* window, ACameraOutputTarget** output) {
    if (!window || !output) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    auto* t = new (std::nothrow) ACameraOutputTarget();
    if (!t) {
        return ACAMERA_ERROR_NOT_ENOUGH_MEMORY;
    }
    t->window = window;
    *output = t;
    return ACAMERA_OK;
}

void ACameraOutputTarget_free(ACameraOutputTarget* output) {
    delete output;
}

camera_status_t ACaptureSessionOutput_create(ANativeWindow* anw, ACaptureSessionOutput** output) {
    if (!anw || !output) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    auto* o = new (std::nothrow) ACaptureSessionOutput();
    if (!o) {
        return ACAMERA_ERROR_NOT_ENOUGH_MEMORY;
    }
    o->window = anw;
    *output = o;
    return ACAMERA_OK;
}

void ACaptureSessionOutput_free(ACaptureSessionOutput* output) {
    delete output;
}

camera_status_t ACaptureSessionOutputContainer_create(ACaptureSessionOutputContainer** container) {
    if (!container) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    auto* c = new (std::nothrow) ACaptureSessionOutputContainer();
    if (!c) {
        return ACAMERA_ERROR_NOT_ENOUGH_MEMORY;
    }
    *container = c;
    return ACAMERA_OK;
}

void ACaptureSessionOutputContainer_free(ACaptureSessionOutputContainer* container) {
    delete container;
}

camera_status_t ACaptureSessionOutputContainer_add(ACaptureSessionOutputContainer* container,
                                                   const ACaptureSessionOutput* output) {
    if (!container || !output || !output->window) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    if (std::find(container->outputs.begin(), container->outputs.end(), output->window) ==
        container->outputs.end()) {
        container->outputs.push_back(output->window);
    }
    return ACAMERA_OK;
}

camera_status_t ACaptureSessionOutputContainer_remove(ACaptureSessionOutputContainer* container,
                                                      const ACaptureSessionOutput* output) {
    if (!container || !output) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    container->outputs.erase(
        std::remove(container->outputs.begin(), container->outputs.end(), output->window),
        container->outputs.end());
    return ACAMERA_OK;
}

// --- ACameraCaptureSession ---

void ACameraCaptureSession_close(ACameraCaptureSession* session) {
    if (!session) {
        return;
    }
    session->closed = true;
    delete session;
}

camera_status_t ACameraCaptureSession_getDevice(ACameraCaptureSession* session,
                                                ACameraDevice** device) {
    if (!session || !device) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    *device = session->device;
    return session->closed ? ACAMERA_ERROR_SESSION_CLOSED : ACAMERA_OK;
}

camera_status_t ACameraCaptureSession_capture(ACameraCaptureSession* session,
                                              ACameraCaptureSession_captureCallbacks* /*callbacks*/,
                                              int numRequests, ACaptureRequest** requests,
                                              int* /*captureSequenceId*/) {
    if (!session || numRequests <= 0 || !requests) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    return ACAMERA_ERROR_SESSION_CLOSED;
}

camera_status_t ACameraCaptureSession_setRepeatingRequest(
    ACameraCaptureSession* session, ACameraCaptureSession_captureCallbacks* /*callbacks*/,
    int numRequests, ACaptureRequest** requests, int* /*captureSequenceId*/) {
    if (!session || numRequests <= 0 || !requests) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    return ACAMERA_ERROR_SESSION_CLOSED;
}

camera_status_t ACameraCaptureSession_stopRepeating(ACameraCaptureSession* session) {
    if (!session) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    return ACAMERA_ERROR_SESSION_CLOSED;
}

camera_status_t ACameraCaptureSession_abortCaptures(ACameraCaptureSession* session) {
    if (!session) {
        return ACAMERA_ERROR_INVALID_PARAMETER;
    }
    return ACAMERA_ERROR_SESSION_CLOSED;
}

ACaptureRequest* shiraz_capture_request_create(void) {
    return new (std::nothrow) ACaptureRequest();
}

ACameraMetadata* shiraz_camera_metadata_create(void) {
    return new (std::nothrow) ACameraMetadata();
}

} // extern "C"
