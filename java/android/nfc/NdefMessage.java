// SPDX-License-Identifier: GPL-3.0-only
package android.nfc;

import android.os.Parcel;
import android.os.Parcelable;
import java.nio.ByteBuffer;
import java.util.Arrays;

public final class NdefMessage implements Parcelable {
    private final NdefRecord[] mRecords;

    public NdefMessage(byte[] data) throws FormatException {
        if (data == null) {
            throw new NullPointerException("data is null");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        mRecords = NdefRecord.parse(buffer, false);
        if (buffer.remaining() > 0) {
            throw new FormatException("trailing data");
        }
    }

    public NdefMessage(NdefRecord record, NdefRecord... records) {
        if (record == null) {
            throw new NullPointerException("record cannot be null");
        }
        if (records == null) {
            throw new NullPointerException("records cannot be null");
        }
        for (int i = 0; i < records.length; i++) {
            if (records[i] == null) {
                throw new NullPointerException("record cannot be null");
            }
        }
        mRecords = new NdefRecord[1 + records.length];
        mRecords[0] = record;
        System.arraycopy(records, 0, mRecords, 1, records.length);
    }

    public NdefMessage(NdefRecord[] records) {
        if (records == null) {
            throw new NullPointerException("records cannot be null");
        }
        if (records.length < 1) {
            throw new IllegalArgumentException("must have at least one record");
        }
        for (int i = 0; i < records.length; i++) {
            if (records[i] == null) {
                throw new NullPointerException("records cannot contain null");
            }
        }
        mRecords = new NdefRecord[records.length];
        System.arraycopy(records, 0, mRecords, 0, records.length);
    }

    public NdefRecord[] getRecords() {
        return mRecords.clone();
    }

    public int getByteArrayLength() {
        int length = 0;
        for (int i = 0; i < mRecords.length; i++) {
            length += mRecords[i].getByteLength();
        }
        return length;
    }

    public byte[] toByteArray() {
        int length = getByteArrayLength();
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (int i = 0; i < mRecords.length; i++) {
            boolean mb = (i == 0);
            boolean me = (i == mRecords.length - 1);
            mRecords[i].writeToByteBuffer(buffer, mb, me);
        }
        return buffer.array();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRecords.length);
        dest.writeTypedArray(mRecords, flags);
    }

    public static final Parcelable.Creator<NdefMessage> CREATOR =
            new Parcelable.Creator<NdefMessage>() {
                @Override
                public NdefMessage createFromParcel(Parcel in) {
                    in.readInt();
                    NdefRecord[] records = in.createTypedArray(NdefRecord.CREATOR);
                    return new NdefMessage(records);
                }

                @Override
                public NdefMessage[] newArray(int size) {
                    return new NdefMessage[size];
                }
            };

    @Override
    public int hashCode() {
        return Arrays.hashCode(mRecords);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof NdefMessage)) {
            return false;
        }
        NdefMessage other = (NdefMessage) obj;
        return Arrays.equals(mRecords, other.mRecords);
    }

    @Override
    public String toString() {
        return "NdefMessage " + Arrays.toString(mRecords);
    }
}
