// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.data;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class UrspRule implements Parcelable {
    private final int mPrecedence;
    private final List<TrafficDescriptor> mTrafficDescriptors;
    private final List<RouteSelectionDescriptor> mRouteSelectionDescriptor;

    UrspRule(
            int precedence,
            List<TrafficDescriptor> trafficDescriptors,
            List<RouteSelectionDescriptor> routeSelectionDescriptor) {
        mPrecedence = precedence;
        mTrafficDescriptors = trafficDescriptors != null
                ? Collections.unmodifiableList(new ArrayList<TrafficDescriptor>(trafficDescriptors))
                : Collections.<TrafficDescriptor>emptyList();
        mRouteSelectionDescriptor = routeSelectionDescriptor != null
                ? Collections.unmodifiableList(
                        new ArrayList<RouteSelectionDescriptor>(routeSelectionDescriptor))
                : Collections.<RouteSelectionDescriptor>emptyList();
    }

    private UrspRule(Parcel p) {
        mPrecedence = p.readInt();
        List<TrafficDescriptor> tds = p.createTypedArrayList(TrafficDescriptor.CREATOR);
        List<RouteSelectionDescriptor> rsds =
                p.createTypedArrayList(RouteSelectionDescriptor.CREATOR);
        mTrafficDescriptors = tds != null
                ? Collections.unmodifiableList(tds)
                : Collections.<TrafficDescriptor>emptyList();
        mRouteSelectionDescriptor = rsds != null
                ? Collections.unmodifiableList(rsds)
                : Collections.<RouteSelectionDescriptor>emptyList();
    }

    public int getPrecedence() {
        return mPrecedence;
    }

    public List<TrafficDescriptor> getTrafficDescriptors() {
        return mTrafficDescriptors;
    }

    public List<RouteSelectionDescriptor> getRouteSelectionDescriptor() {
        return mRouteSelectionDescriptor;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPrecedence);
        dest.writeTypedList(mTrafficDescriptors);
        dest.writeTypedList(mRouteSelectionDescriptor);
    }

    public int describeContents() {
        return 0;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UrspRule)) return false;
        UrspRule that = (UrspRule) o;
        return mPrecedence == that.mPrecedence
                && Objects.equals(mTrafficDescriptors, that.mTrafficDescriptors)
                && Objects.equals(mRouteSelectionDescriptor, that.mRouteSelectionDescriptor);
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mPrecedence),
                mTrafficDescriptors,
                mRouteSelectionDescriptor);
    }

    public String toString() {
        return "{.precedence = " + mPrecedence
                + ", .trafficDescriptors = " + mTrafficDescriptors
                + ", .routeSelectionDescriptor = " + mRouteSelectionDescriptor + "}";
    }

    public static final Parcelable.Creator<UrspRule> CREATOR =
            new Parcelable.Creator<UrspRule>() {
                public UrspRule createFromParcel(Parcel source) {
                    return new UrspRule(source);
                }

                public UrspRule[] newArray(int size) {
                    return new UrspRule[size];
                }
            };
}
