// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CaptureResult extends CameraMetadata<CaptureResult.Key<?>> {
    private final String mCameraId;
    private final CaptureRequest mRequest;
    private final int mSequenceId;
    private final long mFrameNumber;
    private final Map<Key<?>, Object> mResults;

    public CaptureResult() {
        this("0", null, 0, 0L);
    }

    public CaptureResult(
            String cameraId,
            CaptureRequest request,
            int sequenceId,
            long frameNumber) {
        mCameraId = cameraId;
        mRequest = request;
        mSequenceId = sequenceId;
        mFrameNumber = frameNumber;
        mResults = new LinkedHashMap<Key<?>, Object>();
    }

    public CaptureResult(
            String cameraId,
            CaptureRequest request,
            int sequenceId,
            long frameNumber,
            Map<Key<?>, Object> results) {
        mCameraId = cameraId;
        mRequest = request;
        mSequenceId = sequenceId;
        mFrameNumber = frameNumber;
        mResults = new LinkedHashMap<Key<?>, Object>();
        if (results != null) {
            mResults.putAll(results);
        }
    }

    public String getCameraId() {
        return mCameraId;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        if (key == null) {
            throw new NullPointerException("key must not be null");
        }
        return (T) mResults.get(key);
    }

    @Override
    public List<Key<?>> getKeys() {
        if (mResults.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<Key<?>>(mResults.keySet()));
    }

    public CaptureRequest getRequest() {
        return mRequest;
    }

    public long getFrameNumber() {
        return mFrameNumber;
    }

    public int getSequenceId() {
        return mSequenceId;
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
            return String.format("CaptureResult.Key(%s)", mName);
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
    public static final Key<Integer> CONTROL_AE_STATE = createKey("android.control.aeState", Integer.class);
    public static final Key<android.util.Range<Integer>> CONTROL_AE_TARGET_FPS_RANGE = createKey("android.control.aeTargetFpsRange", android.util.Range.class);
    public static final Key<Integer> CONTROL_AF_MODE = createKey("android.control.afMode", Integer.class);
    public static final Key<android.hardware.camera2.params.MeteringRectangle[]> CONTROL_AF_REGIONS = createKey("android.control.afRegions", android.hardware.camera2.params.MeteringRectangle[].class);
    public static final Key<Integer> CONTROL_AF_SCENE_CHANGE = createKey("android.control.afSceneChange", Integer.class);
    public static final Key<Integer> CONTROL_AF_STATE = createKey("android.control.afState", Integer.class);
    public static final Key<Integer> CONTROL_AF_TRIGGER = createKey("android.control.afTrigger", Integer.class);
    public static final Key<Integer> CONTROL_AUTOFRAMING = createKey("android.control.autoframing", Integer.class);
    public static final Key<Integer> CONTROL_AUTOFRAMING_STATE = createKey("android.control.autoframingState", Integer.class);
    public static final Key<Boolean> CONTROL_AWB_LOCK = createKey("android.control.awbLock", Boolean.class);
    public static final Key<Integer> CONTROL_AWB_MODE = createKey("android.control.awbMode", Integer.class);
    public static final Key<android.hardware.camera2.params.MeteringRectangle[]> CONTROL_AWB_REGIONS = createKey("android.control.awbRegions", android.hardware.camera2.params.MeteringRectangle[].class);
    public static final Key<Integer> CONTROL_AWB_STATE = createKey("android.control.awbState", Integer.class);
    public static final Key<Integer> CONTROL_CAPTURE_INTENT = createKey("android.control.captureIntent", Integer.class);
    public static final Key<Integer> CONTROL_EFFECT_MODE = createKey("android.control.effectMode", Integer.class);
    public static final Key<Boolean> CONTROL_ENABLE_ZSL = createKey("android.control.enableZsl", Boolean.class);
    public static final Key<Integer> CONTROL_EXTENDED_SCENE_MODE = createKey("android.control.extendedSceneMode", Integer.class);
    public static final Key<Integer> CONTROL_LOW_LIGHT_BOOST_STATE = createKey("android.control.lowLightBoostState", Integer.class);
    public static final Key<Integer> CONTROL_MODE = createKey("android.control.mode", Integer.class);
    public static final Key<Integer> CONTROL_POST_RAW_SENSITIVITY_BOOST = createKey("android.control.postRawSensitivityBoost", Integer.class);
    public static final Key<Integer> CONTROL_SCENE_MODE = createKey("android.control.sceneMode", Integer.class);
    public static final Key<Integer> CONTROL_SETTINGS_OVERRIDE = createKey("android.control.settingsOverride", Integer.class);
    public static final Key<Integer> CONTROL_VIDEO_STABILIZATION_MODE = createKey("android.control.videoStabilizationMode", Integer.class);
    public static final Key<Float> CONTROL_ZOOM_RATIO = createKey("android.control.zoomRatio", Float.class);
    public static final Key<Integer> DISTORTION_CORRECTION_MODE = createKey("android.distortionCorrection.mode", Integer.class);
    public static final Key<Integer> EDGE_MODE = createKey("android.edge.mode", Integer.class);
    public static final Key<Integer> EXTENSION_CURRENT_TYPE = createKey("android.extension.currentType", Integer.class);
    public static final Key<Integer> EXTENSION_STRENGTH = createKey("android.extension.strength", Integer.class);
    public static final Key<Integer> FLASH_MODE = createKey("android.flash.mode", Integer.class);
    public static final Key<Integer> FLASH_STATE = createKey("android.flash.state", Integer.class);
    public static final Key<Integer> FLASH_STRENGTH_LEVEL = createKey("android.flash.strengthLevel", Integer.class);
    public static final Key<Integer> HOT_PIXEL_MODE = createKey("android.hotPixel.mode", Integer.class);
    public static final Key<android.location.Location> JPEG_GPS_LOCATION = createKey("android.jpeg.gpsLocation", android.location.Location.class);
    public static final Key<Integer> JPEG_ORIENTATION = createKey("android.jpeg.orientation", Integer.class);
    public static final Key<Byte> JPEG_QUALITY = createKey("android.jpeg.quality", Byte.class);
    public static final Key<Byte> JPEG_THUMBNAIL_QUALITY = createKey("android.jpeg.thumbnailQuality", Byte.class);
    public static final Key<android.util.Size> JPEG_THUMBNAIL_SIZE = createKey("android.jpeg.thumbnailSize", android.util.Size.class);
    public static final Key<Float> LENS_APERTURE = createKey("android.lens.aperture", Float.class);
    public static final Key<float[]> LENS_DISTORTION = createKey("android.lens.distortion", float[].class);
    public static final Key<Float> LENS_FILTER_DENSITY = createKey("android.lens.filterDensity", Float.class);
    public static final Key<Float> LENS_FOCAL_LENGTH = createKey("android.lens.focalLength", Float.class);
    public static final Key<Float> LENS_FOCUS_DISTANCE = createKey("android.lens.focusDistance", Float.class);
    public static final Key<android.util.Pair<Float, Float>> LENS_FOCUS_RANGE = createKey("android.lens.focusRange", android.util.Pair.class);
    public static final Key<float[]> LENS_INTRINSIC_CALIBRATION = createKey("android.lens.intrinsicCalibration", float[].class);
    public static final Key<Integer> LENS_OPTICAL_STABILIZATION_MODE = createKey("android.lens.opticalStabilizationMode", Integer.class);
    public static final Key<float[]> LENS_POSE_ROTATION = createKey("android.lens.poseRotation", float[].class);
    public static final Key<float[]> LENS_POSE_TRANSLATION = createKey("android.lens.poseTranslation", float[].class);
    public static final Key<float[]> LENS_RADIAL_DISTORTION = createKey("android.lens.radialDistortion", float[].class);
    public static final Key<Integer> LENS_STATE = createKey("android.lens.state", Integer.class);
    public static final Key<String> LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID = createKey("android.logicalMultiCamera.activePhysicalId", String.class);
    public static final Key<android.graphics.Rect> LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_SENSOR_CROP_REGION = createKey("android.logicalMultiCamera.activePhysicalSensorCropRegion", android.graphics.Rect.class);
    public static final Key<Integer> NOISE_REDUCTION_MODE = createKey("android.noiseReduction.mode", Integer.class);
    public static final Key<Float> REPROCESS_EFFECTIVE_EXPOSURE_FACTOR = createKey("android.reprocess.effectiveExposureFactor", Float.class);
    public static final Key<Byte> REQUEST_PIPELINE_DEPTH = createKey("android.request.pipelineDepth", Byte.class);
    public static final Key<android.graphics.Rect> SCALER_CROP_REGION = createKey("android.scaler.cropRegion", android.graphics.Rect.class);
    public static final Key<android.graphics.Rect> SCALER_RAW_CROP_REGION = createKey("android.scaler.rawCropRegion", android.graphics.Rect.class);
    public static final Key<Integer> SCALER_ROTATE_AND_CROP = createKey("android.scaler.rotateAndCrop", Integer.class);
    public static final Key<float[]> SENSOR_DYNAMIC_BLACK_LEVEL = createKey("android.sensor.dynamicBlackLevel", float[].class);
    public static final Key<Integer> SENSOR_DYNAMIC_WHITE_LEVEL = createKey("android.sensor.dynamicWhiteLevel", Integer.class);
    public static final Key<Long> SENSOR_EXPOSURE_TIME = createKey("android.sensor.exposureTime", Long.class);
    public static final Key<Long> SENSOR_FRAME_DURATION = createKey("android.sensor.frameDuration", Long.class);
    public static final Key<Float> SENSOR_GREEN_SPLIT = createKey("android.sensor.greenSplit", Float.class);
    public static final Key<android.util.Rational[]> SENSOR_NEUTRAL_COLOR_POINT = createKey("android.sensor.neutralColorPoint", android.util.Rational[].class);
    public static final Key<android.util.Pair<Double, Double>[]> SENSOR_NOISE_PROFILE = createKey("android.sensor.noiseProfile", android.util.Pair[].class);
    public static final Key<Integer> SENSOR_PIXEL_MODE = createKey("android.sensor.pixelMode", Integer.class);
    public static final Key<Boolean> SENSOR_RAW_BINNING_FACTOR_USED = createKey("android.sensor.rawBinningFactorUsed", Boolean.class);
    public static final Key<Long> SENSOR_ROLLING_SHUTTER_SKEW = createKey("android.sensor.rollingShutterSkew", Long.class);
    public static final Key<Integer> SENSOR_SENSITIVITY = createKey("android.sensor.sensitivity", Integer.class);
    public static final Key<int[]> SENSOR_TEST_PATTERN_DATA = createKey("android.sensor.testPatternData", int[].class);
    public static final Key<Integer> SENSOR_TEST_PATTERN_MODE = createKey("android.sensor.testPatternMode", Integer.class);
    public static final Key<Long> SENSOR_TIMESTAMP = createKey("android.sensor.timestamp", Long.class);
    public static final Key<Integer> SHADING_MODE = createKey("android.shading.mode", Integer.class);
    public static final Key<android.hardware.camera2.params.Face[]> STATISTICS_FACES = createKey("android.statistics.faces", android.hardware.camera2.params.Face[].class);
    public static final Key<Integer> STATISTICS_FACE_DETECT_MODE = createKey("android.statistics.faceDetectMode", Integer.class);
    public static final Key<android.graphics.Point[]> STATISTICS_HOT_PIXEL_MAP = createKey("android.statistics.hotPixelMap", android.graphics.Point[].class);
    public static final Key<Boolean> STATISTICS_HOT_PIXEL_MAP_MODE = createKey("android.statistics.hotPixelMapMode", Boolean.class);
    public static final Key<android.hardware.camera2.params.LensIntrinsicsSample[]> STATISTICS_LENS_INTRINSICS_SAMPLES = createKey("android.statistics.lensIntrinsicsSamples", android.hardware.camera2.params.LensIntrinsicsSample[].class);
    public static final Key<android.hardware.camera2.params.LensShadingMap> STATISTICS_LENS_SHADING_CORRECTION_MAP = createKey("android.statistics.lensShadingCorrectionMap", android.hardware.camera2.params.LensShadingMap.class);
    public static final Key<Integer> STATISTICS_LENS_SHADING_MAP_MODE = createKey("android.statistics.lensShadingMapMode", Integer.class);
    public static final Key<Integer> STATISTICS_OIS_DATA_MODE = createKey("android.statistics.oisDataMode", Integer.class);
    public static final Key<android.hardware.camera2.params.OisSample[]> STATISTICS_OIS_SAMPLES = createKey("android.statistics.oisSamples", android.hardware.camera2.params.OisSample[].class);
    public static final Key<Integer> STATISTICS_SCENE_FLICKER = createKey("android.statistics.sceneFlicker", Integer.class);
    public static final Key<android.hardware.camera2.params.TonemapCurve> TONEMAP_CURVE = createKey("android.tonemap.curve", android.hardware.camera2.params.TonemapCurve.class);
    public static final Key<Float> TONEMAP_GAMMA = createKey("android.tonemap.gamma", Float.class);
    public static final Key<Integer> TONEMAP_MODE = createKey("android.tonemap.mode", Integer.class);
    public static final Key<Integer> TONEMAP_PRESET_CURVE = createKey("android.tonemap.presetCurve", Integer.class);
}
