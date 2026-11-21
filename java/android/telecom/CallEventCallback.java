// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.os.Bundle;
import java.util.List;

/** Informational call event callbacks for transactional calls. */
public interface CallEventCallback {
    void onCallEndpointChanged(CallEndpoint newCallEndpoint);

    void onAvailableCallEndpointsChanged(List<CallEndpoint> availableEndpoints);

    void onMuteStateChanged(boolean isMuted);

    default void onVideoStateChanged(int videoState) {
    }

    void onCallStreamingFailed(int reason);

    void onEvent(String event, Bundle extras);
}
