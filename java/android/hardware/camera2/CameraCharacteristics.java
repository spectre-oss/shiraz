// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2;

import android.hardware.camera2.params.RecommendedStreamConfigurationMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CameraCharacteristics extends CameraMetadata<CameraCharacteristics.Key<?>> {
    private final Map<Key<?>, Object> mProperties;

    public CameraCharacteristics() {
        mProperties = new LinkedHashMap<Key<?>, Object>();
    }

    public CameraCharacteristics(Map<Key<?>, Object> properties) {
        mProperties = new LinkedHashMap<Key<?>, Object>();
        if (properties != null) {
            mProperties.putAll(properties);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        if (key == null) {
            throw new NullPointerException("key must not be null");
        }
        return (T) mProperties.get(key);
    }

    @Override
    public List<Key<?>> getKeys() {
        if (mProperties.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<Key<?>>(mProperties.keySet()));
    }

    public List<Key<?>> getKeysNeedingPermission() {
        return Collections.emptyList();
    }

    public RecommendedStreamConfigurationMap getRecommendedStreamConfigurationMap(int usecase) {
        if ((usecase >= RecommendedStreamConfigurationMap.USECASE_PREVIEW
                && usecase <= RecommendedStreamConfigurationMap.USECASE_LOW_LATENCY_SNAPSHOT)
                || usecase == RecommendedStreamConfigurationMap.USECASE_10BIT_OUTPUT) {
            return null;
        }
        throw new IllegalArgumentException("Invalid use case: " + usecase);
    }

    public List<CaptureRequest.Key<?>> getAvailableSessionKeys() {
        return Collections.emptyList();
    }

    public List<Key<?>> getAvailableSessionCharacteristicsKeys() {
        return Collections.emptyList();
    }

    public List<CaptureRequest.Key<?>> getAvailablePhysicalCameraRequestKeys() {
        return null;
    }

    public List<CaptureRequest.Key<?>> getAvailableCaptureRequestKeys() {
        return Collections.emptyList();
    }

    public List<CaptureResult.Key<?>> getAvailableCaptureResultKeys() {
        return Collections.emptyList();
    }

    public Set<String> getPhysicalCameraIds() {
        return Collections.emptySet();
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
            return String.format("CameraCharacteristics.Key(%s)", mName);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> Key<T> createKey(String name, Class<?> rawType) {
        return new Key<T>(name, (Class<T>) rawType);
    }

    public static final Key<int[]> AUTOMOTIVE_LENS_FACING = createKey("android.automotive.lens.facing", int[].class);
    public static final Key<Integer> AUTOMOTIVE_LOCATION = createKey("android.automotive.location", Integer.class);
    public static final Key<int[]> COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES = createKey("android.colorCorrection.availableAberrationModes", int[].class);
    public static final Key<int[]> CONTROL_AE_AVAILABLE_ANTIBANDING_MODES = createKey("android.control.aeAvailableAntibandingModes", int[].class);
    public static final Key<int[]> CONTROL_AE_AVAILABLE_MODES = createKey("android.control.aeAvailableModes", int[].class);
    public static final Key<android.util.Range<Integer>[]> CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES = createKey("android.control.aeAvailableTargetFpsRanges", android.util.Range[].class);
    public static final Key<android.util.Range<Integer>> CONTROL_AE_COMPENSATION_RANGE = createKey("android.control.aeCompensationRange", android.util.Range.class);
    public static final Key<android.util.Rational> CONTROL_AE_COMPENSATION_STEP = createKey("android.control.aeCompensationStep", android.util.Rational.class);
    public static final Key<Boolean> CONTROL_AE_LOCK_AVAILABLE = createKey("android.control.aeLockAvailable", Boolean.class);
    public static final Key<int[]> CONTROL_AF_AVAILABLE_MODES = createKey("android.control.afAvailableModes", int[].class);
    public static final Key<Boolean> CONTROL_AUTOFRAMING_AVAILABLE = createKey("android.control.autoframingAvailable", Boolean.class);
    public static final Key<int[]> CONTROL_AVAILABLE_EFFECTS = createKey("android.control.availableEffects", int[].class);
    public static final Key<android.hardware.camera2.params.Capability[]> CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_CAPABILITIES = createKey("android.control.availableExtendedSceneModeCapabilities", android.hardware.camera2.params.Capability[].class);
    public static final Key<int[]> CONTROL_AVAILABLE_MODES = createKey("android.control.availableModes", int[].class);
    public static final Key<int[]> CONTROL_AVAILABLE_SCENE_MODES = createKey("android.control.availableSceneModes", int[].class);
    public static final Key<int[]> CONTROL_AVAILABLE_SETTINGS_OVERRIDES = createKey("android.control.availableSettingsOverrides", int[].class);
    public static final Key<int[]> CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES = createKey("android.control.availableVideoStabilizationModes", int[].class);
    public static final Key<int[]> CONTROL_AWB_AVAILABLE_MODES = createKey("android.control.awbAvailableModes", int[].class);
    public static final Key<Boolean> CONTROL_AWB_LOCK_AVAILABLE = createKey("android.control.awbLockAvailable", Boolean.class);
    public static final Key<android.util.Range<Float>> CONTROL_LOW_LIGHT_BOOST_INFO_LUMINANCE_RANGE = createKey("android.control.lowLightBoostInfoLuminanceRange", android.util.Range.class);
    public static final Key<Integer> CONTROL_MAX_REGIONS_AE = createKey("android.control.maxRegionsAe", Integer.class);
    public static final Key<Integer> CONTROL_MAX_REGIONS_AF = createKey("android.control.maxRegionsAf", Integer.class);
    public static final Key<Integer> CONTROL_MAX_REGIONS_AWB = createKey("android.control.maxRegionsAwb", Integer.class);
    public static final Key<android.util.Range<Integer>> CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE = createKey("android.control.postRawSensitivityBoostRange", android.util.Range.class);
    public static final Key<android.util.Range<Float>> CONTROL_ZOOM_RATIO_RANGE = createKey("android.control.zoomRatioRange", android.util.Range.class);
    public static final Key<Boolean> DEPTH_DEPTH_IS_EXCLUSIVE = createKey("android.depth.depthIsExclusive", Boolean.class);
    public static final Key<int[]> DISTORTION_CORRECTION_AVAILABLE_MODES = createKey("android.distortionCorrection.availableModes", int[].class);
    public static final Key<int[]> EDGE_AVAILABLE_EDGE_MODES = createKey("android.edge.availableEdgeModes", int[].class);
    public static final Key<Boolean> FLASH_INFO_AVAILABLE = createKey("android.flash.info.available", Boolean.class);
    public static final Key<Integer> FLASH_INFO_STRENGTH_DEFAULT_LEVEL = createKey("android.flash.info.strengthDefaultLevel", Integer.class);
    public static final Key<Integer> FLASH_INFO_STRENGTH_MAXIMUM_LEVEL = createKey("android.flash.info.strengthMaximumLevel", Integer.class);
    public static final Key<Integer> FLASH_SINGLE_STRENGTH_DEFAULT_LEVEL = createKey("android.flash.singleStrengthDefaultLevel", Integer.class);
    public static final Key<Integer> FLASH_SINGLE_STRENGTH_MAX_LEVEL = createKey("android.flash.singleStrengthMaxLevel", Integer.class);
    public static final Key<Integer> FLASH_TORCH_STRENGTH_DEFAULT_LEVEL = createKey("android.flash.torchStrengthDefaultLevel", Integer.class);
    public static final Key<Integer> FLASH_TORCH_STRENGTH_MAX_LEVEL = createKey("android.flash.torchStrengthMaxLevel", Integer.class);
    public static final Key<int[]> HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES = createKey("android.hotPixel.availableHotPixelModes", int[].class);
    public static final Key<android.hardware.camera2.params.DeviceStateSensorOrientationMap> INFO_DEVICE_STATE_SENSOR_ORIENTATION_MAP = createKey("android.info.deviceStateSensorOrientationMap", android.hardware.camera2.params.DeviceStateSensorOrientationMap.class);
    public static final Key<Integer> INFO_SESSION_CONFIGURATION_QUERY_VERSION = createKey("android.info.sessionConfigurationQueryVersion", Integer.class);
    public static final Key<Integer> INFO_SUPPORTED_HARDWARE_LEVEL = createKey("android.info.supportedHardwareLevel", Integer.class);
    public static final Key<String> INFO_VERSION = createKey("android.info.version", String.class);
    public static final Key<android.util.Size[]> JPEG_AVAILABLE_THUMBNAIL_SIZES = createKey("android.jpeg.availableThumbnailSizes", android.util.Size[].class);
    public static final Key<float[]> LENS_DISTORTION = createKey("android.lens.distortion", float[].class);
    public static final Key<float[]> LENS_DISTORTION_MAXIMUM_RESOLUTION = createKey("android.lens.distortionMaximumResolution", float[].class);
    public static final Key<Integer> LENS_FACING = createKey("android.lens.facing", Integer.class);
    public static final Key<float[]> LENS_INFO_AVAILABLE_APERTURES = createKey("android.lens.info.availableApertures", float[].class);
    public static final Key<float[]> LENS_INFO_AVAILABLE_FILTER_DENSITIES = createKey("android.lens.info.availableFilterDensities", float[].class);
    public static final Key<float[]> LENS_INFO_AVAILABLE_FOCAL_LENGTHS = createKey("android.lens.info.availableFocalLengths", float[].class);
    public static final Key<int[]> LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION = createKey("android.lens.info.availableOpticalStabilization", int[].class);
    public static final Key<Integer> LENS_INFO_FOCUS_DISTANCE_CALIBRATION = createKey("android.lens.info.focusDistanceCalibration", Integer.class);
    public static final Key<Float> LENS_INFO_HYPERFOCAL_DISTANCE = createKey("android.lens.info.hyperfocalDistance", Float.class);
    public static final Key<Float> LENS_INFO_MINIMUM_FOCUS_DISTANCE = createKey("android.lens.info.minimumFocusDistance", Float.class);
    public static final Key<float[]> LENS_INTRINSIC_CALIBRATION = createKey("android.lens.intrinsicCalibration", float[].class);
    public static final Key<float[]> LENS_INTRINSIC_CALIBRATION_MAXIMUM_RESOLUTION = createKey("android.lens.intrinsicCalibrationMaximumResolution", float[].class);
    public static final Key<Integer> LENS_POSE_REFERENCE = createKey("android.lens.poseReference", Integer.class);
    public static final Key<float[]> LENS_POSE_ROTATION = createKey("android.lens.poseRotation", float[].class);
    public static final Key<float[]> LENS_POSE_TRANSLATION = createKey("android.lens.poseTranslation", float[].class);
    public static final Key<float[]> LENS_RADIAL_DISTORTION = createKey("android.lens.radialDistortion", float[].class);
    public static final Key<Integer> LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE = createKey("android.logicalMultiCamera.sensorSyncType", Integer.class);
    public static final Key<int[]> NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES = createKey("android.noiseReduction.availableNoiseReductionModes", int[].class);
    public static final Key<Integer> REPROCESS_MAX_CAPTURE_STALL = createKey("android.reprocess.maxCaptureStall", Integer.class);
    public static final Key<int[]> REQUEST_AVAILABLE_CAPABILITIES = createKey("android.request.availableCapabilities", int[].class);
    public static final Key<android.hardware.camera2.params.ColorSpaceProfiles> REQUEST_AVAILABLE_COLOR_SPACE_PROFILES = createKey("android.request.availableColorSpaceProfiles", android.hardware.camera2.params.ColorSpaceProfiles.class);
    public static final Key<android.hardware.camera2.params.DynamicRangeProfiles> REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES = createKey("android.request.availableDynamicRangeProfiles", android.hardware.camera2.params.DynamicRangeProfiles.class);
    public static final Key<Integer> REQUEST_MAX_NUM_INPUT_STREAMS = createKey("android.request.maxNumInputStreams", Integer.class);
    public static final Key<Integer> REQUEST_MAX_NUM_OUTPUT_PROC = createKey("android.request.maxNumOutputProc", Integer.class);
    public static final Key<Integer> REQUEST_MAX_NUM_OUTPUT_PROC_STALLING = createKey("android.request.maxNumOutputProcStalling", Integer.class);
    public static final Key<Integer> REQUEST_MAX_NUM_OUTPUT_RAW = createKey("android.request.maxNumOutputRaw", Integer.class);
    public static final Key<Integer> REQUEST_PARTIAL_RESULT_COUNT = createKey("android.request.partialResultCount", Integer.class);
    public static final Key<Byte> REQUEST_PIPELINE_MAX_DEPTH = createKey("android.request.pipelineMaxDepth", Byte.class);
    public static final Key<Long> REQUEST_RECOMMENDED_TEN_BIT_DYNAMIC_RANGE_PROFILE = createKey("android.request.recommendedTenBitDynamicRangeProfile", Long.class);
    public static final Key<Float> SCALER_AVAILABLE_MAX_DIGITAL_ZOOM = createKey("android.scaler.availableMaxDigitalZoom", Float.class);
    public static final Key<int[]> SCALER_AVAILABLE_ROTATE_AND_CROP_MODES = createKey("android.scaler.availableRotateAndCropModes", int[].class);
    public static final Key<long[]> SCALER_AVAILABLE_STREAM_USE_CASES = createKey("android.scaler.availableStreamUseCases", long[].class);
    public static final Key<Integer> SCALER_CROPPING_TYPE = createKey("android.scaler.croppingType", Integer.class);
    public static final Key<android.util.Size> SCALER_DEFAULT_SECURE_IMAGE_SIZE = createKey("android.scaler.defaultSecureImageSize", android.util.Size.class);
    public static final Key<android.hardware.camera2.params.MandatoryStreamCombination[]> SCALER_MANDATORY_CONCURRENT_STREAM_COMBINATIONS = createKey("android.scaler.mandatoryConcurrentStreamCombinations", android.hardware.camera2.params.MandatoryStreamCombination[].class);
    public static final Key<android.hardware.camera2.params.MandatoryStreamCombination[]> SCALER_MANDATORY_MAXIMUM_RESOLUTION_STREAM_COMBINATIONS = createKey("android.scaler.mandatoryMaximumResolutionStreamCombinations", android.hardware.camera2.params.MandatoryStreamCombination[].class);
    public static final Key<android.hardware.camera2.params.MandatoryStreamCombination[]> SCALER_MANDATORY_PREVIEW_STABILIZATION_OUTPUT_STREAM_COMBINATIONS = createKey("android.scaler.mandatoryPreviewStabilizationOutputStreamCombinations", android.hardware.camera2.params.MandatoryStreamCombination[].class);
    public static final Key<android.hardware.camera2.params.MandatoryStreamCombination[]> SCALER_MANDATORY_STREAM_COMBINATIONS = createKey("android.scaler.mandatoryStreamCombinations", android.hardware.camera2.params.MandatoryStreamCombination[].class);
    public static final Key<android.hardware.camera2.params.MandatoryStreamCombination[]> SCALER_MANDATORY_TEN_BIT_OUTPUT_STREAM_COMBINATIONS = createKey("android.scaler.mandatoryTenBitOutputStreamCombinations", android.hardware.camera2.params.MandatoryStreamCombination[].class);
    public static final Key<android.hardware.camera2.params.MandatoryStreamCombination[]> SCALER_MANDATORY_USE_CASE_STREAM_COMBINATIONS = createKey("android.scaler.mandatoryUseCaseStreamCombinations", android.hardware.camera2.params.MandatoryStreamCombination[].class);
    public static final Key<android.hardware.camera2.params.MultiResolutionStreamConfigurationMap> SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP = createKey("android.scaler.multiResolutionStreamConfigurationMap", android.hardware.camera2.params.MultiResolutionStreamConfigurationMap.class);
    public static final Key<android.hardware.camera2.params.StreamConfigurationMap> SCALER_STREAM_CONFIGURATION_MAP = createKey("android.scaler.streamConfigurationMap", android.hardware.camera2.params.StreamConfigurationMap.class);
    public static final Key<android.hardware.camera2.params.StreamConfigurationMap> SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION = createKey("android.scaler.streamConfigurationMapMaximumResolution", android.hardware.camera2.params.StreamConfigurationMap.class);
    public static final Key<int[]> SENSOR_AVAILABLE_TEST_PATTERN_MODES = createKey("android.sensor.availableTestPatternModes", int[].class);
    public static final Key<android.hardware.camera2.params.BlackLevelPattern> SENSOR_BLACK_LEVEL_PATTERN = createKey("android.sensor.blackLevelPattern", android.hardware.camera2.params.BlackLevelPattern.class);
    public static final Key<android.hardware.camera2.params.ColorSpaceTransform> SENSOR_CALIBRATION_TRANSFORM1 = createKey("android.sensor.calibrationTransform1", android.hardware.camera2.params.ColorSpaceTransform.class);
    public static final Key<android.hardware.camera2.params.ColorSpaceTransform> SENSOR_CALIBRATION_TRANSFORM2 = createKey("android.sensor.calibrationTransform2", android.hardware.camera2.params.ColorSpaceTransform.class);
    public static final Key<android.hardware.camera2.params.ColorSpaceTransform> SENSOR_COLOR_TRANSFORM1 = createKey("android.sensor.colorTransform1", android.hardware.camera2.params.ColorSpaceTransform.class);
    public static final Key<android.hardware.camera2.params.ColorSpaceTransform> SENSOR_COLOR_TRANSFORM2 = createKey("android.sensor.colorTransform2", android.hardware.camera2.params.ColorSpaceTransform.class);
    public static final Key<android.hardware.camera2.params.ColorSpaceTransform> SENSOR_FORWARD_MATRIX1 = createKey("android.sensor.forwardMatrix1", android.hardware.camera2.params.ColorSpaceTransform.class);
    public static final Key<android.hardware.camera2.params.ColorSpaceTransform> SENSOR_FORWARD_MATRIX2 = createKey("android.sensor.forwardMatrix2", android.hardware.camera2.params.ColorSpaceTransform.class);
    public static final Key<android.graphics.Rect> SENSOR_INFO_ACTIVE_ARRAY_SIZE = createKey("android.sensor.info.activeArraySize", android.graphics.Rect.class);
    public static final Key<android.graphics.Rect> SENSOR_INFO_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION = createKey("android.sensor.info.activeArraySizeMaximumResolution", android.graphics.Rect.class);
    public static final Key<android.util.Size> SENSOR_INFO_BINNING_FACTOR = createKey("android.sensor.info.binningFactor", android.util.Size.class);
    public static final Key<Integer> SENSOR_INFO_COLOR_FILTER_ARRANGEMENT = createKey("android.sensor.info.colorFilterArrangement", Integer.class);
    public static final Key<android.util.Range<Long>> SENSOR_INFO_EXPOSURE_TIME_RANGE = createKey("android.sensor.info.exposureTimeRange", android.util.Range.class);
    public static final Key<Boolean> SENSOR_INFO_LENS_SHADING_APPLIED = createKey("android.sensor.info.lensShadingApplied", Boolean.class);
    public static final Key<Long> SENSOR_INFO_MAX_FRAME_DURATION = createKey("android.sensor.info.maxFrameDuration", Long.class);
    public static final Key<android.util.SizeF> SENSOR_INFO_PHYSICAL_SIZE = createKey("android.sensor.info.physicalSize", android.util.SizeF.class);
    public static final Key<android.util.Size> SENSOR_INFO_PIXEL_ARRAY_SIZE = createKey("android.sensor.info.pixelArraySize", android.util.Size.class);
    public static final Key<android.util.Size> SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION = createKey("android.sensor.info.pixelArraySizeMaximumResolution", android.util.Size.class);
    public static final Key<android.graphics.Rect> SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE = createKey("android.sensor.info.preCorrectionActiveArraySize", android.graphics.Rect.class);
    public static final Key<android.graphics.Rect> SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION = createKey("android.sensor.info.preCorrectionActiveArraySizeMaximumResolution", android.graphics.Rect.class);
    public static final Key<android.util.Range<Integer>> SENSOR_INFO_SENSITIVITY_RANGE = createKey("android.sensor.info.sensitivityRange", android.util.Range.class);
    public static final Key<Integer> SENSOR_INFO_TIMESTAMP_SOURCE = createKey("android.sensor.info.timestampSource", Integer.class);
    public static final Key<Integer> SENSOR_INFO_WHITE_LEVEL = createKey("android.sensor.info.whiteLevel", Integer.class);
    public static final Key<Integer> SENSOR_MAX_ANALOG_SENSITIVITY = createKey("android.sensor.maxAnalogSensitivity", Integer.class);
    public static final Key<android.graphics.Rect[]> SENSOR_OPTICAL_BLACK_REGIONS = createKey("android.sensor.opticalBlackRegions", android.graphics.Rect[].class);
    public static final Key<Integer> SENSOR_ORIENTATION = createKey("android.sensor.orientation", Integer.class);
    public static final Key<Integer> SENSOR_READOUT_TIMESTAMP = createKey("android.sensor.readoutTimestamp", Integer.class);
    public static final Key<Integer> SENSOR_REFERENCE_ILLUMINANT1 = createKey("android.sensor.referenceIlluminant1", Integer.class);
    public static final Key<Byte> SENSOR_REFERENCE_ILLUMINANT2 = createKey("android.sensor.referenceIlluminant2", Byte.class);
    public static final Key<int[]> SHADING_AVAILABLE_MODES = createKey("android.shading.availableModes", int[].class);
    public static final Key<int[]> STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES = createKey("android.statistics.info.availableFaceDetectModes", int[].class);
    public static final Key<boolean[]> STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES = createKey("android.statistics.info.availableHotPixelMapModes", boolean[].class);
    public static final Key<int[]> STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES = createKey("android.statistics.info.availableLensShadingMapModes", int[].class);
    public static final Key<int[]> STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES = createKey("android.statistics.info.availableOisDataModes", int[].class);
    public static final Key<Integer> STATISTICS_INFO_MAX_FACE_COUNT = createKey("android.statistics.info.maxFaceCount", Integer.class);
    public static final Key<Integer> SYNC_MAX_LATENCY = createKey("android.sync.maxLatency", Integer.class);
    public static final Key<int[]> TONEMAP_AVAILABLE_TONE_MAP_MODES = createKey("android.tonemap.availableToneMapModes", int[].class);
    public static final Key<Integer> TONEMAP_MAX_CURVE_POINTS = createKey("android.tonemap.maxCurvePoints", Integer.class);
}
