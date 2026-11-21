// SPDX-License-Identifier: GPL-3.0-only
package android.mtp;

public class MtpDeviceInfo {
    private String mManufacturer;
    private String mModel;
    private String mVersion;
    private String mSerialNumber;
    private int[] mOperationsSupported;
    private int[] mEventsSupported;

    private MtpDeviceInfo() {
    }

    /** @hide */
    public MtpDeviceInfo(
            String manufacturer,
            String model,
            String version,
            String serialNumber,
            int[] operationsSupported,
            int[] eventsSupported) {
        mManufacturer = manufacturer;
        mModel = model;
        mVersion = version;
        mSerialNumber = serialNumber;
        mOperationsSupported = operationsSupported != null
                ? operationsSupported.clone()
                : new int[0];
        mEventsSupported = eventsSupported != null
                ? eventsSupported.clone()
                : new int[0];
    }

    public final String getManufacturer() {
        return mManufacturer;
    }

    public final String getModel() {
        return mModel;
    }

    public final String getVersion() {
        return mVersion;
    }

    public final String getSerialNumber() {
        return mSerialNumber;
    }

    public final int[] getOperationsSupported() {
        return mOperationsSupported != null ? mOperationsSupported.clone() : new int[0];
    }

    public final int[] getEventsSupported() {
        return mEventsSupported != null ? mEventsSupported.clone() : new int[0];
    }

    private static boolean contains(int[] set, int code) {
        if (set == null) {
            return false;
        }
        for (int i = 0; i < set.length; i++) {
            if (set[i] == code) {
                return true;
            }
        }
        return false;
    }

    public boolean isOperationSupported(int code) {
        return contains(mOperationsSupported, code);
    }

    public boolean isEventSupported(int code) {
        return contains(mEventsSupported, code);
    }
}
