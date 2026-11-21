// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Single training example and resumption token produced for federated compute. */
public final class TrainingExampleRecord implements Parcelable {
    private final byte[] mTrainingExample;
    private final byte[] mResumptionToken;

    private TrainingExampleRecord(byte[] trainingExample, byte[] resumptionToken) {
        this.mTrainingExample =
                trainingExample == null ? null : trainingExample.clone();
        this.mResumptionToken =
                resumptionToken == null ? null : resumptionToken.clone();
    }

    public byte[] getTrainingExample() {
        return mTrainingExample == null ? null : mTrainingExample.clone();
    }

    public byte[] getResumptionToken() {
        return mResumptionToken == null ? null : mResumptionToken.clone();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeByteArray(mTrainingExample);
        dest.writeByteArray(mResumptionToken);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final class Builder {
        private byte[] mTrainingExample;
        private byte[] mResumptionToken;

        public Builder() {
        }

        public Builder setTrainingExample(byte... value) {
            this.mTrainingExample = value == null ? null : value.clone();
            return this;
        }

        public Builder setResumptionToken(byte... value) {
            this.mResumptionToken = value == null ? null : value.clone();
            return this;
        }

        public TrainingExampleRecord build() {
            return new TrainingExampleRecord(mTrainingExample, mResumptionToken);
        }
    }

    public static final Parcelable.Creator<TrainingExampleRecord> CREATOR =
            new Parcelable.Creator<TrainingExampleRecord>() {
                @Override
                public TrainingExampleRecord createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    byte[] ex = in.createByteArray();
                    byte[] tok = in.createByteArray();
                    return new TrainingExampleRecord(ex, tok);
                }

                @Override
                public TrainingExampleRecord[] newArray(int size) {
                    return new TrainingExampleRecord[size];
                }
            };
}
