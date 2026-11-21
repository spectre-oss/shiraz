// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Request parameters passed to {@link ConnectionService} to create a connection or conference. */
public final class ConnectionRequest implements Parcelable {
    private final PhoneAccountHandle mAccountHandle;
    private final Uri mAddress;
    private final Bundle mExtras;
    private final int mVideoState;
    private final List<Uri> mParticipants;
    private final boolean mIsAdhocConference;
    private final Connection.RttTextStream mRttTextStream;

    public ConnectionRequest(
            PhoneAccountHandle accountHandle, Uri handle, Bundle extras) {
        this(accountHandle, handle, extras, VideoProfile.STATE_AUDIO_ONLY);
    }

    public ConnectionRequest(
            PhoneAccountHandle accountHandle, Uri handle, Bundle extras, int videoState) {
        this(accountHandle, handle, extras, videoState, null, false, null);
    }

    ConnectionRequest(
            PhoneAccountHandle accountHandle,
            Uri handle,
            Bundle extras,
            int videoState,
            List<Uri> participants,
            boolean isAdhocConference,
            Connection.RttTextStream rttTextStream) {
        this.mAccountHandle = accountHandle;
        this.mAddress = handle;
        this.mExtras = extras == null ? null : new Bundle(extras);
        this.mVideoState = videoState;
        this.mParticipants =
                participants == null
                        ? null
                        : Collections.unmodifiableList(new ArrayList<Uri>(participants));
        this.mIsAdhocConference = isAdhocConference;
        this.mRttTextStream = rttTextStream;
    }

    private ConnectionRequest(Parcel in) {
        this.mAccountHandle = in.readTypedObject(PhoneAccountHandle.CREATOR);
        String addr = in.readString();
        this.mAddress = addr == null ? null : Uri.parse(addr);
        this.mExtras = in.readBundle(ConnectionRequest.class.getClassLoader());
        this.mVideoState = in.readInt();
        int partCount = in.readInt();
        if (partCount < 0) {
            this.mParticipants = null;
        } else {
            ArrayList<Uri> list = new ArrayList<Uri>(partCount);
            for (int i = 0; i < partCount; i++) {
                String u = in.readString();
                if (u != null) {
                    list.add(Uri.parse(u));
                }
            }
            this.mParticipants = Collections.unmodifiableList(list);
        }
        this.mIsAdhocConference = in.readBoolean();
        this.mRttTextStream = null;
    }

    public PhoneAccountHandle getAccountHandle() {
        return mAccountHandle;
    }

    public Uri getAddress() {
        return mAddress;
    }

    public List<Uri> getParticipants() {
        return mParticipants;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    public int getVideoState() {
        return mVideoState;
    }

    public boolean isAdhocConferenceCall() {
        return mIsAdhocConference;
    }

    public Connection.RttTextStream getRttTextStream() {
        return mRttTextStream;
    }

    public boolean isRequestingRtt() {
        return mRttTextStream != null
                || (mExtras != null
                        && mExtras.getBoolean(TelecomManager.EXTRA_START_CALL_WITH_RTT, false));
    }

    @Override
    public String toString() {
        return "ConnectionRequest{accountHandle="
                + mAccountHandle
                + ", address="
                + mAddress
                + ", videoState="
                + VideoProfile.videoStateToString(mVideoState)
                + ", extras="
                + (mExtras == null ? "" : mExtras)
                + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeTypedObject(mAccountHandle, flags);
        destination.writeString(mAddress == null ? null : mAddress.toString());
        destination.writeBundle(mExtras);
        destination.writeInt(mVideoState);
        if (mParticipants == null) {
            destination.writeInt(-1);
        } else {
            destination.writeInt(mParticipants.size());
            for (Uri u : mParticipants) {
                destination.writeString(u == null ? null : u.toString());
            }
        }
        destination.writeBoolean(mIsAdhocConference);
    }

    public static final Parcelable.Creator<ConnectionRequest> CREATOR =
            new Parcelable.Creator<ConnectionRequest>() {
                @Override
                public ConnectionRequest createFromParcel(Parcel source) {
                    return new ConnectionRequest(source);
                }

                @Override
                public ConnectionRequest[] newArray(int size) {
                    return new ConnectionRequest[size];
                }
            };
}
