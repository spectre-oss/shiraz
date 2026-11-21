// SPDX-License-Identifier: GPL-3.0-only
package android.speech;

import android.os.Bundle;

public interface RecognitionListener {
    void onReadyForSpeech(Bundle params);

    void onBeginningOfSpeech();

    void onRmsChanged(float rmsdB);

    void onBufferReceived(byte[] buffer);

    void onEndOfSpeech();

    void onError(int error);

    void onResults(Bundle results);

    void onPartialResults(Bundle partialResults);

    default void onSegmentResults(Bundle segmentResults) {
    }

    default void onEndOfSegmentedSession() {
    }

    default void onLanguageDetection(Bundle results) {
    }

    void onEvent(int eventType, Bundle params);
}
