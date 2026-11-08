// SPDX-License-Identifier: GPL-3.0-only
// Stack-protected AAPCS64 JNI guest fixture. Libc imports bind explicit personality
// adapters; no host symbol fallback or link-only fixture bodies execute.
#include <fcntl.h>
#include <jni.h>
#include <stddef.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <pthread.h>
#include <semaphore.h>
#include <unistd.h>
extern void *malloc(size_t);
extern void free(void *);
extern void *memcpy(void *, const void *, size_t);
extern int memcmp(const void *, const void *, size_t);
extern int __cxa_thread_atexit_impl(void (*)(void *), void *, void *);
#ifndef SHIRAZ_REGISTERED_SIGNATURE
#define SHIRAZ_REGISTERED_SIGNATURE "(IIIIIIIIIIII)J"
#endif

static JavaVM *guest_vm;
static volatile int initialized;
static pthread_key_t constructor_key;
static int constructor_destructors;
static void constructor_destructor(void *value) {
  if (value == (void *)73) ++constructor_destructors;
}
static void *constructor_worker(void *unused) {
  (void)unused;
  if (pthread_key_create(&constructor_key, constructor_destructor) ||
      pthread_setspecific(constructor_key, (void *)73)) return 0;
  return (void *)73;
}
__attribute__((constructor)) static void setup(void) {
  pthread_t thread;
  void *result = 0;
  initialized = -1;
  if (!pthread_create(&thread, 0, constructor_worker, 0) &&
      !pthread_join(thread, &result) && result == (void *)73 &&
      constructor_destructors == 1 && !pthread_key_delete(constructor_key))
    initialized = 17;
}
static const char signature[] = "(IDBFSDIDIDIDIDIDIDIDIDID)D";
static jlong sum(jint a, jint b, jint c, jint d, jint e, jint f, jint g, jint h,
                 jint i, jint j, jint k, jint l) {
  return a + 2LL * b + 3LL * c + 4LL * d + 5LL * e + 6LL * f + 7LL * g +
         8LL * h + 9LL * i + 10LL * j + 11LL * k + 12LL * l;
}
static jlong registered(JNIEnv *env, jclass klass, jint a, jint b, jint c,
                        jint d, jint e, jint f, jint g, jint h, jint i, jint j,
                        jint k, jint l) {
  (void)klass;
  return (*env)->GetVersion(env) >= JNI_VERSION_1_6
             ? sum(a, b, c, d, e, f, g, h, i, j, k, l)
             : 0;
}
static jstring registered_object(JNIEnv *env, jclass klass, jstring text,
                                 jintArray values) {
  (void)klass;
  if ((*env)->GetArrayLength(env, values) != 3)
    return 0;
  jint array[3];
  (*env)->GetIntArrayRegion(env, values, 0, 3, array);
  if (array[0] + array[1] + array[2] != 7)
    return 0;
  const char *chars = (*env)->GetStringUTFChars(env, text, 0);
  if (!chars)
    return 0;
  int valid = chars[0] == 's' && (*env)->GetStringUTFLength(env, text) == 10;
  (*env)->ReleaseStringUTFChars(env, text, chars);
  return valid ? text : 0;
}
JNIEXPORT jbyte Java_GuestJniFixture_narrowByte(JNIEnv *env, jclass klass) {
  (void)env;
  (void)klass;
  return -113;
}
JNIEXPORT jshort Java_GuestJniFixture_narrowShort(JNIEnv *env, jclass klass) {
  (void)env;
  (void)klass;
  return -32123;
}
JNIEXPORT jchar Java_GuestJniFixture_narrowChar(JNIEnv *env, jclass klass) {
  (void)env;
  (void)klass;
  return 0xfedc;
}
static jint initial_value(JNIEnv *env, jclass klass) {
  (void)env;
  (void)klass;
  return 311;
}
static jint replacement_value(JNIEnv *env, jclass klass) {
  (void)env;
  (void)klass;
  return 411;
}
JNIEXPORT void Java_GuestJniFixture_reregister(JNIEnv *env, jclass klass) {
  (void)klass;
  jclass registered_class =
      (*env)->FindClass(env, "GuestJniFixture$Registered");
  if (!registered_class)
    return;
  if ((*env)->UnregisterNatives(env, registered_class) != JNI_OK)
    return;
  JNINativeMethod method = {"value", "()I", (void *)replacement_value};
  (*env)->RegisterNatives(env, registered_class, &method, 1);
  (*env)->DeleteLocalRef(env, registered_class);
}
JNIEXPORT jdouble Java_GuestJniFixture_reflected(JNIEnv *env, jclass klass,
                                                 jobject reflected) {
  jmethodID method = (*env)->FromReflectedMethod(env, reflected);
  if (!method)
    return -1;
  return (*env)->CallStaticDoubleMethod(env, klass, method, 7, 1.5,
                                        (jlong)1000);
}
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
  (void)reserved;
  if (initialized != 17)
    return JNI_ERR;
  guest_vm = vm;
  JNIEnv *env = 0;
  if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK)
    return JNI_ERR;
  jclass klass = (*env)->FindClass(env, "GuestJniFixture");
  if (!klass)
    return JNI_ERR;
  JNINativeMethod methods[] = {
      {"registered", SHIRAZ_REGISTERED_SIGNATURE, (void *)registered},
      {"registeredObject", "(Ljava/lang/String;[I)Ljava/lang/String;",
       (void *)registered_object},
  };
  jint result = (*env)->RegisterNatives(env, klass, methods, 2);
  (*env)->DeleteLocalRef(env, klass);
  if (result != JNI_OK)
    return JNI_ERR;
  jclass registered_class =
      (*env)->FindClass(env, "GuestJniFixture$Registered");
  if (!registered_class)
    return JNI_ERR;
  JNINativeMethod extra = {"value", "()I", (void *)initial_value};
  result = (*env)->RegisterNatives(env, registered_class, &extra, 1);
  (*env)->DeleteLocalRef(env, registered_class);
  return result == JNI_OK ? JNI_VERSION_1_6 : JNI_ERR;
}
JNIEXPORT jlong Java_GuestJniFixture_twelve(JNIEnv *env, jclass klass, jint a,
                                            jint b, jint c, jint d, jint e,
                                            jint f, jint g, jint h, jint i,
                                            jint j, jint k, jint l) {
  (void)env;
  (void)klass;
  return sum(a, b, c, d, e, f, g, h, i, j, k, l);
}
JNIEXPORT jdouble Java_GuestJniFixture_mixed(
    JNIEnv *env, jclass klass, jint a, jdouble b, jbyte c, jfloat d, jshort e,
    jdouble f, jint g, jdouble h, jint i, jdouble j, jint k, jdouble l, jint m,
    jdouble n, jint o, jdouble p, jint q, jdouble r, jint s, jdouble t, jint u,
    jdouble v, jint w, jdouble x) {
  jmethodID method =
      (*env)->GetStaticMethodID(env, klass, "javaMixed", signature);
  if (!method)
    return -1;
  return (*env)->CallStaticDoubleMethod(env, klass, method, a, b, c, d, e, f, g,
                                        h, i, j, k, l, m, n, o, p, q, r, s, t,
                                        u, v, w, x);
}
static jdouble call_v(JNIEnv *env, jclass klass, jmethodID method, ...) {
  va_list args;
  va_start(args, method);
  jdouble result = (*env)->CallStaticDoubleMethodV(env, klass, method, args);
  va_end(args);
  return result;
}
static jobject new_v(JNIEnv *env, jclass klass, jmethodID method, ...) {
  va_list args;
  va_start(args, method);
  jobject result = (*env)->NewObjectV(env, klass, method, args);
  va_end(args);
  return result;
}
JNIEXPORT jint Java_GuestJniFixture_exercise(JNIEnv *env, jclass klass) {
  JavaVM *vm = 0;
  if ((*env)->GetJavaVM(env, &vm) != JNI_OK || vm != guest_vm)
    return 1;
  JNIEnv *again = 0;
  if ((*vm)->GetEnv(vm, (void **)&again, JNI_VERSION_1_6) != JNI_OK ||
      again != env)
    return 2;
  jmethodID method =
      (*env)->GetStaticMethodID(env, klass, "javaMixed", signature);
  if (!method)
    return 3;
  double expected = 0;
  jvalue args[24];
  for (int index = 1; index <= 24; ++index) {
    if (index % 2 == 0) {
      if (index == 4)
        args[index - 1].f = index + 0.25f;
      else
        args[index - 1].d = index + 0.25;
      expected += index * (index + 0.25);
    } else {
      if (index == 3)
        args[index - 1].b = -index;
      else if (index == 5)
        args[index - 1].s = -index;
      else
        args[index - 1].i = -index;
      expected -= index * index;
    }
  }
  if ((*env)->CallStaticDoubleMethodA(env, klass, method, args) != expected)
    return 4;
  if (call_v(env, klass, method, -1, 2.25, -3, 4.25, -5, 6.25, -7, 8.25, -9,
             10.25, -11, 12.25, -13, 14.25, -15, 16.25, -17, 18.25, -19, 20.25,
             -21, 22.25, -23, 24.25) != expected)
    return 5;
  jclass box = (*env)->FindClass(env, "GuestJniFixture$Box");
  if (!box)
    return 6;
  jmethodID constructor = (*env)->GetMethodID(env, box, "<init>", "(IJFD)V");
  if (!constructor)
    return 7;
  jobject first =
      (*env)->NewObject(env, box, constructor, 7, (jlong)1000, 3.5, 9.25);
  jobject second = new_v(env, box, constructor, 7, (jlong)1000, 3.5, 9.25);
  if (!first || !second)
    return 8;
  jmethodID score = (*env)->GetMethodID(env, box, "score", "(I)D");
  if (!score)
    return 9;
  jdouble v1 = (*env)->CallDoubleMethod(env, first, score, 5);
  jdouble v2 = (*env)->CallNonvirtualDoubleMethod(env, second, box, score, 5);
  if (v1 != 1024.75 || v2 != 1024.75)
    return 10;
  jfieldID small = (*env)->GetFieldID(env, box, "small", "B");
  if (!small)
    return 11;
  (*env)->SetByteField(env, first, small, (jbyte)-9);
  if ((*env)->GetByteField(env, first, small) != -9)
    return 12;
  (*env)->DeleteLocalRef(env, first);
  (*env)->DeleteLocalRef(env, second);
  (*env)->DeleteLocalRef(env, box);
  return 73;
}
JNIEXPORT void Java_GuestJniFixture_throwFromGuest(JNIEnv *env, jclass klass) {
  (void)klass;
  jclass exception = (*env)->FindClass(env, "java/lang/IllegalStateException");
  if (exception)
    (*env)->ThrowNew(env, exception, "guest exception");
}

JNIEXPORT jdouble Java_GuestJniFixture_narrowStack(
    JNIEnv *env, jclass klass, jint a, jint b, jint c, jint d, jint e, jint f,
    jbyte g, jshort h, jchar i, jboolean z, jint j, jlong k, jfloat f0,
    jfloat f1, jfloat f2, jfloat f3, jfloat f4, jfloat f5, jfloat f6, jfloat f7,
    jfloat f8, jdouble d0, jdouble d1, jdouble d2, jdouble d3, jdouble d4,
    jdouble d5, jdouble d6, jdouble d7, jdouble d8) {
  (void)env;
  (void)klass;
  return 1 * (double)a + 2 * (double)b + 3 * (double)c + 4 * (double)d +
         5 * (double)e + 6 * (double)f + 7 * (double)g + 8 * (double)h +
         9 * (double)i + 10 * (double)z + 11 * (double)j + 12 * (double)k +
         13 * (double)f0 + 14 * (double)f1 + 15 * (double)f2 + 16 * (double)f3 +
         17 * (double)f4 + 18 * (double)f5 + 19 * (double)f6 + 20 * (double)f7 +
         21 * (double)f8 + 22 * (double)d0 + 23 * (double)d1 + 24 * (double)d2 +
         25 * (double)d3 + 26 * (double)d4 + 27 * (double)d5 + 28 * (double)d6 +
         29 * (double)d7 + 30 * (double)d8;
}

JNIEXPORT jint Java_GuestJniFixture_memory(JNIEnv *env, jclass klass) {
  (void)env;
  (void)klass;
  void *data = malloc(32);
  if (!data)
    return -1;
  const char value[] = "personality";
  memcpy(data, value, sizeof(value));
  int result = memcmp(data, value, sizeof(value)) == 0 ? 73 : -2;
  free(data);
  return result;
}

JNIEXPORT jint Java_GuestJniFixture_mappedMemory(JNIEnv *env, jclass klass) {
  (void)env;
  (void)klass;
  size_t page = getpagesize();
  char *data = mmap(0, 2 * page, PROT_READ | PROT_WRITE,
                    MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  if (data == MAP_FAILED)
    return -1;
  int result = -2;
  if (page == 16384 && data[0] == 0 && data[page] == 0) {
    data[page] = 'm';
    if (mprotect(data + page, page, PROT_READ) == 0 && data[page] == 'm')
      result = 79;
  }
  if (munmap(data, 2 * page))
    result = -3;
  return result;
}

static pthread_mutex_t jni_gate = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t jni_condition = PTHREAD_COND_INITIALIZER;
static pthread_once_t jni_once = PTHREAD_ONCE_INIT;
static pthread_rwlock_t jni_rwlock = PTHREAD_RWLOCK_INITIALIZER;
static sem_t jni_start;
static int jni_ready, jni_go, jni_calls, jni_initialized, jni_tls_dtors;
static void jni_initialize(void) { jni_initialized = 41; }
struct JniThreadWork { JavaVM *vm; jclass klass; int index, error; };
static void jni_exit_destructor(void *opaque) {
  struct JniThreadWork *work = opaque;
  JNIEnv *env = 0;
  JavaVMAttachArgs args = {JNI_VERSION_1_6, "shiraz-tls-destructor", 0};
  if ((*work->vm)->AttachCurrentThreadAsDaemon(work->vm, &env, &args) != JNI_OK || !env) {
    work->error = 10; return;
  }
  jmethodID method = (*env)->GetStaticMethodID(env, work->klass, "threadValue", "(I)I");
  if (!method || (*env)->CallStaticIntMethod(env, work->klass, method, work->index) !=
                     work->index * 7 + 3 || (*env)->ExceptionCheck(env))
    work->error = 11;
  if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
  if ((*work->vm)->DetachCurrentThread(work->vm) != JNI_OK) work->error = 12;
  pthread_mutex_lock(&jni_gate);
  ++jni_tls_dtors;
  pthread_mutex_unlock(&jni_gate);
}
static void *jni_thread_worker(void *opaque) {
  struct JniThreadWork *work = opaque;
  if (__cxa_thread_atexit_impl(jni_exit_destructor, work, 0)) {
    work->error = 13; return work;
  }
  if (pthread_once(&jni_once, jni_initialize) || jni_initialized != 41 ||
      pthread_mutex_lock(&jni_gate)) { work->error = 7; return work; }
  ++jni_ready;
  pthread_cond_broadcast(&jni_condition);
  while (!jni_go)
    if (pthread_cond_wait(&jni_condition, &jni_gate)) { work->error = 8; break; }
  pthread_mutex_unlock(&jni_gate);
  if (sem_wait(&jni_start)) { work->error = 9; return work; }
  for (int cycle = 0; cycle < 2; ++cycle) {
    JNIEnv *env = (JNIEnv *)1;
    if ((*work->vm)->GetEnv(work->vm, (void **)&env, JNI_VERSION_1_6) != JNI_EDETACHED || env) {
      work->error = 1; return work;
    }
    JavaVMAttachArgs args = {JNI_VERSION_1_6, "shiraz-guest-thread", 0};
    jint result = work->index & 1
        ? (*work->vm)->AttachCurrentThreadAsDaemon(work->vm, &env, &args)
        : (*work->vm)->AttachCurrentThread(work->vm, &env, &args);
    if (result != JNI_OK || !env) { work->error = 2; return work; }
    JNIEnv *again = 0;
    JavaVM *vm = 0;
    if ((*work->vm)->GetEnv(work->vm, (void **)&again, JNI_VERSION_1_6) != JNI_OK ||
        again != env || (*env)->GetJavaVM(env, &vm) != JNI_OK || vm != work->vm)
      work->error = 3;
    jmethodID method = (*env)->GetStaticMethodID(env, work->klass, "threadValue", "(I)I");
    if (!method || (*env)->CallStaticIntMethod(env, work->klass, method, work->index) !=
                       work->index * 7 + 3 || (*env)->ExceptionCheck(env))
      work->error = 4;
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if ((*work->vm)->DetachCurrentThread(work->vm) != JNI_OK) {
      work->error = 5; return work;
    }
    again = (JNIEnv *)1;
    if ((*work->vm)->GetEnv(work->vm, (void **)&again, JNI_VERSION_1_6) != JNI_EDETACHED || again)
      work->error = 6;
    pthread_rwlock_wrlock(&jni_rwlock);
    ++jni_calls;
    pthread_rwlock_unlock(&jni_rwlock);
  }
  return work;
}
JNIEXPORT jint Java_GuestJniFixture_createdThreads(JNIEnv *env, jclass klass) {
  JavaVM *vm = 0;
  if ((*env)->GetJavaVM(env, &vm) != JNI_OK) return -1;
  jclass global = (*env)->NewGlobalRef(env, klass);
  if (!global) return -2;
  struct JniThreadWork work[4];
  pthread_t threads[4];
  int created = 0, result = 83;
  if (sem_init(&jni_start, 0, 0)) {
    (*env)->DeleteGlobalRef(env, global);
    return -33;
  }
  pthread_mutex_lock(&jni_gate);
  jni_ready = jni_go = jni_calls = jni_tls_dtors = 0;
  pthread_mutex_unlock(&jni_gate);
  for (int i = 0; i < 4; ++i) {
    work[i] = (struct JniThreadWork){vm, global, i, 0};
    if (pthread_create(&threads[i], 0, jni_thread_worker, &work[i])) {
      result = -3; break;
    }
    ++created;
  }
  pthread_mutex_lock(&jni_gate);
  struct timespec deadline;
  if (clock_gettime(CLOCK_REALTIME, &deadline)) result = -30;
  else {
    deadline.tv_sec += 5;
    while (jni_ready < created)
      if (pthread_cond_timedwait(&jni_condition, &jni_gate, &deadline)) {
        result = -31; break;
      }
  }
  jni_go = 1;
  pthread_cond_broadcast(&jni_condition);
  pthread_mutex_unlock(&jni_gate);
  for (int i = 0; i < created; ++i) if (sem_post(&jni_start)) result = -34;
  for (int i = 0; i < created; ++i) {
    void *value = 0;
    if (pthread_join(threads[i], &value) || value != &work[i] || work[i].error)
      result = -10 - work[i].error;
  }
  pthread_rwlock_rdlock(&jni_rwlock);
  if (jni_calls != 8 || jni_initialized != 41) result = -32;
  if (jni_tls_dtors != 4) result = -36;
  pthread_rwlock_unlock(&jni_rwlock);
  if (sem_destroy(&jni_start)) result = -35;
  (*env)->DeleteGlobalRef(env, global);
  return result;
}

JNIEXPORT jint Java_GuestJniFixture_fileRoundtrip(JNIEnv *env, jclass klass,
                                                  jstring name) {
  (void)klass;
  const char *path = (*env)->GetStringUTFChars(env, name, 0);
  if (!path)
    return -1;
  int fd = open(path, O_CREAT | O_EXCL | O_RDWR | O_CLOEXEC, 0600);
  int result = -2;
  if (fd >= 0) {
    struct stat info;
    const char data[] = "native";
    result = write(fd, data, 6) == 6 && fstat(fd, &info) == 0 &&
                     info.st_size == 6 && S_ISREG(info.st_mode)
                 ? 73
                 : -3;
    if (close(fd))
      result = -4;
    if (unlink(path))
      result = -5;
  }
  (*env)->ReleaseStringUTFChars(env, name, path);
  return result;
}

// AndroidBitmapInfo is the NDK's five 32-bit fields; host JNI is unwrapped
// only inside the explicit libjnigraphics provider.
struct GuestBitmapInfo { unsigned width, height, stride; int format; unsigned flags; };
_Static_assert(sizeof(struct GuestBitmapInfo) == 20, "NDK bitmap ABI");
extern int AndroidBitmap_getInfo(JNIEnv *, jobject, struct GuestBitmapInfo *);
extern int AndroidBitmap_lockPixels(JNIEnv *, jobject, void **);
extern int AndroidBitmap_unlockPixels(JNIEnv *, jobject);
JNIEXPORT jint Java_GuestJniFixture_bitmap(JNIEnv *env, jclass cls, jobject bitmap) {
  (void)cls;
  struct GuestBitmapInfo info = {0};
  if (AndroidBitmap_getInfo(env, 0, &info) != -1 ||
      AndroidBitmap_getInfo(env, bitmap, &info) || info.width != 2 || info.height != 1 ||
      info.stride != 8 || info.format != 1) return -1;
  void *first = 0, *second = 0;
  if (AndroidBitmap_lockPixels(env, bitmap, &first) || !first) return -2;
  if (AndroidBitmap_lockPixels(env, bitmap, &second) || second != first) return -3;
  unsigned char *bytes = first;
  if (bytes[0] != 128 || bytes[1] != 32 || bytes[2] || bytes[3] != 128 ||
      bytes[4] != 17 || bytes[5] != 34 || bytes[6] != 51 || bytes[7] != 255) return -4;
  bytes[4] = 0; bytes[5] = 128; bytes[6] = 0; bytes[7] = 128;
  if (AndroidBitmap_unlockPixels(env, bitmap) || AndroidBitmap_unlockPixels(env, bitmap) ||
      AndroidBitmap_unlockPixels(env, bitmap) != -1) return -5;
  return 89;
}
