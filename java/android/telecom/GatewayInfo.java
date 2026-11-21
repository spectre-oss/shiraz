// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/** Gateway routing metadata encapsulating original and gateway call addresses. */
public class GatewayInfo implements Parcelable {
    private final String mGatewayProviderPackageName;
    private final Uri mGatewayAddress;
    private final Uri mOriginalAddress;

    public GatewayInfo(
            String packageName, Uri gatewayUri, Uri originalAddress) {
        this.mGatewayProviderPackageName = packageName;
        this.mGatewayAddress = gatewayUri;
        this.mOriginalAddress = originalAddress;
    }

    private GatewayInfo(Parcel in) {
        this.mGatewayProviderPackageName = in.readString();
        String gw = in.readString();
        this.mGatewayAddress = gw == null ? null : Uri.parse(gw);
        String orig = in.readString();
        this.mOriginalAddress = orig == null ? null : Uri.parse(orig);
    }

    public String getGatewayProviderPackageName() {
        return mGatewayProviderPackageName;
    }

    public Uri getGatewayAddress() {
        return mGatewayAddress;
    }

    public Uri getOriginalAddress() {
        return mOriginalAddress;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(mGatewayProviderPackageName) || mGatewayAddress == null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeString(mGatewayProviderPackageName);
        destination.writeString(mGatewayAddress == null ? null : mGatewayAddress.toString());
        destination.writeString(mOriginalAddress == null ? null : mOriginalAddress.toString());
    }

    public static final Parcelable.Creator<GatewayInfo> CREATOR =
            new Parcelable.Creator<GatewayInfo>() {
                @Override
                public GatewayInfo createFromParcel(Parcel source) {
                    return new GatewayInfo(source);
                }

                @Override
                public GatewayInfo[] newArray(int size) {
                    return new GatewayInfo[size];
                }
            };
}
