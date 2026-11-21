// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.ims;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

public interface RegistrationManager {
    int REGISTRATION_STATE_NOT_REGISTERED = 0;
    int REGISTRATION_STATE_REGISTERING = 1;
    int REGISTRATION_STATE_REGISTERED = 2;

    void registerImsRegistrationCallback(Executor executor, RegistrationCallback c)
            throws ImsException;

    void unregisterImsRegistrationCallback(RegistrationCallback c);

    void getRegistrationState(Executor executor, Consumer<Integer> stateCallback);

    void getRegistrationTransportType(Executor executor, Consumer<Integer> transportTypeCallback);

    class RegistrationCallback {
        public RegistrationCallback() {}

        @Deprecated
        public void onRegistered(int imsTransportType) {}

        public void onRegistered(ImsRegistrationAttributes attributes) {
            onRegistered(attributes.getTransportType());
        }

        @Deprecated
        public void onRegistering(int imsTransportType) {}

        public void onRegistering(ImsRegistrationAttributes attributes) {
            onRegistering(attributes.getTransportType());
        }

        public void onUnregistered(ImsReasonInfo info) {}

        public void onTechnologyChangeFailed(int imsTransportType, ImsReasonInfo info) {}
    }
}
