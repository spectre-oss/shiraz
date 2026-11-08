// SPDX-License-Identifier: GPL-3.0-only
#include <android/bitmap.h>
#include <android/looper.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <stdint.h>
#include <unistd.h>
#define JNI(name) Java_org_shiraz_apiprobe_NativeProbe_##name
JNIEXPORT jint JNICALL JNI(arithmetic)(JNIEnv* env, jclass type, jint a,
                                       jint b) {
  (void)env;
  (void)type;
  return a + b;
}
JNIEXPORT jstring JNICALL JNI(echo)(JNIEnv* env, jclass type, jstring value) {
  (void)type;
  const char* utf = (*env)->GetStringUTFChars(env, value, 0);
  if (!utf) return 0;
  jstring result = (*env)->NewStringUTF(env, utf);
  (*env)->ReleaseStringUTFChars(env, value, utf);
  return result;
}
JNIEXPORT jint JNICALL JNI(direct)(JNIEnv* env, jclass type, jobject buffer) {
  (void)type;
  uint8_t* data = (*env)->GetDirectBufferAddress(env, buffer);
  return data && (*env)->GetDirectBufferCapacity(env, buffer) >= 1 ? data[0]
                                                                   : -1;
}
JNIEXPORT jint JNICALL JNI(looper)(JNIEnv* env, jclass type) {
  (void)env;
  (void)type;
  int fd[2];
  if (pipe(fd)) return -1;
  ALooper* loop = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
  int result = -1;
  char value = 42;
  if (loop && ALooper_addFd(loop, fd[0], 7, ALOOPER_EVENT_INPUT, 0, 0) == 1 &&
      write(fd[1], &value, 1) == 1) {
    int events = 0, ready = -1;
    if (ALooper_pollOnce(1000, &ready, &events, 0) == 7 && ready == fd[0] &&
        (events & ALOOPER_EVENT_INPUT) && read(fd[0], &value, 1) == 1)
      result = value;
  }
  if (loop) ALooper_removeFd(loop, fd[0]);
  close(fd[0]);
  close(fd[1]);
  return result;
}
JNIEXPORT jint JNICALL JNI(window)(JNIEnv* env, jclass type, jobject surface) {
  (void)type;
  ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
  if (!window) return -1;
  int width = ANativeWindow_getWidth(window),
      height = ANativeWindow_getHeight(window);
  ANativeWindow_release(window);
  return width > 0 && height > 0 ? width : -1;
}
JNIEXPORT jint JNICALL JNI(bitmap)(JNIEnv* env, jclass type, jobject bitmap) {
  (void)type;
  AndroidBitmapInfo info;
  void* pixels = 0;
  if (AndroidBitmap_getInfo(env, bitmap, &info) !=
          ANDROID_BITMAP_RESULT_SUCCESS ||
      info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
    return -1;
  if (AndroidBitmap_lockPixels(env, bitmap, &pixels) !=
          ANDROID_BITMAP_RESULT_SUCCESS ||
      !pixels)
    return -2;
  uint8_t* p = pixels;
  p[0] = 255;
  p[1] = 0;
  p[2] = 0;
  p[3] = 255;
  return AndroidBitmap_unlockPixels(env, bitmap);
}

#include <android/hardware_buffer.h>
#include <android/sharedmem.h>
#include <dlfcn.h>
#include <pthread.h>
#include <string.h>
#include <sys/mman.h>
#include <time.h>

struct callback_args {
  JavaVM* vm;
  jclass type;
  int result;
};
static void* callback_thread(void* opaque) {
  struct callback_args* args = opaque;
  JNIEnv* env = 0;
  if ((*args->vm)->AttachCurrentThread(args->vm, &env, 0) != JNI_OK) return 0;
  jmethodID method =
      (*env)->GetStaticMethodID(env, args->type, "javaCallback", "()I");
  if (method)
    args->result = (*env)->CallStaticIntMethod(env, args->type, method);
  if ((*env)->ExceptionCheck(env)) {
    (*env)->ExceptionClear(env);
    args->result = -1;
  }
  (*args->vm)->DetachCurrentThread(args->vm);
  return 0;
}
JNIEXPORT jint JNICALL JNI(extended)(JNIEnv* env, jclass type, jstring name,
                                     jintArray array) {
  const char* text = (*env)->GetStringUTFChars(env, name, 0);
  if (!text) return -1;
  int result = -1;
  if (!strcmp(text, "jni_array")) {
    jint value = 42;
    if (array && (*env)->GetArrayLength(env, array) > 1) {
      (*env)->SetIntArrayRegion(env, array, 1, 1, &value);
      result = 42;
    }
  } else if (!strcmp(text, "jni_exception")) {
    jclass exception =
        (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (exception) (*env)->ThrowNew(env, exception, "native probe");
  } else if (!strcmp(text, "jni_callback")) {
    jmethodID method =
        (*env)->GetStaticMethodID(env, type, "javaCallback", "()I");
    if (method) result = (*env)->CallStaticIntMethod(env, type, method);
  } else if (!strcmp(text, "pthread")) {
    struct callback_args args = {0};
    pthread_t thread;
    if ((*env)->GetJavaVM(env, &args.vm) == JNI_OK) {
      args.type = (*env)->NewGlobalRef(env, type);
      if (args.type &&
          pthread_create(&thread, 0, callback_thread, &args) == 0) {
        pthread_join(thread, 0);
        result = args.result;
      }
      if (args.type) (*env)->DeleteGlobalRef(env, args.type);
    }
  } else if (!strcmp(text, "shared_memory")) {
    int fd = ASharedMemory_create("probe", 4096);
    if (fd >= 0) {
      void* a = mmap(0, 4096, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
      void* b = mmap(0, 4096, PROT_READ, MAP_SHARED, fd, 0);
      if (a != MAP_FAILED && b != MAP_FAILED &&
          ASharedMemory_getSize(fd) == 4096) {
        *(int*)a = 42;
        result = *(const int*)b;
      }
      if (a != MAP_FAILED) munmap(a, 4096);
      if (b != MAP_FAILED) munmap(b, 4096);
      close(fd);
    }
  } else if (!strcmp(text, "hardware_buffer")) {
    AHardwareBuffer_Desc desc = {0};
    desc.width = 4;
    desc.height = 4;
    desc.layers = 1;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    desc.usage = AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN |
                 AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN;
    AHardwareBuffer* buffer = 0;
    if (AHardwareBuffer_allocate(&desc, &buffer) == 0 && buffer) {
      AHardwareBuffer_Desc actual = {0};
      AHardwareBuffer_describe(buffer, &actual);
      void* pixels = 0;
      if (actual.width == 4 && actual.height == 4 &&
          AHardwareBuffer_lock(buffer, desc.usage, -1, 0, &pixels) == 0) {
        if (pixels) {
          *(uint8_t*)pixels = 42;
          result = *(uint8_t*)pixels;
        }
        if (AHardwareBuffer_unlock(buffer, 0) != 0) result = -1;
      }
      AHardwareBuffer_release(buffer);
    }
  } else if (!strcmp(text, "dlopen")) {
    void* library = dlopen("libc.so", RTLD_NOW | RTLD_LOCAL);
    if (library) {
      size_t (*length)(const char*) =
          (size_t (*)(const char*))dlsym(library, "strlen");
      if (length && length("probe") == 5) result = 42;
      dlclose(library);
    }
  } else if (!strcmp(text, "clock")) {
    struct timespec value;
    if (clock_gettime(CLOCK_MONOTONIC, &value) == 0 && value.tv_sec > 0)
      result = 42;
  }
  (*env)->ReleaseStringUTFChars(env, name, text);
  return result;
}
