// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <Security/Security.h>

// Read public system anchors only. Never enumerate private keys or identities.
extern "C" JNIEXPORT jobjectArray JNICALL
Java_shiraz_runtime_HostTrustProvider_anchors(JNIEnv* env, jclass) {
    CFArrayRef roots = nullptr;
    const OSStatus status = SecTrustCopyAnchorCertificates(&roots);
    if (status != errSecSuccess || roots == nullptr) {
        if (roots != nullptr) CFRelease(roots);
        env->ThrowNew(env->FindClass("java/security/cert/CertificateException"),
                      "Cannot read macOS system certificate anchors");
        return nullptr;
    }
    const CFIndex count = CFArrayGetCount(roots);
    if (count <= 0 || count > 4096) {
        CFRelease(roots);
        env->ThrowNew(env->FindClass("java/security/cert/CertificateException"),
                      "Invalid macOS system anchor count");
        return nullptr;
    }
    jclass bytes = env->FindClass("[B");
    jobjectArray result = bytes == nullptr ? nullptr : env->NewObjectArray(static_cast<jsize>(count), bytes, nullptr);
    if (bytes != nullptr) env->DeleteLocalRef(bytes);
    for (CFIndex i = 0; result != nullptr && i < count && !env->ExceptionCheck(); ++i) {
        auto certificate = static_cast<SecCertificateRef>(const_cast<void*>(CFArrayGetValueAtIndex(roots, i)));
        CFDataRef data = SecCertificateCopyData(certificate);
        const CFIndex length = data == nullptr ? 0 : CFDataGetLength(data);
        if (length <= 0 || length > 1024 * 1024) {
            if (data != nullptr) CFRelease(data);
            env->ThrowNew(env->FindClass("java/security/cert/CertificateException"), "Invalid system certificate data");
            break;
        }
        jbyteArray encoded = env->NewByteArray(static_cast<jsize>(length));
        if (encoded != nullptr) {
            env->SetByteArrayRegion(encoded, 0, static_cast<jsize>(length),
                                    reinterpret_cast<const jbyte*>(CFDataGetBytePtr(data)));
            env->SetObjectArrayElement(result, static_cast<jsize>(i), encoded);
            env->DeleteLocalRef(encoded);
        }
        CFRelease(data);
    }
    CFRelease(roots);
    return env->ExceptionCheck() ? nullptr : result;
}
