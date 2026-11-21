// SPDX-License-Identifier: GPL-3.0-only
package android.speech;

public interface RecognitionSupportCallback {
    void onSupportResult(RecognitionSupport recognitionSupport);

    void onError(int error);
}
