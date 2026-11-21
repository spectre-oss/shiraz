// SPDX-License-Identifier: GPL-3.0-only
package android.speech.tts;

public interface SynthesisCallback {
    int getMaxBufferSize();

    int start(int sampleRateInHz, int audioFormat, int channelCount);

    int audioAvailable(byte[] buffer, int offset, int length);

    int done();

    void error();

    void error(int errorCode);

    boolean hasStarted();

    boolean hasFinished();

    default void rangeStart(int markerInFrames, int start, int end) {
    }
}
