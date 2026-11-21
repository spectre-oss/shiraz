// SPDX-License-Identifier: GPL-3.0-only
package android.location;

public interface OnNmeaMessageListener {
    void onNmeaMessage(String message, long timestamp);
}
