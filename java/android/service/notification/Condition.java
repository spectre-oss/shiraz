// SPDX-License-Identifier: GPL-3.0-only
package android.service.notification;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class Condition implements Parcelable {
    public static final String SCHEME = "condition";
    public static final int STATE_FALSE = 0;
    public static final int STATE_TRUE = 1;
    public static final int STATE_UNKNOWN = 2;
    public static final int STATE_ERROR = 3;
    public static final int FLAG_RELEVANT_NOW = 1;
    public static final int FLAG_RELEVANT_ALWAYS = 2;
    public static final int SOURCE_UNKNOWN = 0;
    public static final int SOURCE_USER_ACTION = 1;
    public static final int SOURCE_SCHEDULE = 2;
    public static final int SOURCE_CONTEXT = 3;

    public static final Parcelable.Creator<Condition> CREATOR =
            new Parcelable.Creator<Condition>() {
                @Override
                public Condition createFromParcel(Parcel source) {
                    return new Condition(source);
                }

                @Override
                public Condition[] newArray(int size) {
                    return new Condition[size];
                }
            };

    public final Uri id;
    public final String summary;
    public final String line1;
    public final String line2;
    public final int icon;
    public final int state;
    public final int flags;
    public final int source;

    public Condition(Uri id, String summary, int state) {
        this(id, summary, "", "", -1, state, SOURCE_UNKNOWN, FLAG_RELEVANT_ALWAYS);
    }

    public Condition(Uri id, String summary, int state, int source) {
        this(id, summary, "", "", -1, state, source, FLAG_RELEVANT_ALWAYS);
    }

    public Condition(Uri id, String summary, String line1, String line2,
            int icon, int state, int flags) {
        this(id, summary, line1, line2, icon, state, SOURCE_UNKNOWN, flags);
    }

    public Condition(Uri id, String summary, String line1, String line2,
            int icon, int state, int source, int flags) {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (summary == null) throw new IllegalArgumentException("summary is required");
        this.id = id;
        this.summary = summary;
        this.line1 = line1 != null ? line1 : "";
        this.line2 = line2 != null ? line2 : "";
        this.icon = icon;
        this.state = state;
        this.source = source;
        this.flags = flags;
    }

    public Condition(Parcel in) {
        this(
                in.readTypedObject(Uri.CREATOR),
                in.readString(),
                in.readString(),
                in.readString(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(id, 0);
        dest.writeString(summary);
        dest.writeString(line1);
        dest.writeString(line2);
        dest.writeInt(icon);
        dest.writeInt(state);
        dest.writeInt(source);
        dest.writeInt(this.flags);
    }

    @Override
    public String toString() {
        return "Condition[id=" + id + ",summary=" + summary + ",state=" + stateToString(state) + "]";
    }

    public static String stateToString(int state) {
        switch (state) {
            case STATE_FALSE: return "STATE_FALSE";
            case STATE_TRUE: return "STATE_TRUE";
            case STATE_UNKNOWN: return "STATE_UNKNOWN";
            case STATE_ERROR: return "STATE_ERROR";
            default: throw new IllegalArgumentException("state is invalid: " + state);
        }
    }

    public static String relevanceToString(int flags) {
        boolean now = (flags & FLAG_RELEVANT_NOW) != 0;
        boolean always = (flags & FLAG_RELEVANT_ALWAYS) != 0;
        if (!now && !always) return "NONE";
        if (now && always) return "NOW, ALWAYS";
        return now ? "NOW" : "ALWAYS";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Condition)) return false;
        Condition other = (Condition) o;
        return icon == other.icon
                && state == other.state
                && flags == other.flags
                && source == other.source
                && Objects.equals(id, other.id)
                && Objects.equals(summary, other.summary)
                && Objects.equals(line1, other.line1)
                && Objects.equals(line2, other.line2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, summary, line1, line2, icon, state, source, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public Condition copy() {
        return new Condition(id, summary, line1, line2, icon, state, source, flags);
    }

    public static Uri.Builder newId(Context context) {
        return new Uri.Builder()
                .scheme(SCHEME)
                .authority(context.getPackageName());
    }

    public static boolean isValidId(Uri id, String pkg) {
        return id != null
                && pkg != null
                && SCHEME.equals(id.getScheme())
                && pkg.equals(id.getAuthority());
    }
}
