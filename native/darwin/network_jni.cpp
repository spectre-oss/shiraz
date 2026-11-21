// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <cstdint>
extern "C" int shiraz_job_network_snapshot(uint32_t*, uint32_t) noexcept;
extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_DarwinConnectivity_snapshot(JNIEnv* env,
                                                                                       jclass) {
    uint32_t values[3]{};
    if (shiraz_job_network_snapshot(values, 3) != 1)
        return nullptr;
    jint converted[3] = {static_cast<jint>(values[0]), static_cast<jint>(values[1]),
                         static_cast<jint>(values[2])};
    jintArray result = env->NewIntArray(3);
    if (result)
        env->SetIntArrayRegion(result, 0, 3, converted);
    return result;
}
