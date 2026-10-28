// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

/** Legacy call-state listener for hosts without a modem. */
public class PhoneStateListener {
    public static final int LISTEN_NONE=0, LISTEN_CALL_STATE=32;
    final android.os.Handler handler;
    public PhoneStateListener() { this(android.os.Looper.myLooper()); }
    public PhoneStateListener(android.os.Looper looper) {
        handler=new android.os.Handler(looper);
    }
    public void onCallStateChanged(int state,String phoneNumber) {}
}
