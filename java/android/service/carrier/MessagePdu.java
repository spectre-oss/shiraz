// SPDX-License-Identifier: GPL-3.0-only
package android.service.carrier;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MessagePdu implements Parcelable {
    public static final Parcelable.Creator<MessagePdu> CREATOR =
            new Parcelable.Creator<MessagePdu>() {
                @Override
                public MessagePdu createFromParcel(Parcel source) {
                    int size = source.readInt();
                    if (size < 0) {
                        return new MessagePdu(Collections.<byte[]>singletonList(null));
                    }
                    List<byte[]> pdus = new ArrayList<byte[]>(size);
                    for (int i = 0; i < size; i++) {
                        pdus.add(source.createByteArray());
                    }
                    return new MessagePdu(pdus);
                }

                @Override
                public MessagePdu[] newArray(int size) {
                    return new MessagePdu[size];
                }
            };

    private final List<byte[]> mPduList;

    public MessagePdu(List<byte[]> pduList) {
        Objects.requireNonNull(pduList, "pduList must not be null");
        mPduList = Collections.unmodifiableList(new ArrayList<byte[]>(pduList));
    }

    public List<byte[]> getPdus() {
        return mPduList;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPduList.size());
        for (int i = 0; i < mPduList.size(); i++) {
            dest.writeByteArray(mPduList.get(i));
        }
    }
}
