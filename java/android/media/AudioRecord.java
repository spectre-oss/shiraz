// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.content.Context;
import android.media.metrics.LogSessionId;
import android.os.Handler;
import android.os.PersistableBundle;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import shiraz.runtime.CapturePermissions;
import shiraz.runtime.NativeCapture;

/** PCM16 host microphone, gated on explicit per-app foreground consent. */
public class AudioRecord implements AudioRouting, MicrophoneDirection, AudioRecordingMonitor {
    public static final int TIMEBASE_MONOTONIC = 0;
    public static final int TIMEBASE_BOOTTIME = 1;
    public static final int STATE_UNINITIALIZED = 0;
    public static final int STATE_INITIALIZED = 1;
    public static final int RECORDSTATE_STOPPED = 1;
    public static final int RECORDSTATE_RECORDING = 3;
    public static final int SUCCESS = 0;
    public static final int ERROR = -1;
    public static final int ERROR_BAD_VALUE = -2;
    public static final int ERROR_INVALID_OPERATION = -3;
    public static final int ERROR_DEAD_OBJECT = -6;
    public static final int READ_BLOCKING = 0;
    public static final int READ_NON_BLOCKING = 1;

    public interface OnRecordPositionUpdateListener {
        void onMarkerReached(AudioRecord recorder);
        void onPeriodicNotification(AudioRecord recorder);
    }

    @Deprecated
    public interface OnRoutingChangedListener extends AudioRouting.OnRoutingChangedListener {
        void onRoutingChanged(AudioRecord audioRecord);

        @Override
        default void onRoutingChanged(AudioRouting router) {
            if (router instanceof AudioRecord) {
                onRoutingChanged((AudioRecord) router);
            }
        }
    }

    public static final class MetricsConstants {
        public static final String ENCODING = "android.media.audiorecord.encoding";
        public static final String SOURCE = "android.media.audiorecord.source";
        public static final String LATENCY = "android.media.audiorecord.latency";
        public static final String SAMPLERATE = "android.media.audiorecord.samplerate";
        public static final String CHANNELS = "android.media.audiorecord.channels";

        private MetricsConstants() {}
    }

    private final int source;
    private final int rate;
    private final int channelConfig;
    private final int channels;
    private final int bufferSize;
    private final AudioFormat format;
    private volatile long handle;
    private long lease;
    private volatile boolean released;
    private final CapturePermissions permissions;
    private boolean privacySensitive;
    private int markerPosition;
    private int notificationPeriod;
    private OnRecordPositionUpdateListener positionListener;
    private LogSessionId logSessionId = LogSessionId.LOG_SESSION_ID_NONE;

    public AudioRecord(int source, int rate, int channelConfig, int encoding, int bufferSize)
            throws IllegalArgumentException {
        if (source < 0
                || source > 10
                || getMinBufferSize(rate, channelConfig, encoding) < 0
                || bufferSize < 2
                || bufferSize > 4 * 1024 * 1024) {
            throw new IllegalArgumentException("AudioRecord configuration");
        }
        this.source = source;
        this.rate = rate;
        this.channelConfig = channelConfig;
        this.channels = channelConfig == AudioFormat.CHANNEL_IN_MONO ? 1 : 2;
        this.bufferSize = bufferSize;
        this.format = new AudioFormat.Builder()
                .setSampleRate(rate)
                .setChannelMask(channelConfig)
                .setEncoding(encoding)
                .build();
        this.permissions = CapturePermissions.current();
        if (permissions == null) throw new SecurityException("Microphone consent is unavailable");
        permissions.require(CapturePermissions.MICROPHONE);
    }

    public static int getMinBufferSize(int rate, int config, int encoding) {
        if (rate < 8000 || rate > 96000 || (config != 16 && config != 12) || encoding != 2) {
            return ERROR_BAD_VALUE;
        }
        return Math.max(1024, rate / 10 * (config == 16 ? 1 : 2) * 2);
    }

    public synchronized void startRecording() throws IllegalStateException {
        if (released) throw new IllegalStateException("AudioRecord released");
        if (handle != 0) return;
        permissions.require(CapturePermissions.MICROPHONE);
        long opened = 0;
        try {
            opened = NativeCapture.microphoneOpen(rate, channels);
            handle = opened;
            lease = permissions.begin(CapturePermissions.MICROPHONE, new Runnable() {
                public void run() {
                    stop();
                }
            });
        } catch (RuntimeException e) {
            if (opened != 0) NativeCapture.close(opened);
            handle = 0;
            permissions.failure(CapturePermissions.MICROPHONE);
            throw e;
        }
    }

    public void startRecording(MediaSyncEvent syncEvent) throws IllegalStateException {
        startRecording();
    }

    public void stop() throws IllegalStateException {
        long old;
        long id;
        synchronized (this) {
            old = handle;
            handle = 0;
            id = lease;
            lease = 0;
        }
        try {
            if (old != 0) NativeCapture.close(old);
        } finally {
            if (id != 0) permissions.end(id);
        }
    }

    public void release() {
        stop();
        synchronized (this) {
            released = true;
        }
    }

    @Override
    protected void finalize() {
        release();
    }

    public int read(byte[] bytes, int offset, int size) {
        if (bytes == null || offset < 0 || size < 0 || (long) offset + size > bytes.length) {
            return ERROR_BAD_VALUE;
        }
        long id = handle;
        if (id == 0) return ERROR_INVALID_OPERATION;
        if (!permissions.granted(CapturePermissions.MICROPHONE)) {
            stop();
            return ERROR_INVALID_OPERATION;
        }
        try {
            return NativeCapture.microphoneRead(id, bytes, offset, size - size % (channels * 2));
        } catch (SecurityException revoked) {
            stop();
            return ERROR_INVALID_OPERATION;
        } catch (IllegalStateException failure) {
            if (handle == 0) return ERROR_INVALID_OPERATION;
            permissions.failure(CapturePermissions.MICROPHONE);
            stop();
            return ERROR_DEAD_OBJECT;
        }
    }

    public int read(byte[] bytes, int offset, int size, int mode) {
        if (mode != READ_BLOCKING) throw new UnsupportedOperationException("Nonblocking capture read");
        return read(bytes, offset, size);
    }

    public int read(short[] samples, int offset, int size) {
        if (samples == null
                || offset < 0
                || size < 0
                || (long) offset + size > samples.length
                || size > 2 * 1024 * 1024) {
            return ERROR_BAD_VALUE;
        }
        byte[] bytes = new byte[size * 2];
        int n = read(bytes, 0, bytes.length);
        if (n < 0) return n;
        for (int i = 0; i < n / 2; i++) {
            samples[offset + i] = (short) ((bytes[i * 2] & 255) | (bytes[i * 2 + 1] << 8));
        }
        return n / 2;
    }

    public int read(short[] samples, int offset, int size, int mode) {
        if (mode != READ_BLOCKING) throw new UnsupportedOperationException("Nonblocking capture read");
        return read(samples, offset, size);
    }

    public int read(float[] audioData, int offsetInFloats, int sizeInFloats, int readMode) {
        if (audioData == null
                || offsetInFloats < 0
                || sizeInFloats < 0
                || (long) offsetInFloats + sizeInFloats > audioData.length) {
            return ERROR_BAD_VALUE;
        }
        return ERROR_INVALID_OPERATION;
    }

    public int read(ByteBuffer buffer, int size) {
        if (buffer == null
                || !buffer.isDirect()
                || size < 0
                || size > buffer.capacity()
                || size > 4 * 1024 * 1024) {
            return ERROR_BAD_VALUE;
        }
        byte[] bytes = new byte[size];
        int n = read(bytes, 0, size);
        if (n > 0) {
            ByteBuffer copy = buffer.duplicate();
            copy.clear();
            copy.put(bytes, 0, n);
        }
        return n;
    }

    public int read(ByteBuffer buffer, int size, int readMode) {
        if (readMode != READ_BLOCKING) throw new UnsupportedOperationException("Nonblocking capture read");
        return read(buffer, size);
    }

    public int getState() {
        return released ? STATE_UNINITIALIZED : STATE_INITIALIZED;
    }

    public int getRecordingState() {
        return handle != 0 ? RECORDSTATE_RECORDING : RECORDSTATE_STOPPED;
    }

    public int getAudioSessionId() {
        return 1;
    }

    public int getAudioSource() {
        return source;
    }

    public int getSampleRate() {
        return rate;
    }

    public int getChannelConfiguration() {
        return channelConfig;
    }

    public int getChannelCount() {
        return channels;
    }

    public int getAudioFormat() {
        return AudioFormat.ENCODING_PCM_16BIT;
    }

    public int getBufferSizeInFrames() {
        return bufferSize / (channels * 2);
    }

    public AudioFormat getFormat() {
        return format;
    }

    public boolean isPrivacySensitive() {
        return privacySensitive;
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

    public void setRecordPositionUpdateListener(OnRecordPositionUpdateListener listener) {
        setRecordPositionUpdateListener(listener, null);
    }

    public void setRecordPositionUpdateListener(
            OnRecordPositionUpdateListener listener, Handler handler) {
        positionListener = listener;
    }

    public PersistableBundle getMetrics() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(MetricsConstants.SAMPLERATE, rate);
        bundle.putInt(MetricsConstants.CHANNELS, channels);
        bundle.putInt(MetricsConstants.SOURCE, source);
        return bundle;
    }

    @Override
    public boolean setPreferredDevice(AudioDeviceInfo device) {
        return !released && device == null;
    }

    @Override
    public AudioDeviceInfo getPreferredDevice() {
        return null;
    }

    @Override
    public AudioDeviceInfo getRoutedDevice() {
        return handle != 0 ? new AudioDeviceInfo(true) : null;
    }

    @Override
    public void addOnRoutingChangedListener(
            AudioRouting.OnRoutingChangedListener listener, Handler handler) {}

    @Override
    public void removeOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener listener) {}

    @Deprecated
    public void addOnRoutingChangedListener(OnRoutingChangedListener listener, Handler handler) {}

    @Deprecated
    public void removeOnRoutingChangedListener(OnRoutingChangedListener listener) {}

    public List<MicrophoneInfo> getActiveMicrophones() throws IOException {
        return Collections.emptyList();
    }

    @Override
    public void registerAudioRecordingCallback(
            Executor executor, AudioManager.AudioRecordingCallback cb) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(cb, "cb");
    }

    @Override
    public void unregisterAudioRecordingCallback(AudioManager.AudioRecordingCallback cb) {
        Objects.requireNonNull(cb, "cb");
    }

    @Override
    public AudioRecordingConfiguration getActiveRecordingConfiguration() {
        if (handle == 0) return null;
        return new AudioRecordingConfiguration(
                getAudioSessionId(), source, format, format, getRoutedDevice(), false);
    }

    @Override
    public boolean setPreferredMicrophoneDirection(int direction) {
        return false;
    }

    @Override
    public boolean setPreferredMicrophoneFieldDimension(float zoom) {
        return false;
    }

    public void setLogSessionId(LogSessionId logSessionId) {
        this.logSessionId = Objects.requireNonNull(logSessionId, "logSessionId");
    }

    public LogSessionId getLogSessionId() {
        return logSessionId;
    }

    public int getTimestamp(AudioTimestamp timestamp, int timebase) {
        if (timestamp == null) throw new IllegalArgumentException("timestamp");
        if (timebase != TIMEBASE_MONOTONIC && timebase != TIMEBASE_BOOTTIME) return ERROR_BAD_VALUE;
        long current = handle;
        if (current == 0 || timebase == TIMEBASE_BOOTTIME) return ERROR_INVALID_OPERATION;
        try {
            long[] value = NativeCapture.microphoneTimestamp(current);
            if (value == null) return ERROR_INVALID_OPERATION;
            timestamp.framePosition = value[0];
            timestamp.nanoTime = value[1];
            return SUCCESS;
        } catch (SecurityException denied) {
            stop();
            return ERROR_INVALID_OPERATION;
        } catch (IllegalStateException stopped) {
            return ERROR_DEAD_OBJECT;
        }
    }

    public static class Builder {
        private int source;
        private int bufferSize = 8192;
        private boolean privacySensitive;
        private AudioFormat format =
                new AudioFormat.Builder().setSampleRate(44100).setChannelMask(16).build();

        public Builder() {}

        public Builder setAudioSource(int value) throws IllegalArgumentException {
            if (value < 0 || value > 10) {
                throw new IllegalArgumentException("Invalid audio source " + value);
            }
            source = value;
            return this;
        }

        public Builder setContext(Context context) {
            Objects.requireNonNull(context, "context");
            return this;
        }

        public Builder setBufferSizeInBytes(int value) throws IllegalArgumentException {
            if (value <= 0) {
                throw new IllegalArgumentException("Invalid buffer size " + value);
            }
            bufferSize = value;
            return this;
        }

        public Builder setAudioFormat(AudioFormat value) throws IllegalArgumentException {
            if (value == null) throw new NullPointerException("format");
            format = value;
            return this;
        }

        public Builder setAudioPlaybackCaptureConfig(AudioPlaybackCaptureConfiguration config) {
            Objects.requireNonNull(config, "config");
            throw new UnsupportedOperationException("Playback capture is unavailable");
        }

        public Builder setPrivacySensitive(boolean privacySensitive) {
            this.privacySensitive = privacySensitive;
            return this;
        }

        public AudioRecord build() throws UnsupportedOperationException {
            AudioRecord record = new AudioRecord(
                    source,
                    format.getSampleRate(),
                    format.getChannelMask(),
                    format.getEncoding(),
                    bufferSize);
            record.privacySensitive = privacySensitive;
            return record;
        }
    }
}
