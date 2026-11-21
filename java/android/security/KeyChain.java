// SPDX-License-Identifier: GPL-3.0-only
package android.security;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.Objects;

public final class KeyChain {
    public static final String ACTION_KEYCHAIN_CHANGED = "android.security.action.KEYCHAIN_CHANGED";
    public static final String ACTION_KEY_ACCESS_CHANGED = "android.security.action.KEY_ACCESS_CHANGED";
    @Deprecated
    public static final String ACTION_STORAGE_CHANGED = "android.security.STORAGE_CHANGED";
    public static final String ACTION_TRUST_STORE_CHANGED = "android.security.action.TRUST_STORE_CHANGED";
    public static final String EXTRA_CERTIFICATE = "CERT";
    public static final String EXTRA_KEY_ACCESSIBLE = "android.security.extra.KEY_ACCESSIBLE";
    public static final String EXTRA_KEY_ALIAS = "android.security.extra.KEY_ALIAS";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_PKCS12 = "PKCS12";
    public static final String KEY_ALIAS_SELECTION_DENIED = "android:alias-selection-denied";

    public KeyChain() {
    }

    public static Intent createInstallIntent() {
        Intent intent = new Intent("android.credentials.INSTALL");
        intent.setClassName("com.android.certinstaller", "com.android.certinstaller.CertInstallerMain");
        return intent;
    }

    public static Intent createManageCredentialsIntent(AppUriAuthenticationPolicy policy) {
        Objects.requireNonNull(policy, "Authentication policy must not be null");
        Intent intent = new Intent("android.security.MANAGE_CREDENTIALS");
        intent.putExtra("android.security.extra.AUTHENTICATION_POLICY", policy);
        return intent;
    }

    public static void choosePrivateKeyAlias(Activity activity, KeyChainAliasCallback response,
            String[] keyTypes, Principal[] issuers, String host, int port, String alias) {
        Objects.requireNonNull(activity, "activity == null");
        Objects.requireNonNull(response, "response == null");
        response.alias(null);
    }

    public static void choosePrivateKeyAlias(Activity activity, KeyChainAliasCallback response,
            String[] keyTypes, Principal[] issuers, Uri uri, String alias) {
        Objects.requireNonNull(activity, "activity == null");
        Objects.requireNonNull(response, "response == null");
        response.alias(null);
    }

    public static boolean isCredentialManagementApp(Context context) {
        return false;
    }

    public static AppUriAuthenticationPolicy getCredentialManagementAppPolicy(Context context)
            throws SecurityException {
        throw new SecurityException("The caller is not the credential management app");
    }

    public static boolean removeCredentialManagementApp(Context context) {
        return false;
    }

    public static PrivateKey getPrivateKey(Context context, String alias)
            throws InterruptedException, KeyChainException {
        Objects.requireNonNull(alias, "alias == null");
        return null;
    }

    public static X509Certificate[] getCertificateChain(Context context, String alias)
            throws InterruptedException, KeyChainException {
        Objects.requireNonNull(alias, "alias == null");
        return null;
    }

    public static boolean isKeyAlgorithmSupported(String algorithm) {
        if (algorithm == null) return false;
        String upper = algorithm.toUpperCase(Locale.US);
        return "RSA".equals(upper) || "EC".equals(upper);
    }

    @Deprecated
    public static boolean isBoundKeyAlgorithm(String algorithm) {
        return false;
    }
}
