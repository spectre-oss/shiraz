// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Action used to change the visibility of child views in a CustomDescription RemoteViews. */
public final class VisibilitySetterAction implements OnClickAction, Parcelable {
    private final LinkedHashMap<Integer, Integer> mVisibilities;

    VisibilitySetterAction(LinkedHashMap<Integer, Integer> visibilities) {
        mVisibilities = new LinkedHashMap<Integer, Integer>(visibilities);
    }

    VisibilitySetterAction(Parcel in) {
        mVisibilities = new LinkedHashMap<Integer, Integer>();
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            int id = in.readInt();
            int vis = in.readInt();
            mVisibilities.put(Integer.valueOf(id), Integer.valueOf(vis));
        }
    }

    @Override
    public String toString() {
        return "VisibilitySetterAction: [" + mVisibilities + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mVisibilities.size());
        for (Map.Entry<Integer, Integer> e : mVisibilities.entrySet()) {
            parcel.writeInt(e.getKey().intValue());
            parcel.writeInt(e.getValue().intValue());
        }
    }

    public static final Parcelable.Creator<VisibilitySetterAction> CREATOR =
            new Parcelable.Creator<VisibilitySetterAction>() {
                @Override
                public VisibilitySetterAction createFromParcel(Parcel parcel) {
                    return new VisibilitySetterAction(parcel);
                }

                @Override
                public VisibilitySetterAction[] newArray(int size) {
                    return new VisibilitySetterAction[size];
                }
            };

    public static final class Builder {
        private final LinkedHashMap<Integer, Integer> mVisibilities = new LinkedHashMap<Integer, Integer>();

        public Builder(int id, int visibility) {
            setVisibility(id, visibility);
        }

        public Builder setVisibility(int id, int visibility) {
            mVisibilities.put(Integer.valueOf(id), Integer.valueOf(visibility));
            return this;
        }

        public VisibilitySetterAction build() {
            return new VisibilitySetterAction(mVisibilities);
        }
    }
}
