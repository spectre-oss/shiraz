// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.icu.util.ULocale;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates metadata describing a next-generation audio presentation.
 */
public final class AudioPresentation implements Parcelable {
    public static final int CONTENT_UNKNOWN = -1;
    public static final int CONTENT_MAIN = 0;
    public static final int CONTENT_MUSIC_AND_EFFECTS = 1;
    public static final int CONTENT_VISUALLY_IMPAIRED = 2;
    public static final int CONTENT_HEARING_IMPAIRED = 3;
    public static final int CONTENT_DIALOG = 4;
    public static final int CONTENT_COMMENTARY = 5;
    public static final int CONTENT_EMERGENCY = 6;
    public static final int CONTENT_VOICEOVER = 7;

    public static final int MASTERING_NOT_INDICATED = 0;
    public static final int MASTERED_FOR_STEREO = 1;
    public static final int MASTERED_FOR_SURROUND = 2;
    public static final int MASTERED_FOR_3D = 3;
    public static final int MASTERED_FOR_HEADPHONE = 4;

    public static final int PRESENTATION_ID_UNKNOWN = -1;
    public static final int PROGRAM_ID_UNKNOWN = -1;

    private final int mPresentationId;
    private final int mProgramId;
    private final String mLanguageTag;
    private final int mMasteringIndication;
    private final boolean mAudioDescriptionAvailable;
    private final boolean mSpokenSubtitlesAvailable;
    private final boolean mDialogueEnhancementAvailable;
    private final Map<Locale, String> mLabels;

    AudioPresentation(
            int presentationId,
            int programId,
            String languageTag,
            int masteringIndication,
            boolean audioDescriptionAvailable,
            boolean spokenSubtitlesAvailable,
            boolean dialogueEnhancementAvailable,
            Map<Locale, String> labels) {
        mPresentationId = presentationId;
        mProgramId = programId;
        mLanguageTag = languageTag != null ? languageTag : "";
        mMasteringIndication = masteringIndication;
        mAudioDescriptionAvailable = audioDescriptionAvailable;
        mSpokenSubtitlesAvailable = spokenSubtitlesAvailable;
        mDialogueEnhancementAvailable = dialogueEnhancementAvailable;
        mLabels = Collections.unmodifiableMap(new LinkedHashMap<Locale, String>(labels));
    }

    AudioPresentation(Parcel in) {
        mPresentationId = in.readInt();
        mProgramId = in.readInt();
        mLanguageTag = in.readString();
        mMasteringIndication = in.readInt();
        mAudioDescriptionAvailable = in.readBoolean();
        mSpokenSubtitlesAvailable = in.readBoolean();
        mDialogueEnhancementAvailable = in.readBoolean();
        int count = in.readInt();
        Map<Locale, String> map = new LinkedHashMap<Locale, String>();
        for (int i = 0; i < count; i++) {
            String tag = in.readString();
            String label = in.readString();
            map.put(Locale.forLanguageTag(tag != null ? tag : ""), label != null ? label : "");
        }
        mLabels = Collections.unmodifiableMap(map);
    }

    public int getPresentationId() {
        return mPresentationId;
    }

    public int getProgramId() {
        return mProgramId;
    }

    public Map<Locale, String> getLabels() {
        return mLabels;
    }

    public Locale getLocale() {
        return Locale.forLanguageTag(mLanguageTag);
    }

    public int getMasteringIndication() {
        return mMasteringIndication;
    }

    public boolean hasAudioDescription() {
        return mAudioDescriptionAvailable;
    }

    public boolean hasSpokenSubtitles() {
        return mSpokenSubtitlesAvailable;
    }

    public boolean hasDialogueEnhancement() {
        return mDialogueEnhancementAvailable;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AudioPresentation)) return false;
        AudioPresentation other = (AudioPresentation) obj;
        return mPresentationId == other.mPresentationId
                && mProgramId == other.mProgramId
                && Objects.equals(mLanguageTag, other.mLanguageTag)
                && mMasteringIndication == other.mMasteringIndication
                && mAudioDescriptionAvailable == other.mAudioDescriptionAvailable
                && mSpokenSubtitlesAvailable == other.mSpokenSubtitlesAvailable
                && mDialogueEnhancementAvailable == other.mDialogueEnhancementAvailable
                && Objects.equals(mLabels, other.mLabels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mPresentationId,
                mProgramId,
                mLanguageTag,
                mMasteringIndication,
                mAudioDescriptionAvailable,
                mSpokenSubtitlesAvailable,
                mDialogueEnhancementAvailable,
                mLabels);
    }

    @Override
    public String toString() {
        return "AudioPresentation{id=" + mPresentationId
                + ", programId=" + mProgramId
                + ", locale=" + mLanguageTag
                + ", mastering=" + mMasteringIndication + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPresentationId);
        dest.writeInt(mProgramId);
        dest.writeString(mLanguageTag);
        dest.writeInt(mMasteringIndication);
        dest.writeBoolean(mAudioDescriptionAvailable);
        dest.writeBoolean(mSpokenSubtitlesAvailable);
        dest.writeBoolean(mDialogueEnhancementAvailable);
        dest.writeInt(mLabels.size());
        for (Map.Entry<Locale, String> entry : mLabels.entrySet()) {
            dest.writeString(entry.getKey() != null ? entry.getKey().toLanguageTag() : "");
            dest.writeString(entry.getValue());
        }
    }

    public static final Parcelable.Creator<AudioPresentation> CREATOR =
            new Parcelable.Creator<AudioPresentation>() {
                @Override
                public AudioPresentation createFromParcel(Parcel in) {
                    return new AudioPresentation(in);
                }

                @Override
                public AudioPresentation[] newArray(int size) {
                    return new AudioPresentation[size];
                }
            };

    public static final class Builder {
        private final int mPresentationId;
        private int mProgramId = PROGRAM_ID_UNKNOWN;
        private String mLanguageTag = "";
        private int mMasteringIndication = MASTERING_NOT_INDICATED;
        private boolean mAudioDescriptionAvailable;
        private boolean mSpokenSubtitlesAvailable;
        private boolean mDialogueEnhancementAvailable;
        private final Map<Locale, String> mLabels = new LinkedHashMap<Locale, String>();

        public Builder(int presentationId) {
            mPresentationId = presentationId;
        }

        public Builder setProgramId(int programId) {
            mProgramId = programId;
            return this;
        }

        public Builder setLocale(ULocale language) {
            Objects.requireNonNull(language, "language");
            mLanguageTag = language.toLanguageTag();
            return this;
        }

        public Builder setMasteringIndication(int masteringIndication) {
            if (masteringIndication != MASTERING_NOT_INDICATED
                    && masteringIndication != MASTERED_FOR_STEREO
                    && masteringIndication != MASTERED_FOR_SURROUND
                    && masteringIndication != MASTERED_FOR_3D
                    && masteringIndication != MASTERED_FOR_HEADPHONE) {
                throw new IllegalArgumentException("Unknown mastering indication: " + masteringIndication);
            }
            mMasteringIndication = masteringIndication;
            return this;
        }

        public Builder setLabels(Map<ULocale, CharSequence> labels) {
            Objects.requireNonNull(labels, "labels");
            mLabels.clear();
            for (Map.Entry<ULocale, CharSequence> entry : labels.entrySet()) {
                Locale loc = entry.getKey() != null ? entry.getKey().toLocale() : Locale.ROOT;
                String val = entry.getValue() != null ? entry.getValue().toString() : "";
                mLabels.put(loc, val);
            }
            return this;
        }

        public Builder setHasAudioDescription(boolean audioDescriptionAvailable) {
            mAudioDescriptionAvailable = audioDescriptionAvailable;
            return this;
        }

        public Builder setHasSpokenSubtitles(boolean spokenSubtitlesAvailable) {
            mSpokenSubtitlesAvailable = spokenSubtitlesAvailable;
            return this;
        }

        public Builder setHasDialogueEnhancement(boolean dialogueEnhancementAvailable) {
            mDialogueEnhancementAvailable = dialogueEnhancementAvailable;
            return this;
        }

        public AudioPresentation build() {
            return new AudioPresentation(
                    mPresentationId,
                    mProgramId,
                    mLanguageTag,
                    mMasteringIndication,
                    mAudioDescriptionAvailable,
                    mSpokenSubtitlesAvailable,
                    mDialogueEnhancementAvailable,
                    mLabels);
        }
    }
}
