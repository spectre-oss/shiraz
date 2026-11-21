// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SignalStrengthUpdateRequest implements Parcelable {
    private final List<SignalThresholdInfo> mSignalThresholdInfos;
    private final boolean mIsReportingRequestedWhileIdle;

    private SignalStrengthUpdateRequest(
            List<SignalThresholdInfo> signalThresholdInfos,
            boolean isReportingRequestedWhileIdle) {
        mSignalThresholdInfos = Collections.unmodifiableList(
                new ArrayList<SignalThresholdInfo>(signalThresholdInfos));
        mIsReportingRequestedWhileIdle = isReportingRequestedWhileIdle;
    }

    private SignalStrengthUpdateRequest(Parcel in) {
        ArrayList<SignalThresholdInfo> list = new ArrayList<SignalThresholdInfo>();
        in.readTypedList(list, SignalThresholdInfo.CREATOR);
        mSignalThresholdInfos = Collections.unmodifiableList(list);
        mIsReportingRequestedWhileIdle = in.readInt() != 0;
    }

    public Collection<SignalThresholdInfo> getSignalThresholdInfos() {
        return mSignalThresholdInfos;
    }

    public boolean isReportingRequestedWhileIdle() {
        return mIsReportingRequestedWhileIdle;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mSignalThresholdInfos);
        dest.writeInt(mIsReportingRequestedWhileIdle ? 1 : 0);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SignalStrengthUpdateRequest)) {
            return false;
        }
        SignalStrengthUpdateRequest request = (SignalStrengthUpdateRequest) other;
        return mIsReportingRequestedWhileIdle == request.mIsReportingRequestedWhileIdle
                && Objects.equals(mSignalThresholdInfos, request.mSignalThresholdInfos);
    }

    public int hashCode() {
        return Objects.hash(
                mSignalThresholdInfos, Boolean.valueOf(mIsReportingRequestedWhileIdle));
    }

    public String toString() {
        return "SignalStrengthUpdateRequest{mSignalThresholdInfos=" + mSignalThresholdInfos
                + " mIsReportingRequestedWhileIdle=" + mIsReportingRequestedWhileIdle + "}";
    }

    public static final Parcelable.Creator<SignalStrengthUpdateRequest> CREATOR =
            new Parcelable.Creator<SignalStrengthUpdateRequest>() {
                public SignalStrengthUpdateRequest createFromParcel(Parcel source) {
                    return new SignalStrengthUpdateRequest(source);
                }

                public SignalStrengthUpdateRequest[] newArray(int size) {
                    return new SignalStrengthUpdateRequest[size];
                }
            };

    public static final class Builder {
        private List<SignalThresholdInfo> mSignalThresholdInfos = null;
        private boolean mIsReportingRequestedWhileIdle = false;

        public Builder() {
        }

        public Builder setSignalThresholdInfos(
                Collection<SignalThresholdInfo> signalThresholdInfos) {
            Objects.requireNonNull(signalThresholdInfos,
                    "SignalThresholdInfo collection must not be null");
            for (SignalThresholdInfo info : signalThresholdInfos) {
                Objects.requireNonNull(info,
                        "SignalThresholdInfo in the collection must not be null");
            }
            mSignalThresholdInfos = new ArrayList<SignalThresholdInfo>(signalThresholdInfos);
            return this;
        }

        public Builder setReportingRequestedWhileIdle(boolean isReportingRequestedWhileIdle) {
            mIsReportingRequestedWhileIdle = isReportingRequestedWhileIdle;
            return this;
        }

        public SignalStrengthUpdateRequest build() {
            Objects.requireNonNull(mSignalThresholdInfos,
                    "SignalThresholdInfo collection must not be null");
            if (mSignalThresholdInfos.isEmpty()) {
                throw new IllegalArgumentException(
                        "SignalThresholdInfo collection must not be empty");
            }
            return new SignalStrengthUpdateRequest(
                    mSignalThresholdInfos, mIsReportingRequestedWhileIdle);
        }
    }
}
