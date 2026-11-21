// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>

#import <CoreLocation/CoreLocation.h>
#import <Foundation/Foundation.h>

#include <atomic>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>

namespace {

constexpr jint kStatusUnavailable = 0;
constexpr jint kStatusNotDetermined = 1;
constexpr jint kStatusRestricted = 2;
constexpr jint kStatusDenied = 3;
constexpr jint kStatusAuthorized = 4;

constexpr const char* kUnbundledMessage =
    "CoreLocation on macOS requires a bundled .app with CFBundleIdentifier and "
    "NSLocationUsageDescription";

bool has_valid_location_bundle() {
    @autoreleasepool {
        NSBundle* bundle = [NSBundle mainBundle];
        if (!bundle)
            return false;
        NSString* bundleId = [bundle bundleIdentifier];
        if (!bundleId || [bundleId length] == 0)
            return false;
        NSString* bundlePath = [bundle bundlePath];
        if (!bundlePath || ![bundlePath hasSuffix:@".app"])
            return false;
        NSDictionary* info = [bundle infoDictionary];
        if (!info)
            return false;
        id usage = [info objectForKey:@"NSLocationUsageDescription"];
        id whenInUse = [info objectForKey:@"NSLocationWhenInUseUsageDescription"];
        bool hasUsage = ([usage isKindOfClass:[NSString class]] && [usage length] > 0) ||
                        ([whenInUse isKindOfClass:[NSString class]] && [whenInUse length] > 0);
        return hasUsage;
    }
}

jint map_authorization_status(CLAuthorizationStatus status) {
    switch (status) {
    case kCLAuthorizationStatusNotDetermined:
        return kStatusNotDetermined;
    case kCLAuthorizationStatusRestricted:
        return kStatusRestricted;
    case kCLAuthorizationStatusDenied:
        return kStatusDenied;
    case kCLAuthorizationStatusAuthorizedAlways:
        return kStatusAuthorized;
    default:
        return kStatusUnavailable;
    }
}

struct LocationFix {
    bool valid = false;
    double latitude = 0.0;
    double longitude = 0.0;
    double altitude = 0.0;
    double horizontalAccuracy = 0.0;
    double speed = 0.0;
    double course = 0.0;
    double timestampMs = 0.0;
};

struct LocationSession;

} // namespace

@interface ShirazLocationDelegate : NSObject <CLLocationManagerDelegate>
@property(nonatomic, assign) LocationSession* session;
@end

namespace {

struct LocationSession {
    std::mutex mutex;
    std::condition_variable readyCv;
    bool ready = false;
    bool stopRequested = false;
    bool fineAccuracy = true;
    double minDistanceMeters = 0.0;
    jint status = kStatusNotDetermined;
    LocationFix fix;
    std::string lastError;
    CFRunLoopRef runLoop = nullptr;
    std::thread worker;
};

} // namespace

@implementation ShirazLocationDelegate

- (void)locationManager:(CLLocationManager*)manager
     didUpdateLocations:(NSArray<CLLocation*>*)locations {
    (void)manager;
    if (!self.session || !locations || [locations count] == 0)
        return;
    CLLocation* latest = [locations lastObject];
    if (!latest)
        return;
    std::lock_guard<std::mutex> lock(self.session->mutex);
    self.session->fix.valid = true;
    self.session->fix.latitude = latest.coordinate.latitude;
    self.session->fix.longitude = latest.coordinate.longitude;
    self.session->fix.altitude = latest.altitude;
    self.session->fix.horizontalAccuracy = latest.horizontalAccuracy;
    self.session->fix.speed = latest.speed >= 0.0 ? latest.speed : 0.0;
    self.session->fix.course = latest.course >= 0.0 ? latest.course : 0.0;
    self.session->fix.timestampMs = [latest.timestamp timeIntervalSince1970] * 1000.0;
    self.session->lastError.clear();
}

- (void)locationManager:(CLLocationManager*)manager didFailWithError:(NSError*)error {
    (void)manager;
    if (!self.session || !error)
        return;
    const char* desc = [[error localizedDescription] UTF8String];
    std::lock_guard<std::mutex> lock(self.session->mutex);
    self.session->lastError = desc ? desc : "CoreLocation error";
}

- (void)locationManagerDidChangeAuthorization:(CLLocationManager*)manager {
    if (!self.session || !manager)
        return;
    const jint mapped = map_authorization_status([manager authorizationStatus]);
    std::lock_guard<std::mutex> lock(self.session->mutex);
    self.session->status = mapped;
}

@end

namespace {

void run_location_worker(LocationSession* session) {
    @autoreleasepool {
        CFRunLoopRef loop = CFRunLoopGetCurrent();
        CFRetain(loop);

        CLLocationManager* manager = [[CLLocationManager alloc] init];
        ShirazLocationDelegate* delegate = [[ShirazLocationDelegate alloc] init];
        delegate.session = session;
        manager.delegate = delegate;
        manager.desiredAccuracy =
            session->fineAccuracy ? kCLLocationAccuracyBest : kCLLocationAccuracyHundredMeters;
        manager.distanceFilter =
            (std::isfinite(session->minDistanceMeters) && session->minDistanceMeters > 0.0)
                ? session->minDistanceMeters
                : kCLDistanceFilterNone;

        const jint initialStatus = map_authorization_status([manager authorizationStatus]);

        {
            std::lock_guard<std::mutex> lock(session->mutex);
            session->runLoop = loop;
            session->status = initialStatus;
            session->ready = true;
        }
        session->readyCv.notify_all();

        if (!session->stopRequested) {
            [manager startUpdatingLocation];
            // Keep the run loop alive until nativeStop() signals CFRunLoopStop.
            while (true) {
                {
                    std::lock_guard<std::mutex> lock(session->mutex);
                    if (session->stopRequested)
                        break;
                }
                CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.25, true);
            }
            [manager stopUpdatingLocation];
        }

        manager.delegate = nil;
        delegate.session = nullptr;
        {
            std::lock_guard<std::mutex> lock(session->mutex);
            session->runLoop = nullptr;
        }
        CFRelease(loop);
    }
}

LocationSession* to_session(jlong handle) {
    return handle > 0 ? reinterpret_cast<LocationSession*>(static_cast<intptr_t>(handle)) : nullptr;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_shiraz_runtime_DarwinLocation_nativeAuthorizationStatus(JNIEnv*, jclass) {
    if (!has_valid_location_bundle())
        return kStatusUnavailable;
    @autoreleasepool {
        CLLocationManager* manager = [[CLLocationManager alloc] init];
        return map_authorization_status([manager authorizationStatus]);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_shiraz_runtime_DarwinLocation_nativeLocationServicesEnabled(JNIEnv*, jclass) {
    if (!has_valid_location_bundle())
        return JNI_FALSE;
    @autoreleasepool {
        return [CLLocationManager locationServicesEnabled] ? JNI_TRUE : JNI_FALSE;
    }
}

extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_DarwinLocation_nativeStart(
    JNIEnv*, jclass, jboolean fineAccuracy, jdouble minDistanceMeters) {
    if (!has_valid_location_bundle())
        return 0;
    auto* session = new LocationSession();
    session->fineAccuracy = (fineAccuracy == JNI_TRUE);
    session->minDistanceMeters = minDistanceMeters;
    session->worker = std::thread(run_location_worker, session);

    std::unique_lock<std::mutex> lock(session->mutex);
    session->readyCv.wait(lock, [&]() { return session->ready; });
    return static_cast<jlong>(reinterpret_cast<intptr_t>(session));
}

extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_DarwinLocation_nativeStop(JNIEnv*, jclass,
                                                                                jlong handle) {
    LocationSession* session = to_session(handle);
    if (!session)
        return;
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        session->stopRequested = true;
        if (session->runLoop)
            CFRunLoopStop(session->runLoop);
    }
    if (session->worker.joinable())
        session->worker.join();
    delete session;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_shiraz_runtime_DarwinLocation_nativePollFix(JNIEnv* env, jclass, jlong handle) {
    LocationSession* session = to_session(handle);
    if (!session)
        return nullptr;
    LocationFix snapshot;
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        snapshot = session->fix;
    }
    if (!snapshot.valid)
        return nullptr;
    jdouble values[7] = {
        snapshot.latitude, snapshot.longitude, snapshot.altitude,    snapshot.horizontalAccuracy,
        snapshot.speed,    snapshot.course,    snapshot.timestampMs,
    };
    jdoubleArray out = env->NewDoubleArray(7);
    if (!out)
        return nullptr;
    env->SetDoubleArrayRegion(out, 0, 7, values);
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_shiraz_runtime_DarwinLocation_nativeLastError(JNIEnv* env, jclass, jlong handle) {
    if (!has_valid_location_bundle())
        return env->NewStringUTF(kUnbundledMessage);
    LocationSession* session = to_session(handle);
    if (!session)
        return env->NewStringUTF("");
    std::string message;
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        message = session->lastError;
    }
    return env->NewStringUTF(message.c_str());
}
