// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.Parcelable;

public final class TableRequest extends BroadcastInfoRequest implements Parcelable {
    public static final int TABLE_NAME_PAT = 0;
    public static final int TABLE_NAME_PMT = 1;
    public static final int TABLE_NAME_CAT = 2;
    public static final int TABLE_NAME_NIT = 3;
    public static final int TABLE_NAME_BAT = 4;
    public static final int TABLE_NAME_SDT = 5;
    public static final int TABLE_NAME_EIT = 6;
    public static final int TABLE_NAME_TDT = 7;
    public static final int TABLE_NAME_TOT = 8;
    public static final int TABLE_NAME_SIT = 9;

    private final int mTableId;
    private final int mTableName;
    private final int mVersion;

    public TableRequest(int requestId, int option, int tableId, int tableName, int version) {
        super(TvInputManager.BROADCAST_INFO_TYPE_TABLE, requestId, option);
        mTableId = tableId;
        mTableName = tableName;
        mVersion = version;
    }

    TableRequest(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_TYPE_TABLE, source);
        mTableId = source.readInt();
        mTableName = source.readInt();
        mVersion = source.readInt();
    }

    static TableRequest createFromParcelBody(Parcel source) {
        return new TableRequest(source);
    }

    public int getTableId() {
        return mTableId;
    }

    public int getTableName() {
        return mTableName;
    }

    public int getVersion() {
        return mVersion;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mTableId);
        dest.writeInt(mTableName);
        dest.writeInt(mVersion);
    }

    public static final Parcelable.Creator<TableRequest> CREATOR =
            new Parcelable.Creator<TableRequest>() {
                @Override
                public TableRequest createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public TableRequest[] newArray(int size) {
                    return new TableRequest[size];
                }
            };
}
