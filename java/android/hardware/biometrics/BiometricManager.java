// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.biometrics;

public class BiometricManager {
    public static final int BIOMETRIC_SUCCESS = 0;
    public static final int BIOMETRIC_ERROR_HW_UNAVAILABLE = 1;
    public static final int BIOMETRIC_ERROR_NONE_ENROLLED = 11;
    public static final int BIOMETRIC_ERROR_NO_HARDWARE = 12;
    public static final int BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED = 15;
    public static final long BIOMETRIC_NO_AUTHENTICATION = -1L;

    public interface Authenticators {
        int BIOMETRIC_STRONG = 15;
        int BIOMETRIC_WEAK = 255;
        int DEVICE_CREDENTIAL = 32768;
    }

    public static class Strings {
        private final CharSequence mButtonLabel;
        private final CharSequence mPromptMessage;
        private final CharSequence mSettingName;

        Strings(
                CharSequence buttonLabel,
                CharSequence promptMessage,
                CharSequence settingName) {
            mButtonLabel = buttonLabel;
            mPromptMessage = promptMessage;
            mSettingName = settingName;
        }

        public CharSequence getButtonLabel() {
            return mButtonLabel;
        }

        public CharSequence getPromptMessage() {
            return mPromptMessage;
        }

        public CharSequence getSettingName() {
            return mSettingName;
        }
    }

    public BiometricManager() {
    }

    @Deprecated
    public int canAuthenticate() {
        return canAuthenticate(Authenticators.BIOMETRIC_WEAK);
    }

    public int canAuthenticate(int authenticators) {
        return BIOMETRIC_ERROR_NO_HARDWARE;
    }

    public Strings getStrings(int authenticators) {
        return new Strings(null, null, null);
    }

    public long getLastAuthenticationTime(int authenticators) {
        int allowed = Authenticators.BIOMETRIC_STRONG
                | Authenticators.BIOMETRIC_WEAK
                | Authenticators.DEVICE_CREDENTIAL;
        if (authenticators == 0 || (authenticators & ~allowed) != 0) {
            throw new IllegalArgumentException("Invalid authenticators " + authenticators);
        }
        return BIOMETRIC_NO_AUTHENTICATION;
    }
}
