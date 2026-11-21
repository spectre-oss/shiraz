// SPDX-License-Identifier: GPL-3.0-only
package android.service.notification;

import android.app.Notification;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;

/** Snapshot of a notification owned by the current app. */
public class StatusBarNotification implements Parcelable, Cloneable {
    public static final Parcelable.Creator<StatusBarNotification> CREATOR =
            new Parcelable.Creator<StatusBarNotification>() {
                @Override
                public StatusBarNotification createFromParcel(Parcel parcel) {
                    return new StatusBarNotification(parcel);
                }

                @Override
                public StatusBarNotification[] newArray(int size) {
                    return new StatusBarNotification[size];
                }
            };

    private final String pkg;
    private final String opPkg;
    private final int id;
    private final String tag;
    private final int uid;
    private final int initialPid;
    private final Notification notification;
    private final UserHandle user;
    private final long time;
    private String overrideGroupKey;

    public StatusBarNotification(String pkg, String tag, int id, Notification notification,
            long time) {
        this(pkg, pkg, id, tag, 0, 0, 0, notification, UserHandle.of(0), time);
    }

    public StatusBarNotification(String pkg, String opPkg, int id, String tag, int uid,
            int initialPid, int score, Notification notification, UserHandle user, long postTime) {
        this.pkg = pkg;
        this.opPkg = opPkg != null ? opPkg : pkg;
        this.id = id;
        this.tag = tag;
        this.uid = uid;
        this.initialPid = initialPid;
        this.notification = notification;
        this.user = user != null ? user : UserHandle.of(0);
        this.time = postTime;
    }

    public StatusBarNotification(Parcel in) {
        this.pkg = in.readString();
        this.opPkg = in.readString();
        this.id = in.readInt();
        this.tag = in.readString();
        this.uid = in.readInt();
        this.initialPid = in.readInt();
        this.notification = in.readTypedObject(Notification.CREATOR);
        this.user = in.readTypedObject(UserHandle.CREATOR);
        this.time = in.readLong();
        this.overrideGroupKey = in.readString();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(pkg);
        out.writeString(opPkg);
        out.writeInt(id);
        out.writeString(tag);
        out.writeInt(uid);
        out.writeInt(initialPid);
        out.writeTypedObject(notification, flags);
        out.writeTypedObject(user, flags);
        out.writeLong(time);
        out.writeString(overrideGroupKey);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public StatusBarNotification clone() {
        try {
            return (StatusBarNotification) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public String toString() {
        return "StatusBarNotification(pkg=" + pkg + " id=" + id + " tag=" + tag + ")";
    }

    public boolean isGroup() {
        return overrideGroupKey != null || isAppGroup();
    }

    public boolean isAppGroup() {
        return notification != null
                && (notification.getGroup() != null || notification.getSortKey() != null);
    }

    public boolean isOngoing() {
        return notification != null
                && (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0;
    }

    public boolean isClearable() {
        return notification != null
                && (notification.flags
                        & (Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT)) == 0;
    }

    public int getUserId() {
        return user != null ? user.hashCode() : 0;
    }

    public String getPackageName() { return pkg; }
    public int getId() { return id; }
    public String getTag() { return tag; }
    public int getUid() { return uid; }
    public String getOpPkg() { return opPkg; }
    public Notification getNotification() { return notification; }
    public UserHandle getUser() { return user; }
    public long getPostTime() { return time; }
    public String getKey() { return pkg + "|" + id + "|" + tag; }

    public String getGroupKey() {
        if (overrideGroupKey != null) {
            return pkg + "|g:" + overrideGroupKey;
        }
        String group = notification != null ? notification.getGroup() : null;
        if (group != null) {
            return pkg + "|g:" + group;
        }
        return getKey();
    }

    public void setOverrideGroupKey(String overrideGroupKey) {
        this.overrideGroupKey = overrideGroupKey;
    }

    public String getOverrideGroupKey() {
        return overrideGroupKey;
    }
}
