// SPDX-License-Identifier: GPL-3.0-only
// Read-only host SDK observation, not a guest implementation or JNI substitute.
#include <jni.h>
#include <mach-o/dyld.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum { MAX_IMAGES = 1024, MAX_PATH = 4096, MAX_ATTEMPTS = 3 };
struct Snapshot {
  uint32_t count;
  size_t lengths[MAX_IMAGES];
  char paths[MAX_IMAGES][MAX_PATH];
};

// No dyld add/remove callbacks are installed or retained. Count changes or a
// differing second complete copy fail/retry instead of publishing partial data.
// This is an end-of-main observation, not a historical load/unload inventory.
static int capture(struct Snapshot *snapshot, const char **error) {
  uint32_t count = _dyld_image_count();
  if (!count || count > MAX_IMAGES) {
    *error = "native closure image count exceeds bound";
    return -1;
  }
  snapshot->count = count;
  for (uint32_t index = 0; index < count; ++index) {
    const char *path = _dyld_get_image_name(index);
    if (!path) return 0;
    size_t length = strnlen(path, MAX_PATH);
    if (!length || length == MAX_PATH) {
      *error = "native closure image path missing or exceeds bound";
      return -1;
    }
    snapshot->lengths[index] = length;
    memcpy(snapshot->paths[index], path, length + 1);
  }
  return _dyld_image_count() == count ? 1 : 0;
}

static jstring fail(JNIEnv *env, const char *message) {
  jclass type = (*env)->FindClass(env, "java/lang/IllegalStateException");
  if (type) (*env)->ThrowNew(env, type, message);
  return NULL;
}

JNIEXPORT jstring JNICALL Java_NativeClosureInventory_nativeSnapshot(JNIEnv *env, jclass type) {
  (void)type;
  struct Snapshot *copies = calloc(2, sizeof(*copies));
  if (!copies) return fail(env, "native closure observation allocation failed");
  const char *error = "native closure image inventory remained unstable";
  int stable = 0;
  for (unsigned attempt = 0; attempt < MAX_ATTEMPTS; ++attempt) {
    int first = capture(&copies[0], &error);
    if (first < 0) break;
    if (!first) continue;
    int second = capture(&copies[1], &error);
    if (second < 0) break;
    if (!second || copies[0].count != copies[1].count) continue;
    stable = 1;
    for (uint32_t index = 0; index < copies[0].count; ++index)
      if (copies[0].lengths[index] != copies[1].lengths[index] ||
          memcmp(copies[0].paths[index], copies[1].paths[index], copies[0].lengths[index])) {
        stable = 0;
        break;
      }
    if (stable) break;
  }
  if (!stable) {
    free(copies);
    return fail(env, error);
  }
  const struct Snapshot *snapshot = &copies[1];
  size_t capacity = (size_t)snapshot->count * (MAX_PATH * 2 + 80) + 256;
  char *encoded = malloc(capacity);
  if (!encoded) {
    free(copies);
    return fail(env, "native closure encoding allocation failed");
  }
  size_t used = (size_t)snprintf(encoded, capacity,
      "M5_NATIVE_CLOSURE_BEGIN count=%u snapshot=end-of-main\n", snapshot->count);
  static const char hex[] = "0123456789abcdef";
  for (uint32_t index = 0; index < snapshot->count; ++index) {
    used += (size_t)snprintf(encoded + used, capacity - used,
        "M5_NATIVE_CLOSURE_PATH index=%u hex=", index);
    for (size_t offset = 0; offset < snapshot->lengths[index]; ++offset) {
      unsigned char value = (unsigned char)snapshot->paths[index][offset];
      encoded[used++] = hex[value >> 4];
      encoded[used++] = hex[value & 15];
    }
    encoded[used++] = '\n';
  }
  snprintf(encoded + used, capacity - used, "M5_NATIVE_CLOSURE_END count=%u\n", snapshot->count);
  jstring result = (*env)->NewStringUTF(env, encoded);
  free(encoded);
  free(copies);
  return result;
}
