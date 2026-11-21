// SPDX-License-Identifier: GPL-3.0-only

package android.view.displayhash;

import java.util.Collections;
import java.util.Set;

public final class DisplayHashManager {
    DisplayHashManager() {
    }

    public Set<String> getSupportedHashAlgorithms() {
        return Collections.emptySet();
    }

    public VerifiedDisplayHash verifyDisplayHash(DisplayHash displayHash) {
        if (displayHash == null) {
            throw new NullPointerException("displayHash must not be null");
        }
        return null;
    }
}
