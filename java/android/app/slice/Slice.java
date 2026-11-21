// SPDX-License-Identifier: GPL-3.0-only
package android.app.slice;

import android.app.PendingIntent;
import android.app.RemoteInput;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Structured, templated piece of app content and actions addressable by {@link Uri}. */
public final class Slice implements Parcelable {
    public static final String HINT_TITLE = "title";
    public static final String HINT_LIST = "list";
    public static final String HINT_LIST_ITEM = "list_item";
    public static final String HINT_LARGE = "large";
    public static final String HINT_ACTIONS = "actions";
    public static final String HINT_SELECTED = "selected";
    public static final String HINT_HORIZONTAL = "horizontal";
    public static final String HINT_NO_TINT = "no_tint";
    public static final String HINT_PARTIAL = "partial";
    public static final String HINT_SUMMARY = "summary";
    public static final String HINT_SEE_MORE = "see_more";
    public static final String HINT_SHORTCUT = "shortcut";
    public static final String HINT_KEYWORDS = "keywords";
    public static final String HINT_TTL = "ttl";
    public static final String HINT_LAST_UPDATED = "last_updated";
    public static final String HINT_PERMISSION_REQUEST = "permission_request";
    public static final String HINT_ERROR = "error";

    public static final String SUBTYPE_COLOR = "color";
    public static final String SUBTYPE_CONTENT_DESCRIPTION = "content_description";
    public static final String SUBTYPE_MAX = "max";
    public static final String SUBTYPE_MESSAGE = "message";
    public static final String SUBTYPE_PRIORITY = "priority";
    public static final String SUBTYPE_RANGE = "range";
    public static final String SUBTYPE_SOURCE = "source";
    public static final String SUBTYPE_TOGGLE = "toggle";
    public static final String SUBTYPE_VALUE = "value";
    public static final String SUBTYPE_LAYOUT_DIRECTION = "layout_direction";
    public static final String SUBTYPE_MILLIS = "millis";

    public static final String EXTRA_TOGGLE_STATE = "android.app.slice.extra.TOGGLE_STATE";
    public static final String EXTRA_RANGE_VALUE = "android.app.slice.extra.RANGE_VALUE";

    private static final String EXTRA_CALLER_NEEDED = "caller_needed";

    private final SliceItem[] mItems;
    private final String[] mHints;
    private final SliceSpec mSpec;
    private final Uri mUri;

    Slice(ArrayList<SliceItem> items, String[] hints, Uri uri, SliceSpec spec) {
        this.mHints = hints == null ? new String[0] : hints;
        this.mItems = items.toArray(new SliceItem[items.size()]);
        this.mUri = Objects.requireNonNull(uri, "uri");
        this.mSpec = spec;
    }

    protected Slice(Parcel in) {
        String[] hints = in.createStringArray();
        this.mHints = hints == null ? new String[0] : hints;
        SliceItem[] items = in.createTypedArray(SliceItem.CREATOR);
        this.mItems = items == null ? new SliceItem[0] : items;
        String uriStr = in.readString();
        this.mUri = uriStr == null ? Uri.EMPTY : Uri.parse(uriStr);
        this.mSpec = in.readTypedObject(SliceSpec.CREATOR);
    }

    public SliceSpec getSpec() {
        return mSpec;
    }

    public Uri getUri() {
        return mUri;
    }

    public List<SliceItem> getItems() {
        return Collections.unmodifiableList(Arrays.asList(mItems));
    }

    public List<String> getHints() {
        return Collections.unmodifiableList(Arrays.asList(mHints));
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(mHints);
        dest.writeTypedArray(mItems, flags);
        dest.writeString(mUri == null ? null : mUri.toString());
        dest.writeTypedObject(mSpec, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public boolean isCallerNeeded() {
        for (int i = 0; i < mHints.length; i++) {
            if (EXTRA_CALLER_NEEDED.equals(mHints[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "Slice{uri="
                + mUri
                + ", spec="
                + mSpec
                + ", items="
                + mItems.length
                + ", hints="
                + Arrays.toString(mHints)
                + "}";
    }

    public static class Builder {
        private final Uri mUri;
        private final ArrayList<SliceItem> mItems = new ArrayList<SliceItem>();
        private final ArrayList<String> mHints = new ArrayList<String>();
        private SliceSpec mSpec;
        private int mChildId;

        public Builder(Uri uri, SliceSpec spec) {
            this.mUri = Objects.requireNonNull(uri, "uri");
            this.mSpec = spec;
        }

        public Builder(Slice.Builder parent) {
            Objects.requireNonNull(parent, "parent");
            this.mUri = parent.getChildUri();
        }

        private Uri getChildUri() {
            return mUri.buildUpon()
                    .appendPath("_gen")
                    .appendPath(String.valueOf(mChildId++))
                    .build();
        }

        public Slice.Builder setCallerNeeded(boolean callerNeeded) {
            if (callerNeeded) {
                if (!mHints.contains(EXTRA_CALLER_NEEDED)) {
                    mHints.add(EXTRA_CALLER_NEEDED);
                }
            } else {
                mHints.remove(EXTRA_CALLER_NEEDED);
            }
            return this;
        }

        public Slice.Builder addHints(List<String> hints) {
            if (hints != null) {
                mHints.addAll(hints);
            }
            return this;
        }

        public Slice.Builder addSubSlice(Slice slice, String subType) {
            Objects.requireNonNull(slice, "slice");
            mItems.add(
                    new SliceItem(
                            slice,
                            SliceItem.FORMAT_SLICE,
                            subType,
                            slice.getHints().toArray(new String[slice.getHints().size()])));
            return this;
        }

        public Slice.Builder addAction(PendingIntent action, Slice s, String subType) {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(s, "slice");
            List<String> hints = s.getHints();
            mItems.add(
                    new SliceItem(
                            action,
                            s,
                            SliceItem.FORMAT_ACTION,
                            subType,
                            hints.toArray(new String[hints.size()])));
            return this;
        }

        public Slice.Builder addText(
                CharSequence text, String subType, List<String> hints) {
            mItems.add(new SliceItem(text, SliceItem.FORMAT_TEXT, subType, hints));
            return this;
        }

        public Slice.Builder addIcon(Icon icon, String subType, List<String> hints) {
            Objects.requireNonNull(icon, "icon");
            mItems.add(new SliceItem(icon, SliceItem.FORMAT_IMAGE, subType, hints));
            return this;
        }

        public Slice.Builder addRemoteInput(
                RemoteInput remoteInput, String subType, List<String> hints) {
            Objects.requireNonNull(remoteInput, "remoteInput");
            mItems.add(
                    new SliceItem(remoteInput, SliceItem.FORMAT_REMOTE_INPUT, subType, hints));
            return this;
        }

        public Slice.Builder addInt(int value, String subType, List<String> hints) {
            mItems.add(
                    new SliceItem(Integer.valueOf(value), SliceItem.FORMAT_INT, subType, hints));
            return this;
        }

        public Slice.Builder addLong(long value, String subType, List<String> hints) {
            mItems.add(
                    new SliceItem(Long.valueOf(value), SliceItem.FORMAT_LONG, subType, hints));
            return this;
        }

        public Slice.Builder addBundle(
                Bundle bundle, String subType, List<String> hints) {
            Objects.requireNonNull(bundle, "bundle");
            mItems.add(new SliceItem(bundle, SliceItem.FORMAT_BUNDLE, subType, hints));
            return this;
        }

        public Slice build() {
            return new Slice(
                    mItems, mHints.toArray(new String[mHints.size()]), mUri, mSpec);
        }
    }

    public static final Parcelable.Creator<Slice> CREATOR =
            new Parcelable.Creator<Slice>() {
                @Override
                public Slice createFromParcel(Parcel in) {
                    return new Slice(in);
                }

                @Override
                public Slice[] newArray(int size) {
                    return new Slice[size];
                }
            };
}
