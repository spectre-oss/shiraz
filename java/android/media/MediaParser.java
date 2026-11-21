// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.media.metrics.LogSessionId;
import android.util.Pair;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MediaParser {
    public static final String PARAMETER_ADTS_ENABLE_CBR_SEEKING = "android.media.mediaparser.adts.enableCbrSeeking";
    public static final String PARAMETER_AMR_ENABLE_CBR_SEEKING = "android.media.mediaparser.amr.enableCbrSeeking";
    public static final String PARAMETER_FLAC_DISABLE_ID3 = "android.media.mediaparser.flac.disableId3";
    public static final String PARAMETER_MATROSKA_DISABLE_CUES_SEEKING = "android.media.mediaparser.matroska.disableCuesSeeking";
    public static final String PARAMETER_MP3_DISABLE_ID3 = "android.media.mediaparser.mp3.disableId3";
    public static final String PARAMETER_MP3_ENABLE_CBR_SEEKING = "android.media.mediaparser.mp3.enableCbrSeeking";
    public static final String PARAMETER_MP3_ENABLE_INDEX_SEEKING = "android.media.mediaparser.mp3.enableIndexSeeking";
    public static final String PARAMETER_MP4_IGNORE_EDIT_LISTS = "android.media.mediaparser.mp4.ignoreEditLists";
    public static final String PARAMETER_MP4_IGNORE_TFDT_BOX = "android.media.mediaparser.mp4.ignoreTfdtBox";
    public static final String PARAMETER_MP4_TREAT_VIDEO_FRAMES_AS_KEYFRAMES = "android.media.mediaparser.mp4.treatVideoFramesAsKeyframes";
    public static final String PARAMETER_TS_ALLOW_NON_IDR_AVC_KEYFRAMES = "android.media.mediaparser.ts.allowNonIdrAvcKeyframes";
    public static final String PARAMETER_TS_DETECT_ACCESS_UNITS = "android.media.mediaparser.ts.ignoreDetectAccessUnits";
    public static final String PARAMETER_TS_ENABLE_HDMV_DTS_AUDIO_STREAMS = "android.media.mediaparser.ts.enableHdmvDtsAudioStreams";
    public static final String PARAMETER_TS_IGNORE_AAC_STREAM = "android.media.mediaparser.ts.ignoreAacStream";
    public static final String PARAMETER_TS_IGNORE_AVC_STREAM = "android.media.mediaparser.ts.ignoreAvcStream";
    public static final String PARAMETER_TS_IGNORE_SPLICE_INFO_STREAM = "android.media.mediaparser.ts.ignoreSpliceInfoStream";
    public static final String PARAMETER_TS_MODE = "android.media.mediaparser.ts.mode";

    public static final String PARSER_NAME_UNKNOWN = "android.media.mediaparser.UNKNOWN";
    public static final String PARSER_NAME_MATROSKA = "android.media.mediaparser.MatroskaParser";
    public static final String PARSER_NAME_FMP4 = "android.media.mediaparser.FragmentedMp4Parser";
    public static final String PARSER_NAME_MP4 = "android.media.mediaparser.Mp4Parser";
    public static final String PARSER_NAME_MP3 = "android.media.mediaparser.Mp3Parser";
    public static final String PARSER_NAME_ADTS = "android.media.mediaparser.AdtsParser";
    public static final String PARSER_NAME_AC3 = "android.media.mediaparser.Ac3Parser";
    public static final String PARSER_NAME_TS = "android.media.mediaparser.TsParser";
    public static final String PARSER_NAME_FLV = "android.media.mediaparser.FlvParser";
    public static final String PARSER_NAME_OGG = "android.media.mediaparser.OggParser";
    public static final String PARSER_NAME_PS = "android.media.mediaparser.PsParser";
    public static final String PARSER_NAME_WAV = "android.media.mediaparser.WavParser";
    public static final String PARSER_NAME_AMR = "android.media.mediaparser.AmrParser";
    public static final String PARSER_NAME_AC4 = "android.media.mediaparser.Ac4Parser";
    public static final String PARSER_NAME_FLAC = "android.media.mediaparser.FlacParser";

    public static final int SAMPLE_FLAG_KEY_FRAME = 1;
    public static final int SAMPLE_FLAG_HAS_SUPPLEMENTAL_DATA = 1 << 28;
    public static final int SAMPLE_FLAG_LAST_SAMPLE = 1 << 29;
    public static final int SAMPLE_FLAG_ENCRYPTED = 1 << 30;
    public static final int SAMPLE_FLAG_DECODE_ONLY = Integer.MIN_VALUE;

    public interface InputReader {
        int read(byte[] buffer, int offset, int readLength) throws IOException;
        long getPosition();
        long getLength();
    }

    public interface SeekableInputReader extends InputReader {
        void seekToPosition(long position);
    }

    public interface OutputConsumer {
        void onSeekMapFound(SeekMap seekMap);
        void onTrackCountFound(int numberOfTracks);
        void onTrackDataFound(int trackIndex, TrackData trackData);
        void onSampleDataFound(int trackIndex, InputReader sampleData) throws IOException;
        void onSampleCompleted(int trackIndex, long timeMicros, int flags, int size, int offset,
                MediaCodec.CryptoInfo cryptoInfo);
    }

    public static final class SeekPoint {
        public static final SeekPoint START = new SeekPoint(0L, 0L);

        public final long timeMicros;
        public final long position;

        SeekPoint(long timeMicros, long position) {
            this.timeMicros = timeMicros;
            this.position = position;
        }

        @Override
        public String toString() {
            return "[timeMicros=" + timeMicros + ", position=" + position + "]";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SeekPoint)) {
                return false;
            }
            SeekPoint other = (SeekPoint) obj;
            return timeMicros == other.timeMicros && position == other.position;
        }

        @Override
        public int hashCode() {
            return 31 * (int) timeMicros + (int) position;
        }
    }

    public static final class SeekMap {
        public static final int UNKNOWN_DURATION = Integer.MIN_VALUE;

        private final long mDurationMicros;
        private final boolean mSeekable;

        SeekMap(long durationMicros, boolean seekable) {
            mDurationMicros = durationMicros;
            mSeekable = seekable;
        }

        public boolean isSeekable() {
            return mSeekable;
        }

        public long getDurationMicros() {
            return mDurationMicros;
        }

        public Pair<SeekPoint, SeekPoint> getSeekPoints(long timeMicros) {
            return new Pair<SeekPoint, SeekPoint>(SeekPoint.START, SeekPoint.START);
        }
    }

    public static final class TrackData {
        public final MediaFormat mediaFormat;
        public final DrmInitData drmInitData;

        TrackData(MediaFormat mediaFormat, DrmInitData drmInitData) {
            this.mediaFormat = mediaFormat;
            this.drmInitData = drmInitData;
        }
    }

    public static final class ParsingException extends IOException {
        ParsingException(String message) {
            super(message);
        }

        ParsingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class UnrecognizedInputFormatException extends IOException {
        UnrecognizedInputFormatException(String message) {
            super(message);
        }
    }

    private static final Set<String> KNOWN_PARAMETERS = new HashSet<String>();
    private static final Set<String> KNOWN_PARSERS = new HashSet<String>();

    static {
        KNOWN_PARAMETERS.add(PARAMETER_ADTS_ENABLE_CBR_SEEKING);
        KNOWN_PARAMETERS.add(PARAMETER_AMR_ENABLE_CBR_SEEKING);
        KNOWN_PARAMETERS.add(PARAMETER_FLAC_DISABLE_ID3);
        KNOWN_PARAMETERS.add(PARAMETER_MATROSKA_DISABLE_CUES_SEEKING);
        KNOWN_PARAMETERS.add(PARAMETER_MP3_DISABLE_ID3);
        KNOWN_PARAMETERS.add(PARAMETER_MP3_ENABLE_CBR_SEEKING);
        KNOWN_PARAMETERS.add(PARAMETER_MP3_ENABLE_INDEX_SEEKING);
        KNOWN_PARAMETERS.add(PARAMETER_MP4_IGNORE_EDIT_LISTS);
        KNOWN_PARAMETERS.add(PARAMETER_MP4_IGNORE_TFDT_BOX);
        KNOWN_PARAMETERS.add(PARAMETER_MP4_TREAT_VIDEO_FRAMES_AS_KEYFRAMES);
        KNOWN_PARAMETERS.add(PARAMETER_TS_ALLOW_NON_IDR_AVC_KEYFRAMES);
        KNOWN_PARAMETERS.add(PARAMETER_TS_DETECT_ACCESS_UNITS);
        KNOWN_PARAMETERS.add(PARAMETER_TS_ENABLE_HDMV_DTS_AUDIO_STREAMS);
        KNOWN_PARAMETERS.add(PARAMETER_TS_IGNORE_AAC_STREAM);
        KNOWN_PARAMETERS.add(PARAMETER_TS_IGNORE_AVC_STREAM);
        KNOWN_PARAMETERS.add(PARAMETER_TS_IGNORE_SPLICE_INFO_STREAM);
        KNOWN_PARAMETERS.add(PARAMETER_TS_MODE);

        KNOWN_PARSERS.add(PARSER_NAME_MATROSKA);
        KNOWN_PARSERS.add(PARSER_NAME_FMP4);
        KNOWN_PARSERS.add(PARSER_NAME_MP4);
        KNOWN_PARSERS.add(PARSER_NAME_MP3);
        KNOWN_PARSERS.add(PARSER_NAME_ADTS);
        KNOWN_PARSERS.add(PARSER_NAME_AC3);
        KNOWN_PARSERS.add(PARSER_NAME_TS);
        KNOWN_PARSERS.add(PARSER_NAME_FLV);
        KNOWN_PARSERS.add(PARSER_NAME_OGG);
        KNOWN_PARSERS.add(PARSER_NAME_PS);
        KNOWN_PARSERS.add(PARSER_NAME_WAV);
        KNOWN_PARSERS.add(PARSER_NAME_AMR);
        KNOWN_PARSERS.add(PARSER_NAME_AC4);
        KNOWN_PARSERS.add(PARSER_NAME_FLAC);
    }

    private final OutputConsumer mOutputConsumer;
    private final Map<String, Object> mParameters = new HashMap<String, Object>();
    private String mParserName;
    private MediaExtractor mExtractor;
    private boolean mInitialized;
    private boolean mHasMoreSamples;
    private boolean mReleased;
    private LogSessionId mLogSessionId = LogSessionId.LOG_SESSION_ID_NONE;

    private MediaParser(OutputConsumer outputConsumer, String parserName) {
        mOutputConsumer = Objects.requireNonNull(outputConsumer, "outputConsumer");
        mParserName = parserName;
    }

    public static MediaParser createByName(String name, OutputConsumer outputConsumer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(outputConsumer, "outputConsumer");
        if (!KNOWN_PARSERS.contains(name)) {
            throw new IllegalArgumentException("Unknown MediaParser name: " + name);
        }
        return new MediaParser(outputConsumer, name);
    }

    public static MediaParser create(OutputConsumer outputConsumer, String... parserNames) {
        Objects.requireNonNull(outputConsumer, "outputConsumer");
        if (parserNames != null) {
            for (int i = 0; i < parserNames.length; i++) {
                if (!KNOWN_PARSERS.contains(parserNames[i])) {
                    throw new IllegalArgumentException("Unknown MediaParser name: " + parserNames[i]);
                }
            }
        }
        return new MediaParser(outputConsumer,
                (parserNames != null && parserNames.length == 1) ? parserNames[0] : PARSER_NAME_UNKNOWN);
    }

    public static List<String> getParserNames(MediaFormat mediaFormat) {
        Objects.requireNonNull(mediaFormat, "mediaFormat");
        String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
        if (mime == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>();
        if ("video/mp4".equals(mime) || "audio/mp4".equals(mime)) {
            names.add(PARSER_NAME_MP4);
            names.add(PARSER_NAME_FMP4);
        } else if ("audio/raw".equals(mime) || "audio/wav".equals(mime) || "audio/x-wav".equals(mime)) {
            names.add(PARSER_NAME_WAV);
        } else if ("audio/aac".equals(mime) || "audio/mp4a-latm".equals(mime)) {
            names.add(PARSER_NAME_ADTS);
        } else if ("audio/mpeg".equals(mime)) {
            names.add(PARSER_NAME_MP3);
        } else if ("video/webm".equals(mime) || "audio/webm".equals(mime)) {
            names.add(PARSER_NAME_MATROSKA);
        } else if ("audio/ogg".equals(mime)) {
            names.add(PARSER_NAME_OGG);
        } else if ("audio/flac".equals(mime)) {
            names.add(PARSER_NAME_FLAC);
        }
        return Collections.unmodifiableList(names);
    }

    public MediaParser setParameter(String parameterName, Object value) {
        if (mInitialized) {
            throw new IllegalStateException("setParameter() must be called before advance()");
        }
        Objects.requireNonNull(parameterName, "parameterName");
        Objects.requireNonNull(value, "value");
        mParameters.put(parameterName, value);
        return this;
    }

    public boolean supportsParameter(String parameterName) {
        Objects.requireNonNull(parameterName, "parameterName");
        return KNOWN_PARAMETERS.contains(parameterName);
    }

    public String getParserName() {
        return mParserName;
    }

    public boolean advance(final SeekableInputReader seekableInputReader) throws IOException {
        if (mReleased) {
            throw new IllegalStateException("MediaParser has been released");
        }
        Objects.requireNonNull(seekableInputReader, "seekableInputReader");
        if (!mInitialized) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = seekableInputReader.read(buf, 0, buf.length)) > 0) {
                bos.write(buf, 0, n);
            }
            final byte[] allBytes = bos.toByteArray();
            if (PARSER_NAME_UNKNOWN.equals(mParserName)) {
                if (allBytes.length >= 12 && allBytes[0] == 'R' && allBytes[1] == 'I'
                        && allBytes[2] == 'F' && allBytes[3] == 'F') {
                    mParserName = PARSER_NAME_WAV;
                } else if (allBytes.length >= 4 && (allBytes[0] & 0xFF) == 0xFF
                        && (allBytes[1] & 0xF6) == 0xF0) {
                    mParserName = PARSER_NAME_ADTS;
                } else if (allBytes.length >= 8 && allBytes[4] == 'f' && allBytes[5] == 't'
                        && allBytes[6] == 'y' && allBytes[7] == 'p') {
                    mParserName = PARSER_NAME_MP4;
                } else {
                    throw new UnrecognizedInputFormatException("Unrecognized media stream format");
                }
            }
            mExtractor = new MediaExtractor();
            try {
                mExtractor.setDataSource(new MediaDataSource() {
                    @Override
                    public int readAt(long position, byte[] buffer, int offset, int size) {
                        if (position < 0 || position >= allBytes.length) {
                            return -1;
                        }
                        int avail = (int) Math.min(size, allBytes.length - position);
                        System.arraycopy(allBytes, (int) position, buffer, offset, avail);
                        return avail;
                    }

                    @Override
                    public long getSize() {
                        return allBytes.length;
                    }

                    @Override
                    public void close() {
                    }
                });
            } catch (IOException e) {
                throw new ParsingException("Failed to parse container", e);
            }
            int trackCount = mExtractor.getTrackCount();
            mOutputConsumer.onTrackCountFound(trackCount);
            DrmInitData drm = mExtractor.getDrmInitData();
            long maxDur = SeekMap.UNKNOWN_DURATION;
            for (int i = 0; i < trackCount; i++) {
                MediaFormat tf = mExtractor.getTrackFormat(i);
                if (tf.containsKey(MediaFormat.KEY_DURATION)) {
                    maxDur = Math.max(maxDur, tf.getLong(MediaFormat.KEY_DURATION));
                }
                mOutputConsumer.onTrackDataFound(i, new TrackData(tf, drm));
                mExtractor.selectTrack(i);
            }
            mOutputConsumer.onSeekMapFound(new SeekMap(maxDur, true));
            mInitialized = true;
            mHasMoreSamples = mExtractor.getSampleTrackIndex() >= 0;
        }
        if (!mHasMoreSamples || mExtractor == null) {
            return false;
        }
        int trackIdx = mExtractor.getSampleTrackIndex();
        if (trackIdx < 0) {
            mHasMoreSamples = false;
            return false;
        }
        int sampleSize = (int) Math.max(0L, mExtractor.getSampleSize());
        long timeUs = mExtractor.getSampleTime();
        int exFlags = mExtractor.getSampleFlags();
        int parserFlags = ((exFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) ? SAMPLE_FLAG_KEY_FRAME : 0;
        ByteBuffer sampleBuf = ByteBuffer.allocate(Math.max(1, sampleSize));
        final int actualRead = mExtractor.readSampleData(sampleBuf, 0);
        if (actualRead < 0) {
            mHasMoreSamples = false;
            return false;
        }
        final byte[] sampleBytes = new byte[actualRead];
        sampleBuf.get(sampleBytes);
        InputReader sampleReader = new InputReader() {
            private int mPos;

            @Override
            public int read(byte[] buffer, int offset, int readLength) {
                if (mPos >= sampleBytes.length) {
                    return -1;
                }
                int n = Math.min(readLength, sampleBytes.length - mPos);
                System.arraycopy(sampleBytes, mPos, buffer, offset, n);
                mPos += n;
                return n;
            }

            @Override
            public long getPosition() {
                return mPos;
            }

            @Override
            public long getLength() {
                return sampleBytes.length;
            }
        };
        mOutputConsumer.onSampleDataFound(trackIdx, sampleReader);
        mHasMoreSamples = mExtractor.advance();
        if (!mHasMoreSamples) {
            parserFlags |= SAMPLE_FLAG_LAST_SAMPLE;
        }
        mOutputConsumer.onSampleCompleted(trackIdx, timeUs, parserFlags, actualRead, 0, null);
        return mHasMoreSamples;
    }

    public void seek(SeekPoint seekPoint) {
        Objects.requireNonNull(seekPoint, "seekPoint");
        if (mExtractor != null) {
            mExtractor.seekTo(seekPoint.timeMicros, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            mHasMoreSamples = mExtractor.getSampleTrackIndex() >= 0;
        }
    }

    public void release() {
        mReleased = true;
        if (mExtractor != null) {
            mExtractor.release();
            mExtractor = null;
        }
    }

    public void setLogSessionId(LogSessionId logSessionId) {
        mLogSessionId = Objects.requireNonNull(logSessionId, "logSessionId");
    }

    public LogSessionId getLogSessionId() {
        return mLogSessionId;
    }
}
