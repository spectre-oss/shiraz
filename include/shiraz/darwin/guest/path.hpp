// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <jni.h>
#include <functional>

namespace shiraz::guest {
// Read-only AndroidXPath 1.0.1 / API27 binary view, not a general SkPath object.
// Native iterator callers must retain the Java Path until iterator destruction.
// Resolve only the audited module's exact Path.mNativePath/J request to the
// real Shiraz backing field. true means handled, including a real JNI error;
// false leaves the caller's ordinary GetFieldID behavior untouched.
bool resolve_path_field_alias(JNIEnv *, jclass, const char *, const char *,
                              const void *native_entry, jfieldID &);
// Published before constructors/JNI calls; immutable executable-image snapshot.
// Only the audited AndroidXPath module's native entrypoints may read this view.
void set_path_reader_validator(std::function<bool(const void *)>);
bool prepare_path_field(JNIEnv *, jobject, jfieldID, const void *native_entry);
}
