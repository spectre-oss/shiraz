// SPDX-License-Identifier: GPL-3.0-only
package android.speech;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class RecognitionPart implements Parcelable {
    public static final int CONFIDENCE_LEVEL_UNKNOWN = 0;
    public static final int CONFIDENCE_LEVEL_LOW = 1;
    public static final int CONFIDENCE_LEVEL_MEDIUM_LOW = 2;
    public static final int CONFIDENCE_LEVEL_MEDIUM = 3;
    public static final int CONFIDENCE_LEVEL_MEDIUM_HIGH = 4;
    public static final int CONFIDENCE_LEVEL_HIGH = 5;

    private final String mRawText;
    private final String mFormattedText;
    private final long mTimestampMillis;
    private final int mConfidenceLevel;

    private RecognitionPart(
            String rawText,
            String formattedText,
            long timestampMillis,
            int confidenceLevel) {
        Objects.requireNonNull(rawText, "rawText must not be null");
        if (timestampMillis < 0) {
            throw new IllegalArgumentException("timestampMillis must be >= 0");
        }
        if (confidenceLevel < CONFIDENCE_LEVEL_UNKNOWN || confidenceLevel > CONFIDENCE_LEVEL_HIGH) {
            throw new IllegalArgumentException("Invalid confidenceLevel: " + confidenceLevel);
        }
        mRawText = rawText;
        mFormattedText = formattedText;
        mTimestampMillis = timestampMillis;
        mConfidenceLevel = confidenceLevel;
    }

    private RecognitionPart(Parcel in) {
        String raw = in.readString();
        String formatted = in.readString();
        long timestamp = in.readLong();
        int confidence = in.readInt();
        mRawText = Objects.requireNonNull(raw, "rawText must not be null");
        mFormattedText = formatted;
        mTimestampMillis = timestamp;
        mConfidenceLevel = confidence;
    }

    public String getRawText() {
        return mRawText;
    }

    public String getFormattedText() {
        return mFormattedText;
    }

    public long getTimestampMillis() {
        return mTimestampMillis;
    }

    public int getConfidenceLevel() {
        return mConfidenceLevel;
    }

    @Override
    public String toString() {
        return "RecognitionPart { rawText = " + mRawText
                + ", formattedText = " + mFormattedText
                + ", timestampMillis = " + mTimestampMillis
                + ", confidenceLevel = " + mConfidenceLevel + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RecognitionPart that = (RecognitionPart) o;
        return mTimestampMillis == that.mTimestampMillis
                && mConfidenceLevel == that.mConfidenceLevel
                && Objects.equals(mRawText, that.mRawText)
                && Objects.equals(mFormattedText, that.mFormattedText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRawText, mFormattedText, mTimestampMillis, mConfidenceLevel);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mRawText);
        dest.writeString(mFormattedText);
        dest.writeLong(mTimestampMillis);
        dest.writeInt(mConfidenceLevel);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<RecognitionPart> CREATOR =
            new Parcelable.Creator<RecognitionPart>() {
                @Override
                public RecognitionPart createFromParcel(Parcel in) {
                    return new RecognitionPart(in);
                }

                @Override
                public RecognitionPart[] newArray(int size) {
                    return new RecognitionPart[size];
                }
            };

    public static final class Builder {
        private String mRawText;
        private String mFormattedText;
        private long mTimestampMillis = 0L;
        private int mConfidenceLevel = CONFIDENCE_LEVEL_UNKNOWN;

        public Builder(String rawText) {
            mRawText = Objects.requireNonNull(rawText, "rawText must not be null");
        }

        public Builder setRawText(String value) {
            mRawText = Objects.requireNonNull(value, "rawText must not be null");
            return this;
        }

        public Builder setFormattedText(String value) {
            mFormattedText = value;
            return this;
        }

        public Builder setTimestampMillis(long value) {
            if (value < 0) {
                throw new IllegalArgumentException("timestampMillis must be >= 0");
            }
            mTimestampMillis = value;
            return this;
        }

        public Builder setConfidenceLevel(int value) {
            if (value < CONFIDENCE_LEVEL_UNKNOWN || value > CONFIDENCE_LEVEL_HIGH) {
                throw new IllegalArgumentException("Invalid confidenceLevel: " + value);
            }
            mConfidenceLevel = value;
            return this;
        }

        public RecognitionPart build() {
            return new RecognitionPart(
                    mRawText,
                    mFormattedText,
                    mTimestampMillis,
                    mConfidenceLevel);
        }
    }
}
