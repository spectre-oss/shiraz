// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** JavaScript decision logic wrapper used for ad selection test overrides. */
public final class DecisionLogic implements Parcelable {
    private final String mLogic;

    public DecisionLogic(String buyerDecisionLogic) {
        this.mLogic = Objects.requireNonNull(buyerDecisionLogic, "buyerDecisionLogic");
    }

    public String getLogic() {
        return mLogic;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeString(mLogic);
    }

    @Override
    public String toString() {
        return mLogic;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLogic);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DecisionLogic)) {
            return false;
        }
        DecisionLogic that = (DecisionLogic) o;
        return Objects.equals(mLogic, that.mLogic);
    }

    public static final Parcelable.Creator<DecisionLogic> CREATOR =
            new Parcelable.Creator<DecisionLogic>() {
                @Override
                public DecisionLogic createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    return new DecisionLogic(in.readString());
                }

                @Override
                public DecisionLogic[] newArray(int size) {
                    return new DecisionLogic[size];
                }
            };
}
