// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;
import android.os.Bundle;
public abstract class CellLocation {
    public static CellLocation getEmpty() { return newFromBundle(new Bundle()); }
    public static CellLocation newFromBundle(Bundle values) {
        switch (new TelephonyManager().getPhoneType()) {
            case 1: return new android.telephony.gsm.GsmCellLocation(values);
            case 2: return new android.telephony.cdma.CdmaCellLocation(values);
            default: return null;
        }
    }
    public static void requestLocationUpdate() { /* No host modem. */ }
    public abstract void fillInNotifierBundle(Bundle values);
    public abstract boolean isEmpty();
    public abstract void setStateInvalid();
}
