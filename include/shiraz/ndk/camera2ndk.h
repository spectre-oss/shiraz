// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

struct ANativeWindow;
typedef struct ANativeWindow ANativeWindow;

typedef enum {
    ACAMERA_OK = 0,
    ACAMERA_ERROR_BASE = -10000,
    ACAMERA_ERROR_UNKNOWN = ACAMERA_ERROR_BASE,
    ACAMERA_ERROR_INVALID_PARAMETER = ACAMERA_ERROR_BASE - 1,
    ACAMERA_ERROR_CAMERA_DISCONNECTED = ACAMERA_ERROR_BASE - 2,
    ACAMERA_ERROR_NOT_ENOUGH_MEMORY = ACAMERA_ERROR_BASE - 3,
    ACAMERA_ERROR_METADATA_NOT_FOUND = ACAMERA_ERROR_BASE - 4,
    ACAMERA_ERROR_CAMERA_DEVICE = ACAMERA_ERROR_BASE - 5,
    ACAMERA_ERROR_CAMERA_SERVICE = ACAMERA_ERROR_BASE - 6,
    ACAMERA_ERROR_SESSION_CLOSED = ACAMERA_ERROR_BASE - 7,
    ACAMERA_ERROR_INVALID_OPERATION = ACAMERA_ERROR_BASE - 8,
    ACAMERA_ERROR_STREAM_CONFIGURE_FAIL = ACAMERA_ERROR_BASE - 9,
    ACAMERA_ERROR_CAMERA_IN_USE = ACAMERA_ERROR_BASE - 10,
    ACAMERA_ERROR_MAX_CAMERA_IN_USE = ACAMERA_ERROR_BASE - 11,
    ACAMERA_ERROR_CAMERA_DISABLED = ACAMERA_ERROR_BASE - 12,
    ACAMERA_ERROR_PERMISSION_DENIED = ACAMERA_ERROR_BASE - 13,
    ACAMERA_ERROR_UNSUPPORTED_OPERATION = ACAMERA_ERROR_BASE - 14,
} camera_status_t;

enum {
    ACAMERA_TYPE_BYTE = 0,
    ACAMERA_TYPE_INT32 = 1,
    ACAMERA_TYPE_FLOAT = 2,
    ACAMERA_TYPE_INT64 = 3,
    ACAMERA_TYPE_DOUBLE = 4,
    ACAMERA_TYPE_RATIONAL = 5,
};

typedef enum {
    TEMPLATE_PREVIEW = 1,
    TEMPLATE_STILL_CAPTURE = 2,
    TEMPLATE_RECORD = 3,
    TEMPLATE_VIDEO_SNAPSHOT = 4,
    TEMPLATE_ZERO_SHUTTER_LAG = 5,
    TEMPLATE_MANUAL = 6,
} ACameraDevice_request_template;

typedef struct ACameraMetadata_rational {
    int32_t numerator;
    int32_t denominator;
} ACameraMetadata_rational;

typedef struct ACameraMetadata_const_entry {
    uint32_t tag;
    uint8_t type;
    uint32_t count;
    union {
        const uint8_t* u8;
        const int32_t* i32;
        const float* f;
        const int64_t* i64;
        const double* d;
        const ACameraMetadata_rational* r;
    } data;
} ACameraMetadata_const_entry;

typedef struct ACameraIdList {
    int numCameras;
    const char** cameraIds;
} ACameraIdList;

typedef struct ACameraManager ACameraManager;
typedef struct ACameraMetadata ACameraMetadata;
typedef struct ACameraDevice ACameraDevice;
typedef struct ACaptureRequest ACaptureRequest;
typedef struct ACameraOutputTarget ACameraOutputTarget;
typedef struct ACaptureSessionOutput ACaptureSessionOutput;
typedef struct ACaptureSessionOutputContainer ACaptureSessionOutputContainer;
typedef struct ACameraCaptureSession ACameraCaptureSession;

typedef void (*ACameraManager_AvailabilityCallback)(void* context, const char* cameraId);

typedef struct ACameraManager_AvailabilityCallbacks {
    void* context;
    ACameraManager_AvailabilityCallback onCameraAvailable;
    ACameraManager_AvailabilityCallback onCameraUnavailable;
} ACameraManager_AvailabilityCallbacks;

typedef void (*ACameraDevice_StateCallback)(void* context, ACameraDevice* device);
typedef void (*ACameraDevice_ErrorStateCallback)(void* context, ACameraDevice* device, int error);

typedef struct ACameraDevice_StateCallbacks {
    void* context;
    ACameraDevice_StateCallback onDisconnected;
    ACameraDevice_ErrorStateCallback onError;
} ACameraDevice_StateCallbacks;

typedef void (*ACameraCaptureSession_stateCallback)(void* context, ACameraCaptureSession* session);

typedef struct ACameraCaptureSession_stateCallbacks {
    void* context;
    ACameraCaptureSession_stateCallback onClosed;
    ACameraCaptureSession_stateCallback onReady;
    ACameraCaptureSession_stateCallback onActive;
} ACameraCaptureSession_stateCallbacks;

typedef struct ACameraCaptureSession_captureCallbacks {
    void* context;
    void (*onCaptureStarted)(void*, ACameraCaptureSession*, const ACaptureRequest*, int64_t);
    void (*onCaptureProgressed)(void*, ACameraCaptureSession*, ACaptureRequest*,
                                const ACameraMetadata*);
    void (*onCaptureCompleted)(void*, ACameraCaptureSession*, ACaptureRequest*,
                               const ACameraMetadata*);
    void (*onCaptureFailed)(void*, ACameraCaptureSession*, ACaptureRequest*, void*);
    void (*onCaptureSequenceCompleted)(void*, ACameraCaptureSession*, int, int64_t);
    void (*onCaptureSequenceAborted)(void*, ACameraCaptureSession*, int);
    void (*onCaptureBufferLost)(void*, ACameraCaptureSession*, ACaptureRequest*, ANativeWindow*,
                                int64_t);
} ACameraCaptureSession_captureCallbacks;

// ACameraManager
ACameraManager* ACameraManager_create(void);
void ACameraManager_delete(ACameraManager* manager);
camera_status_t ACameraManager_getCameraIdList(ACameraManager* manager,
                                               ACameraIdList** cameraIdList);
void ACameraManager_deleteCameraIdList(ACameraIdList* cameraIdList);
camera_status_t
ACameraManager_registerAvailabilityCallback(ACameraManager* manager,
                                            const ACameraManager_AvailabilityCallbacks* callback);
camera_status_t
ACameraManager_unregisterAvailabilityCallback(ACameraManager* manager,
                                              const ACameraManager_AvailabilityCallbacks* callback);
camera_status_t ACameraManager_getCameraCharacteristics(ACameraManager* manager,
                                                        const char* cameraId,
                                                        ACameraMetadata** characteristics);
camera_status_t ACameraManager_openCamera(ACameraManager* manager, const char* cameraId,
                                          ACameraDevice_StateCallbacks* callback,
                                          ACameraDevice** device);

// ACameraMetadata
camera_status_t ACameraMetadata_getConstEntry(const ACameraMetadata* metadata, uint32_t tag,
                                              ACameraMetadata_const_entry* entry);
camera_status_t ACameraMetadata_getAllTags(const ACameraMetadata* metadata, int32_t* numEntries,
                                           const uint32_t** tags);
ACameraMetadata* ACameraMetadata_copy(const ACameraMetadata* src);
void ACameraMetadata_free(ACameraMetadata* metadata);
bool ACameraMetadata_isLogicalMultiCamera(const ACameraMetadata* staticMetadata,
                                          size_t* numPhysicalCameras,
                                          const char* const** physicalCameraIds);

// ACameraDevice
camera_status_t ACameraDevice_close(ACameraDevice* device);
const char* ACameraDevice_getId(const ACameraDevice* device);
camera_status_t ACameraDevice_createCaptureRequest(const ACameraDevice* device,
                                                   ACameraDevice_request_template templateId,
                                                   ACaptureRequest** request);
camera_status_t ACameraDevice_createCaptureSession(
    ACameraDevice* device, const ACaptureSessionOutputContainer* outputs,
    const ACameraCaptureSession_stateCallbacks* callbacks, ACameraCaptureSession** session);
camera_status_t ACameraDevice_createCaptureSessionWithSessionParameters(
    ACameraDevice* device, const ACaptureSessionOutputContainer* outputs,
    const ACaptureRequest* sessionParameters, const ACameraCaptureSession_stateCallbacks* callbacks,
    ACameraCaptureSession** session);
camera_status_t ACameraDevice_isSessionConfigurationSupported(
    const ACameraDevice* device, const ACaptureSessionOutputContainer* sessionOutputContainer);

// ACaptureRequest
camera_status_t ACaptureRequest_addTarget(ACaptureRequest* request,
                                          const ACameraOutputTarget* output);
camera_status_t ACaptureRequest_removeTarget(ACaptureRequest* request,
                                             const ACameraOutputTarget* output);
camera_status_t ACaptureRequest_getConstEntry(const ACaptureRequest* request, uint32_t tag,
                                              ACameraMetadata_const_entry* entry);
camera_status_t ACaptureRequest_getAllTags(const ACaptureRequest* request, int32_t* numEntries,
                                           const uint32_t** tags);
camera_status_t ACaptureRequest_setEntry_u8(ACaptureRequest* request, uint32_t tag, uint32_t count,
                                            const uint8_t* data);
camera_status_t ACaptureRequest_setEntry_i32(ACaptureRequest* request, uint32_t tag, uint32_t count,
                                             const int32_t* data);
camera_status_t ACaptureRequest_setEntry_float(ACaptureRequest* request, uint32_t tag,
                                               uint32_t count, const float* data);
camera_status_t ACaptureRequest_setEntry_i64(ACaptureRequest* request, uint32_t tag, uint32_t count,
                                             const int64_t* data);
camera_status_t ACaptureRequest_setEntry_double(ACaptureRequest* request, uint32_t tag,
                                                uint32_t count, const double* data);
camera_status_t ACaptureRequest_setEntry_rational(ACaptureRequest* request, uint32_t tag,
                                                  uint32_t count,
                                                  const ACameraMetadata_rational* data);
ACaptureRequest* ACaptureRequest_copy(const ACaptureRequest* src);
void ACaptureRequest_free(ACaptureRequest* request);

// Targets & outputs
camera_status_t ACameraOutputTarget_create(ANativeWindow* window, ACameraOutputTarget** output);
void ACameraOutputTarget_free(ACameraOutputTarget* output);
camera_status_t ACaptureSessionOutputContainer_create(ACaptureSessionOutputContainer** container);
void ACaptureSessionOutputContainer_free(ACaptureSessionOutputContainer* container);
camera_status_t ACaptureSessionOutputContainer_add(ACaptureSessionOutputContainer* container,
                                                   const ACaptureSessionOutput* output);
camera_status_t ACaptureSessionOutputContainer_remove(ACaptureSessionOutputContainer* container,
                                                      const ACaptureSessionOutput* output);
camera_status_t ACaptureSessionOutput_create(ANativeWindow* anw, ACaptureSessionOutput** output);
void ACaptureSessionOutput_free(ACaptureSessionOutput* output);

// ACameraCaptureSession
void ACameraCaptureSession_close(ACameraCaptureSession* session);
camera_status_t ACameraCaptureSession_getDevice(ACameraCaptureSession* session,
                                                ACameraDevice** device);
camera_status_t ACameraCaptureSession_capture(ACameraCaptureSession* session,
                                              ACameraCaptureSession_captureCallbacks* callbacks,
                                              int numRequests, ACaptureRequest** requests,
                                              int* captureSequenceId);
camera_status_t ACameraCaptureSession_setRepeatingRequest(
    ACameraCaptureSession* session, ACameraCaptureSession_captureCallbacks* callbacks,
    int numRequests, ACaptureRequest** requests, int* captureSequenceId);
camera_status_t ACameraCaptureSession_stopRepeating(ACameraCaptureSession* session);
camera_status_t ACameraCaptureSession_abortCaptures(ACameraCaptureSession* session);

// Shiraz test helper for standalone ACaptureRequest / ACameraMetadata construction
ACaptureRequest* shiraz_capture_request_create(void);
ACameraMetadata* shiraz_camera_metadata_create(void);

#ifdef __cplusplus
}
#endif
