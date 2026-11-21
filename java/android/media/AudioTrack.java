// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.content.Context;
import android.media.metrics.LogSessionId;
import android.os.Handler;
import android.os.PersistableBundle;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Executor;
import shiraz.runtime.NativePlayback;

/** Streaming PCM16 output to the host audio device. */
public class AudioTrack implements AudioRouting, VolumeAutomation {
    public static final int STATE_UNINITIALIZED = 0;
    public static final int STATE_INITIALIZED = 1;
    public static final int STATE_NO_STATIC_DATA = 2;
    public static final int PLAYSTATE_STOPPED = 1;
    public static final int PLAYSTATE_PAUSED = 2;
    public static final int PLAYSTATE_PLAYING = 3;
    public static final int MODE_STATIC = 0;
    public static final int MODE_STREAM = 1;
    public static final int SUCCESS = 0;
    public static final int ERROR = -1;
    public static final int ERROR_BAD_VALUE = -2;
    public static final int ERROR_INVALID_OPERATION = -3;
    public static final int ERROR_DEAD_OBJECT = -6;
    public static final int WRITE_BLOCKING = 0;
    public static final int WRITE_NON_BLOCKING = 1;

    public static final int PERFORMANCE_MODE_NONE = 0;
    public static final int PERFORMANCE_MODE_LOW_LATENCY = 1;
    public static final int PERFORMANCE_MODE_POWER_SAVING = 2;

    public static final int ENCAPSULATION_MODE_NONE = 0;
    public static final int ENCAPSULATION_MODE_ELEMENTARY_STREAM = 1;
    public static final int ENCAPSULATION_METADATA_TYPE_FRAMEWORK_TUNER = 1;
    public static final int ENCAPSULATION_METADATA_TYPE_DVB_AD_DESCRIPTOR = 2;
    public static final int ENCAPSULATION_METADATA_TYPE_SUPPLEMENTARY_AUDIO_PLACEMENT = 3;
    public static final int SUPPLEMENTARY_AUDIO_PLACEMENT_NORMAL = 0;
    public static final int SUPPLEMENTARY_AUDIO_PLACEMENT_LEFT = 1;
    public static final int SUPPLEMENTARY_AUDIO_PLACEMENT_RIGHT = 2;

    public static final int DUAL_MONO_MODE_OFF = 0;
    public static final int DUAL_MONO_MODE_LR = 1;
    public static final int DUAL_MONO_MODE_LL = 2;
    public static final int DUAL_MONO_MODE_RR = 3;

    public interface OnPlaybackPositionUpdateListener {
        void onMarkerReached(AudioTrack track);
        void onPeriodicNotification(AudioTrack track);
    }

    @Deprecated
    public interface OnRoutingChangedListener extends AudioRouting.OnRoutingChangedListener {
        void onRoutingChanged(AudioTrack audioTrack);

        @Override
        default void onRoutingChanged(AudioRouting router) {
            if (router instanceof AudioTrack) {
                onRoutingChanged((AudioTrack) router);
            }
        }
    }

    public interface OnCodecFormatChangedListener {
        void onCodecFormatChanged(AudioTrack audioTrack, AudioMetadataReadMap info);
    }

    public abstract static class StreamEventCallback {
        public StreamEventCallback() {}
        public void onTearDown(AudioTrack track) {}
        public void onPresentationEnded(AudioTrack track) {}
        public void onDataRequest(AudioTrack track, int sizeInFrames) {}
    }

    public static final class MetricsConstants {
        @Deprecated public static final String STREAMTYPE = "android.media.audiotrack.streamtype";
        public static final String CONTENTTYPE = "android.media.audiotrack.type";
        public static final String USAGE = "android.media.audiotrack.usage";
        @Deprecated public static final String SAMPLERATE = "android.media.audiorecord.samplerate";
        @Deprecated public static final String CHANNELMASK = "android.media.audiorecord.channelmask";

        private MetricsConstants() {}
    }

    private long handle;
    private final int streamType;
    private final int rate;
    private final int mask;
    private final int channels;
    private final int buffer;
    private final int session;
    private final AudioAttributes attributes;
    private final AudioFormat format;
    private int state = PLAYSTATE_STOPPED;
    private float volume = 1;
    private float shaperVolume = 1;
    private int startThresholdFrames;
    private int markerPosition;
    private int notificationPeriod;
    private int offloadDelay;
    private int offloadPadding;
    private int dualMonoMode = DUAL_MONO_MODE_OFF;
    private float audioDescMixDb = Float.NEGATIVE_INFINITY;
    private LogSessionId logSessionId = LogSessionId.LOG_SESSION_ID_NONE;

    public AudioTrack(int stream, int rate, int mask, int encoding, int bytes, int mode)
            throws IllegalArgumentException {
        this(stream, rate, mask, encoding, bytes, mode, 0);
    }

    public AudioTrack(int stream, int rate, int mask, int encoding, int bytes, int mode, int session)
            throws IllegalArgumentException {
        if (getMinBufferSize(rate, mask, encoding) < 0
                || bytes < 2
                || bytes > 4 * 1024 * 1024
                || mode != MODE_STREAM
                || session < 0) {
            throw new IllegalArgumentException("PCM streaming AudioTrack configuration");
        }
        this.streamType = stream;
        this.rate = rate;
        this.mask = mask;
        this.channels = mask == 4 ? 1 : 2;
        this.buffer = bytes;
        this.session = session == 0 ? 1 : session;
        this.attributes = new AudioAttributes.Builder().setLegacyStreamType(stream).build();
        this.format = new AudioFormat.Builder()
                .setSampleRate(rate)
                .setChannelMask(mask)
                .setEncoding(encoding)
                .build();
        this.startThresholdFrames = bytes / (channels * 2);
        this.handle = NativePlayback.trackOpen(rate, channels, bytes);
    }

    public AudioTrack(
            AudioAttributes attributes, AudioFormat format, int bytes, int mode, int session)
            throws IllegalArgumentException {
        if (attributes == null || format == null) {
            throw new IllegalArgumentException("Null AudioAttributes or AudioFormat");
        }
        int rate = format.getSampleRate();
        int mask = format.getChannelMask();
        int encoding = format.getEncoding();
        if (getMinBufferSize(rate, mask, encoding) < 0
                || bytes < 2
                || bytes > 4 * 1024 * 1024
                || mode != MODE_STREAM
                || session < 0) {
            throw new IllegalArgumentException("PCM streaming AudioTrack configuration");
        }
        this.streamType = AudioManager.STREAM_MUSIC;
        this.rate = rate;
        this.mask = mask;
        this.channels = mask == 4 ? 1 : 2;
        this.buffer = bytes;
        this.session = session == 0 ? 1 : session;
        this.attributes = attributes;
        this.format = format;
        this.startThresholdFrames = bytes / (channels * 2);
        this.handle = NativePlayback.trackOpen(rate, channels, bytes);
    }

    public static int getMinBufferSize(int rate, int mask, int encoding) {
        return rate < 8000 || rate > 192000 || (mask != 4 && mask != 12) || encoding != 2
                ? ERROR_BAD_VALUE
                : Math.max(1024, rate / 10 * (mask == 4 ? 1 : 2) * 2);
    }

    public static int getNativeOutputSampleRate(int stream) {
        return 48000;
    }

    public static float getMaxVolume() {
        return 1;
    }

    public static float getMinVolume() {
        return 0;
    }

    private void live() {
        if (handle == 0) throw new IllegalStateException("AudioTrack released");
    }

    public synchronized void play() throws IllegalStateException {
        live();
        NativePlayback.control(handle, 2);
        state = PLAYSTATE_PLAYING;
    }

    public synchronized void shirazStartPending() {
        live();
        NativePlayback.control(handle, 3);
    }

    public synchronized void pause() throws IllegalStateException {
        live();
        NativePlayback.control(handle, 1);
        state = PLAYSTATE_PAUSED;
    }

    public synchronized void stop() throws IllegalStateException {
        live();
        NativePlayback.control(handle, 0);
        state = PLAYSTATE_STOPPED;
    }

    public synchronized void release() {
        if (handle != 0) NativePlayback.close(handle);
        handle = 0;
        state = PLAYSTATE_STOPPED;
    }

    @Override
    protected void finalize() {
        release();
    }

    public synchronized void flush() {
        if (handle != 0 && state != PLAYSTATE_PLAYING) NativePlayback.control(handle, 0);
    }

    public int write(byte[] bytes, int offset, int length) {
        return write(bytes, offset, length, WRITE_BLOCKING);
    }

    public synchronized int write(byte[] bytes, int offset, int length, int mode) {
        if (handle == 0) return ERROR_INVALID_OPERATION;
        if (bytes == null
                || offset < 0
                || length < 0
                || (long) offset + length > bytes.length
                || length > 4 * 1024 * 1024
                || (mode != 0 && mode != 1)) {
            return ERROR_BAD_VALUE;
        }
        return NativePlayback.write(handle, bytes, offset, length, mode == 0, volume * shaperVolume);
    }

    public int write(short[] samples, int offset, int length) {
        return write(samples, offset, length, WRITE_BLOCKING);
    }

    public int write(short[] samples, int offset, int length, int mode) {
        if (samples == null
                || offset < 0
                || length < 0
                || (long) offset + length > samples.length
                || length > 2 * 1024 * 1024
                || (mode != 0 && mode != 1)) {
            return ERROR_BAD_VALUE;
        }
        byte[] bytes = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            bytes[2 * i] = (byte) samples[offset + i];
            bytes[2 * i + 1] = (byte) (samples[offset + i] >> 8);
        }
        int result = write(bytes, 0, bytes.length, mode);
        return result < 0 ? result : result / 2;
    }

    public int write(float[] audioData, int offsetInFloats, int sizeInFloats, int writeMode) {
        if (audioData == null
                || offsetInFloats < 0
                || sizeInFloats < 0
                || (long) offsetInFloats + sizeInFloats > audioData.length
                || (writeMode != 0 && writeMode != 1)) {
            return ERROR_BAD_VALUE;
        }
        return ERROR_INVALID_OPERATION;
    }

    public int write(ByteBuffer bytes, int length, int mode) {
        if (bytes == null
                || length < 0
                || length > bytes.remaining()
                || length > 4 * 1024 * 1024) {
            return ERROR_BAD_VALUE;
        }
        byte[] copy = new byte[length];
        bytes.duplicate().get(copy);
        int n = write(copy, 0, length, mode);
        if (n > 0) bytes.position(bytes.position() + n);
        return n;
    }

    public int write(ByteBuffer bytes, int length, int mode, long time) {
        return write(bytes, length, mode);
    }

    public int reloadStaticData() {
        return ERROR_INVALID_OPERATION;
    }

    public int attachAuxEffect(int effectId) {
        return effectId == 0 ? SUCCESS : ERROR_INVALID_OPERATION;
    }

    public int setAuxEffectSendLevel(float level) {
        return handle == 0 ? ERROR_INVALID_OPERATION : SUCCESS;
    }

    public synchronized int getState() {
        return handle == 0 ? STATE_UNINITIALIZED : STATE_INITIALIZED;
    }

    @Deprecated
    protected void setState(int state) {}

    public int getPlayState() {
        return state;
    }

    public int getAudioSessionId() {
        return session;
    }

    public AudioAttributes getAudioAttributes() {
        return attributes;
    }

    public int getAudioFormat() {
        return AudioFormat.ENCODING_PCM_16BIT;
    }

    public int getStreamType() {
        return streamType;
    }

    public int getChannelConfiguration() {
        return mask;
    }

    public AudioFormat getFormat() {
        return format;
    }

    public int getSampleRate() {
        return rate;
    }

    public int getChannelCount() {
        return channels;
    }

    public int getBufferSizeInFrames() {
        return buffer / (channels * 2);
    }

    public int getBufferCapacityInFrames() {
        return getBufferSizeInFrames();
    }

    public int setBufferSizeInFrames(int frames) {
        return frames < 1 ? ERROR_BAD_VALUE : getBufferSizeInFrames();
    }

    public int setStartThresholdInFrames(int startThresholdInFrames) {
        if (startThresholdInFrames < 1) throw new IllegalArgumentException("startThresholdInFrames");
        this.startThresholdFrames = Math.min(startThresholdInFrames, getBufferCapacityInFrames());
        return this.startThresholdFrames;
    }

    public int getStartThresholdInFrames() {
        return startThresholdFrames;
    }

    @Deprecated
    protected int getNativeFrameCount() {
        return getBufferCapacityInFrames();
    }

    public int getNotificationMarkerPosition() {
        return markerPosition;
    }

    public int setNotificationMarkerPosition(int markerInFrames) {
        if (markerInFrames < 0) return ERROR_BAD_VALUE;
        markerPosition = markerInFrames;
        return SUCCESS;
    }

    public int getPositionNotificationPeriod() {
        return notificationPeriod;
    }

    public int setPositionNotificationPeriod(int periodInFrames) {
        if (periodInFrames < 0) return ERROR_BAD_VALUE;
        notificationPeriod = periodInFrames;
        return SUCCESS;
    }

    public int setPlaybackHeadPosition(int positionInFrames) {
        return ERROR_INVALID_OPERATION;
    }

    public int setLoopPoints(int startInFrames, int endInFrames, int loopCount) {
        return ERROR_INVALID_OPERATION;
    }

    public int setPresentation(AudioPresentation presentation) {
        Objects.requireNonNull(presentation, "presentation");
        return ERROR_INVALID_OPERATION;
    }

    public void setPlaybackPositionUpdateListener(OnPlaybackPositionUpdateListener listener) {}

    public void setPlaybackPositionUpdateListener(
            OnPlaybackPositionUpdateListener listener, Handler handler) {}

    public synchronized int getPlaybackHeadPosition() {
        return handle == 0 ? 0 : (int) NativePlayback.position(handle);
    }

    public synchronized boolean getTimestamp(AudioTimestamp timestamp) {
        if (timestamp == null) throw new NullPointerException("timestamp");
        if (handle == 0 || state != PLAYSTATE_PLAYING) return false;
        long[] value = NativePlayback.timestamp(handle);
        if (value == null) return false;
        timestamp.framePosition = value[0];
        timestamp.nanoTime = value[1];
        return true;
    }

    public PersistableBundle getMetrics() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(MetricsConstants.SAMPLERATE, rate);
        bundle.putInt(MetricsConstants.CHANNELMASK, mask);
        return bundle;
    }

    public int getPlaybackRate() {
        return rate;
    }

    public int setPlaybackRate(int sampleRateInHz) {
        if (sampleRateInHz <= 0) return ERROR_BAD_VALUE;
        return sampleRateInHz == rate ? SUCCESS : ERROR_INVALID_OPERATION;
    }

    public int getUnderrunCount() {
        return 0;
    }

    public int getPerformanceMode() {
        return PERFORMANCE_MODE_NONE;
    }

    public synchronized int setVolume(float gain) {
        if (!Float.isFinite(gain)) return ERROR_BAD_VALUE;
        volume = Math.max(0, Math.min(1, gain));
        return SUCCESS;
    }

    @Deprecated
    public int setStereoVolume(float left, float right) {
        if (left != right) throw new UnsupportedOperationException("Independent channel volume");
        return setVolume(left);
    }

    @Override
    public VolumeShaper createVolumeShaper(VolumeShaper.Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return new VolumeShaper(configuration, new VolumeShaper.VolumeSink() {
            @Override
            public void onShaperVolumeChanged(float v) {
                synchronized (AudioTrack.this) {
                    shaperVolume = v;
                }
            }
        });
    }

    public void setOffloadDelayPadding(int delayInFrames, int paddingInFrames) {
        if (delayInFrames < 0 || paddingInFrames < 0) {
            throw new IllegalArgumentException("Negative offload delay/padding");
        }
        offloadDelay = delayInFrames;
        offloadPadding = paddingInFrames;
    }

    public int getOffloadDelay() {
        return offloadDelay;
    }

    public int getOffloadPadding() {
        return offloadPadding;
    }

    public void setOffloadEndOfStream() {
        throw new IllegalStateException("Offloaded playback is not enabled");
    }

    public boolean isOffloadedPlayback() {
        return false;
    }

    public static boolean isDirectPlaybackSupported(AudioFormat format, AudioAttributes attributes) {
        return false;
    }

    public boolean setAudioDescriptionMixLeveldB(float level) {
        audioDescMixDb = level;
        return false;
    }

    public float getAudioDescriptionMixLeveldB() {
        return audioDescMixDb;
    }

    public boolean setDualMonoMode(int dualMonoMode) {
        if (dualMonoMode < DUAL_MONO_MODE_OFF || dualMonoMode > DUAL_MONO_MODE_RR) {
            return false;
        }
        this.dualMonoMode = dualMonoMode;
        return false;
    }

    public int getDualMonoMode() {
        return dualMonoMode;
    }

    @Override
    public boolean setPreferredDevice(AudioDeviceInfo device) {
        return device == null;
    }

    @Override
    public AudioDeviceInfo getPreferredDevice() {
        return null;
    }

    @Override
    public AudioDeviceInfo getRoutedDevice() {
        return state == PLAYSTATE_PLAYING ? new AudioDeviceInfo() : null;
    }

    @Override
    public void addOnRoutingChangedListener(
            AudioRouting.OnRoutingChangedListener listener, Handler handler) {
        if (listener == null) throw new IllegalArgumentException("listener");
    }

    @Override
    public void removeOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener listener) {}

    @Deprecated
    public void addOnRoutingChangedListener(OnRoutingChangedListener listener, Handler handler) {
        if (listener == null) throw new IllegalArgumentException("listener");
    }

    @Deprecated
    public void removeOnRoutingChangedListener(OnRoutingChangedListener listener) {}

    public void addOnCodecFormatChangedListener(
            Executor executor, OnCodecFormatChangedListener listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
    }

    public void removeOnCodecFormatChangedListener(OnCodecFormatChangedListener listener) {
        Objects.requireNonNull(listener, "listener");
    }

    public void registerStreamEventCallback(Executor executor, StreamEventCallback eventCallback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(eventCallback, "eventCallback");
    }

    public void unregisterStreamEventCallback(StreamEventCallback eventCallback) {
        Objects.requireNonNull(eventCallback, "eventCallback");
    }

    public void setLogSessionId(LogSessionId logSessionId) {
        this.logSessionId = Objects.requireNonNull(logSessionId, "logSessionId");
    }

    public LogSessionId getLogSessionId() {
        return logSessionId;
    }

    public PlaybackParams getPlaybackParams() {
        return new PlaybackParams();
    }

    public void setPlaybackParams(PlaybackParams params) {
        Objects.requireNonNull(params, "params");
        if (params.getSpeed() != 1 || params.getPitch() != 1) {
            throw new UnsupportedOperationException("Playback speed and pitch changes");
        }
    }

    public static class Builder {
        private AudioAttributes attributes = new AudioAttributes.Builder().build();
        private AudioFormat format =
                new AudioFormat.Builder().setSampleRate(48000).setChannelMask(12).build();
        private int bytes = 19200;
        private int mode = MODE_STREAM;
        private int session;

        public Builder() {}

        public Builder setAudioAttributes(AudioAttributes v) throws IllegalArgumentException {
            if (v == null) throw new IllegalArgumentException("Illegal null AudioAttributes");
            attributes = v;
            return this;
        }

        public Builder setAudioFormat(AudioFormat v) throws IllegalArgumentException {
            if (v == null) throw new IllegalArgumentException("Illegal null AudioFormat");
            format = v;
            return this;
        }

        public Builder setBufferSizeInBytes(int v) throws IllegalArgumentException {
            if (v <= 0) throw new IllegalArgumentException("Invalid buffer size " + v);
            bytes = v;
            return this;
        }

        public Builder setEncapsulationMode(int encapsulationMode) {
            if (encapsulationMode != ENCAPSULATION_MODE_NONE
                    && encapsulationMode != ENCAPSULATION_MODE_ELEMENTARY_STREAM) {
                throw new IllegalArgumentException("Invalid encapsulation mode " + encapsulationMode);
            }
            return this;
        }

        public Builder setTransferMode(int v) throws IllegalArgumentException {
            if (v != MODE_STREAM && v != MODE_STATIC) {
                throw new IllegalArgumentException("Invalid transfer mode " + v);
            }
            mode = v;
            return this;
        }

        public Builder setSessionId(int v) throws IllegalArgumentException {
            if (v < 0) throw new IllegalArgumentException("Invalid audio session ID " + v);
            session = v;
            return this;
        }

        public Builder setPerformanceMode(int v) {
            return this;
        }

        public Builder setContext(Context context) {
            Objects.requireNonNull(context, "context");
            return this;
        }

        public Builder setOffloadedPlayback(boolean v) {
            if (v) throw new UnsupportedOperationException("Offloaded playback");
            return this;
        }

        public AudioTrack build() throws UnsupportedOperationException {
            return new AudioTrack(attributes, format, bytes, mode, session);
        }
    }
}
