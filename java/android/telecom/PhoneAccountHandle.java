// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.content.ComponentName;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserHandle;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Unique identifier for a {@link PhoneAccount} combining its service component, ID, and user. */
public final class PhoneAccountHandle implements Parcelable {
    private static final AtomicInteger NEXT_ICON_ID = new AtomicInteger(1);
    private static final ConcurrentHashMap<Integer, Icon> LOCAL_ICONS =
            new ConcurrentHashMap<Integer, Icon>();

    private final ComponentName mComponentName;
    private final String mId;
    private final UserHandle mUserHandle;

    public PhoneAccountHandle(ComponentName componentName, String id) {
        this(componentName, id, Process.myUserHandle());
    }

    public PhoneAccountHandle(
            ComponentName componentName, String id, UserHandle userHandle) {
        this.mComponentName = Objects.requireNonNull(componentName, "componentName");
        this.mId = Objects.requireNonNull(id, "id");
        this.mUserHandle = userHandle != null ? userHandle : UserHandle.SYSTEM;
    }

    private PhoneAccountHandle(Parcel in) {
        String flat = in.readString();
        this.mComponentName = flat == null ? null : ComponentName.unflattenFromString(flat);
        this.mId = in.readString();
        int userId = in.readInt();
        this.mUserHandle = userId < 0 ? UserHandle.SYSTEM : UserHandle.of(userId);
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public String getId() {
        return mId;
    }

    public UserHandle getUserHandle() {
        return mUserHandle;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mComponentName, mId, mUserHandle);
    }

    @Override
    public String toString() {
        return mComponentName + ", " + mId + ", " + mUserHandle;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PhoneAccountHandle)) {
            return false;
        }
        PhoneAccountHandle h = (PhoneAccountHandle) other;
        return Objects.equals(mComponentName, h.mComponentName)
                && Objects.equals(mId, h.mId)
                && Objects.equals(mUserHandle, h.mUserHandle);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mComponentName == null ? null : mComponentName.flattenToString());
        out.writeString(mId);
        out.writeInt(mUserHandle == null ? -1 : mUserHandle.getIdentifier());
    }

    static void writeIconToParcel(Icon icon, Parcel dest) {
        if (icon == null) {
            dest.writeInt(0);
            return;
        }
        int type = icon.getType();
        if (type == Icon.TYPE_RESOURCE) {
            dest.writeInt(1);
            dest.writeString(icon.getResPackage());
            dest.writeInt(icon.getResId());
        } else if (type == Icon.TYPE_URI) {
            dest.writeInt(2);
            dest.writeString(icon.getUri().toString());
        } else {
            int id = NEXT_ICON_ID.getAndIncrement();
            if (LOCAL_ICONS.size() >= 1024) {
                LOCAL_ICONS.clear();
            }
            LOCAL_ICONS.put(Integer.valueOf(id), icon);
            dest.writeInt(3);
            dest.writeInt(id);
        }
    }

    static Icon readIconFromParcel(Parcel in) {
        int mode = in.readInt();
        if (mode == 0) {
            return null;
        }
        if (mode == 1) {
            String pkg = in.readString();
            int resId = in.readInt();
            return Icon.createWithResource(pkg, resId);
        }
        if (mode == 2) {
            return Icon.createWithContentUri(in.readString());
        }
        if (mode == 3) {
            return LOCAL_ICONS.get(Integer.valueOf(in.readInt()));
        }
        throw new IllegalArgumentException("Invalid Icon parcel mode: " + mode);
    }

    static boolean iconsEqual(Icon a, Icon b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.getType() != b.getType()) {
            return false;
        }
        int type = a.getType();
        if (type == Icon.TYPE_RESOURCE) {
            return a.getResId() == b.getResId()
                    && Objects.equals(a.getResPackage(), b.getResPackage());
        }
        if (type == Icon.TYPE_URI) {
            return Objects.equals(a.getUri(), b.getUri());
        }
        return false;
    }

    static int iconHashCode(Icon icon) {
        if (icon == null) {
            return 0;
        }
        int type = icon.getType();
        if (type == Icon.TYPE_RESOURCE) {
            return Objects.hash(
                    Integer.valueOf(type),
                    icon.getResPackage(),
                    Integer.valueOf(icon.getResId()));
        }
        if (type == Icon.TYPE_URI) {
            return Objects.hash(Integer.valueOf(type), icon.getUri());
        }
        return icon.hashCode();
    }

    public static final Parcelable.Creator<PhoneAccountHandle> CREATOR =
            new Parcelable.Creator<PhoneAccountHandle>() {
                @Override
                public PhoneAccountHandle createFromParcel(Parcel in) {
                    return new PhoneAccountHandle(in);
                }

                @Override
                public PhoneAccountHandle[] newArray(int size) {
                    return new PhoneAccountHandle[size];
                }
            };
}
