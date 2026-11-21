// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

public final class InputBinding implements Parcelable {
    private final InputConnection mConnection;
    private final IBinder mConnectionToken;
    private final int mUid;
    private final int mPid;

    public InputBinding(InputConnection conn, IBinder connToken, int uid, int pid) {
        mConnection = conn;
        mConnectionToken = connToken;
        mUid = uid;
        mPid = pid;
    }

    public InputBinding(InputConnection conn, InputBinding binding) {
        mConnection = conn;
        mConnectionToken = binding != null ? binding.getConnectionToken() : null;
        mUid = binding != null ? binding.getUid() : 0;
        mPid = binding != null ? binding.getPid() : 0;
    }

    InputBinding(Parcel in) {
        mConnection = null;
        mConnectionToken = in.readStrongBinder();
        mUid = in.readInt();
        mPid = in.readInt();
    }

    public InputConnection getConnection() {
        return mConnection;
    }

    public IBinder getConnectionToken() {
        return mConnectionToken;
    }

    public int getUid() {
        return mUid;
    }

    public int getPid() {
        return mPid;
    }

    @Override
    public String toString() {
        return "InputBinding{" + mConnectionToken + " / uid " + mUid + " / pid " + mPid + "}";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStrongBinder(mConnectionToken);
        dest.writeInt(mUid);
        dest.writeInt(mPid);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<InputBinding> CREATOR = new Creator<InputBinding>() {
        @Override
        public InputBinding createFromParcel(Parcel in) {
            return new InputBinding(in);
        }

        @Override
        public InputBinding[] newArray(int size) {
            return new InputBinding[size];
        }
    };
}
