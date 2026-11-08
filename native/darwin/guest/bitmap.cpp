// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/bitmap.hpp"
#include "shiraz/android_bitmap.h"
namespace shiraz::guest {
Provider bitmap_provider(UnwrapEnvironment unwrap) {
  if (!unwrap) throw ElfError("bitmap provider requires checked JNI environment unwrapping");
  static_assert(sizeof(AndroidBitmapInfo) == 20 && alignof(AndroidBitmapInfo) == 4);
  Provider provider{"libjnigraphics.so", {}, {}, false};
  for (unsigned operation = 0; operation < 3; ++operation) {
    void *entry = closure([unwrap, operation](Capture &capture) {
      CallCursor cursor(capture, Convention::Aapcs64);
      void *guest = cursor.read<void *>();
      jobject bitmap = cursor.read<jobject>();
      HostErrno preserve;
      auto *host = static_cast<JNIEnv *>(unwrap(guest));
      if (!host) throw ElfError("bitmap provider received null host JNI environment");
      int result;
      if (operation == 0) result = AndroidBitmap_getInfo(host, bitmap, cursor.read<AndroidBitmapInfo *>());
      else if (operation == 1) result = AndroidBitmap_lockPixels(host, bitmap, cursor.read<void **>());
      else result = AndroidBitmap_unlockPixels(host, bitmap);
      return_value(capture, result);
    });
    provider.exports.push_back({operation == 0 ? "AndroidBitmap_getInfo" : operation == 1 ?
        "AndroidBitmap_lockPixels" : "AndroidBitmap_unlockPixels", "", reinterpret_cast<uintptr_t>(entry),
        SymbolKind::Function, "bitmap/checked-shadow-jni", false});
  }
  return provider;
}
}
