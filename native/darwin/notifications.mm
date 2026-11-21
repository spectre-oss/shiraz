// SPDX-License-Identifier: GPL-3.0-only
// macOS UserNotifications (UNUserNotificationCenter) backend for
// shiraz.runtime.DesktopNotifications.
#import <Foundation/Foundation.h>
#import <UserNotifications/UserNotifications.h>
#include <dispatch/dispatch.h>
#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <limits>
#include <memory>
#include <mutex>
#include <set>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

namespace {

constexpr int64_t kCallTimeoutNs = 1500LL * 1000LL * 1000LL; // 1.5 s

struct Pending {};

void checkPending(JNIEnv* env) {
    if (env && env->ExceptionCheck())
        throw Pending{};
}

void throwJava(JNIEnv* env, const char* type, const char* message) {
    if (!env || env->ExceptionCheck())
        return;
    jclass cls = env->FindClass(type);
    if (!cls)
        return;
    env->ThrowNew(cls, message);
    env->DeleteLocalRef(cls);
}

template <class F> auto guarded(JNIEnv* env, F fn) -> decltype(fn()) {
    try {
        return fn();
    } catch (const Pending&) {
    } catch (const std::bad_alloc&) {
        throwJava(env, "java/lang/OutOfMemoryError", "Cannot allocate desktop notification data");
    } catch (const std::invalid_argument& e) {
        throwJava(env, "java/lang/IllegalArgumentException", e.what());
    } catch (const std::exception& e) {
        throwJava(env, "java/lang/IllegalStateException", e.what());
    }
    return {};
}

// Validate UTF-16 JNI string (no NULs, no unpaired surrogates, bounded length) -> UTF-8.
std::string validatedText(JNIEnv* env, jstring value, jsize limit) {
    if (!value)
        throw std::invalid_argument("Null notification text");
    jsize count = env->GetStringLength(value);
    if (count > limit)
        throw std::invalid_argument("Notification text exceeds host limit");
    std::vector<jchar> input(static_cast<size_t>(count));
    if (count > 0)
        env->GetStringRegion(value, 0, count, input.data());
    checkPending(env);
    std::string result;
    result.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        uint32_t cp = input[static_cast<size_t>(i)];
        if (cp >= 0xd800 && cp <= 0xdbff) {
            if (++i == count || input[static_cast<size_t>(i)] < 0xdc00 ||
                input[static_cast<size_t>(i)] > 0xdfff) {
                throw std::invalid_argument("Unpaired notification surrogate");
            }
            cp = 0x10000 + ((cp - 0xd800) << 10) + input[static_cast<size_t>(i)] - 0xdc00;
        } else if (cp >= 0xdc00 && cp <= 0xdfff) {
            throw std::invalid_argument("Unpaired notification surrogate");
        }
        if (cp == 0)
            throw std::invalid_argument("NUL in notification text");
        if (cp < 0x80) {
            result.push_back(static_cast<char>(cp));
        } else if (cp < 0x800) {
            result.push_back(static_cast<char>(0xc0 | (cp >> 6)));
            result.push_back(static_cast<char>(0x80 | (cp & 63)));
        } else if (cp < 0x10000) {
            result.push_back(static_cast<char>(0xe0 | (cp >> 12)));
            result.push_back(static_cast<char>(0x80 | ((cp >> 6) & 63)));
            result.push_back(static_cast<char>(0x80 | (cp & 63)));
        } else {
            result.push_back(static_cast<char>(0xf0 | (cp >> 18)));
            result.push_back(static_cast<char>(0x80 | ((cp >> 12) & 63)));
            result.push_back(static_cast<char>(0x80 | ((cp >> 6) & 63)));
            result.push_back(static_cast<char>(0x80 | (cp & 63)));
        }
    }
    return result;
}

UNUserNotificationCenter* requireBundledCenter() {
    NSBundle* bundle = [NSBundle mainBundle];
    NSString* bundleId = bundle ? bundle.bundleIdentifier : nil;
    NSString* bundlePath = bundle ? bundle.bundlePath : nil;
    if (!bundleId || bundleId.length == 0 || !bundlePath ||
        ![bundlePath.lowercaseString hasSuffix:@".app"]) {
        throw std::runtime_error(
            "Desktop notifications on macOS require a bundled .app with CFBundleIdentifier");
    }
    @try {
        UNUserNotificationCenter* center = [UNUserNotificationCenter currentNotificationCenter];
        if (!center) {
            throw std::runtime_error("UNUserNotificationCenter unavailable");
        }
        return center;
    } @catch (NSException* ex) {
        std::string reason = ex.reason ? [ex.reason UTF8String] : "UNUserNotificationCenter failed";
        throw std::runtime_error("macOS notification center unavailable: " + reason);
    }
}

struct Client {
    jlong handle = 0;
    std::string application;
    std::string prefix;
    uint32_t nextNotifId = 1;
    std::set<uint32_t> ids;
    bool closed = false;
    std::mutex mutex;

    std::string identifierFor(uint32_t id) const {
        return prefix + std::to_string(id);
    }

    void requireOpen() const {
        if (closed)
            throw std::logic_error("Notification connection closed");
    }
};

std::mutex gRegistryMutex;
std::unordered_map<jlong, std::shared_ptr<Client>> gClients;
jlong gNextHandle = 1;

std::shared_ptr<Client> lookupClient(jlong handle) {
    std::lock_guard<std::mutex> lock(gRegistryMutex);
    auto it = gClients.find(handle);
    if (it == gClients.end())
        throw std::logic_error("Notification connection closed");
    return it->second;
}

void reconcileDeliveredLocked(Client& c, UNUserNotificationCenter* center) {
    dispatch_semaphore_t sem = dispatch_semaphore_create(0);
    __block std::set<uint32_t> deliveredIds;
    __block bool gotList = false;
    NSString* nsPrefix = [NSString stringWithUTF8String:c.prefix.c_str()];

    [center
        getDeliveredNotificationsWithCompletionHandler:^(NSArray<UNNotification*>* notifications) {
          for (UNNotification* n in notifications) {
              NSString* ident = n.request.identifier;
              if (ident && [ident hasPrefix:nsPrefix]) {
                  NSString* tail = [ident substringFromIndex:nsPrefix.length];
                  unsigned long long parsed = 0;
                  NSScanner* scanner = [NSScanner scannerWithString:tail];
                  if ([scanner scanUnsignedLongLong:&parsed] && scanner.isAtEnd && parsed > 0 &&
                      parsed <= std::numeric_limits<uint32_t>::max()) {
                      deliveredIds.insert(static_cast<uint32_t>(parsed));
                  }
              }
          }
          gotList = true;
          dispatch_semaphore_signal(sem);
        }];

    dispatch_time_t deadline = dispatch_time(DISPATCH_TIME_NOW, kCallTimeoutNs);
    if (dispatch_semaphore_wait(sem, deadline) == 0 && gotList) {
        std::set<uint32_t> kept;
        for (uint32_t id : c.ids) {
            if (deliveredIds.count(id))
                kept.insert(id);
        }
        c.ids.swap(kept);
    }
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_shiraz_runtime_DesktopNotifications_open(JNIEnv* env, jclass, jstring application) {
    return guarded(env, [&]() -> jlong {
        std::string app = validatedText(env, application, 256);
        @autoreleasepool {
            UNUserNotificationCenter* center = requireBundledCenter();

            // Check authorization status without blocking on an interactive alert if already
            // determined; if notDetermined, request authorization with a bounded timeout.
            dispatch_semaphore_t sem = dispatch_semaphore_create(0);
            __block UNAuthorizationStatus authStatus = UNAuthorizationStatusNotDetermined;
            [center getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings* s) {
              if (s)
                  authStatus = s.authorizationStatus;
              dispatch_semaphore_signal(sem);
            }];
            if (dispatch_semaphore_wait(sem, dispatch_time(DISPATCH_TIME_NOW, kCallTimeoutNs)) !=
                0) {
                throw std::runtime_error("Timed out querying macOS notification settings");
            }
            if (authStatus == UNAuthorizationStatusDenied) {
                throw std::runtime_error("macOS notification permission denied");
            }
            if (authStatus == UNAuthorizationStatusNotDetermined) {
                dispatch_semaphore_t reqSem = dispatch_semaphore_create(0);
                __block BOOL granted = NO;
                __block std::string reqErr;
                UNAuthorizationOptions opts = UNAuthorizationOptionAlert |
                                              UNAuthorizationOptionSound |
                                              UNAuthorizationOptionBadge;
                [center requestAuthorizationWithOptions:opts
                                      completionHandler:^(BOOL ok, NSError* error) {
                                        granted = ok;
                                        if (error && error.localizedDescription) {
                                            reqErr = [error.localizedDescription UTF8String];
                                        }
                                        dispatch_semaphore_signal(reqSem);
                                      }];
                if (dispatch_semaphore_wait(
                        reqSem, dispatch_time(DISPATCH_TIME_NOW, kCallTimeoutNs)) == 0) {
                    if (!reqErr.empty())
                        throw std::runtime_error("macOS notification authorization error: " +
                                                 reqErr);
                    if (!granted)
                        throw std::runtime_error("macOS notification permission not granted");
                }
            }

            std::lock_guard<std::mutex> lock(gRegistryMutex);
            if (gClients.size() >= 128 || gNextHandle == std::numeric_limits<jlong>::max())
                throw std::runtime_error("Desktop notification connection limit exceeded");
            jlong id = gNextHandle++;
            auto c = std::make_shared<Client>();
            c->handle = id;
            c->application = std::move(app);
            c->prefix = "shiraz-notif-" + std::to_string(id) + "-";
            gClients.emplace(id, std::move(c));
            return id;
        }
    });
}

extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_DesktopNotifications_close(JNIEnv* env,
                                                                                 jclass,
                                                                                 jlong handle) {
    guarded(env, [&]() -> int {
        std::shared_ptr<Client> c;
        {
            std::lock_guard<std::mutex> lock(gRegistryMutex);
            auto it = gClients.find(handle);
            if (it == gClients.end())
                return 0;
            c = it->second;
            gClients.erase(it);
        }
        std::lock_guard<std::mutex> lock(c->mutex);
        c->closed = true;
        return 0;
    });
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_shiraz_runtime_DesktopNotifications_capabilities(JNIEnv* env, jclass, jlong handle) {
    return guarded(env, [&]() -> jobjectArray {
        auto c = lookupClient(handle);
        std::lock_guard<std::mutex> lock(c->mutex);
        c->requireOpen();
        static constexpr const char* kCaps[] = {"body", "sound"};
        jclass cls = env->FindClass("java/lang/String");
        checkPending(env);
        jobjectArray result = env->NewObjectArray(2, cls, nullptr);
        env->DeleteLocalRef(cls);
        checkPending(env);
        for (jsize i = 0; i < 2; ++i) {
            jstring s = env->NewStringUTF(kCaps[i]);
            checkPending(env);
            env->SetObjectArrayElement(result, i, s);
            env->DeleteLocalRef(s);
            checkPending(env);
        }
        return result;
    });
}

extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_DesktopNotifications_post(
    JNIEnv* env, jclass, jlong handle, jlong replaces, jstring summary, jstring body, jint urgency,
    jint timeout, jboolean suppressSound) {
    return guarded(env, [&]() -> jlong {
        if (replaces < 0 || replaces > std::numeric_limits<uint32_t>::max() || urgency < 0 ||
            urgency > 2 || timeout < -1) {
            throw std::invalid_argument("Invalid desktop notification options");
        }
        std::string title = validatedText(env, summary, 8192);
        std::string content = validatedText(env, body, 8192);

        auto c = lookupClient(handle);
        std::lock_guard<std::mutex> lock(c->mutex);
        c->requireOpen();

        @autoreleasepool {
            UNUserNotificationCenter* center = requireBundledCenter();
            uint32_t id = static_cast<uint32_t>(replaces);
            if (id != 0) {
                if (!c->ids.count(id)) {
                    throw std::invalid_argument(
                        "Replacement notification is not owned by this connection");
                }
            } else {
                if (c->ids.size() >= 256)
                    throw std::runtime_error("Active notification limit exceeded");
                if (c->nextNotifId == 0)
                    c->nextNotifId = 1;
                id = c->nextNotifId++;
            }

            UNMutableNotificationContent* note = [[UNMutableNotificationContent alloc] init];
            NSString* nsTitle = [NSString stringWithUTF8String:title.c_str()];
            NSString* nsBody = [NSString stringWithUTF8String:content.c_str()];
            note.title = nsTitle ? nsTitle : @"";
            note.body = nsBody ? nsBody : @"";
            note.sound = suppressSound ? nil : [UNNotificationSound defaultSound];
            if (@available(macOS 12.0, *)) {
                if (urgency == 0)
                    note.interruptionLevel = UNNotificationInterruptionLevelPassive;
                else if (urgency == 2)
                    note.interruptionLevel = UNNotificationInterruptionLevelTimeSensitive;
                else
                    note.interruptionLevel = UNNotificationInterruptionLevelActive;
            }

            std::string ident = c->identifierFor(id);
            NSString* nsIdent = [NSString stringWithUTF8String:ident.c_str()];
            UNNotificationRequest* req = [UNNotificationRequest requestWithIdentifier:nsIdent
                                                                              content:note
                                                                              trigger:nil];

            dispatch_semaphore_t sem = dispatch_semaphore_create(0);
            __block std::string addErr;
            [center addNotificationRequest:req
                     withCompletionHandler:^(NSError* error) {
                       if (error && error.localizedDescription)
                           addErr = [error.localizedDescription UTF8String];
                       dispatch_semaphore_signal(sem);
                     }];
            if (dispatch_semaphore_wait(sem, dispatch_time(DISPATCH_TIME_NOW, kCallTimeoutNs)) !=
                0) {
                throw std::runtime_error("macOS notification delivery timed out");
            }
            if (!addErr.empty())
                throw std::runtime_error("macOS notification delivery failed: " + addErr);

            c->ids.insert(id);
            return static_cast<jlong>(id);
        }
    });
}

extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_DesktopNotifications_cancel(JNIEnv* env,
                                                                                  jclass,
                                                                                  jlong handle,
                                                                                  jlong id) {
    guarded(env, [&]() -> int {
        if (id <= 0 || id > std::numeric_limits<uint32_t>::max())
            throw std::invalid_argument("Invalid notification identifier");
        auto c = lookupClient(handle);
        std::lock_guard<std::mutex> lock(c->mutex);
        c->requireOpen();
        uint32_t nid = static_cast<uint32_t>(id);
        if (!c->ids.count(nid))
            return 0;
        @autoreleasepool {
            UNUserNotificationCenter* center = requireBundledCenter();
            NSString* nsIdent = [NSString stringWithUTF8String:c->identifierFor(nid).c_str()];
            NSArray<NSString*>* list = @[ nsIdent ];
            [center removePendingNotificationRequestsWithIdentifiers:list];
            [center removeDeliveredNotificationsWithIdentifiers:list];
        }
        c->ids.erase(nid);
        return 0;
    });
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_shiraz_runtime_DesktopNotifications_activeIds(JNIEnv* env, jclass, jlong handle) {
    return guarded(env, [&]() -> jlongArray {
        auto c = lookupClient(handle);
        std::lock_guard<std::mutex> lock(c->mutex);
        c->requireOpen();
        @autoreleasepool {
            UNUserNotificationCenter* center = requireBundledCenter();
            reconcileDeliveredLocked(*c, center);
        }
        std::vector<jlong> out;
        out.reserve(c->ids.size());
        for (uint32_t id : c->ids)
            out.push_back(static_cast<jlong>(id));
        jlongArray result = env->NewLongArray(static_cast<jsize>(out.size()));
        checkPending(env);
        if (!out.empty()) {
            env->SetLongArrayRegion(result, 0, static_cast<jsize>(out.size()), out.data());
            checkPending(env);
        }
        return result;
    });
}
