// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.telecom.PhoneAccountHandle;

public final class VisualVoicemailSms implements Parcelable {
    private final PhoneAccountHandle mPhoneAccountHandle;
    private final String mPrefix;
    private final Bundle mFields;
    private final String mMessageBody;

    VisualVoicemailSms(PhoneAccountHandle phoneAccountHandle, String prefix, Bundle fields,
            String messageBody) {
        mPhoneAccountHandle = phoneAccountHandle;
        mPrefix = prefix;
        mFields = fields != null ? new Bundle(fields) : null;
        mMessageBody = messageBody;
    }

    private VisualVoicemailSms(Parcel in) {
        if (in.readInt() != 0) {
            mPhoneAccountHandle = PhoneAccountHandle.CREATOR.createFromParcel(in);
        } else {
            mPhoneAccountHandle = null;
        }
        mPrefix = in.readString();
        mFields = in.readBundle();
        mMessageBody = in.readString();
    }

    public PhoneAccountHandle getPhoneAccountHandle() {
        return mPhoneAccountHandle;
    }

    public String getPrefix() {
        return mPrefix;
    }

    public Bundle getFields() {
        return mFields != null ? new Bundle(mFields) : null;
    }

    public String getMessageBody() {
        return mMessageBody;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        if (mPhoneAccountHandle != null) {
            dest.writeInt(1);
            mPhoneAccountHandle.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeString(mPrefix);
        dest.writeBundle(mFields);
        dest.writeString(mMessageBody);
    }

    public static final Parcelable.Creator<VisualVoicemailSms> CREATOR =
            new Parcelable.Creator<VisualVoicemailSms>() {
                public VisualVoicemailSms createFromParcel(Parcel in) {
                    return new VisualVoicemailSms(in);
                }

                public VisualVoicemailSms[] newArray(int size) {
                    return new VisualVoicemailSms[size];
                }
            };
}
