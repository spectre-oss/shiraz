// SPDX-License-Identifier: GPL-3.0-only

package android.view.textservice;

import android.os.Bundle;
import java.util.Locale;

/** Spell checking is unavailable when the host has no spelling service. */
public class SpellCheckerSession {
    public static final String SERVICE_META_DATA = "android.view.textservice.scs";

    private final SpellCheckerInfo mSpellCheckerInfo;
    private boolean mClosed;

    SpellCheckerSession(SpellCheckerInfo info) {
        mSpellCheckerInfo = info;
    }

    public boolean isSessionDisconnected() {
        return mClosed || mSpellCheckerInfo == null;
    }

    public SpellCheckerInfo getSpellChecker() {
        return mSpellCheckerInfo;
    }

    public void cancel() {
    }

    public void close() {
        mClosed = true;
    }

    public void getSentenceSuggestions(TextInfo[] textInfos, int suggestionsLimit) {
    }

    @Deprecated
    public void getSuggestions(TextInfo textInfo, int suggestionsLimit) {
    }

    @Deprecated
    public void getSuggestions(TextInfo[] textInfos, int suggestionsLimit, boolean sequentialWords) {
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    public interface SpellCheckerSessionListener {
        void onGetSuggestions(SuggestionsInfo[] results);

        void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results);
    }

    public static class SpellCheckerSessionParams {
        private final Locale mLocale;
        private final boolean mShouldReferToSpellCheckerLanguageSettings;
        private final int mSupportedAttributes;
        private final Bundle mExtras;

        SpellCheckerSessionParams(
                Locale locale,
                boolean referToSettings,
                int supportedAttributes,
                Bundle extras) {
            mLocale = locale;
            mShouldReferToSpellCheckerLanguageSettings = referToSettings;
            mSupportedAttributes = supportedAttributes;
            mExtras = extras != null ? new Bundle(extras) : Bundle.EMPTY;
        }

        public Locale getLocale() {
            return mLocale;
        }

        public boolean shouldReferToSpellCheckerLanguageSettings() {
            return mShouldReferToSpellCheckerLanguageSettings;
        }

        public int getSupportedAttributes() {
            return mSupportedAttributes;
        }

        public Bundle getExtras() {
            return mExtras;
        }

        public static final class Builder {
            private Locale mLocale;
            private boolean mShouldReferToSpellCheckerLanguageSettings = false;
            private int mSupportedAttributes = 0;
            private Bundle mExtras = Bundle.EMPTY;

            public Builder() {
            }

            public SpellCheckerSessionParams build() {
                if (mLocale == null && !mShouldReferToSpellCheckerLanguageSettings) {
                    throw new NullPointerException(
                            "Locale should not be null if you are not referring to settings.");
                }
                return new SpellCheckerSessionParams(
                        mLocale,
                        mShouldReferToSpellCheckerLanguageSettings,
                        mSupportedAttributes,
                        mExtras);
            }

            public Builder setLocale(Locale locale) {
                mLocale = locale;
                return this;
            }

            public Builder setShouldReferToSpellCheckerLanguageSettings(
                    boolean shouldReferToSpellCheckerLanguageSettings) {
                mShouldReferToSpellCheckerLanguageSettings =
                        shouldReferToSpellCheckerLanguageSettings;
                return this;
            }

            public Builder setSupportedAttributes(int supportedAttributes) {
                mSupportedAttributes = supportedAttributes;
                return this;
            }

            public Builder setExtras(Bundle extras) {
                if (extras == null) {
                    throw new NullPointerException("extras must not be null");
                }
                mExtras = extras;
                return this;
            }
        }
    }
}
