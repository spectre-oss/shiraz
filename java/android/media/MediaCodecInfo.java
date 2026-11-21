// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.util.Range;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MediaCodecInfo {
    private final boolean encoder;
    private final String videoMime;

    MediaCodecInfo(boolean encoder) {
        this.encoder = encoder;
        this.videoMime = null;
    }

    MediaCodecInfo(String mime) {
        this.encoder = false;
        this.videoMime = mime;
    }

    public String getName() {
        return videoMime != null
                ? "OMX.shiraz.system." + videoMime.substring(videoMime.indexOf('/') + 1)
                : encoder ? "OMX.shiraz.aac.encoder" : "OMX.shiraz.aac.decoder";
    }

    public String getCanonicalName() {
        return getName();
    }

    public boolean isEncoder() {
        return encoder;
    }

    public boolean isAlias() {
        return false;
    }

    public boolean isHardwareAccelerated() {
        return false;
    }

    public boolean isSoftwareOnly() {
        return videoMime == null || !videoMime.startsWith("video/");
    }

    public boolean isVendor() {
        return false;
    }

    public String[] getSupportedTypes() {
        return new String[]{videoMime != null ? videoMime : "audio/mp4a-latm"};
    }

    public CodecCapabilities getCapabilitiesForType(String type) {
        if (!(videoMime != null ? videoMime : "audio/mp4a-latm").equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("codec MIME");
        }
        return new CodecCapabilities(videoMime, encoder);
    }

    public static final class CodecProfileLevel {
        public static final int AACObjectMain = 1;
        public static final int AACObjectLC = 2;
        public static final int AACObjectSSR = 3;
        public static final int AACObjectLTP = 4;
        public static final int AACObjectHE = 5;
        public static final int AACObjectScalable = 6;
        public static final int AACObjectERLC = 17;
        public static final int AACObjectLD = 23;
        public static final int AACObjectHE_PS = 29;
        public static final int AACObjectELD = 39;
        public static final int AACObjectXHE = 42;
        public static final int AVCProfileBaseline = 0x01;
        public static final int AVCProfileMain = 0x02;
        public static final int AVCProfileExtended = 0x04;
        public static final int AVCProfileHigh = 0x08;
        public static final int AVCProfileHigh10 = 0x10;
        public static final int AVCProfileHigh422 = 0x20;
        public static final int AVCProfileHigh444 = 0x40;
        public static final int AVCProfileConstrainedBaseline = 0x10000;
        public static final int AVCProfileConstrainedHigh = 0x80000;
        public static final int AVCLevel1 = 0x01;
        public static final int AVCLevel1b = 0x02;
        public static final int AVCLevel11 = 0x04;
        public static final int AVCLevel12 = 0x08;
        public static final int AVCLevel13 = 0x10;
        public static final int AVCLevel2 = 0x20;
        public static final int AVCLevel21 = 0x40;
        public static final int AVCLevel22 = 0x80;
        public static final int AVCLevel3 = 0x100;
        public static final int AVCLevel31 = 0x200;
        public static final int AVCLevel32 = 0x400;
        public static final int AVCLevel4 = 0x800;
        public static final int AVCLevel41 = 0x1000;
        public static final int AVCLevel42 = 0x2000;
        public static final int AVCLevel5 = 0x4000;
        public static final int AVCLevel51 = 0x8000;
        public static final int AVCLevel52 = 0x10000;
        public static final int AVCLevel6 = 0x20000;
        public static final int AVCLevel61 = 0x40000;
        public static final int AVCLevel62 = 0x80000;
        public static final int HEVCProfileMain = 0x01;
        public static final int HEVCProfileMain10 = 0x02;
        public static final int HEVCProfileMainStill = 0x04;
        public static final int HEVCProfileMain10HDR10 = 0x1000;
        public static final int HEVCProfileMain10HDR10Plus = 0x2000;
        public static final int VP8ProfileMain = 0x01;
        public static final int VP9Profile0 = 0x01;
        public static final int VP9Profile1 = 0x02;
        public static final int VP9Profile2 = 0x04;
        public static final int VP9Profile3 = 0x08;
        public static final int AV1ProfileMain8 = 0x01;
        public static final int AV1ProfileMain10 = 0x02;

        public int profile;
        public int level;

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CodecProfileLevel)) {
                return false;
            }
            CodecProfileLevel other = (CodecProfileLevel) obj;
            return other.profile == profile && other.level == level;
        }

        @Override
        public int hashCode() {
            return 31 * profile + level;
        }
    }

    public static final class CodecCapabilities {
        public static final int COLOR_FormatMonochrome = 1;
        public static final int COLOR_Format8bitRGB332 = 2;
        public static final int COLOR_Format12bitRGB444 = 3;
        public static final int COLOR_Format16bitARGB4444 = 4;
        public static final int COLOR_Format16bitARGB1555 = 5;
        public static final int COLOR_Format16bitRGB565 = 6;
        public static final int COLOR_Format16bitBGR565 = 7;
        public static final int COLOR_Format18bitRGB666 = 8;
        public static final int COLOR_Format18bitARGB1665 = 9;
        public static final int COLOR_Format19bitARGB1666 = 10;
        public static final int COLOR_Format24bitRGB888 = 11;
        public static final int COLOR_Format24bitBGR888 = 12;
        public static final int COLOR_Format24bitARGB1887 = 13;
        public static final int COLOR_Format25bitARGB1888 = 14;
        public static final int COLOR_Format32bitBGRA8888 = 15;
        public static final int COLOR_Format32bitARGB8888 = 16;
        public static final int COLOR_FormatYUV411Planar = 17;
        public static final int COLOR_FormatYUV411PackedPlanar = 18;
        public static final int COLOR_FormatYUV420Planar = 19;
        public static final int COLOR_FormatYUV420PackedPlanar = 20;
        public static final int COLOR_FormatYUV420SemiPlanar = 21;
        public static final int COLOR_FormatYUV422Planar = 22;
        public static final int COLOR_FormatYUV422PackedPlanar = 23;
        public static final int COLOR_FormatYUV422SemiPlanar = 24;
        public static final int COLOR_FormatYCbYCr = 25;
        public static final int COLOR_FormatYCrYCb = 26;
        public static final int COLOR_FormatCbYCrY = 27;
        public static final int COLOR_FormatCrYCbY = 28;
        public static final int COLOR_FormatYUV444Interleaved = 29;
        public static final int COLOR_FormatRawBayer8bit = 30;
        public static final int COLOR_FormatRawBayer10bit = 31;
        public static final int COLOR_FormatRawBayer8bitcompressed = 32;
        public static final int COLOR_FormatL2 = 33;
        public static final int COLOR_FormatL4 = 34;
        public static final int COLOR_FormatL8 = 35;
        public static final int COLOR_FormatL16 = 36;
        public static final int COLOR_FormatL24 = 37;
        public static final int COLOR_FormatL32 = 38;
        public static final int COLOR_FormatYUV420PackedSemiPlanar = 39;
        public static final int COLOR_FormatYUV422PackedSemiPlanar = 40;
        public static final int COLOR_Format18BitBGR666 = 41;
        public static final int COLOR_Format24BitARGB6666 = 42;
        public static final int COLOR_Format24BitABGR6666 = 43;
        public static final int COLOR_FormatYUVP010 = 54;
        public static final int COLOR_TI_FormatYUV420PackedSemiPlanar = 0x7f000100;
        public static final int COLOR_FormatSurface = 0x7F000789;
        public static final int COLOR_Format32bitABGR8888 = 0x7F00A000;
        public static final int COLOR_FormatYUV420Flexible = 0x7F420888;
        public static final int COLOR_FormatYUV422Flexible = 0x7F422888;
        public static final int COLOR_FormatYUV444Flexible = 0x7F444888;
        public static final int COLOR_FormatRGBFlexible = 0x7F36B888;
        public static final int COLOR_FormatRGBAFlexible = 0x7F36A888;
        public static final int COLOR_Format32bitABGR2101010 = 0x7F00AAA2;
        public static final int COLOR_QCOM_FormatYUV420SemiPlanar = 0x7fa30c00;

        public static final String FEATURE_AdaptivePlayback = "adaptive-playback";
        public static final String FEATURE_SecurePlayback = "secure-playback";
        public static final String FEATURE_TunneledPlayback = "tunneled-playback";
        public static final String FEATURE_DynamicTimestamp = "dynamic-timestamp";
        public static final String FEATURE_FrameParsing = "frame-parsing";
        public static final String FEATURE_MultipleFrames = "multiple-frames";
        public static final String FEATURE_PartialFrame = "partial-frame";
        public static final String FEATURE_IntraRefresh = "intra-refresh";
        public static final String FEATURE_LowLatency = "low-latency";
        public static final String FEATURE_QpBounds = "qp-bounds";
        public static final String FEATURE_EncodingStatistics = "encoding-statistics";
        public static final String FEATURE_HdrEditing = "hdr-editing";
        public static final String FEATURE_HlgEditing = "hlg-editing";
        public static final String FEATURE_DynamicColorAspects = "dynamic-color-aspects";
        public static final String FEATURE_Roi = "region-of-interest";
        public static final String FEATURE_DetachedSurface = "detached-surface";

        private final String mime;
        private final boolean isEncoder;
        public CodecProfileLevel[] profileLevels;
        public int[] colorFormats = new int[0];

        public CodecCapabilities() {
            this("audio/mp4a-latm", false);
        }

        CodecCapabilities(String type) {
            this(type, false);
        }

        CodecCapabilities(String type, boolean isEncoder) {
            this.mime = type == null ? "audio/mp4a-latm" : type;
            this.isEncoder = isEncoder;
            this.profileLevels = new CodecProfileLevel[0];
            if (type == null) {
                CodecProfileLevel p = new CodecProfileLevel();
                p.profile = CodecProfileLevel.AACObjectLC;
                this.profileLevels = new CodecProfileLevel[]{p};
            }
            if (this.mime.startsWith("video/")) {
                this.colorFormats = new int[]{COLOR_FormatYUV420Flexible};
            }
        }

        public boolean isFeatureSupported(String feature) {
            return false;
        }

        public boolean isFeatureRequired(String feature) {
            return false;
        }

        public String getMimeType() {
            return mime;
        }

        public MediaFormat getDefaultFormat() {
            if (mime.startsWith("video/")) {
                return MediaFormat.createVideoFormat(mime, 1920, 1080);
            }
            return MediaFormat.createAudioFormat(mime, 44100, 2);
        }

        public VideoCapabilities getVideoCapabilities() {
            return mime.startsWith("video/") ? new VideoCapabilities() : null;
        }

        public AudioCapabilities getAudioCapabilities() {
            return mime.startsWith("audio/") ? new AudioCapabilities() : null;
        }

        public EncoderCapabilities getEncoderCapabilities() {
            return isEncoder ? new EncoderCapabilities() : null;
        }

        public boolean isFormatSupported(MediaFormat format) {
            if (format == null || !mime.equalsIgnoreCase(format.getString("mime"))) {
                return false;
            }
            if (mime.startsWith("video/")) {
                int w = format.getInteger("width", 1);
                int h = format.getInteger("height", 1);
                return new VideoCapabilities().isSizeSupported(w, h);
            }
            int channels = format.getInteger("channel-count", 1);
            int rate = format.getInteger("sample-rate", 48000);
            return channels >= 1 && channels <= 2
                    && (mime.equals("audio/opus")
                            ? rate == 48000
                            : new AudioCapabilities().isSampleRateSupported(rate));
        }

        public int getMaxSupportedInstances() {
            return 16;
        }

        public static CodecCapabilities createFromProfileLevel(String mime, int profile, int level) {
            if (mime == null) {
                return null;
            }
            CodecCapabilities caps = new CodecCapabilities(mime, false);
            CodecProfileLevel pl = new CodecProfileLevel();
            pl.profile = profile;
            pl.level = level;
            caps.profileLevels = new CodecProfileLevel[]{pl};
            return caps;
        }
    }

    public static final class EncoderCapabilities {
        public static final int BITRATE_MODE_CQ = 0;
        public static final int BITRATE_MODE_VBR = 1;
        public static final int BITRATE_MODE_CBR = 2;
        public static final int BITRATE_MODE_CBR_FD = 3;

        EncoderCapabilities() {
        }

        public Range<Integer> getQualityRange() {
            return new Range<Integer>(0, 100);
        }

        public Range<Integer> getComplexityRange() {
            return new Range<Integer>(0, 10);
        }

        public boolean isBitrateModeSupported(int mode) {
            return mode == BITRATE_MODE_CBR || mode == BITRATE_MODE_VBR;
        }
    }

    public static final class VideoCapabilities {
        public static final class PerformancePoint {
            public static final PerformancePoint SD_24 = new PerformancePoint(720, 480, 24);
            public static final PerformancePoint SD_25 = new PerformancePoint(720, 576, 25);
            public static final PerformancePoint SD_30 = new PerformancePoint(720, 480, 30);
            public static final PerformancePoint SD_48 = new PerformancePoint(720, 480, 48);
            public static final PerformancePoint SD_50 = new PerformancePoint(720, 576, 50);
            public static final PerformancePoint SD_60 = new PerformancePoint(720, 480, 60);
            public static final PerformancePoint HD_24 = new PerformancePoint(1280, 720, 24);
            public static final PerformancePoint HD_25 = new PerformancePoint(1280, 720, 25);
            public static final PerformancePoint HD_30 = new PerformancePoint(1280, 720, 30);
            public static final PerformancePoint HD_50 = new PerformancePoint(1280, 720, 50);
            public static final PerformancePoint HD_60 = new PerformancePoint(1280, 720, 60);
            public static final PerformancePoint HD_100 = new PerformancePoint(1280, 720, 100);
            public static final PerformancePoint HD_120 = new PerformancePoint(1280, 720, 120);
            public static final PerformancePoint HD_200 = new PerformancePoint(1280, 720, 200);
            public static final PerformancePoint HD_240 = new PerformancePoint(1280, 720, 240);
            public static final PerformancePoint FHD_24 = new PerformancePoint(1920, 1080, 24);
            public static final PerformancePoint FHD_25 = new PerformancePoint(1920, 1080, 25);
            public static final PerformancePoint FHD_30 = new PerformancePoint(1920, 1080, 30);
            public static final PerformancePoint FHD_50 = new PerformancePoint(1920, 1080, 50);
            public static final PerformancePoint FHD_60 = new PerformancePoint(1920, 1080, 60);
            public static final PerformancePoint FHD_100 = new PerformancePoint(1920, 1080, 100);
            public static final PerformancePoint FHD_120 = new PerformancePoint(1920, 1080, 120);
            public static final PerformancePoint FHD_200 = new PerformancePoint(1920, 1080, 200);
            public static final PerformancePoint FHD_240 = new PerformancePoint(1920, 1080, 240);
            public static final PerformancePoint UHD_24 = new PerformancePoint(3840, 2160, 24);
            public static final PerformancePoint UHD_25 = new PerformancePoint(3840, 2160, 25);
            public static final PerformancePoint UHD_30 = new PerformancePoint(3840, 2160, 30);
            public static final PerformancePoint UHD_50 = new PerformancePoint(3840, 2160, 50);
            public static final PerformancePoint UHD_60 = new PerformancePoint(3840, 2160, 60);
            public static final PerformancePoint UHD_100 = new PerformancePoint(3840, 2160, 100);
            public static final PerformancePoint UHD_120 = new PerformancePoint(3840, 2160, 120);
            public static final PerformancePoint UHD_200 = new PerformancePoint(3840, 2160, 200);
            public static final PerformancePoint UHD_240 = new PerformancePoint(3840, 2160, 240);

            private final int mWidth;
            private final int mHeight;
            private final int mMaxFrameRate;

            public PerformancePoint(int width, int height, int frameRate) {
                if (width <= 0 || height <= 0 || frameRate <= 0) {
                    throw new IllegalArgumentException("PerformancePoint dimensions/frameRate must be > 0");
                }
                mWidth = width;
                mHeight = height;
                mMaxFrameRate = frameRate;
            }

            public boolean covers(MediaFormat format) {
                Objects.requireNonNull(format, "format");
                int w = format.getInteger("width", 0);
                int h = format.getInteger("height", 0);
                int fps = Math.max(1, format.getInteger("frame-rate", 30));
                if (w <= 0 || h <= 0) {
                    return false;
                }
                return covers(new PerformancePoint(w, h, fps));
            }

            public boolean covers(PerformancePoint other) {
                Objects.requireNonNull(other, "other");
                long myBlocks = (long) ((mWidth + 15) / 16) * ((mHeight + 15) / 16);
                long otherBlocks = (long) ((other.mWidth + 15) / 16) * ((other.mHeight + 15) / 16);
                return myBlocks >= otherBlocks
                        && mMaxFrameRate >= other.mMaxFrameRate
                        && myBlocks * mMaxFrameRate >= otherBlocks * other.mMaxFrameRate;
            }

            @Override
            public String toString() {
                return "PerformancePoint(" + mWidth + "x" + mHeight + "@" + mMaxFrameRate + ")";
            }

            @Override
            public int hashCode() {
                return Objects.hash(Integer.valueOf(mWidth), Integer.valueOf(mHeight), Integer.valueOf(mMaxFrameRate));
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof PerformancePoint)) {
                    return false;
                }
                PerformancePoint other = (PerformancePoint) o;
                return mWidth == other.mWidth && mHeight == other.mHeight && mMaxFrameRate == other.mMaxFrameRate;
            }
        }

        public Range<Integer> getBitrateRange() {
            return new Range<Integer>(1, 100000000);
        }

        public Range<Integer> getSupportedWidths() {
            return new Range<Integer>(1, 8192);
        }

        public Range<Integer> getSupportedHeights() {
            return new Range<Integer>(1, 8192);
        }

        public Range<Integer> getSupportedFrameRates() {
            return new Range<Integer>(1, 120);
        }

        public int getWidthAlignment() {
            return 1;
        }

        public int getHeightAlignment() {
            return 1;
        }

        public Range<Integer> getSupportedWidthsFor(int height) {
            if (height <= 0 || height > 8192) {
                throw new IllegalArgumentException("Unsupported height: " + height);
            }
            int maxW = (int) Math.min(8192L, (16L * 1024 * 1024) / height);
            return new Range<Integer>(1, Math.max(1, maxW));
        }

        public Range<Integer> getSupportedHeightsFor(int width) {
            if (width <= 0 || width > 8192) {
                throw new IllegalArgumentException("Unsupported width: " + width);
            }
            int maxH = (int) Math.min(8192L, (16L * 1024 * 1024) / width);
            return new Range<Integer>(1, Math.max(1, maxH));
        }

        public Range<Double> getSupportedFrameRatesFor(int width, int height) {
            if (!isSizeSupported(width, height)) {
                throw new IllegalArgumentException("Unsupported size: " + width + "x" + height);
            }
            return new Range<Double>(1.0, 120.0);
        }

        public Range<Double> getAchievableFrameRatesFor(int width, int height) {
            if (!isSizeSupported(width, height)) {
                throw new IllegalArgumentException("Unsupported size: " + width + "x" + height);
            }
            return new Range<Double>(1.0, 60.0);
        }

        public List<PerformancePoint> getSupportedPerformancePoints() {
            return Collections.singletonList(PerformancePoint.UHD_60);
        }

        public boolean isSizeSupported(int w, int h) {
            return w > 0 && h > 0 && w <= 8192 && h <= 8192 && (long) w * h <= 16 * 1024 * 1024;
        }

        public boolean areSizeAndRateSupported(int w, int h, double rate) {
            return isSizeSupported(w, h) && rate > 0 && rate <= 120;
        }
    }

    public static final class AudioCapabilities {
        private static final int[] SAMPLE_RATES = {
            8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000
        };

        public Range<Integer> getBitrateRange() {
            return new Range<Integer>(8000, 320000);
        }

        public int[] getSupportedSampleRates() {
            return Arrays.copyOf(SAMPLE_RATES, SAMPLE_RATES.length);
        }

        @SuppressWarnings("unchecked")
        public Range<Integer>[] getSupportedSampleRateRanges() {
            Range<Integer>[] ranges = (Range<Integer>[]) new Range<?>[SAMPLE_RATES.length];
            for (int i = 0; i < SAMPLE_RATES.length; i++) {
                ranges[i] = new Range<Integer>(SAMPLE_RATES[i], SAMPLE_RATES[i]);
            }
            return ranges;
        }

        public int getMinInputChannelCount() {
            return 1;
        }

        public int getMaxInputChannelCount() {
            return 2;
        }

        @SuppressWarnings("unchecked")
        public Range<Integer>[] getInputChannelCountRanges() {
            return (Range<Integer>[]) new Range<?>[]{new Range<Integer>(1, 2)};
        }

        public boolean isSampleRateSupported(int rate) {
            return rate >= 8000 && rate <= 48000 && MediaCodec.rateIndex(rate) >= 0;
        }
    }
}
