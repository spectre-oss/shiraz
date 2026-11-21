// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.Parcelable;

public final class CommandRequest extends BroadcastInfoRequest implements Parcelable {
    public static final String ARGUMENT_TYPE_XML = "xml";
    public static final String ARGUMENT_TYPE_JSON = "json";

    private final String mNamespace;
    private final String mName;
    private final String mArguments;
    private final String mArgumentType;

    public CommandRequest(
            int requestId,
            int option,
            String namespace,
            String name,
            String arguments,
            String argumentType) {
        super(TvInputManager.BROADCAST_INFO_TYPE_COMMAND, requestId, option);
        mNamespace = namespace;
        mName = name;
        mArguments = arguments;
        mArgumentType = argumentType;
    }

    CommandRequest(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_TYPE_COMMAND, source);
        mNamespace = source.readString();
        mName = source.readString();
        mArguments = source.readString();
        mArgumentType = source.readString();
    }

    static CommandRequest createFromParcelBody(Parcel source) {
        return new CommandRequest(source);
    }

    public String getNamespace() {
        return mNamespace;
    }

    public String getName() {
        return mName;
    }

    public String getArguments() {
        return mArguments;
    }

    public String getArgumentType() {
        return mArgumentType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mNamespace);
        dest.writeString(mName);
        dest.writeString(mArguments);
        dest.writeString(mArgumentType);
    }

    public static final Parcelable.Creator<CommandRequest> CREATOR =
            new Parcelable.Creator<CommandRequest>() {
                @Override
                public CommandRequest createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public CommandRequest[] newArray(int size) {
                    return new CommandRequest[size];
                }
            };
}
