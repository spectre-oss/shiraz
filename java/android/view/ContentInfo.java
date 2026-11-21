// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;
import java.util.ArrayList;
import java.util.function.Predicate;

public final class ContentInfo implements Parcelable {
    public static final int SOURCE_APP = 0;
    public static final int SOURCE_CLIPBOARD = 1;
    public static final int SOURCE_INPUT_METHOD = 2;
    public static final int SOURCE_DRAG_AND_DROP = 3;
    public static final int SOURCE_AUTOFILL = 4;
    public static final int SOURCE_PROCESS_TEXT = 5;

    public static final int FLAG_CONVERT_TO_PLAIN_TEXT = 1 << 0;

    private final ClipData mClip;
    private final int mSource;
    private final int mFlags;
    private final Uri mLinkUri;
    private final Bundle mExtras;

    ContentInfo(Builder b) {
        if (b.mClip == null) {
            throw new NullPointerException("clip must not be null");
        }
        if (b.mSource < SOURCE_APP || b.mSource > SOURCE_PROCESS_TEXT) {
            throw new IllegalArgumentException("Invalid source: " + b.mSource);
        }
        if ((b.mFlags & ~FLAG_CONVERT_TO_PLAIN_TEXT) != 0) {
            throw new IllegalArgumentException("Invalid flags: " + b.mFlags);
        }
        mClip = b.mClip;
        mSource = b.mSource;
        mFlags = b.mFlags;
        mLinkUri = b.mLinkUri;
        mExtras = b.mExtras;
    }

    public ClipData getClip() {
        return mClip;
    }

    public int getSource() {
        return mSource;
    }

    public int getFlags() {
        return mFlags;
    }

    public Uri getLinkUri() {
        return mLinkUri;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    public Pair<ContentInfo, ContentInfo> partition(Predicate<ClipData.Item> itemPredicate) {
        if (itemPredicate == null) {
            throw new NullPointerException("itemPredicate must not be null");
        }
        if (mClip.getItemCount() == 1) {
            boolean matched = itemPredicate.test(mClip.getItemAt(0));
            return Pair.create(matched ? this : null, matched ? null : this);
        }
        ArrayList<ClipData.Item> acceptedItems = new ArrayList<ClipData.Item>();
        ArrayList<ClipData.Item> remainingItems = new ArrayList<ClipData.Item>();
        for (int i = 0; i < mClip.getItemCount(); i++) {
            ClipData.Item item = mClip.getItemAt(i);
            if (itemPredicate.test(item)) {
                acceptedItems.add(item);
            } else {
                remainingItems.add(item);
            }
        }
        if (acceptedItems.isEmpty()) {
            return Pair.create(null, this);
        }
        if (remainingItems.isEmpty()) {
            return Pair.create(this, null);
        }
        ContentInfo accepted = new Builder(this)
                .setClip(createClipData(mClip.getDescription(), acceptedItems))
                .build();
        ContentInfo remaining = new Builder(this)
                .setClip(createClipData(mClip.getDescription(), remainingItems))
                .build();
        return Pair.create(accepted, remaining);
    }

    private static ClipData createClipData(ClipDescription desc, ArrayList<ClipData.Item> items) {
        ClipData clip = new ClipData(new ClipDescription(desc), items.get(0));
        for (int i = 1; i < items.size(); i++) {
            clip.addItem(items.get(i));
        }
        return clip;
    }

    @Override
    public String toString() {
        return "ContentInfo{clip=" + mClip + ", source=" + mSource + ", flags=" + mFlags
                + ", linkUri=" + mLinkUri + ", extras=" + mExtras + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        ClipDescription desc = mClip.getDescription();
        CharSequence label = desc.getLabel();
        dest.writeString(label != null ? label.toString() : null);
        int mimeCount = desc.getMimeTypeCount();
        String[] mimeTypes = new String[mimeCount];
        for (int i = 0; i < mimeCount; i++) {
            mimeTypes[i] = desc.getMimeType(i);
        }
        dest.writeStringArray(mimeTypes);
        dest.writeLong(desc.getTimestamp());
        int itemCount = mClip.getItemCount();
        dest.writeInt(itemCount);
        for (int i = 0; i < itemCount; i++) {
            ClipData.Item item = mClip.getItemAt(i);
            CharSequence text = item.getText();
            dest.writeString(text != null ? text.toString() : null);
            dest.writeString(item.getHtmlText());
            dest.writeTypedObject(item.getIntent(), flags);
            Uri itemUri = item.getUri();
            dest.writeString(itemUri != null ? itemUri.toString() : null);
        }
        dest.writeInt(mSource);
        dest.writeInt(mFlags);
        dest.writeString(mLinkUri != null ? mLinkUri.toString() : null);
        dest.writeBundle(mExtras);
    }

    public static final Parcelable.Creator<ContentInfo> CREATOR =
            new Parcelable.Creator<ContentInfo>() {
                @Override
                public ContentInfo createFromParcel(Parcel in) {
                    String label = in.readString();
                    String[] mimeTypes = in.createStringArray();
                    if (mimeTypes == null) {
                        mimeTypes = new String[0];
                    }
                    long timestamp = in.readLong();
                    ClipDescription desc = new ClipDescription(label, mimeTypes);
                    desc.setTimestamp(timestamp);
                    int itemCount = in.readInt();
                    ArrayList<ClipData.Item> items = new ArrayList<ClipData.Item>(Math.max(1, itemCount));
                    for (int i = 0; i < itemCount; i++) {
                        String text = in.readString();
                        String html = in.readString();
                        Intent intent = in.readTypedObject(Intent.CREATOR);
                        String uriStr = in.readString();
                        Uri uri = uriStr != null ? Uri.parse(uriStr) : null;
                        items.add(new ClipData.Item(text, html, intent, uri));
                    }
                    ClipData clip = items.isEmpty()
                            ? new ClipData(desc, new ClipData.Item(""))
                            : createClipData(desc, items);
                    int source = in.readInt();
                    int flags = in.readInt();
                    String linkStr = in.readString();
                    Uri linkUri = linkStr != null ? Uri.parse(linkStr) : null;
                    Bundle extras = in.readBundle(ContentInfo.class.getClassLoader());
                    return new Builder(clip, source)
                            .setFlags(flags)
                            .setLinkUri(linkUri)
                            .setExtras(extras)
                            .build();
                }

                @Override
                public ContentInfo[] newArray(int size) {
                    return new ContentInfo[size];
                }
            };

    public static final class Builder {
        ClipData mClip;
        int mSource;
        int mFlags;
        Uri mLinkUri;
        Bundle mExtras;

        public Builder(ContentInfo other) {
            if (other == null) {
                throw new NullPointerException("other must not be null");
            }
            mClip = other.mClip;
            mSource = other.mSource;
            mFlags = other.mFlags;
            mLinkUri = other.mLinkUri;
            mExtras = other.mExtras;
        }

        public Builder(ClipData clip, int source) {
            mClip = clip;
            mSource = source;
        }

        public Builder setClip(ClipData clip) {
            mClip = clip;
            return this;
        }

        public Builder setSource(int source) {
            mSource = source;
            return this;
        }

        public Builder setFlags(int flags) {
            mFlags = flags;
            return this;
        }

        public Builder setLinkUri(Uri linkUri) {
            mLinkUri = linkUri;
            return this;
        }

        public Builder setExtras(Bundle extras) {
            mExtras = extras;
            return this;
        }

        public ContentInfo build() {
            return new ContentInfo(this);
        }
    }
}
