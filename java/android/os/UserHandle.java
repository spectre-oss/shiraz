// SPDX-License-Identifier: GPL-3.0-only
package android.os;

/** Logical Android user identity. Shiraz sessions currently expose only user zero. */
public final class UserHandle implements Parcelable {
    public static final UserHandle SYSTEM = new UserHandle(0);
    private final int identifier;
    public UserHandle(int identifier) { this.identifier = identifier; }
    public static UserHandle of(int identifier) { return identifier == 0 ? SYSTEM : new UserHandle(identifier); }
    public int getIdentifier() { return identifier; }
    public static int myUserId() { return 0; }
    @Override public boolean equals(Object other) { return other instanceof UserHandle && ((UserHandle) other).identifier == identifier; }
    @Override public int hashCode() { return identifier; }
    @Override public String toString() { return "UserHandle{" + identifier + "}"; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel destination, int flags) { throw new UnsupportedOperationException("UserHandle Parcel transport is not implemented"); }
    public static final Parcelable.Creator<UserHandle> CREATOR = new Parcelable.Creator<UserHandle>() {
        public UserHandle createFromParcel(Parcel source) { throw new UnsupportedOperationException("UserHandle Parcel transport is not implemented"); }
        public UserHandle[] newArray(int size) { return new UserHandle[size]; }
    };
}
