// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import java.util.function.Consumer;

/** Transactional call state callbacks invoked to confirm or veto state changes. */
public interface CallControlCallback {
    void onSetActive(Consumer<Boolean> wasCompleted);

    void onSetInactive(Consumer<Boolean> wasCompleted);

    void onAnswer(int videoState, Consumer<Boolean> wasCompleted);

    void onDisconnect(DisconnectCause disconnectCause, Consumer<Boolean> wasCompleted);

    void onCallStreamingStarted(Consumer<Boolean> wasCompleted);
}
