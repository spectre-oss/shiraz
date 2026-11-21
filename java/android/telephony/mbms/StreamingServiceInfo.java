// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.mbms;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StreamingServiceInfo extends ServiceInfo implements Parcelable {
    StreamingServiceInfo(
            Map<Locale, String> names,
            String className,
            List<Locale> locales,
            String serviceId,
            Date start,
            Date end) {
        super(names, className, locales, serviceId, start, end);
    }

    private StreamingServiceInfo(Parcel in) {
        super(in);
    }

    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
    }

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<StreamingServiceInfo> CREATOR =
            new Parcelable.Creator<StreamingServiceInfo>() {
                public StreamingServiceInfo createFromParcel(Parcel in) {
                    return new StreamingServiceInfo(in);
                }

                public StreamingServiceInfo[] newArray(int size) {
                    return new StreamingServiceInfo[size];
                }
            };
}
