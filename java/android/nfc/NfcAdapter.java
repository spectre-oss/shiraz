// SPDX-License-Identifier: GPL-3.0-only
package android.nfc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import java.util.Collections;

/** No host NFC controller is bridged; API 35 surface reports disabled/unsupported deterministically. */
public final class NfcAdapter {
    public static final String ACTION_NDEF_DISCOVERED = "android.nfc.action.NDEF_DISCOVERED";
    public static final String ACTION_TECH_DISCOVERED = "android.nfc.action.TECH_DISCOVERED";
    public static final String ACTION_TAG_DISCOVERED = "android.nfc.action.TAG_DISCOVERED";
    public static final String ACTION_TRANSACTION_DETECTED =
            "android.nfc.action.TRANSACTION_DETECTED";
    public static final String ACTION_PREFERRED_PAYMENT_CHANGED =
            "android.nfc.action.PREFERRED_PAYMENT_CHANGED";
    public static final String ACTION_ADAPTER_STATE_CHANGED =
            "android.nfc.action.ADAPTER_STATE_CHANGED";

    public static final String EXTRA_TAG = "android.nfc.extra.TAG";
    public static final String EXTRA_NDEF_MESSAGES = "android.nfc.extra.NDEF_MESSAGES";
    public static final String EXTRA_ID = "android.nfc.extra.ID";
    public static final String EXTRA_ADAPTER_STATE = "android.nfc.extra.ADAPTER_STATE";
    public static final String EXTRA_READER_PRESENCE_CHECK_DELAY = "presence";
    public static final String EXTRA_AID = "android.nfc.extra.AID";
    public static final String EXTRA_DATA = "android.nfc.extra.DATA";
    public static final String EXTRA_SECURE_ELEMENT_NAME =
            "android.nfc.extra.SECURE_ELEMENT_NAME";
    public static final String EXTRA_PREFERRED_PAYMENT_CHANGED_REASON =
            "android.nfc.extra.PREFERRED_PAYMENT_CHANGED_REASON";

    public static final int STATE_OFF = 1;
    public static final int STATE_TURNING_ON = 2;
    public static final int STATE_ON = 3;
    public static final int STATE_TURNING_OFF = 4;

    public static final int FLAG_READER_DISABLE = 0;
    public static final int FLAG_READER_NFC_A = 0x1;
    public static final int FLAG_READER_NFC_B = 0x2;
    public static final int FLAG_READER_NFC_F = 0x4;
    public static final int FLAG_READER_NFC_V = 0x8;
    public static final int FLAG_READER_NFC_BARCODE = 0x10;
    public static final int FLAG_READER_SKIP_NDEF_CHECK = 0x80;
    public static final int FLAG_READER_NO_PLATFORM_SOUNDS = 0x100;
    public static final int FLAG_READER_KEEP = 0x80000000;

    public static final int FLAG_LISTEN_DISABLE = 0;
    public static final int FLAG_LISTEN_NFC_PASSIVE_A = 0x1;
    public static final int FLAG_LISTEN_NFC_PASSIVE_B = 0x2;
    public static final int FLAG_LISTEN_NFC_PASSIVE_F = 0x4;
    public static final int FLAG_LISTEN_KEEP = 0x80000000;

    public static final int PREFERRED_PAYMENT_LOADED = 1;
    public static final int PREFERRED_PAYMENT_CHANGED = 2;
    public static final int PREFERRED_PAYMENT_UPDATED = 3;

    public interface ReaderCallback {
        void onTagDiscovered(Tag tag);
    }

    public interface OnTagRemovedListener {
        void onTagRemoved();
    }

    @Deprecated
    public interface CreateNdefMessageCallback {
        NdefMessage createNdefMessage(NfcEvent event);
    }

    @Deprecated
    public interface OnNdefPushCompleteCallback {
        void onNdefPushComplete(NfcEvent event);
    }

    @Deprecated
    public interface CreateBeamUrisCallback {
        Uri[] createBeamUris(NfcEvent event);
    }

    NfcAdapter() {
    }

    public static NfcAdapter getDefaultAdapter(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context");
        }
        return null;
    }

    public boolean isEnabled() {
        return false;
    }

    public boolean isObserveModeSupported() {
        return false;
    }

    public boolean isObserveModeEnabled() {
        return false;
    }

    public boolean setObserveModeEnabled(boolean enabled) {
        return false;
    }

    public void enableForegroundDispatch(
            Activity activity,
            PendingIntent intent,
            IntentFilter[] filters,
            String[][] techLists) {
        if (activity == null || intent == null) {
            throw new NullPointerException();
        }
    }

    public void disableForegroundDispatch(Activity activity) {
        if (activity == null) {
            throw new NullPointerException();
        }
    }

    public void enableReaderMode(
            Activity activity, ReaderCallback callback, int flags, Bundle extras) {
        if (activity == null || callback == null) {
            throw new NullPointerException();
        }
    }

    public void disableReaderMode(Activity activity) {
        if (activity == null) {
            throw new NullPointerException();
        }
    }

    public void setDiscoveryTechnology(
            Activity activity, int pollTechnology, int listenTechnology) {
        if (activity == null) {
            throw new NullPointerException();
        }
    }

    public void resetDiscoveryTechnology(Activity activity) {
        if (activity == null) {
            throw new NullPointerException();
        }
    }

    public boolean isSecureNfcSupported() {
        return false;
    }

    public NfcAntennaInfo getNfcAntennaInfo() {
        return new NfcAntennaInfo(
                0, 0, false, Collections.<AvailableNfcAntenna>emptyList());
    }

    public boolean isSecureNfcEnabled() {
        return false;
    }

    public boolean isReaderOptionSupported() {
        return false;
    }

    public boolean isReaderOptionEnabled() {
        return false;
    }

    public boolean ignore(
            Tag tag,
            int debounceMs,
            OnTagRemovedListener tagRemovedListener,
            Handler handler) {
        if (tag == null) {
            throw new NullPointerException("tag");
        }
        return false;
    }
}
