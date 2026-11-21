// SPDX-License-Identifier: GPL-3.0-only
package android.media;

public class MediaActionSound {
    public static final int SHUTTER_CLICK = 0;
    public static final int FOCUS_COMPLETE = 1;
    public static final int START_VIDEO_RECORDING = 2;
    public static final int STOP_VIDEO_RECORDING = 3;

    private static final int NUM_MEDIA_SOUND_STREAMS = 4;
    private final boolean[] mLoaded = new boolean[NUM_MEDIA_SOUND_STREAMS];
    private boolean mReleased;

    public MediaActionSound() {
    }

    public static boolean mustPlayShutterSound() {
        return false;
    }

    private void checkSound(int soundName) {
        if (mReleased) {
            throw new IllegalStateException("MediaActionSound has been released");
        }
        if (soundName < 0 || soundName >= NUM_MEDIA_SOUND_STREAMS) {
            throw new RuntimeException("Unknown sound requested: " + soundName);
        }
    }

    public synchronized void load(int soundName) {
        checkSound(soundName);
        mLoaded[soundName] = true;
    }

    public synchronized void play(int soundName) {
        checkSound(soundName);
        mLoaded[soundName] = true;
        final int freqHz;
        switch (soundName) {
            case SHUTTER_CLICK:
                freqHz = 1200;
                break;
            case FOCUS_COMPLETE:
                freqHz = 880;
                break;
            case START_VIDEO_RECORDING:
                freqHz = 660;
                break;
            case STOP_VIDEO_RECORDING:
            default:
                freqHz = 440;
                break;
        }
        try {
            int sampleRate = 16000;
            int samples = sampleRate / 25; // 40ms cue
            short[] pcm = new short[samples];
            for (int i = 0; i < samples; i++) {
                double env = 1.0 - ((double) i / samples);
                pcm[i] = (short) (Math.sin(2.0 * Math.PI * freqHz * i / sampleRate) * 6000.0 * env);
            }
            AudioTrack track = new AudioTrack(
                    AudioManager.STREAM_SYSTEM,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    samples * 2,
                    AudioTrack.MODE_STATIC);
            try {
                track.write(pcm, 0, samples);
                track.play();
            } finally {
                track.release();
            }
        } catch (RuntimeException ignored) {
            // Headless host without audio output device: cue state remains valid.
        }
    }

    public synchronized void release() {
        mReleased = true;
        for (int i = 0; i < mLoaded.length; i++) {
            mLoaded[i] = false;
        }
    }
}
