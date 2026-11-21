// SPDX-License-Identifier: GPL-3.0-only

package android.view.inspector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class IntFlagMapping {
    private final List<Flag> mFlags = new ArrayList<Flag>();

    public IntFlagMapping() {
    }

    public Set<String> get(int value) {
        Set<String> enabledFlagNames = new HashSet<String>(mFlags.size());
        for (int i = 0; i < mFlags.size(); i++) {
            Flag flag = mFlags.get(i);
            if (flag.isEnabledFor(value)) {
                enabledFlagNames.add(flag.mName);
            }
        }
        return Collections.unmodifiableSet(enabledFlagNames);
    }

    public void add(int mask, int target, String name) {
        mFlags.add(new Flag(mask, target, name));
    }

    private static final class Flag {
        private final String mName;
        private final int mTarget;
        private final int mMask;

        Flag(int mask, int target, String name) {
            mTarget = target;
            mMask = mask;
            mName = Objects.requireNonNull(name);
        }

        boolean isEnabledFor(int value) {
            return (value & mMask) == mTarget;
        }
    }
}
