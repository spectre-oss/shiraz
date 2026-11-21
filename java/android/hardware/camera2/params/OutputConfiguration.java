// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2.params;

import android.graphics.ImageFormat;
import android.hardware.camera2.MultiResolutionImageReader;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Size;
import android.view.Surface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class OutputConfiguration implements Parcelable {
    public static final int SURFACE_GROUP_ID_NONE = -1;

    public static final int MIRROR_MODE_AUTO = 0;
    public static final int MIRROR_MODE_NONE = 1;
    public static final int MIRROR_MODE_H = 2;
    public static final int MIRROR_MODE_V = 3;

    public static final int TIMESTAMP_BASE_DEFAULT = 0;
    public static final int TIMESTAMP_BASE_SENSOR = 1;
    public static final int TIMESTAMP_BASE_MONOTONIC = 2;
    public static final int TIMESTAMP_BASE_REALTIME = 3;
    public static final int TIMESTAMP_BASE_CHOREOGRAPHER_SYNCED = 4;

    private static final int MAX_SURFACES_COUNT = 4;

    private final List<Surface> mSurfaces = new ArrayList<Surface>();
    private final int mSurfaceGroupId;
    private final int mFormat;
    private final Size mConfiguredSize;
    private final long mUsage;
    private boolean mIsDeferredConfig;
    private boolean mIsShared;
    private String mPhysicalCameraId;
    private long mDynamicRangeProfile = DynamicRangeProfiles.STANDARD;
    private long mStreamUseCase = 0L;
    private int mTimestampBase = TIMESTAMP_BASE_DEFAULT;
    private int mMirrorMode = MIRROR_MODE_AUTO;
    private boolean mReadoutTimestampEnabled;
    private final Set<Integer> mSensorPixelModesUsed = new LinkedHashSet<Integer>();

    public OutputConfiguration(Surface surface) {
        this(SURFACE_GROUP_ID_NONE, surface);
    }

    public OutputConfiguration(int surfaceGroupId, Surface surface) {
        if (surface == null) {
            throw new NullPointerException("Surface must not be null");
        }
        mSurfaceGroupId = surfaceGroupId;
        mSurfaces.add(surface);
        mFormat = ImageFormat.UNKNOWN;
        mConfiguredSize = new Size(1, 1);
        mUsage = 0L;
        mIsDeferredConfig = false;
    }

    public <T> OutputConfiguration(Size surfaceSize, Class<T> klass) {
        if (surfaceSize == null) {
            throw new NullPointerException("surfaceSize must not be null");
        }
        if (klass == null) {
            throw new NullPointerException("klass must not be null");
        }
        if (surfaceSize.getWidth() <= 0 || surfaceSize.getHeight() <= 0) {
            throw new IllegalArgumentException("Surface size needs to be non-zero");
        }
        mSurfaceGroupId = SURFACE_GROUP_ID_NONE;
        mFormat = ImageFormat.PRIVATE;
        mConfiguredSize = surfaceSize;
        mUsage = 0L;
        mIsDeferredConfig = true;
        mIsShared = true;
    }

    public OutputConfiguration(int format, Size surfaceSize) {
        this(SURFACE_GROUP_ID_NONE, format, surfaceSize, 0L);
    }

    public OutputConfiguration(int surfaceGroupId, int format, Size surfaceSize) {
        this(surfaceGroupId, format, surfaceSize, 0L);
    }

    public OutputConfiguration(int format, Size surfaceSize, long usage) {
        this(SURFACE_GROUP_ID_NONE, format, surfaceSize, usage);
    }

    public OutputConfiguration(int surfaceGroupId, int format, Size surfaceSize, long usage) {
        if (surfaceSize == null) {
            throw new NullPointerException("surfaceSize must not be null");
        }
        if (surfaceSize.getWidth() <= 0 || surfaceSize.getHeight() <= 0) {
            throw new IllegalArgumentException("Surface size needs to be non-zero");
        }
        mSurfaceGroupId = surfaceGroupId;
        mFormat = format;
        mConfiguredSize = surfaceSize;
        mUsage = usage;
        mIsDeferredConfig = true;
    }

    private OutputConfiguration(Parcel source) {
        mSurfaceGroupId = source.readInt();
        mFormat = source.readInt();
        int w = source.readInt();
        int h = source.readInt();
        mConfiguredSize = (w > 0 && h > 0) ? new Size(w, h) : new Size(1, 1);
        mUsage = source.readLong();
        mIsDeferredConfig = source.readInt() != 0;
        mIsShared = source.readInt() != 0;
        mPhysicalCameraId = source.readString();
        mDynamicRangeProfile = source.readLong();
        mStreamUseCase = source.readLong();
        mTimestampBase = source.readInt();
        mMirrorMode = source.readInt();
        mReadoutTimestampEnabled = source.readInt() != 0;
        int modeCount = source.readInt();
        for (int i = 0; i < modeCount; i++) {
            mSensorPixelModesUsed.add(Integer.valueOf(source.readInt()));
        }
        int surfaceCount = source.readInt();
        for (int i = 0; i < surfaceCount; i++) {
            int sw = Math.min(4096, Math.max(1, source.readInt()));
            int sh = Math.min(4096, Math.max(1, source.readInt()));
            Surface s = new Surface();
            s.shirazConfigure(sw, sh, 1);
            mSurfaces.add(s);
        }
    }

    public void setDynamicRangeProfile(long profile) {
        mDynamicRangeProfile = profile;
    }

    public long getDynamicRangeProfile() {
        return mDynamicRangeProfile;
    }

    public static Collection<OutputConfiguration> createInstancesForMultiResolutionOutput(
            MultiResolutionImageReader multiResolutionImageReader) {
        if (multiResolutionImageReader == null) {
            throw new NullPointerException("multiResolutionImageReader must not be null");
        }
        return multiResolutionImageReader.shirazCreateOutputConfigurations();
    }

    public static List<OutputConfiguration> createInstancesForMultiResolutionOutput(
            Collection<MultiResolutionStreamInfo> streams, int format) {
        if (streams == null) {
            throw new NullPointerException("streams must not be null");
        }
        if (streams.size() < 2) {
            throw new IllegalArgumentException("streams must contain at least 2 elements");
        }
        int groupId = Math.abs(Objects.hash(streams, Integer.valueOf(format))) | 1;
        List<OutputConfiguration> out = new ArrayList<OutputConfiguration>(streams.size());
        for (MultiResolutionStreamInfo info : streams) {
            OutputConfiguration cfg = new OutputConfiguration(
                    groupId, format, new Size(info.getWidth(), info.getHeight()));
            cfg.setPhysicalCameraId(info.getPhysicalCameraId());
            out.add(cfg);
        }
        return out;
    }

    public static void setSurfacesForMultiResolutionOutput(
            Collection<OutputConfiguration> outputConfigurations,
            MultiResolutionImageReader multiResolutionImageReader) {
        if (outputConfigurations == null) {
            throw new NullPointerException("outputConfigurations must not be null");
        }
        if (multiResolutionImageReader == null) {
            throw new NullPointerException("multiResolutionImageReader must not be null");
        }
        Surface surface = multiResolutionImageReader.getSurface();
        for (OutputConfiguration cfg : outputConfigurations) {
            if (cfg != null && cfg.getSurface() == null && surface != null) {
                cfg.addSurface(surface);
            }
        }
    }

    public void enableSurfaceSharing() {
        mIsShared = true;
    }

    public void setPhysicalCameraId(String physicalCameraId) {
        mPhysicalCameraId = physicalCameraId;
    }

    public void addSensorPixelModeUsed(int sensorPixelModeUsed) {
        mSensorPixelModesUsed.add(Integer.valueOf(sensorPixelModeUsed));
    }

    public void removeSensorPixelModeUsed(int sensorPixelModeUsed) {
        mSensorPixelModesUsed.remove(Integer.valueOf(sensorPixelModeUsed));
    }

    public void addSurface(Surface surface) {
        if (surface == null) {
            throw new NullPointerException("Surface must not be null");
        }
        if (mSurfaces.contains(surface)) {
            throw new IllegalStateException("Surface is already added!");
        }
        if (mSurfaces.size() == 1 && !mIsShared) {
            throw new IllegalStateException("Cannot have 2 surfaces for a non-sharing configuration");
        }
        if (mSurfaces.size() + 1 > MAX_SURFACES_COUNT) {
            throw new IllegalArgumentException("Exceeds maximum number of surfaces");
        }
        mSurfaces.add(surface);
    }

    public void removeSurface(Surface surface) {
        if (getSurface() == surface) {
            throw new IllegalArgumentException(
                    "Cannot remove surface associated with this output configuration");
        }
        if (!mSurfaces.remove(surface)) {
            throw new IllegalArgumentException("Surface is not part of this output configuration");
        }
    }

    public void setStreamUseCase(long streamUseCase) {
        mStreamUseCase = streamUseCase;
    }

    public long getStreamUseCase() {
        return mStreamUseCase;
    }

    public void setTimestampBase(int timestampBase) {
        if (timestampBase < TIMESTAMP_BASE_DEFAULT
                || timestampBase > TIMESTAMP_BASE_CHOREOGRAPHER_SYNCED) {
            throw new IllegalArgumentException("Not a valid timestamp base value " + timestampBase);
        }
        mTimestampBase = timestampBase;
    }

    public int getTimestampBase() {
        return mTimestampBase;
    }

    public void setMirrorMode(int mirrorMode) {
        if (mirrorMode < MIRROR_MODE_AUTO || mirrorMode > MIRROR_MODE_V) {
            throw new IllegalArgumentException("Not a valid mirror mode " + mirrorMode);
        }
        mMirrorMode = mirrorMode;
    }

    public int getMirrorMode() {
        return mMirrorMode;
    }

    public void setReadoutTimestampEnabled(boolean on) {
        mReadoutTimestampEnabled = on;
    }

    public boolean isReadoutTimestampEnabled() {
        return mReadoutTimestampEnabled;
    }

    public int getMaxSharedSurfaceCount() {
        return MAX_SURFACES_COUNT;
    }

    public Surface getSurface() {
        if (mSurfaces.isEmpty()) {
            return null;
        }
        return mSurfaces.get(0);
    }

    public List<Surface> getSurfaces() {
        return Collections.unmodifiableList(mSurfaces);
    }

    public int getSurfaceGroupId() {
        return mSurfaceGroupId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (dest == null) {
            throw new IllegalArgumentException("dest must not be null");
        }
        dest.writeInt(mSurfaceGroupId);
        dest.writeInt(mFormat);
        dest.writeInt(mConfiguredSize.getWidth());
        dest.writeInt(mConfiguredSize.getHeight());
        dest.writeLong(mUsage);
        dest.writeInt(mIsDeferredConfig ? 1 : 0);
        dest.writeInt(mIsShared ? 1 : 0);
        dest.writeString(mPhysicalCameraId);
        dest.writeLong(mDynamicRangeProfile);
        dest.writeLong(mStreamUseCase);
        dest.writeInt(mTimestampBase);
        dest.writeInt(mMirrorMode);
        dest.writeInt(mReadoutTimestampEnabled ? 1 : 0);
        dest.writeInt(mSensorPixelModesUsed.size());
        for (Integer mode : mSensorPixelModesUsed) {
            dest.writeInt(mode.intValue());
        }
        dest.writeInt(mSurfaces.size());
        for (int i = 0; i < mSurfaces.size(); i++) {
            dest.writeInt(mConfiguredSize.getWidth());
            dest.writeInt(mConfiguredSize.getHeight());
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof OutputConfiguration)) return false;
        OutputConfiguration other = (OutputConfiguration) obj;
        return mSurfaceGroupId == other.mSurfaceGroupId
                && mFormat == other.mFormat
                && Objects.equals(mConfiguredSize, other.mConfiguredSize)
                && mUsage == other.mUsage
                && mIsShared == other.mIsShared
                && Objects.equals(mPhysicalCameraId, other.mPhysicalCameraId)
                && mDynamicRangeProfile == other.mDynamicRangeProfile
                && mStreamUseCase == other.mStreamUseCase
                && mTimestampBase == other.mTimestampBase
                && mMirrorMode == other.mMirrorMode
                && mReadoutTimestampEnabled == other.mReadoutTimestampEnabled
                && Objects.equals(mSensorPixelModesUsed, other.mSensorPixelModesUsed)
                && mSurfaces.size() == other.mSurfaces.size();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mSurfaceGroupId),
                Integer.valueOf(mFormat),
                mConfiguredSize,
                Long.valueOf(mUsage),
                Boolean.valueOf(mIsShared),
                mPhysicalCameraId,
                Long.valueOf(mDynamicRangeProfile),
                Long.valueOf(mStreamUseCase),
                Integer.valueOf(mTimestampBase),
                Integer.valueOf(mMirrorMode),
                Boolean.valueOf(mReadoutTimestampEnabled),
                Integer.valueOf(mSurfaces.size()));
    }

    public static final Parcelable.Creator<OutputConfiguration> CREATOR =
            new Parcelable.Creator<OutputConfiguration>() {
                @Override
                public OutputConfiguration createFromParcel(Parcel source) {
                    return new OutputConfiguration(source);
                }

                @Override
                public OutputConfiguration[] newArray(int size) {
                    return new OutputConfiguration[size];
                }
            };
}
