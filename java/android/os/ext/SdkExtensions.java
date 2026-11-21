// SPDX-License-Identifier: GPL-3.0-only
package android.os.ext;

import android.os.Build;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Queries SDK extension versions exposed by the runtime profile. */
public class SdkExtensions {
    public static final int AD_SERVICES = 1000000;

    private SdkExtensions() {
    }

    public static int getExtensionVersion(int extension) {
        if (extension < Build.VERSION_CODES.R) {
            throw new IllegalArgumentException("not a valid extension: " + extension);
        }
        return 0;
    }

    public static Map<Integer, Integer> getAllExtensionVersions() {
        return Collections.unmodifiableMap(new LinkedHashMap<Integer, Integer>());
    }
}
