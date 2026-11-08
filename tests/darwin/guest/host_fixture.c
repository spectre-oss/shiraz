// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
JNIEXPORT jint Java_GuestJniFixture_hostCheck(JNIEnv *env, jclass klass) {
  (void)klass;
  return (*env)->GetVersion(env) >= JNI_VERSION_1_6 ? 19 : -1;
}

// Invoke a real Java native method for the first time on this pthread in the
// final native TSD round. The fixture itself supplies no guest cleanup scope.
#include <pthread.h>
#include <limits.h>
#include <stdlib.h>
_Static_assert(PTHREAD_DESTRUCTOR_ITERATIONS == 4, "fixture requires four native TSD rounds");
struct LateTls {
  JavaVM *vm;
  jclass klass;
  jmethodID begin, check;
  pthread_key_t key;
  int rounds, error;
};
static void late_tls_callback(void *opaque) {
  struct LateTls *work = opaque;
  if (++work->rounds < 4) {
    if (pthread_setspecific(work->key, work)) work->error = 1;
    return;
  }
  JNIEnv *env = 0;
  if ((*work->vm)->AttachCurrentThread(work->vm, &env, 0) != JNI_OK) {
    work->error = 2; return;
  }
  jlong address = (*env)->CallStaticLongMethod(env, work->klass, work->begin);
  if (!address || (*env)->ExceptionCheck(env) ||
      (*env)->CallStaticIntMethod(env, work->klass, work->check, address) != 73)
    work->error = 3;
  if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
  if ((*work->vm)->DetachCurrentThread(work->vm) != JNI_OK) work->error = 4;
}
static void *late_tls_worker(void *opaque) {
  struct LateTls *work = opaque;
  if (pthread_setspecific(work->key, work)) work->error = 5;
  return 0;
}
JNIEXPORT jint Java_GuestJniFixture_hostLateTls(JNIEnv *env, jclass klass) {
  struct LateTls work = {0};
  if ((*env)->GetJavaVM(env, &work.vm) != JNI_OK) return -1;
  work.klass = (*env)->NewGlobalRef(env, klass);
  work.begin = (*env)->GetStaticMethodID(env, klass, "tlsBegin", "()J");
  work.check = (*env)->GetStaticMethodID(env, klass, "tlsCheck", "(J)I");
  if (!work.klass || !work.begin || !work.check) {
    if (work.klass) (*env)->DeleteGlobalRef(env, work.klass);
    return -2;
  }
  if (pthread_key_create(&work.key, late_tls_callback)) {
    (*env)->DeleteGlobalRef(env, work.klass);
    return -3;
  }
  pthread_t thread;
  int created = pthread_create(&thread, 0, late_tls_worker, &work);
  if (!created && pthread_join(thread, 0)) abort();
  pthread_key_delete(work.key);
  (*env)->DeleteGlobalRef(env, work.klass);
  return !created && work.rounds == 4 && !work.error ? 73 : -10 - work.error;
}
