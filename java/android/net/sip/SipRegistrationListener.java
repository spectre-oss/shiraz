// SPDX-License-Identifier: GPL-3.0-only
package android.net.sip;

public interface SipRegistrationListener {
    void onRegistering(String localProfileUri);

    void onRegistrationDone(String localProfileUri, long expiryTime);

    void onRegistrationFailed(String localProfileUri, int errorCode, String errorMessage);
}
