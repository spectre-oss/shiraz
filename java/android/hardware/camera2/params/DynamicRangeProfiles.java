// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.camera2.params;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DynamicRangeProfiles {
    public static final long STANDARD = 1L;
    public static final long HLG10 = 2L;
    public static final long HDR10 = 4L;
    public static final long HDR10_PLUS = 8L;
    public static final long DOLBY_VISION_10B_HDR_REF = 16L;
    public static final long DOLBY_VISION_10B_HDR_REF_PO = 32L;
    public static final long DOLBY_VISION_10B_HDR_OEM = 64L;
    public static final long DOLBY_VISION_10B_HDR_OEM_PO = 128L;
    public static final long DOLBY_VISION_8B_HDR_REF = 256L;
    public static final long DOLBY_VISION_8B_HDR_REF_PO = 512L;
    public static final long DOLBY_VISION_8B_HDR_OEM = 1024L;
    public static final long DOLBY_VISION_8B_HDR_OEM_PO = 2048L;
    public static final long PUBLIC_MAX = 4096L;

    private final Map<Long, Set<Long>> mProfileMap = new LinkedHashMap<Long, Set<Long>>();
    private final Map<Long, Boolean> mExtraLatencyMap = new LinkedHashMap<Long, Boolean>();

    public DynamicRangeProfiles(long[] elements) {
        Objects.requireNonNull(elements, "elements");
        if (elements.length % 3 != 0) {
            throw new IllegalArgumentException(
                    "Dynamic range profile map length " + elements.length
                            + " is not divisible by 3");
        }
        for (int i = 0; i < elements.length; i += 3) {
            long profile = elements[i];
            if (profile == STANDARD) {
                throw new IllegalArgumentException(
                        "Dynamic range profile map must not include a STANDARD profile entry");
            }
            Long key = Long.valueOf(profile);
            long constraintsBitmap = elements[i + 1];
            LinkedHashSet<Long> constraints = new LinkedHashSet<Long>();
            if (constraintsBitmap != 0L) {
                for (long bit = STANDARD; bit < PUBLIC_MAX; bit <<= 1) {
                    if ((constraintsBitmap & bit) != 0L) {
                        constraints.add(Long.valueOf(bit));
                    }
                }
            }
            mProfileMap.put(key, Collections.unmodifiableSet(constraints));
            mExtraLatencyMap.put(key, Boolean.valueOf(elements[i + 2] != 0L));
        }
        LinkedHashSet<Long> stdConstraints = new LinkedHashSet<Long>();
        stdConstraints.add(Long.valueOf(STANDARD));
        mProfileMap.put(Long.valueOf(STANDARD), Collections.unmodifiableSet(stdConstraints));
        mExtraLatencyMap.put(Long.valueOf(STANDARD), Boolean.FALSE);
    }

    public Set<Long> getSupportedProfiles() {
        return Collections.unmodifiableSet(mProfileMap.keySet());
    }

    public Set<Long> getProfileCaptureRequestConstraints(long profile) {
        Set<Long> constraints = mProfileMap.get(Long.valueOf(profile));
        if (constraints == null) {
            throw new IllegalArgumentException("Unsupported profile " + profile);
        }
        return constraints;
    }

    public boolean isExtraLatencyPresent(long profile) {
        Boolean extra = mExtraLatencyMap.get(Long.valueOf(profile));
        if (extra == null) {
            throw new IllegalArgumentException("Unsupported profile " + profile);
        }
        return extra.booleanValue();
    }
}
