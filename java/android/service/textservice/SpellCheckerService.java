// SPDX-License-Identifier: GPL-3.0-only
package android.service.textservice;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;
import java.util.Locale;

/** SpellCheckerService provides an abstract base class for each spell checker service. */
public abstract class SpellCheckerService extends Service {
    public static final String SERVICE_INTERFACE = "android.service.textservice.SpellCheckerService";

    private final IBinder mBinder = new Binder();

    public SpellCheckerService() {
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return mBinder;
    }

    public abstract Session createSession();

    public abstract static class Session {
        private String mLocale = Locale.getDefault().toString();
        private Bundle mBundle = new Bundle();
        private int mSupportedAttributes;

        public Session() {
        }

        public abstract void onCreate();

        public abstract SuggestionsInfo onGetSuggestions(TextInfo textInfo, int suggestionsLimit);

        public SuggestionsInfo[] onGetSuggestionsMultiple(
                TextInfo[] textInfos, int suggestionsLimit, boolean sequentialWords) {
            if (textInfos == null) return new SuggestionsInfo[0];
            SuggestionsInfo[] retval = new SuggestionsInfo[textInfos.length];
            for (int i = 0; i < textInfos.length; i++) {
                SuggestionsInfo si = onGetSuggestions(textInfos[i], suggestionsLimit);
                if (si != null && textInfos[i] != null) {
                    si.setCookieAndSequence(textInfos[i].getCookie(), textInfos[i].getSequence());
                }
                retval[i] = si;
            }
            return retval;
        }

        public SentenceSuggestionsInfo[] onGetSentenceSuggestionsMultiple(
                TextInfo[] textInfos, int suggestionsLimit) {
            if (textInfos == null) return new SentenceSuggestionsInfo[0];
            SentenceSuggestionsInfo[] result = new SentenceSuggestionsInfo[textInfos.length];
            for (int i = 0; i < textInfos.length; i++) {
                TextInfo ti = textInfos[i];
                SuggestionsInfo si = onGetSuggestions(ti, suggestionsLimit);
                if (si == null) {
                    si = new SuggestionsInfo(0, new String[0]);
                }
                int len = ti != null && ti.getText() != null ? ti.getText().length() : 0;
                if (ti != null) {
                    si.setCookieAndSequence(ti.getCookie(), ti.getSequence());
                }
                result[i] = new SentenceSuggestionsInfo(
                        new SuggestionsInfo[]{si}, new int[]{0}, new int[]{len});
            }
            return result;
        }

        public void onCancel() {
        }

        public void onClose() {
        }

        public String getLocale() {
            return mLocale;
        }

        public Bundle getBundle() {
            return mBundle;
        }

        public int getSupportedAttributes() {
            return mSupportedAttributes;
        }
    }
}
