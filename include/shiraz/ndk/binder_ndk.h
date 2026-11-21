// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/types.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct _JNIEnv JNIEnv;
typedef void* jobject;

typedef int32_t binder_status_t;
typedef int32_t binder_exception_t;
typedef uint32_t transaction_code_t;
typedef uint32_t binder_flags_t;

enum {
    STATUS_OK = 0,
    STATUS_UNKNOWN_ERROR = (-2147483647 - 1),
    STATUS_NO_MEMORY = -12,
    STATUS_INVALID_OPERATION = -38,
    STATUS_BAD_VALUE = -22,
    STATUS_BAD_TYPE = (STATUS_UNKNOWN_ERROR + 1),
    STATUS_NAME_NOT_FOUND = -2,
    STATUS_PERMISSION_DENIED = -1,
    STATUS_NO_INIT = -19,
    STATUS_ALREADY_EXISTS = -17,
    STATUS_DEAD_OBJECT = -32,
    STATUS_FAILED_TRANSACTION = (STATUS_UNKNOWN_ERROR + 2),
    STATUS_BAD_INDEX = -75,
    STATUS_NOT_ENOUGH_DATA = -61,
    STATUS_WOULD_BLOCK = -11,
    STATUS_TIMED_OUT = -110,
    STATUS_UNKNOWN_TRANSACTION = -74,
    STATUS_FDS_NOT_ALLOWED = (STATUS_UNKNOWN_ERROR + 7),
    STATUS_UNEXPECTED_NULL = (STATUS_UNKNOWN_ERROR + 8),
};

enum {
    EX_NONE = 0,
    EX_SECURITY = -1,
    EX_BAD_PARCELABLE = -2,
    EX_ILLEGAL_ARGUMENT = -3,
    EX_NULL_POINTER = -4,
    EX_ILLEGAL_STATE = -5,
    EX_NETWORK_MAIN_THREAD = -6,
    EX_UNSUPPORTED_OPERATION = -7,
    EX_SERVICE_SPECIFIC = -8,
    EX_PARCELABLE = -9,
    EX_TRANSACTION_FAILED = -129,
};

enum {
    FLAG_ONEWAY = 0x01,
};

typedef struct AIBinder AIBinder;
typedef struct AIBinder_Class AIBinder_Class;
typedef struct AIBinder_Weak AIBinder_Weak;
typedef struct AIBinder_DeathRecipient AIBinder_DeathRecipient;
typedef struct AParcel AParcel;
typedef struct AStatus AStatus;

typedef void* (*AIBinder_Class_onCreate)(void* args);
typedef void (*AIBinder_Class_onDestroy)(void* userData);
typedef binder_status_t (*AIBinder_Class_onTransact)(AIBinder* binder, transaction_code_t code,
                                                     const AParcel* in, AParcel* out);
typedef binder_status_t (*AIBinder_onDump)(AIBinder* binder, int fd, const char** args,
                                           uint32_t numArgs);
typedef void (*AIBinder_DeathRecipient_onBinderDied)(void* cookie);
typedef void (*AIBinder_DeathRecipient_onBinderUnlinked)(void* cookie);

typedef bool (*AParcel_stringAllocator)(void* stringData, int32_t length, char** buffer);
typedef bool (*AParcel_byteArrayAllocator)(void* arrayData, int32_t length, int8_t** outBuffer);
typedef bool (*AParcel_int32ArrayAllocator)(void* arrayData, int32_t length, int32_t** outBuffer);

// AIBinder_Class
AIBinder_Class* AIBinder_Class_define(const char* interfaceDescriptor,
                                      AIBinder_Class_onCreate onCreate,
                                      AIBinder_Class_onDestroy onDestroy,
                                      AIBinder_Class_onTransact onTransact);
void AIBinder_Class_setOnDump(AIBinder_Class* clazz, AIBinder_onDump onDump);
void AIBinder_Class_disableInterfaceTokenHeader(AIBinder_Class* clazz);
const char* AIBinder_Class_getDescriptor(const AIBinder_Class* clazz);

// AIBinder
AIBinder* AIBinder_new(const AIBinder_Class* clazz, void* args);
bool AIBinder_isRemote(const AIBinder* binder);
bool AIBinder_isAlive(const AIBinder* binder);
binder_status_t AIBinder_ping(AIBinder* binder);
binder_status_t AIBinder_dump(AIBinder* binder, int fd, const char** args, uint32_t numArgs);
binder_status_t AIBinder_linkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                     void* cookie);
binder_status_t AIBinder_unlinkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                       void* cookie);
uid_t AIBinder_getCallingUid(void);
pid_t AIBinder_getCallingPid(void);
void AIBinder_incStrong(AIBinder* binder);
void AIBinder_decStrong(AIBinder* binder);
int32_t AIBinder_debugGetRefCount(AIBinder* binder);
bool AIBinder_associateClass(AIBinder* binder, const AIBinder_Class* clazz);
const AIBinder_Class* AIBinder_getClass(AIBinder* binder);
void* AIBinder_getUserData(AIBinder* binder);
binder_status_t AIBinder_prepareTransaction(AIBinder* binder, AParcel** in);
binder_status_t AIBinder_transact(AIBinder* binder, transaction_code_t code, AParcel** in,
                                  AParcel** out, binder_flags_t flags);

// AIBinder_Weak
AIBinder_Weak* AIBinder_Weak_new(AIBinder* binder);
void AIBinder_Weak_delete(AIBinder_Weak* weakBinder);
AIBinder* AIBinder_Weak_promote(AIBinder_Weak* weakBinder);
AIBinder_Weak* AIBinder_Weak_clone(const AIBinder_Weak* weak);

// AIBinder_DeathRecipient
AIBinder_DeathRecipient*
AIBinder_DeathRecipient_new(AIBinder_DeathRecipient_onBinderDied onBinderDied);
void AIBinder_DeathRecipient_delete(AIBinder_DeathRecipient* recipient);
void AIBinder_DeathRecipient_setOnUnlinked(AIBinder_DeathRecipient* recipient,
                                           AIBinder_DeathRecipient_onBinderUnlinked onUnlinked);

// Java interop & Process
AIBinder* AIBinder_fromJavaBinder(JNIEnv* env, jobject binder);
jobject AIBinder_toJavaBinder(JNIEnv* env, AIBinder* binder);
bool ABinderProcess_setThreadPoolMaxThreadCount(uint32_t numThreads);
void ABinderProcess_startThreadPool(void);
void ABinderProcess_joinThreadPool(void);
bool ABinderProcess_isThreadPoolStarted(void);

// AParcel
AParcel* AParcel_create(void);
void AParcel_delete(AParcel* parcel);
binder_status_t AParcel_setDataPosition(const AParcel* parcel, int32_t position);
int32_t AParcel_getDataPosition(const AParcel* parcel);
int32_t AParcel_getDataSize(const AParcel* parcel);

binder_status_t AParcel_writeInt32(AParcel* parcel, int32_t value);
binder_status_t AParcel_writeUint32(AParcel* parcel, uint32_t value);
binder_status_t AParcel_writeInt64(AParcel* parcel, int64_t value);
binder_status_t AParcel_writeUint64(AParcel* parcel, uint64_t value);
binder_status_t AParcel_writeFloat(AParcel* parcel, float value);
binder_status_t AParcel_writeDouble(AParcel* parcel, double value);
binder_status_t AParcel_writeBool(AParcel* parcel, bool value);
binder_status_t AParcel_writeChar(AParcel* parcel, uint16_t value);
binder_status_t AParcel_writeByte(AParcel* parcel, int8_t value);
binder_status_t AParcel_writeString(AParcel* parcel, const char* string, int32_t length);
binder_status_t AParcel_writeByteArray(AParcel* parcel, const int8_t* arrayData, int32_t length);
binder_status_t AParcel_writeInt32Array(AParcel* parcel, const int32_t* arrayData, int32_t length);
binder_status_t AParcel_writeStrongBinder(AParcel* parcel, AIBinder* binder);
binder_status_t AParcel_writeParcelFileDescriptor(AParcel* parcel, int fd);
binder_status_t AParcel_writeStatusHeader(AParcel* parcel, const AStatus* status);

binder_status_t AParcel_readInt32(const AParcel* parcel, int32_t* value);
binder_status_t AParcel_readUint32(const AParcel* parcel, uint32_t* value);
binder_status_t AParcel_readInt64(const AParcel* parcel, int64_t* value);
binder_status_t AParcel_readUint64(const AParcel* parcel, uint64_t* value);
binder_status_t AParcel_readFloat(const AParcel* parcel, float* value);
binder_status_t AParcel_readDouble(const AParcel* parcel, double* value);
binder_status_t AParcel_readBool(const AParcel* parcel, bool* value);
binder_status_t AParcel_readChar(const AParcel* parcel, uint16_t* value);
binder_status_t AParcel_readByte(const AParcel* parcel, int8_t* value);
binder_status_t AParcel_readString(const AParcel* parcel, void* stringData,
                                   AParcel_stringAllocator allocator);
binder_status_t AParcel_readByteArray(const AParcel* parcel, void* arrayData,
                                      AParcel_byteArrayAllocator allocator);
binder_status_t AParcel_readInt32Array(const AParcel* parcel, void* arrayData,
                                       AParcel_int32ArrayAllocator allocator);
binder_status_t AParcel_readStrongBinder(const AParcel* parcel, AIBinder** binder);
binder_status_t AParcel_readParcelFileDescriptor(const AParcel* parcel, int* fd);
binder_status_t AParcel_readStatusHeader(const AParcel* parcel, AStatus** status);

// AStatus
AStatus* AStatus_newOk(void);
AStatus* AStatus_fromExceptionCode(binder_exception_t exception);
AStatus* AStatus_fromExceptionCodeWithMessage(binder_exception_t exception, const char* message);
AStatus* AStatus_fromServiceSpecificError(int32_t serviceSpecific);
AStatus* AStatus_fromServiceSpecificErrorWithMessage(int32_t serviceSpecific, const char* message);
AStatus* AStatus_fromStatus(binder_status_t status);
bool AStatus_isOk(const AStatus* status);
binder_exception_t AStatus_getExceptionCode(const AStatus* status);
int32_t AStatus_getServiceSpecificError(const AStatus* status);
binder_status_t AStatus_getStatus(const AStatus* status);
const char* AStatus_getMessage(const AStatus* status);
const char* AStatus_getDescription(const AStatus* status);
void AStatus_deleteDescription(const char* description);
void AStatus_delete(AStatus* status);

#ifdef __cplusplus
}
#endif
