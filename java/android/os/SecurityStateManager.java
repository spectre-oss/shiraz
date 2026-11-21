// SPDX-License-Identifier: GPL-3.0-only
package android.os;

/** Exposes kernel and security patch level metadata for the running environment. */
public class SecurityStateManager {
    public static final String KEY_SYSTEM_SPL = "system_spl";
    public static final String KEY_VENDOR_SPL = "vendor_spl";
    public static final String KEY_KERNEL_VERSION = "kernel_version";

    public SecurityStateManager() {
    }

    public Bundle getGlobalSecurityState() {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_SYSTEM_SPL, Build.VERSION.SECURITY_PATCH);
        bundle.putString(KEY_VENDOR_SPL, Build.VERSION.SECURITY_PATCH);
        bundle.putString(KEY_KERNEL_VERSION, System.getProperty("os.version", Build.UNKNOWN));
        return bundle;
    }
}
