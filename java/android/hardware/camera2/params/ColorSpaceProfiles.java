// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.camera2.params;

import android.graphics.ColorSpace;
import android.graphics.ImageFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ColorSpaceProfiles {
    public static final int UNSPECIFIED = -1;

    private final Map<ColorSpace.Named, Map<Integer, Set<Long>>> mProfileMap =
            new LinkedHashMap<ColorSpace.Named, Map<Integer, Set<Long>>>();

    public ColorSpaceProfiles(long[] elements) {
        Objects.requireNonNull(elements, "elements");
        if (elements.length % 3 != 0) {
            throw new IllegalArgumentException(
                    "Color space profile map length " + elements.length + " is not divisible by 3");
        }
        ColorSpace.Named[] namedValues = ColorSpace.Named.values();
        for (int i = 0; i < elements.length; i += 3) {
            int csOrd = (int) elements[i];
            if (csOrd < 0 || csOrd >= namedValues.length) {
                throw new IllegalArgumentException("Unknown ColorSpace ordinal " + csOrd);
            }
            ColorSpace.Named cs = namedValues[csOrd];
            Integer format = Integer.valueOf((int) elements[i + 1]);
            long dynBitmap = elements[i + 2];

            Map<Integer, Set<Long>> formatMap = mProfileMap.get(cs);
            if (formatMap == null) {
                formatMap = new LinkedHashMap<Integer, Set<Long>>();
                mProfileMap.put(cs, formatMap);
            }
            Set<Long> dynSet = formatMap.get(format);
            if (dynSet == null) {
                dynSet = new LinkedHashSet<Long>();
                formatMap.put(format, dynSet);
            }
            if (dynBitmap != 0L) {
                for (long bit = DynamicRangeProfiles.STANDARD;
                        bit < DynamicRangeProfiles.PUBLIC_MAX;
                        bit <<= 1) {
                    if ((dynBitmap & bit) != 0L) {
                        dynSet.add(Long.valueOf(bit));
                    }
                }
            }
        }
    }

    public Set<ColorSpace.Named> getSupportedColorSpaces(int imageFormat) {
        LinkedHashSet<ColorSpace.Named> result = new LinkedHashSet<ColorSpace.Named>();
        for (Map.Entry<ColorSpace.Named, Map<Integer, Set<Long>>> entry : mProfileMap.entrySet()) {
            if (imageFormat == ImageFormat.UNKNOWN
                    || entry.getValue().containsKey(Integer.valueOf(imageFormat))) {
                result.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public Set<Integer> getSupportedImageFormatsForColorSpace(ColorSpace.Named colorSpace) {
        Map<Integer, Set<Long>> formatMap = mProfileMap.get(colorSpace);
        if (formatMap == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<Integer>(formatMap.keySet()));
    }

    public Set<Long> getSupportedDynamicRangeProfiles(
            ColorSpace.Named colorSpace, int imageFormat) {
        Map<Integer, Set<Long>> formatMap = mProfileMap.get(colorSpace);
        if (formatMap == null) {
            return Collections.emptySet();
        }
        if (imageFormat == ImageFormat.UNKNOWN) {
            LinkedHashSet<Long> merged = new LinkedHashSet<Long>();
            for (Set<Long> s : formatMap.values()) {
                merged.addAll(s);
            }
            return Collections.unmodifiableSet(merged);
        }
        Set<Long> dynSet = formatMap.get(Integer.valueOf(imageFormat));
        if (dynSet == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<Long>(dynSet));
    }

    public Set<ColorSpace.Named> getSupportedColorSpacesForDynamicRange(
            int imageFormat, long dynamicRangeProfile) {
        LinkedHashSet<ColorSpace.Named> result = new LinkedHashSet<ColorSpace.Named>();
        Long targetProfile = Long.valueOf(dynamicRangeProfile);
        for (Map.Entry<ColorSpace.Named, Map<Integer, Set<Long>>> entry : mProfileMap.entrySet()) {
            Map<Integer, Set<Long>> formatMap = entry.getValue();
            if (imageFormat == ImageFormat.UNKNOWN) {
                for (Set<Long> s : formatMap.values()) {
                    if (s.contains(targetProfile)) {
                        result.add(entry.getKey());
                        break;
                    }
                }
            } else {
                Set<Long> s = formatMap.get(Integer.valueOf(imageFormat));
                if (s != null && s.contains(targetProfile)) {
                    result.add(entry.getKey());
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
