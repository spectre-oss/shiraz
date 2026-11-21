// SPDX-License-Identifier: GPL-3.0-only
package android.speech.tts;

import android.os.Bundle;

public final class SynthesisRequest {
    private static final int DEFAULT_SPEECH_RATE = 100;
    private static final int DEFAULT_PITCH = 100;

    private final CharSequence mText;
    private final Bundle mParams;
    private String mVoiceName;
    private String mLanguage;
    private String mCountry;
    private String mVariant;
    private int mSpeechRate = DEFAULT_SPEECH_RATE;
    private int mPitch = DEFAULT_PITCH;
    private int mCallerUid;

    public SynthesisRequest(String text, Bundle params) {
        mText = text;
        mParams = params != null ? new Bundle(params) : new Bundle();
    }

    public SynthesisRequest(CharSequence text, Bundle params) {
        mText = text;
        mParams = params != null ? new Bundle(params) : new Bundle();
    }

    @Deprecated
    public String getText() {
        return mText != null ? mText.toString() : null;
    }

    public CharSequence getCharSequenceText() {
        return mText;
    }

    public String getVoiceName() {
        return mVoiceName;
    }

    public String getLanguage() {
        return mLanguage;
    }

    public String getCountry() {
        return mCountry;
    }

    public String getVariant() {
        return mVariant;
    }

    public int getSpeechRate() {
        return mSpeechRate;
    }

    public int getPitch() {
        return mPitch;
    }

    public Bundle getParams() {
        return mParams;
    }

    public int getCallerUid() {
        return mCallerUid;
    }

    void setLanguage(String language, String country, String variant) {
        mLanguage = language;
        mCountry = country;
        mVariant = variant;
    }

    void setVoiceName(String voiceName) {
        mVoiceName = voiceName;
    }

    void setSpeechRate(int speechRate) {
        mSpeechRate = speechRate;
    }

    void setPitch(int pitch) {
        mPitch = pitch;
    }

    void setCallerUid(int uid) {
        mCallerUid = uid;
    }
}
