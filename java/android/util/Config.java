// SPDX-License-Identifier: GPL-3.0-only
package android.util;

/** Legacy Android build configuration constants preserved for API 35 compatibility. */
@Deprecated
public final class Config {
    public static final boolean DEBUG = false;
    public static final boolean RELEASE = true;
    public static final boolean PROFILE = false;
    public static final boolean LOGV = false;
    public static final boolean LOGD = true;

    Config() {}
}
