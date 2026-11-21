// SPDX-License-Identifier: GPL-3.0-only
package android.app.usage;

import android.content.res.Configuration;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UsageEvents implements Parcelable {
    private final List<Event> mEvents;
    private int mIndex;

    public UsageEvents() {
        mEvents = Collections.emptyList();
    }

    public UsageEvents(List<Event> events) {
        mEvents = events != null ? new ArrayList<Event>(events) : Collections.<Event>emptyList();
    }

    UsageEvents(Parcel in) {
        int count = in.readInt();
        if (count <= 0) {
            mEvents = Collections.emptyList();
        } else {
            List<Event> list = new ArrayList<Event>(count);
            for (int i = 0; i < count; i++) {
                Event ev = new Event();
                ev.mPackage = in.readString();
                ev.mClass = in.readString();
                ev.mTimeStamp = in.readLong();
                ev.mEventType = in.readInt();
                ev.mConfiguration = in.readTypedObject(Configuration.CREATOR);
                ev.mShortcutId = in.readString();
                ev.mBucketAndReason = in.readInt();
                ev.mExtras = in.readTypedObject(PersistableBundle.CREATOR);
                list.add(ev);
            }
            mEvents = list;
        }
    }

    public boolean hasNextEvent() {
        return mIndex < mEvents.size();
    }

    public boolean getNextEvent(Event eventOut) {
        if (eventOut == null) throw new NullPointerException("eventOut");
        if (mIndex >= mEvents.size()) {
            return false;
        }
        Event src = mEvents.get(mIndex++);
        eventOut.mPackage = src.mPackage;
        eventOut.mClass = src.mClass;
        eventOut.mTimeStamp = src.mTimeStamp;
        eventOut.mEventType = src.mEventType;
        eventOut.mConfiguration = src.mConfiguration;
        eventOut.mShortcutId = src.mShortcutId;
        eventOut.mBucketAndReason = src.mBucketAndReason;
        eventOut.mExtras = src.mExtras;
        return true;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mEvents.size());
        for (int i = 0; i < mEvents.size(); i++) {
            Event ev = mEvents.get(i);
            dest.writeString(ev.mPackage);
            dest.writeString(ev.mClass);
            dest.writeLong(ev.mTimeStamp);
            dest.writeInt(ev.mEventType);
            dest.writeTypedObject(ev.mConfiguration, flags);
            dest.writeString(ev.mShortcutId);
            dest.writeInt(ev.mBucketAndReason);
            dest.writeTypedObject(ev.mExtras, flags);
        }
    }

    public static final Parcelable.Creator<UsageEvents> CREATOR =
            new Parcelable.Creator<UsageEvents>() {
                @Override
                public UsageEvents createFromParcel(Parcel in) {
                    return new UsageEvents(in);
                }

                @Override
                public UsageEvents[] newArray(int size) {
                    return new UsageEvents[size];
                }
            };

    public static final class Event {
        public static final int NONE = 0;
        public static final int MOVE_TO_FOREGROUND = 1;
        public static final int ACTIVITY_RESUMED = 1;
        public static final int MOVE_TO_BACKGROUND = 2;
        public static final int ACTIVITY_PAUSED = 2;
        public static final int CONFIGURATION_CHANGE = 5;
        public static final int USER_INTERACTION = 7;
        public static final int SHORTCUT_INVOCATION = 8;
        public static final int STANDBY_BUCKET_CHANGED = 11;
        public static final int SCREEN_INTERACTIVE = 15;
        public static final int SCREEN_NON_INTERACTIVE = 16;
        public static final int KEYGUARD_SHOWN = 17;
        public static final int KEYGUARD_HIDDEN = 18;
        public static final int FOREGROUND_SERVICE_START = 19;
        public static final int FOREGROUND_SERVICE_STOP = 20;
        public static final int ACTIVITY_STOPPED = 23;
        public static final int DEVICE_SHUTDOWN = 26;
        public static final int DEVICE_STARTUP = 27;

        public String mPackage;
        public String mClass;
        public long mTimeStamp;
        public int mEventType;
        public Configuration mConfiguration;
        public String mShortcutId;
        public int mBucketAndReason;
        public PersistableBundle mExtras;

        public Event() {
        }

        public String getPackageName() {
            return mPackage;
        }

        public String getClassName() {
            return mClass;
        }

        public long getTimeStamp() {
            return mTimeStamp;
        }

        public int getEventType() {
            return mEventType;
        }

        public PersistableBundle getExtras() {
            return mExtras == null ? PersistableBundle.EMPTY : mExtras;
        }

        public Configuration getConfiguration() {
            return mConfiguration;
        }

        public String getShortcutId() {
            return mShortcutId;
        }

        public int getAppStandbyBucket() {
            return (mBucketAndReason & 0xFFFF0000) >>> 16;
        }
    }
}
