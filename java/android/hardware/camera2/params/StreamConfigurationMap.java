// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2.params;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.media.ImageReader;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class StreamConfigurationMap {
    private final Map<Integer, Size[]> mOutputFormats;
    private final Map<Integer, Size[]> mInputFormats;

    public StreamConfigurationMap() {
        mOutputFormats = new LinkedHashMap<Integer, Size[]>();
        mInputFormats = new LinkedHashMap<Integer, Size[]>();
    }

    public StreamConfigurationMap(Map<Integer, Size[]> outputFormats, Map<Integer, Size[]> inputFormats) {
        mOutputFormats = new LinkedHashMap<Integer, Size[]>();
        if (outputFormats != null) {
            for (Map.Entry<Integer, Size[]> e : outputFormats.entrySet()) {
                Size[] sizes = e.getValue();
                if (sizes != null) {
                    mOutputFormats.put(e.getKey(), Arrays.copyOf(sizes, sizes.length));
                }
            }
        }
        mInputFormats = new LinkedHashMap<Integer, Size[]>();
        if (inputFormats != null) {
            for (Map.Entry<Integer, Size[]> e : inputFormats.entrySet()) {
                Size[] sizes = e.getValue();
                if (sizes != null) {
                    mInputFormats.put(e.getKey(), Arrays.copyOf(sizes, sizes.length));
                }
            }
        }
    }

    public int[] getOutputFormats() {
        int[] res = new int[mOutputFormats.size()];
        int i = 0;
        for (Integer k : mOutputFormats.keySet()) {
            res[i++] = k.intValue();
        }
        return res;
    }

    public int[] getValidOutputFormatsForInput(int inputFormat) {
        if (!mInputFormats.containsKey(Integer.valueOf(inputFormat))) {
            return new int[0];
        }
        return getOutputFormats();
    }

    public int[] getInputFormats() {
        int[] res = new int[mInputFormats.size()];
        int i = 0;
        for (Integer k : mInputFormats.keySet()) {
            res[i++] = k.intValue();
        }
        return res;
    }

    public Size[] getInputSizes(int format) {
        Size[] sizes = mInputFormats.get(Integer.valueOf(format));
        return sizes != null ? Arrays.copyOf(sizes, sizes.length) : null;
    }

    public boolean isOutputSupportedFor(int format) {
        return mOutputFormats.containsKey(Integer.valueOf(format));
    }

    public static <T> boolean isOutputSupportedFor(Class<T> klass) {
        if (klass == null) {
            throw new NullPointerException("klass must not be null");
        }
        if (klass == ImageReader.class
                || klass == SurfaceHolder.class
                || klass == SurfaceTexture.class) {
            return true;
        }
        String name = klass.getName();
        return "android.media.MediaRecorder".equals(name)
                || "android.media.MediaCodec".equals(name)
                || "android.renderscript.Allocation".equals(name);
    }

    public boolean isOutputSupportedFor(Surface surface) {
        if (surface == null) {
            throw new NullPointerException("surface must not be null");
        }
        return surface.isValid() && !mOutputFormats.isEmpty();
    }

    public <T> Size[] getOutputSizes(Class<T> klass) {
        if (!isOutputSupportedFor(klass)) {
            return null;
        }
        return getOutputSizes(ImageFormat.PRIVATE);
    }

    public Size[] getOutputSizes(int format) {
        Size[] sizes = mOutputFormats.get(Integer.valueOf(format));
        return sizes != null ? Arrays.copyOf(sizes, sizes.length) : null;
    }

    public Size[] getHighSpeedVideoSizes() {
        return new Size[0];
    }

    @SuppressWarnings("unchecked")
    public Range<Integer>[] getHighSpeedVideoFpsRangesFor(Size size) {
        if (size == null) {
            throw new NullPointerException("size must not be null");
        }
        throw new IllegalArgumentException("High speed video size " + size + " is not supported");
    }

    @SuppressWarnings("unchecked")
    public Range<Integer>[] getHighSpeedVideoFpsRanges() {
        return (Range<Integer>[]) new Range<?>[0];
    }

    public Size[] getHighSpeedVideoSizesFor(Range<Integer> fpsRange) {
        if (fpsRange == null) {
            throw new NullPointerException("fpsRange must not be null");
        }
        throw new IllegalArgumentException("High speed video FPS range " + fpsRange + " is not supported");
    }

    public Size[] getHighResolutionOutputSizes(int format) {
        return null;
    }

    public long getOutputMinFrameDuration(int format, Size size) {
        if (size == null) {
            throw new NullPointerException("size must not be null");
        }
        if (!isOutputSupportedFor(format)) {
            throw new IllegalArgumentException("format " + format + " is not supported");
        }
        return 0L;
    }

    public <T> long getOutputMinFrameDuration(Class<T> klass, Size size) {
        if (!isOutputSupportedFor(klass)) {
            throw new IllegalArgumentException("klass " + klass + " is not supported");
        }
        if (size == null) {
            throw new NullPointerException("size must not be null");
        }
        return 0L;
    }

    public long getOutputStallDuration(int format, Size size) {
        if (size == null) {
            throw new NullPointerException("size must not be null");
        }
        if (!isOutputSupportedFor(format)) {
            throw new IllegalArgumentException("format " + format + " is not supported");
        }
        return 0L;
    }

    public <T> long getOutputStallDuration(Class<T> klass, Size size) {
        if (!isOutputSupportedFor(klass)) {
            throw new IllegalArgumentException("klass " + klass + " is not supported");
        }
        if (size == null) {
            throw new NullPointerException("size must not be null");
        }
        return 0L;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof StreamConfigurationMap)) return false;
        StreamConfigurationMap other = (StreamConfigurationMap) obj;
        return mapEquals(mOutputFormats, other.mOutputFormats)
                && mapEquals(mInputFormats, other.mInputFormats);
    }

    private static boolean mapEquals(Map<Integer, Size[]> a, Map<Integer, Size[]> b) {
        if (a.size() != b.size()) return false;
        for (Map.Entry<Integer, Size[]> e : a.entrySet()) {
            Size[] other = b.get(e.getKey());
            if (!Arrays.equals(e.getValue(), other)) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mOutputFormats.keySet(), mInputFormats.keySet());
    }

    @Override
    public String toString() {
        return "StreamConfigurationMap(outputs=" + mOutputFormats.keySet()
                + ", inputs=" + mInputFormats.keySet() + ")";
    }
}
