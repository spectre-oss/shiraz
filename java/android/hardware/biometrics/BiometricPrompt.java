// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.biometrics;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.CancellationSignal;
import android.security.identity.IdentityCredential;
import android.security.identity.PresentationSession;
import android.text.TextUtils;
import java.security.Signature;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.crypto.Cipher;
import javax.crypto.Mac;

public class BiometricPrompt {
    public static final int BIOMETRIC_ACQUIRED_GOOD = 0;
    public static final int BIOMETRIC_ACQUIRED_PARTIAL = 1;
    public static final int BIOMETRIC_ACQUIRED_INSUFFICIENT = 2;
    public static final int BIOMETRIC_ACQUIRED_IMAGER_DIRTY = 3;
    public static final int BIOMETRIC_ACQUIRED_TOO_SLOW = 4;
    public static final int BIOMETRIC_ACQUIRED_TOO_FAST = 5;

    public static final int BIOMETRIC_ERROR_HW_UNAVAILABLE = 1;
    public static final int BIOMETRIC_ERROR_UNABLE_TO_PROCESS = 2;
    public static final int BIOMETRIC_ERROR_TIMEOUT = 3;
    public static final int BIOMETRIC_ERROR_NO_SPACE = 4;
    public static final int BIOMETRIC_ERROR_CANCELED = 5;
    public static final int BIOMETRIC_ERROR_LOCKOUT = 7;
    public static final int BIOMETRIC_ERROR_VENDOR = 8;
    public static final int BIOMETRIC_ERROR_LOCKOUT_PERMANENT = 9;
    public static final int BIOMETRIC_ERROR_USER_CANCELED = 10;
    public static final int BIOMETRIC_ERROR_NO_BIOMETRICS = 11;
    public static final int BIOMETRIC_ERROR_HW_NOT_PRESENT = 12;
    public static final int BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL = 14;
    public static final int BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED = 15;

    public static final int AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL = 1;
    public static final int AUTHENTICATION_RESULT_TYPE_BIOMETRIC = 2;

    public static final long BIOMETRIC_NO_AUTHENTICATION = -1L;

    private final int mLogoRes;
    private final Bitmap mLogoBitmap;
    private final String mLogoDescription;
    private final CharSequence mTitle;
    private final CharSequence mSubtitle;
    private final CharSequence mDescription;
    private final PromptContentView mContentView;
    private final CharSequence mNegativeButtonText;
    private final boolean mConfirmationRequired;
    private final int mAllowedAuthenticators;

    private BiometricPrompt(
            int logoRes,
            Bitmap logoBitmap,
            String logoDescription,
            CharSequence title,
            CharSequence subtitle,
            CharSequence description,
            PromptContentView contentView,
            CharSequence negativeButtonText,
            boolean confirmationRequired,
            int allowedAuthenticators) {
        mLogoRes = logoRes;
        mLogoBitmap = logoBitmap;
        mLogoDescription = logoDescription;
        mTitle = title;
        mSubtitle = subtitle;
        mDescription = description;
        mContentView = contentView;
        mNegativeButtonText = negativeButtonText;
        mConfirmationRequired = confirmationRequired;
        mAllowedAuthenticators = allowedAuthenticators;
    }

    public int getLogoRes() {
        return mLogoRes;
    }

    public Bitmap getLogoBitmap() {
        return mLogoBitmap;
    }

    public String getLogoDescription() {
        return mLogoDescription;
    }

    public CharSequence getTitle() {
        return mTitle;
    }

    public CharSequence getSubtitle() {
        return mSubtitle;
    }

    public CharSequence getDescription() {
        return mDescription;
    }

    public PromptContentView getContentView() {
        return mContentView;
    }

    public CharSequence getNegativeButtonText() {
        return mNegativeButtonText;
    }

    public boolean isConfirmationRequired() {
        return mConfirmationRequired;
    }

    public int getAllowedAuthenticators() {
        return mAllowedAuthenticators;
    }

    public void authenticate(
            CryptoObject crypto,
            CancellationSignal cancel,
            Executor executor,
            final AuthenticationCallback callback) {
        Objects.requireNonNull(crypto, "Must supply a crypto object");
        Objects.requireNonNull(cancel, "Must supply a cancellation signal");
        Objects.requireNonNull(executor, "Must supply an executor");
        Objects.requireNonNull(callback, "Must supply a callback");
        if (cancel.isCanceled()) {
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onAuthenticationError(
                        BIOMETRIC_ERROR_HW_NOT_PRESENT, "Biometric hardware not present");
            }
        });
    }

    public void authenticate(
            CancellationSignal cancel,
            Executor executor,
            final AuthenticationCallback callback) {
        Objects.requireNonNull(cancel, "Must supply a cancellation signal");
        Objects.requireNonNull(executor, "Must supply an executor");
        Objects.requireNonNull(callback, "Must supply a callback");
        if (cancel.isCanceled()) {
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onAuthenticationError(
                        BIOMETRIC_ERROR_HW_NOT_PRESENT, "Biometric hardware not present");
            }
        });
    }

    public static class Builder {
        private final Context mContext;
        private int mLogoRes = -1;
        private Bitmap mLogoBitmap;
        private String mLogoDescription;
        private CharSequence mTitle;
        private CharSequence mSubtitle;
        private CharSequence mDescription;
        private PromptContentView mContentView;
        private CharSequence mNegativeButtonText;
        private Executor mNegativeButtonExecutor;
        private DialogInterface.OnClickListener mNegativeButtonListener;
        private boolean mConfirmationRequired = true;
        private boolean mDeviceCredentialAllowed;
        private int mAllowedAuthenticators;

        public Builder(Context context) {
            mContext = context;
        }

        public Builder setLogoRes(int logoRes) {
            if (mLogoBitmap != null) {
                throw new IllegalStateException(
                        "Exclusively one of logo resource or logo bitmap can be set");
            }
            mLogoRes = logoRes;
            return this;
        }

        public Builder setLogoBitmap(Bitmap logoBitmap) {
            Objects.requireNonNull(logoBitmap, "logoBitmap");
            if (mLogoRes != -1) {
                throw new IllegalStateException(
                        "Exclusively one of logo resource or logo bitmap can be set");
            }
            mLogoBitmap = logoBitmap;
            return this;
        }

        public Builder setLogoDescription(String logoDescription) {
            if (logoDescription == null || logoDescription.isEmpty()) {
                throw new IllegalArgumentException("Logo description passed in can not be null");
            }
            mLogoDescription = logoDescription;
            return this;
        }

        public Builder setTitle(CharSequence title) {
            mTitle = title;
            return this;
        }

        public Builder setSubtitle(CharSequence subtitle) {
            mSubtitle = subtitle;
            return this;
        }

        public Builder setDescription(CharSequence description) {
            mDescription = description;
            return this;
        }

        public Builder setContentView(PromptContentView view) {
            mContentView = view;
            return this;
        }

        public Builder setNegativeButton(
                CharSequence text,
                Executor executor,
                DialogInterface.OnClickListener listener) {
            if (TextUtils.isEmpty(text)) {
                throw new IllegalArgumentException("Text must be set and non-empty");
            }
            Objects.requireNonNull(executor, "Executor must not be null");
            Objects.requireNonNull(listener, "Listener must not be null");
            mNegativeButtonText = text;
            mNegativeButtonExecutor = executor;
            mNegativeButtonListener = listener;
            return this;
        }

        public Builder setConfirmationRequired(boolean requireConfirmation) {
            mConfirmationRequired = requireConfirmation;
            return this;
        }

        @Deprecated
        public Builder setDeviceCredentialAllowed(boolean allowed) {
            mDeviceCredentialAllowed = allowed;
            return this;
        }

        public Builder setAllowedAuthenticators(int authenticators) {
            mAllowedAuthenticators = authenticators;
            return this;
        }

        public BiometricPrompt build() {
            if (TextUtils.isEmpty(mTitle)) {
                throw new IllegalArgumentException("Title must be set and non-empty");
            }
            int authenticators = mAllowedAuthenticators;
            if (authenticators == 0 && mDeviceCredentialAllowed) {
                authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
            }
            boolean allowDeviceCredential =
                    (authenticators & BiometricManager.Authenticators.DEVICE_CREDENTIAL) != 0;
            if (TextUtils.isEmpty(mNegativeButtonText) && !allowDeviceCredential) {
                throw new IllegalArgumentException("Negative text must be set and non-empty");
            }
            return new BiometricPrompt(
                    mLogoRes,
                    mLogoBitmap,
                    mLogoDescription,
                    mTitle,
                    mSubtitle,
                    mDescription,
                    mContentView,
                    mNegativeButtonText,
                    mConfirmationRequired,
                    authenticators);
        }
    }

    public static final class CryptoObject {
        private final Object mCrypto;
        private final long mOpHandle;

        public CryptoObject(Signature signature) {
            mCrypto = Objects.requireNonNull(signature, "signature");
            mOpHandle = 0L;
        }

        public CryptoObject(Cipher cipher) {
            mCrypto = Objects.requireNonNull(cipher, "cipher");
            mOpHandle = 0L;
        }

        public CryptoObject(Mac mac) {
            mCrypto = Objects.requireNonNull(mac, "mac");
            mOpHandle = 0L;
        }

        @Deprecated
        public CryptoObject(IdentityCredential credential) {
            mCrypto = Objects.requireNonNull(credential, "credential");
            mOpHandle = 0L;
        }

        public CryptoObject(PresentationSession session) {
            mCrypto = Objects.requireNonNull(session, "session");
            mOpHandle = 0L;
        }

        public CryptoObject(long operationHandle) {
            mCrypto = null;
            mOpHandle = operationHandle;
        }

        public Signature getSignature() {
            return mCrypto instanceof Signature ? (Signature) mCrypto : null;
        }

        public Cipher getCipher() {
            return mCrypto instanceof Cipher ? (Cipher) mCrypto : null;
        }

        public Mac getMac() {
            return mCrypto instanceof Mac ? (Mac) mCrypto : null;
        }

        @Deprecated
        public IdentityCredential getIdentityCredential() {
            return mCrypto instanceof IdentityCredential ? (IdentityCredential) mCrypto : null;
        }

        public PresentationSession getPresentationSession() {
            return mCrypto instanceof PresentationSession ? (PresentationSession) mCrypto : null;
        }

        public long getOperationHandle() {
            return mOpHandle;
        }
    }

    public static class AuthenticationResult {
        private final CryptoObject mCryptoObject;
        private final int mAuthenticationType;

        public AuthenticationResult(CryptoObject crypto, int authenticationType) {
            mCryptoObject = crypto;
            mAuthenticationType = authenticationType;
        }

        public CryptoObject getCryptoObject() {
            return mCryptoObject;
        }

        public int getAuthenticationType() {
            return mAuthenticationType;
        }
    }

    public abstract static class AuthenticationCallback {
        public AuthenticationCallback() {
        }

        public void onAuthenticationError(int errorCode, CharSequence errString) {
        }

        public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
        }

        public void onAuthenticationSucceeded(AuthenticationResult result) {
        }

        public void onAuthenticationFailed() {
        }
    }
}
