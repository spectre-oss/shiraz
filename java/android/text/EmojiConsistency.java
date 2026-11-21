// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import java.util.Collections;
import java.util.Set;

/** Provides the Unicode emoji codepoint consistency set for compatibility verification. */
public final class EmojiConsistency {
    private EmojiConsistency() {
    }

    public static Set<int[]> getEmojiConsistencySet() {
        return Collections.emptySet();
    }
}
