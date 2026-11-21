// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2.params;

import android.util.Range;
import android.util.Size;
import android.view.Surface;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class RecommendedStreamConfigurationMap {
    public static final int USECASE_PREVIEW = 0;
    public static final int USECASE_RECORD = 1;
    public static final int USECASE_VIDEO_SNAPSHOT = 2;
    public static final int USECASE_SNAPSHOT = 3;
    public static final int USECASE_ZSL = 4;
    public static final int USECASE_RAW = 5;
    public static final int USECASE_LOW_LATENCY_SNAPSHOT = 6;
    public static final int USECASE_10BIT_OUTPUT = 8;

    private final StreamConfigurationMap mRecommendedMap;
    private final int mUsecase;

    public RecommendedStreamConfigurationMap(StreamConfigurationMap recommendedMap, int usecase) {
        if (recommendedMap == null) {
            throw new NullPointerException("recommendedMap must not be null");
        }
        mRecommendedMap = recommendedMap;
        mUsecase = usecase;
    }

    public int getRecommendedUseCase() {
        return mUsecase;
    }

    public Set<Integer> getOutputFormats() {
        return toIntSet(mRecommendedMap.getOutputFormats());
    }

    public Set<Integer> getValidOutputFormatsForInput(int inputFormat) {
        return toIntSet(mRecommendedMap.getValidOutputFormatsForInput(inputFormat));
    }

    public Set<Integer> getInputFormats() {
        return toIntSet(mRecommendedMap.getInputFormats());
    }

    public Set<Size> getInputSizes(int format) {
        return toSet(mRecommendedMap.getInputSizes(format));
    }

    public boolean isOutputSupportedFor(int format) {
        return mRecommendedMap.isOutputSupportedFor(format);
    }

    public Set<Size> getOutputSizes(int format) {
        return toSet(mRecommendedMap.getOutputSizes(format));
    }

    public Set<Size> getHighSpeedVideoSizes() {
        return toSet(mRecommendedMap.getHighSpeedVideoSizes());
    }

    public Set<Range<Integer>> getHighSpeedVideoFpsRangesFor(Size size) {
        return toSet(mRecommendedMap.getHighSpeedVideoFpsRangesFor(size));
    }

    public Set<Range<Integer>> getHighSpeedVideoFpsRanges() {
        return toSet(mRecommendedMap.getHighSpeedVideoFpsRanges());
    }

    public Set<Size> getHighSpeedVideoSizesFor(Range<Integer> fpsRange) {
        return toSet(mRecommendedMap.getHighSpeedVideoSizesFor(fpsRange));
    }

    public Set<Size> getHighResolutionOutputSizes(int format) {
        return toSet(mRecommendedMap.getHighResolutionOutputSizes(format));
    }

    public long getOutputMinFrameDuration(int format, Size size) {
        return mRecommendedMap.getOutputMinFrameDuration(format, size);
    }

    public long getOutputStallDuration(int format, Size size) {
        return mRecommendedMap.getOutputStallDuration(format, size);
    }

    public <T> Set<Size> getOutputSizes(Class<T> klass) {
        return toSet(mRecommendedMap.getOutputSizes(klass));
    }

    public <T> long getOutputMinFrameDuration(Class<T> klass, Size size) {
        return mRecommendedMap.getOutputMinFrameDuration(klass, size);
    }

    public <T> long getOutputStallDuration(Class<T> klass, Size size) {
        return mRecommendedMap.getOutputStallDuration(klass, size);
    }

    public boolean isOutputSupportedFor(Surface surface) {
        return mRecommendedMap.isOutputSupportedFor(surface);
    }

    private static Set<Integer> toIntSet(int[] arr) {
        if (arr == null || arr.length == 0) {
            return Collections.emptySet();
        }
        Set<Integer> set = new LinkedHashSet<Integer>(arr.length);
        for (int v : arr) {
            set.add(Integer.valueOf(v));
        }
        return Collections.unmodifiableSet(set);
    }

    private static <E> Set<E> toSet(E[] arr) {
        if (arr == null) {
            return null;
        }
        if (arr.length == 0) {
            return Collections.emptySet();
        }
        Set<E> set = new LinkedHashSet<E>(arr.length);
        for (E e : arr) {
            set.add(e);
        }
        return Collections.unmodifiableSet(set);
    }
}
