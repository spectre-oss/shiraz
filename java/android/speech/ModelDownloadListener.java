// SPDX-License-Identifier: GPL-3.0-only
package android.speech;

public interface ModelDownloadListener {
    void onProgress(int completedPercent);

    void onSuccess();

    void onScheduled();

    void onError(int error);
}
