// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EncoderProfiles {
    private final int mDurationSecs;
    private final int mFileFormat;
    private final List<AudioProfile> mAudioProfiles;
    private final List<VideoProfile> mVideoProfiles;

    EncoderProfiles(
            int duration,
            int fileFormat,
            List<VideoProfile> videoProfiles,
            List<AudioProfile> audioProfiles) {
        mDurationSecs = duration;
        mFileFormat = fileFormat;
        mVideoProfiles = Collections.unmodifiableList(new ArrayList<VideoProfile>(videoProfiles));
        mAudioProfiles = Collections.unmodifiableList(new ArrayList<AudioProfile>(audioProfiles));
    }

    public int getDefaultDurationSeconds() {
        return mDurationSecs;
    }

    public int getRecommendedFileFormat() {
        return mFileFormat;
    }

    public List<AudioProfile> getAudioProfiles() {
        return mAudioProfiles;
    }

    public List<VideoProfile> getVideoProfiles() {
        return mVideoProfiles;
    }

    public static final class VideoProfile {
        public static final int HDR_NONE = 0;
        public static final int HDR_HLG = 1;
        public static final int HDR_HDR10 = 2;
        public static final int HDR_HDR10PLUS = 3;
        public static final int HDR_DOLBY_VISION = 4;

        public static final int YUV_420 = 0;
        public static final int YUV_422 = 1;
        public static final int YUV_444 = 2;

        private final int mCodec;
        private final int mWidth;
        private final int mHeight;
        private final int mFrameRate;
        private final int mBitrate;
        private final int mProfile;
        private final int mChromaSubsampling;
        private final int mBitDepth;
        private final int mHdrFormat;

        VideoProfile(
                int codec,
                int width,
                int height,
                int frameRate,
                int bitrate,
                int profile,
                int chromaSubsampling,
                int bitDepth,
                int hdrFormat) {
            mCodec = codec;
            mWidth = width;
            mHeight = height;
            mFrameRate = frameRate;
            mBitrate = bitrate;
            mProfile = profile;
            mChromaSubsampling = chromaSubsampling;
            mBitDepth = bitDepth;
            mHdrFormat = hdrFormat;
        }

        public int getCodec() {
            return mCodec;
        }

        public String getMediaType() {
            switch (mCodec) {
                case MediaRecorder.VideoEncoder.H263:
                    return MediaFormat.MIMETYPE_VIDEO_H263;
                case MediaRecorder.VideoEncoder.H264:
                    return MediaFormat.MIMETYPE_VIDEO_AVC;
                case MediaRecorder.VideoEncoder.MPEG_4_SP:
                    return MediaFormat.MIMETYPE_VIDEO_MPEG4;
                case MediaRecorder.VideoEncoder.VP8:
                    return MediaFormat.MIMETYPE_VIDEO_VP8;
                case MediaRecorder.VideoEncoder.HEVC:
                    return MediaFormat.MIMETYPE_VIDEO_HEVC;
                case MediaRecorder.VideoEncoder.VP9:
                    return MediaFormat.MIMETYPE_VIDEO_VP9;
                case MediaRecorder.VideoEncoder.DOLBY_VISION:
                    return MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION;
                case MediaRecorder.VideoEncoder.AV1:
                    return MediaFormat.MIMETYPE_VIDEO_AV1;
                default:
                    return MediaFormat.MIMETYPE_VIDEO_AVC;
            }
        }

        public int getBitrate() {
            return mBitrate;
        }

        public int getFrameRate() {
            return mFrameRate;
        }

        public int getWidth() {
            return mWidth;
        }

        public int getHeight() {
            return mHeight;
        }

        public int getProfile() {
            return mProfile;
        }

        public int getBitDepth() {
            return mBitDepth;
        }

        public int getChromaSubsampling() {
            return mChromaSubsampling;
        }

        public int getHdrFormat() {
            return mHdrFormat;
        }
    }

    public static final class AudioProfile {
        private final int mCodec;
        private final int mChannels;
        private final int mSampleRate;
        private final int mBitrate;
        private final int mProfile;

        AudioProfile(int codec, int channels, int sampleRate, int bitrate, int profile) {
            mCodec = codec;
            mChannels = channels;
            mSampleRate = sampleRate;
            mBitrate = bitrate;
            mProfile = profile;
        }

        public int getCodec() {
            return mCodec;
        }

        public String getMediaType() {
            switch (mCodec) {
                case MediaRecorder.AudioEncoder.AMR_NB:
                    return MediaFormat.MIMETYPE_AUDIO_AMR_NB;
                case MediaRecorder.AudioEncoder.AMR_WB:
                    return MediaFormat.MIMETYPE_AUDIO_AMR_WB;
                case MediaRecorder.AudioEncoder.AAC:
                case MediaRecorder.AudioEncoder.HE_AAC:
                case MediaRecorder.AudioEncoder.AAC_ELD:
                    return MediaFormat.MIMETYPE_AUDIO_AAC;
                case MediaRecorder.AudioEncoder.VORBIS:
                    return MediaFormat.MIMETYPE_AUDIO_VORBIS;
                case MediaRecorder.AudioEncoder.OPUS:
                    return MediaFormat.MIMETYPE_AUDIO_OPUS;
                default:
                    return MediaFormat.MIMETYPE_AUDIO_AAC;
            }
        }

        public int getBitrate() {
            return mBitrate;
        }

        public int getSampleRate() {
            return mSampleRate;
        }

        public int getChannels() {
            return mChannels;
        }

        public int getProfile() {
            return mProfile;
        }
    }
}
