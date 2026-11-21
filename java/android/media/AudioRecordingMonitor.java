// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import java.util.concurrent.Executor;

public interface AudioRecordingMonitor {
    void registerAudioRecordingCallback(Executor executor, AudioManager.AudioRecordingCallback cb);

    void unregisterAudioRecordingCallback(AudioManager.AudioRecordingCallback cb);

    AudioRecordingConfiguration getActiveRecordingConfiguration();
}
