// SPDX-License-Identifier: GPL-3.0-only
package android.nfc;
/** No host NFC controller is bridged. */
public final class NfcAdapter {
 private NfcAdapter(){}
 public static NfcAdapter getDefaultAdapter(android.content.Context context){if(context==null)throw new IllegalArgumentException("context");return null;}
 public boolean isEnabled(){return false;}
}
