// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.ims;

import android.content.Context;
import android.telephony.AccessNetworkConstants;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class ImsRcsManager {
    public static final int CAPABILITY_TYPE_NONE = 0;
    public static final int CAPABILITY_TYPE_OPTIONS_UCE = 1 << 0;
    public static final int CAPABILITY_TYPE_PRESENCE_UCE = 1 << 1;

    public static final String ACTION_SHOW_CAPABILITY_DISCOVERY_OPT_IN =
            "android.telephony.ims.action.SHOW_CAPABILITY_DISCOVERY_OPT_IN";

    private final Context mContext;
    private final int mSubId;

    ImsRcsManager(Context context, int subId) {
        mContext = context;
        mSubId = subId;
    }

    public RcsUceAdapter getUceAdapter() {
        return new RcsUceAdapter(mContext, mSubId);
    }

    public void registerImsRegistrationCallback(
            Executor executor, RegistrationManager.RegistrationCallback c) throws ImsException {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(c, "c cannot be null");
        throw new ImsException("IMS RCS service is unavailable on host", ImsException.CODE_ERROR_SERVICE_UNAVAILABLE);
    }

    public void unregisterImsRegistrationCallback(RegistrationManager.RegistrationCallback c) {
        Objects.requireNonNull(c, "c cannot be null");
    }

    public void getRegistrationState(Executor executor, final Consumer<Integer> stateCallback) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(stateCallback, "stateCallback cannot be null");
        executor.execute(new Runnable() {
            public void run() {
                stateCallback.accept(Integer.valueOf(RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED));
            }
        });
    }

    public void getRegistrationTransportType(
            Executor executor, final Consumer<Integer> transportTypeCallback) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(transportTypeCallback, "transportTypeCallback cannot be null");
        executor.execute(new Runnable() {
            public void run() {
                transportTypeCallback.accept(Integer.valueOf(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
            }
        });
    }

    public void registerImsStateCallback(Executor executor, final ImsStateCallback callback)
            throws ImsException {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        executor.execute(new Runnable() {
            public void run() {
                callback.onUnavailable(ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED);
            }
        });
    }

    public void unregisterImsStateCallback(ImsStateCallback callback) {
        Objects.requireNonNull(callback, "callback cannot be null");
    }
}
