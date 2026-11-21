// SPDX-License-Identifier: GPL-3.0-only
package android.media.audiofx;

import java.util.Arrays;

public class Visualizer {
    public static final int STATE_UNINITIALIZED = 0;
    public static final int STATE_INITIALIZED = 1;
    public static final int STATE_ENABLED = 2;

    public static final int SCALING_MODE_NORMALIZED = 0;
    public static final int SCALING_MODE_AS_PLAYED = 1;

    public static final int MEASUREMENT_MODE_NONE = 0;
    public static final int MEASUREMENT_MODE_PEAK_RMS = 1;

    public static final int SUCCESS = 0;
    public static final int ERROR = -1;
    public static final int ALREADY_EXISTS = -2;
    public static final int ERROR_NO_INIT = -3;
    public static final int ERROR_BAD_VALUE = -4;
    public static final int ERROR_INVALID_OPERATION = -5;
    public static final int ERROR_NO_MEMORY = -6;
    public static final int ERROR_DEAD_OBJECT = -7;

    public static final class MeasurementPeakRms {
        public int mPeak;
        public int mRms;

        public MeasurementPeakRms() {
        }
    }

    public interface OnDataCaptureListener {
        void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate);

        void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate);
    }

    private static final int MIN_CAPTURE_SIZE = 128;
    private static final int MAX_CAPTURE_SIZE = 1024;
    private static final int MAX_CAPTURE_RATE = 20000;
    private static final int DEFAULT_SAMPLING_RATE_MILLIHERTZ = 44100000;

    private final int mAudioSession;
    private int mState;
    private int mCaptureSize = MAX_CAPTURE_SIZE;
    private int mScalingMode = SCALING_MODE_NORMALIZED;
    private int mMeasurementMode = MEASUREMENT_MODE_NONE;
    private OnDataCaptureListener mCaptureListener;

    public Visualizer(int audioSession)
            throws UnsupportedOperationException, RuntimeException {
        if (audioSession < 0) {
            throw new RuntimeException("Invalid audio session: " + audioSession);
        }
        mAudioSession = audioSession;
        mState = STATE_INITIALIZED;
    }

    public void release() {
        mState = STATE_UNINITIALIZED;
        mCaptureListener = null;
    }

    @Override
    protected void finalize() {
        release();
    }

    private void checkInitialized() {
        if (mState == STATE_UNINITIALIZED) {
            throw new IllegalStateException("Visualizer is not initialized");
        }
    }

    public int setEnabled(boolean enabled) throws IllegalStateException {
        checkInitialized();
        mState = enabled ? STATE_ENABLED : STATE_INITIALIZED;
        return SUCCESS;
    }

    public boolean getEnabled() {
        return mState == STATE_ENABLED;
    }

    public static int[] getCaptureSizeRange() {
        return new int[]{MIN_CAPTURE_SIZE, MAX_CAPTURE_SIZE};
    }

    public static int getMaxCaptureRate() {
        return MAX_CAPTURE_RATE;
    }

    public int setCaptureSize(int size) throws IllegalStateException {
        checkInitialized();
        if (mState == STATE_ENABLED) {
            return ERROR_INVALID_OPERATION;
        }
        if (size < MIN_CAPTURE_SIZE || size > MAX_CAPTURE_SIZE
                || (size & (size - 1)) != 0) {
            return ERROR_BAD_VALUE;
        }
        mCaptureSize = size;
        return SUCCESS;
    }

    public int getCaptureSize() throws IllegalStateException {
        checkInitialized();
        return mCaptureSize;
    }

    public int setScalingMode(int mode) throws IllegalStateException {
        checkInitialized();
        if (mode != SCALING_MODE_NORMALIZED && mode != SCALING_MODE_AS_PLAYED) {
            return ERROR_BAD_VALUE;
        }
        mScalingMode = mode;
        return SUCCESS;
    }

    public int getScalingMode() throws IllegalStateException {
        checkInitialized();
        return mScalingMode;
    }

    public int setMeasurementMode(int mode) throws IllegalStateException {
        checkInitialized();
        if (mode != MEASUREMENT_MODE_NONE && mode != MEASUREMENT_MODE_PEAK_RMS) {
            return ERROR_BAD_VALUE;
        }
        mMeasurementMode = mode;
        return SUCCESS;
    }

    public int getMeasurementMode() throws IllegalStateException {
        checkInitialized();
        return mMeasurementMode;
    }

    public int getSamplingRate() throws IllegalStateException {
        checkInitialized();
        return DEFAULT_SAMPLING_RATE_MILLIHERTZ;
    }

    public int getWaveForm(byte[] waveform) throws IllegalStateException {
        checkInitialized();
        if (mState != STATE_ENABLED) {
            return ERROR_INVALID_OPERATION;
        }
        if (waveform == null || waveform.length < mCaptureSize) {
            return ERROR_BAD_VALUE;
        }
        Arrays.fill(waveform, 0, mCaptureSize, (byte) 0x80);
        return SUCCESS;
    }

    public int getFft(byte[] fft) throws IllegalStateException {
        checkInitialized();
        if (mState != STATE_ENABLED) {
            return ERROR_INVALID_OPERATION;
        }
        if (fft == null || fft.length < mCaptureSize) {
            return ERROR_BAD_VALUE;
        }
        Arrays.fill(fft, 0, mCaptureSize, (byte) 0);
        return SUCCESS;
    }

    public int getMeasurementPeakRms(MeasurementPeakRms measurement) {
        if (mState == STATE_UNINITIALIZED) {
            return ERROR_NO_INIT;
        }
        if (measurement == null) {
            return ERROR_BAD_VALUE;
        }
        if (mMeasurementMode != MEASUREMENT_MODE_PEAK_RMS || mState != STATE_ENABLED) {
            return ERROR_INVALID_OPERATION;
        }
        measurement.mPeak = -9600;
        measurement.mRms = -9600;
        return SUCCESS;
    }

    public int setDataCaptureListener(
            OnDataCaptureListener listener,
            int rate,
            boolean waveform,
            boolean fft) {
        if (mState == STATE_UNINITIALIZED) {
            return ERROR_NO_INIT;
        }
        if (listener != null && (rate <= 0 || rate > MAX_CAPTURE_RATE)) {
            return ERROR_BAD_VALUE;
        }
        mCaptureListener = listener;
        if (listener != null && mState == STATE_ENABLED) {
            if (waveform) {
                byte[] waveBuf = new byte[mCaptureSize];
                Arrays.fill(waveBuf, (byte) 0x80);
                listener.onWaveFormDataCapture(this, waveBuf, DEFAULT_SAMPLING_RATE_MILLIHERTZ);
            }
            if (fft) {
                byte[] fftBuf = new byte[mCaptureSize];
                listener.onFftDataCapture(this, fftBuf, DEFAULT_SAMPLING_RATE_MILLIHERTZ);
            }
        }
        return SUCCESS;
    }
}
