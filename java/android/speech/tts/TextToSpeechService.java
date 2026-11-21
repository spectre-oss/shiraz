// SPDX-License-Identifier: GPL-3.0-only
package android.speech.tts;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Set;

public abstract class TextToSpeechService extends Service {
    private final Binder mBinder = new TtsServiceBinder(this);

    public TextToSpeechService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        String[] defaultLocale = onGetLanguage();
        if (defaultLocale != null && defaultLocale.length >= 1) {
            String lang = defaultLocale[0] != null ? defaultLocale[0] : "";
            String country = defaultLocale.length >= 2 && defaultLocale[1] != null
                    ? defaultLocale[1] : "";
            String variant = defaultLocale.length >= 3 && defaultLocale[2] != null
                    ? defaultLocale[2] : "";
            onLoadLanguage(lang, country, variant);
        }
    }

    @Override
    public void onDestroy() {
        onStop();
        super.onDestroy();
    }

    protected abstract int onIsLanguageAvailable(String lang, String country, String variant);

    protected abstract String[] onGetLanguage();

    protected abstract int onLoadLanguage(String lang, String country, String variant);

    protected abstract void onStop();

    protected abstract void onSynthesizeText(
            SynthesisRequest request, SynthesisCallback callback);

    protected Set<String> onGetFeaturesForLanguage(String lang, String country, String variant) {
        return new HashSet<String>();
    }

    public List<Voice> onGetVoices() {
        ArrayList<Voice> voices = new ArrayList<Voice>();
        for (Locale locale : Locale.getAvailableLocales()) {
            int expectedStatus = getExpectedLanguageAvailableStatus(locale);
            try {
                int status = onIsLanguageAvailable(
                        locale.getISO3Language(),
                        locale.getISO3Country(),
                        locale.getVariant());
                if (status != expectedStatus) {
                    continue;
                }
            } catch (MissingResourceException e) {
                continue;
            }
            Set<String> features = onGetFeaturesForLanguage(
                    locale.getISO3Language(),
                    locale.getISO3Country(),
                    locale.getVariant());
            if (features == null) {
                features = new HashSet<String>();
            }
            String voiceName = onGetDefaultVoiceNameFor(
                    locale.getISO3Language(),
                    locale.getISO3Country(),
                    locale.getVariant());
            if (voiceName != null) {
                voices.add(new Voice(
                        voiceName,
                        locale,
                        Voice.QUALITY_NORMAL,
                        Voice.LATENCY_NORMAL,
                        false,
                        features));
            }
        }
        return voices;
    }

    private static int getExpectedLanguageAvailableStatus(Locale locale) {
        int expectedStatus = TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
        if (locale.getVariant().isEmpty()) {
            if (locale.getCountry().isEmpty()) {
                expectedStatus = TextToSpeech.LANG_AVAILABLE;
            } else {
                expectedStatus = TextToSpeech.LANG_COUNTRY_AVAILABLE;
            }
        }
        return expectedStatus;
    }

    public String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
        String safeLang = lang != null ? lang : "";
        String safeCountry = country != null ? country : "";
        String safeVariant = variant != null ? variant : "";
        int localeStatus = onIsLanguageAvailable(safeLang, safeCountry, safeVariant);
        Locale iso2Locale;
        switch (localeStatus) {
            case TextToSpeech.LANG_AVAILABLE:
                iso2Locale = new Locale(safeLang);
                break;
            case TextToSpeech.LANG_COUNTRY_AVAILABLE:
                iso2Locale = new Locale(safeLang, safeCountry);
                break;
            case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
                iso2Locale = new Locale(safeLang, safeCountry, safeVariant);
                break;
            default:
                return null;
        }
        String languageTag = iso2Locale.toLanguageTag();
        if (onIsValidVoiceName(languageTag) == TextToSpeech.SUCCESS) {
            return languageTag;
        }
        return null;
    }

    public int onLoadVoice(String voiceName) {
        if (voiceName == null) {
            return TextToSpeech.ERROR;
        }
        Locale locale = Locale.forLanguageTag(voiceName);
        if (locale == null || locale.getLanguage().isEmpty()) {
            return TextToSpeech.ERROR;
        }
        int expectedStatus = getExpectedLanguageAvailableStatus(locale);
        try {
            int status = onIsLanguageAvailable(
                    locale.getISO3Language(),
                    locale.getISO3Country(),
                    locale.getVariant());
            if (status != expectedStatus) {
                return TextToSpeech.ERROR;
            }
            onLoadLanguage(
                    locale.getISO3Language(),
                    locale.getISO3Country(),
                    locale.getVariant());
            return TextToSpeech.SUCCESS;
        } catch (MissingResourceException e) {
            return TextToSpeech.ERROR;
        }
    }

    public int onIsValidVoiceName(String voiceName) {
        if (voiceName == null) {
            return TextToSpeech.ERROR;
        }
        Locale locale = Locale.forLanguageTag(voiceName);
        if (locale == null || locale.getLanguage().isEmpty()) {
            return TextToSpeech.ERROR;
        }
        int expectedStatus = getExpectedLanguageAvailableStatus(locale);
        try {
            int status = onIsLanguageAvailable(
                    locale.getISO3Language(),
                    locale.getISO3Country(),
                    locale.getVariant());
            if (status != expectedStatus) {
                return TextToSpeech.ERROR;
            }
            return TextToSpeech.SUCCESS;
        } catch (MissingResourceException e) {
            return TextToSpeech.ERROR;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE.equals(
                intent.getAction())) {
            return mBinder;
        }
        return null;
    }

    private static final class TtsServiceBinder extends Binder {
        private final TextToSpeechService mService;

        TtsServiceBinder(TextToSpeechService service) {
            mService = service;
        }

        TextToSpeechService getService() {
            return mService;
        }
    }
}
