// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.Surface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CaptureRequest extends CameraMetadata<CaptureRequest.Key<?>> implements Parcelable {
    private final Map<Key<?>, Object> mSettings;
    private final Map<String, Map<Key<?>, Object>> mPhysicalSettings;
    private final Set<Surface> mTargets;
    private final Object mUserTag;
    private final boolean mIsReprocess;

    private CaptureRequest(
            Map<Key<?>, Object> settings,
            Map<String, Map<Key<?>, Object>> physicalSettings,
            Set<Surface> targets,
            Object userTag,
            boolean isReprocess) {
        mSettings = new LinkedHashMap<Key<?>, Object>(settings);
        mPhysicalSettings = new LinkedHashMap<String, Map<Key<?>, Object>>();
        for (Map.Entry<String, Map<Key<?>, Object>> e : physicalSettings.entrySet()) {
            mPhysicalSettings.put(e.getKey(), new LinkedHashMap<Key<?>, Object>(e.getValue()));
        }
        mTargets = new LinkedHashSet<Surface>(targets);
        mUserTag = userTag;
        mIsReprocess = isReprocess;
    }

    private CaptureRequest(Parcel in) {
        mSettings = new LinkedHashMap<Key<?>, Object>();
        mPhysicalSettings = new LinkedHashMap<String, Map<Key<?>, Object>>();
        mTargets = new LinkedHashSet<Surface>();
        mIsReprocess = in.readInt() != 0;
        int targetCount = in.readInt();
        for (int i = 0; i < targetCount; i++) {
            int w = Math.min(4096, Math.max(1, in.readInt()));
            int h = Math.min(4096, Math.max(1, in.readInt()));
            Surface s = new Surface();
            s.shirazConfigure(w, h, 1);
            mTargets.add(s);
        }
        mUserTag = null;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        if (key == null) {
            throw new NullPointerException("key must not be null");
        }
        return (T) mSettings.get(key);
    }

    @Override
    public List<Key<?>> getKeys() {
        if (mSettings.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<Key<?>>(mSettings.keySet()));
    }

    public Object getTag() {
        return mUserTag;
    }

    public boolean isReprocess() {
        return mIsReprocess;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CaptureRequest)) return false;
        CaptureRequest that = (CaptureRequest) other;
        return mIsReprocess == that.mIsReprocess
                && Objects.equals(mUserTag, that.mUserTag)
                && Objects.equals(mSettings, that.mSettings)
                && Objects.equals(mPhysicalSettings, that.mPhysicalSettings)
                && mTargets.size() == that.mTargets.size();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mSettings,
                mPhysicalSettings,
                Integer.valueOf(mTargets.size()),
                mUserTag,
                Boolean.valueOf(mIsReprocess));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mIsReprocess ? 1 : 0);
        dest.writeInt(mTargets.size());
        for (int i = 0; i < mTargets.size(); i++) {
            dest.writeInt(1);
            dest.writeInt(1);
        }
    }

    @Override
    protected void finalize() {
        try {
            super.finalize();
        } catch (Throwable ignored) {
        }
    }

    public static final Parcelable.Creator<CaptureRequest> CREATOR =
            new Parcelable.Creator<CaptureRequest>() {
                @Override
                public CaptureRequest createFromParcel(Parcel in) {
                    return new CaptureRequest(in);
                }

                @Override
                public CaptureRequest[] newArray(int size) {
                    return new CaptureRequest[size];
                }
            };

    public static final class Builder {
        private final Map<Key<?>, Object> mSettings = new LinkedHashMap<Key<?>, Object>();
        private final Map<String, Map<Key<?>, Object>> mPhysicalSettings =
                new LinkedHashMap<String, Map<Key<?>, Object>>();
        private final Set<Surface> mTargets = new LinkedHashSet<Surface>();
        private Object mUserTag;
        private final boolean mIsReprocess;

        public Builder() {
            mIsReprocess = false;
        }

        public Builder(boolean isReprocess) {
            mIsReprocess = isReprocess;
        }

        public void addTarget(Surface outputTarget) {
            if (outputTarget == null) {
                throw new NullPointerException("Surface must not be null");
            }
            mTargets.add(outputTarget);
        }

        public void removeTarget(Surface outputTarget) {
            if (outputTarget == null) {
                throw new NullPointerException("Surface must not be null");
            }
            mTargets.remove(outputTarget);
        }

        public <T> void set(Key<T> key, T value) {
            if (key == null) {
                throw new NullPointerException("key must not be null");
            }
            if (value == null) {
                mSettings.remove(key);
            } else {
                mSettings.put(key, value);
            }
        }

        @SuppressWarnings("unchecked")
        public <T> T get(Key<T> key) {
            if (key == null) {
                throw new NullPointerException("key must not be null");
            }
            return (T) mSettings.get(key);
        }

        public <T> Builder setPhysicalCameraKey(Key<T> key, T value, String physicalCameraId) {
            if (key == null) throw new NullPointerException("key must not be null");
            if (physicalCameraId == null) {
                throw new NullPointerException("physicalCameraId must not be null");
            }
            Map<Key<?>, Object> map = mPhysicalSettings.get(physicalCameraId);
            if (map == null) {
                map = new LinkedHashMap<Key<?>, Object>();
                mPhysicalSettings.put(physicalCameraId, map);
            }
            if (value == null) {
                map.remove(key);
            } else {
                map.put(key, value);
            }
            return this;
        }

        @SuppressWarnings("unchecked")
        public <T> T getPhysicalCameraKey(Key<T> key, String physicalCameraId) {
            if (key == null) throw new NullPointerException("key must not be null");
            if (physicalCameraId == null) {
                throw new NullPointerException("physicalCameraId must not be null");
            }
            Map<Key<?>, Object> map = mPhysicalSettings.get(physicalCameraId);
            return map != null ? (T) map.get(key) : null;
        }

        public void setTag(Object tag) {
            mUserTag = tag;
        }

        public CaptureRequest build() {
            return new CaptureRequest(mSettings, mPhysicalSettings, mTargets, mUserTag, mIsReprocess);
        }
    }

    public static final class Key<T> {
        private final String mName;
        private final Class<T> mType;

        public Key(String name, Class<T> type) {
            if (name == null) throw new NullPointerException("name must not be null");
            if (type == null) throw new NullPointerException("type must not be null");
            mName = name;
            mType = type;
        }

        public String getName() {
            return mName;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(mName, mType);
        }

        @Override
        public final boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key<?> other = (Key<?>) o;
            return mName.equals(other.mName) && mType.equals(other.mType);
        }

        @Override
        public String toString() {
            return String.format("CaptureRequest.Key(%s)", mName);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> Key<T> createKey(String name, Class<?> rawType) {
        return new Key<T>(name, (Class<T>) rawType);
    }

    public static final Key<Boolean> BLACK_LEVEL_LOCK = createKey("android.blackLevel.lock", Boolean.class);
    public static final Key<Integer> COLOR_CORRECTION_ABERRATION_MODE = createKey("android.colorCorrection.aberrationMode", Integer.class);
    public static final Key<android.hardware.camera2.params.RggbChannelVector> COLOR_CORRECTION_GAINS = createKey("android.colorCorrection.gains", android.hardware.camera2.params.RggbChannelVector.class);
    public static final Key<Integer> COLOR_CORRECTION_MODE = createKey("android.colorCorrection.mode", Integer.class);
    public static final Key<android.hardware.camera2.params.ColorSpaceTransform> COLOR_CORRECTION_TRANSFORM = createKey("android.colorCorrection.transform", android.hardware.camera2.params.ColorSpaceTransform.class);
    public static final Key<Integer> CONTROL_AE_ANTIBANDING_MODE = createKey("android.control.aeAntibandingMode", Integer.class);
    public static final Key<Integer> CONTROL_AE_EXPOSURE_COMPENSATION = createKey("android.control.aeExposureCompensation", Integer.class);
    public static final Key<Boolean> CONTROL_AE_LOCK = createKey("android.control.aeLock", Boolean.class);
    public static final Key<Integer> CONTROL_AE_MODE = createKey("android.control.aeMode", Integer.class);
    public static final Key<Integer> CONTROL_AE_PRECAPTURE_TRIGGER = createKey("android.control.aePrecaptureTrigger", Integer.class);
    public static final Key<android.hardware.camera2.params.MeteringRectangle[]> CONTROL_AE_REGIONS = createKey("android.control.aeRegions", android.hardware.camera2.params.MeteringRectangle[].class);
    public static final Key<android.util.Range<Integer>> CONTROL_AE_TARGET_FPS_RANGE = createKey("android.control.aeTargetFpsRange", android.util.Range.class);
    public static final Key<Integer> CONTROL_AF_MODE = createKey("android.control.afMode", Integer.class);
    public static final Key<android.hardware.camera2.params.MeteringRectangle[]> CONTROL_AF_REGIONS = createKey("android.control.afRegions", android.hardware.camera2.params.MeteringRectangle[].class);
    public static final Key<Integer> CONTROL_AF_TRIGGER = createKey("android.control.afTrigger", Integer.class);
    public static final Key<Integer> CONTROL_AUTOFRAMING = createKey("android.control.autoframing", Integer.class);
    public static final Key<Boolean> CONTROL_AWB_LOCK = createKey("android.control.awbLock", Boolean.class);
    public static final Key<Integer> CONTROL_AWB_MODE = createKey("android.control.awbMode", Integer.class);
    public static final Key<android.hardware.camera2.params.MeteringRectangle[]> CONTROL_AWB_REGIONS = createKey("android.control.awbRegions", android.hardware.camera2.params.MeteringRectangle[].class);
    public static final Key<Integer> CONTROL_CAPTURE_INTENT = createKey("android.control.captureIntent", Integer.class);
    public static final Key<Integer> CONTROL_EFFECT_MODE = createKey("android.control.effectMode", Integer.class);
    public static final Key<Boolean> CONTROL_ENABLE_ZSL = createKey("android.control.enableZsl", Boolean.class);
    public static final Key<Integer> CONTROL_EXTENDED_SCENE_MODE = createKey("android.control.extendedSceneMode", Integer.class);
    public static final Key<Integer> CONTROL_MODE = createKey("android.control.mode", Integer.class);
    public static final Key<Integer> CONTROL_POST_RAW_SENSITIVITY_BOOST = createKey("android.control.postRawSensitivityBoost", Integer.class);
    public static final Key<Integer> CONTROL_SCENE_MODE = createKey("android.control.sceneMode", Integer.class);
    public static final Key<Integer> CONTROL_SETTINGS_OVERRIDE = createKey("android.control.settingsOverride", Integer.class);
    public static final Key<Integer> CONTROL_VIDEO_STABILIZATION_MODE = createKey("android.control.videoStabilizationMode", Integer.class);
    public static final Key<Float> CONTROL_ZOOM_RATIO = createKey("android.control.zoomRatio", Float.class);
    public static final Key<Integer> DISTORTION_CORRECTION_MODE = createKey("android.distortionCorrection.mode", Integer.class);
    public static final Key<Integer> EDGE_MODE = createKey("android.edge.mode", Integer.class);
    public static final Key<Integer> EXTENSION_STRENGTH = createKey("android.extension.strength", Integer.class);
    public static final Key<Integer> FLASH_MODE = createKey("android.flash.mode", Integer.class);
    public static final Key<Integer> FLASH_STRENGTH_LEVEL = createKey("android.flash.strengthLevel", Integer.class);
    public static final Key<Integer> HOT_PIXEL_MODE = createKey("android.hotPixel.mode", Integer.class);
    public static final Key<android.location.Location> JPEG_GPS_LOCATION = createKey("android.jpeg.gpsLocation", android.location.Location.class);
    public static final Key<Integer> JPEG_ORIENTATION = createKey("android.jpeg.orientation", Integer.class);
    public static final Key<Byte> JPEG_QUALITY = createKey("android.jpeg.quality", Byte.class);
    public static final Key<Byte> JPEG_THUMBNAIL_QUALITY = createKey("android.jpeg.thumbnailQuality", Byte.class);
    public static final Key<android.util.Size> JPEG_THUMBNAIL_SIZE = createKey("android.jpeg.thumbnailSize", android.util.Size.class);
    public static final Key<Float> LENS_APERTURE = createKey("android.lens.aperture", Float.class);
    public static final Key<Float> LENS_FILTER_DENSITY = createKey("android.lens.filterDensity", Float.class);
    public static final Key<Float> LENS_FOCAL_LENGTH = createKey("android.lens.focalLength", Float.class);
    public static final Key<Float> LENS_FOCUS_DISTANCE = createKey("android.lens.focusDistance", Float.class);
    public static final Key<Integer> LENS_OPTICAL_STABILIZATION_MODE = createKey("android.lens.opticalStabilizationMode", Integer.class);
    public static final Key<Integer> NOISE_REDUCTION_MODE = createKey("android.noiseReduction.mode", Integer.class);
    public static final Key<Float> REPROCESS_EFFECTIVE_EXPOSURE_FACTOR = createKey("android.reprocess.effectiveExposureFactor", Float.class);
    public static final Key<android.graphics.Rect> SCALER_CROP_REGION = createKey("android.scaler.cropRegion", android.graphics.Rect.class);
    public static final Key<Integer> SCALER_ROTATE_AND_CROP = createKey("android.scaler.rotateAndCrop", Integer.class);
    public static final Key<Long> SENSOR_EXPOSURE_TIME = createKey("android.sensor.exposureTime", Long.class);
    public static final Key<Long> SENSOR_FRAME_DURATION = createKey("android.sensor.frameDuration", Long.class);
    public static final Key<Integer> SENSOR_PIXEL_MODE = createKey("android.sensor.pixelMode", Integer.class);
    public static final Key<Integer> SENSOR_SENSITIVITY = createKey("android.sensor.sensitivity", Integer.class);
    public static final Key<int[]> SENSOR_TEST_PATTERN_DATA = createKey("android.sensor.testPatternData", int[].class);
    public static final Key<Integer> SENSOR_TEST_PATTERN_MODE = createKey("android.sensor.testPatternMode", Integer.class);
    public static final Key<Integer> SHADING_MODE = createKey("android.shading.mode", Integer.class);
    public static final Key<Integer> STATISTICS_FACE_DETECT_MODE = createKey("android.statistics.faceDetectMode", Integer.class);
    public static final Key<Boolean> STATISTICS_HOT_PIXEL_MAP_MODE = createKey("android.statistics.hotPixelMapMode", Boolean.class);
    public static final Key<Integer> STATISTICS_LENS_SHADING_MAP_MODE = createKey("android.statistics.lensShadingMapMode", Integer.class);
    public static final Key<Integer> STATISTICS_OIS_DATA_MODE = createKey("android.statistics.oisDataMode", Integer.class);
    public static final Key<android.hardware.camera2.params.TonemapCurve> TONEMAP_CURVE = createKey("android.tonemap.curve", android.hardware.camera2.params.TonemapCurve.class);
    public static final Key<Float> TONEMAP_GAMMA = createKey("android.tonemap.gamma", Float.class);
    public static final Key<Integer> TONEMAP_MODE = createKey("android.tonemap.mode", Integer.class);
    public static final Key<Integer> TONEMAP_PRESET_CURVE = createKey("android.tonemap.presetCurve", Integer.class);
}
