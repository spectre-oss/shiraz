// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RouteListingPreference implements Parcelable {
    public static final String ACTION_TRANSFER_MEDIA = "android.media.action.TRANSFER_MEDIA";
    public static final String EXTRA_ROUTE_ID = "android.media.extra.ROUTE_ID";

    private final List<Item> mItems;
    private final boolean mUseSystemOrdering;
    private final ComponentName mLinkedItemComponentName;

    private RouteListingPreference(Builder builder) {
        mItems = Collections.unmodifiableList(new ArrayList<Item>(builder.mItems));
        mUseSystemOrdering = builder.mUseSystemOrdering;
        mLinkedItemComponentName = builder.mLinkedItemComponentName;
    }

    private RouteListingPreference(Parcel in) {
        int count = in.readInt();
        List<Item> items = new ArrayList<Item>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            items.add(Item.CREATOR.createFromParcel(in));
        }
        mItems = Collections.unmodifiableList(items);
        mUseSystemOrdering = in.readInt() != 0;
        mLinkedItemComponentName = in.readTypedObject(ComponentName.CREATOR);
    }

    public List<Item> getItems() {
        return mItems;
    }

    public boolean getUseSystemOrdering() {
        return mUseSystemOrdering;
    }

    public ComponentName getLinkedItemComponentName() {
        return mLinkedItemComponentName;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mItems.size());
        for (int i = 0; i < mItems.size(); i++) {
            mItems.get(i).writeToParcel(dest, flags);
        }
        dest.writeInt(mUseSystemOrdering ? 1 : 0);
        dest.writeTypedObject(mLinkedItemComponentName, flags);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RouteListingPreference)) return false;
        RouteListingPreference that = (RouteListingPreference) other;
        return mUseSystemOrdering == that.mUseSystemOrdering
                && Objects.equals(mItems, that.mItems)
                && Objects.equals(mLinkedItemComponentName, that.mLinkedItemComponentName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mItems, Boolean.valueOf(mUseSystemOrdering), mLinkedItemComponentName);
    }

    public static final Parcelable.Creator<RouteListingPreference> CREATOR =
            new Parcelable.Creator<RouteListingPreference>() {
                @Override
                public RouteListingPreference createFromParcel(Parcel in) {
                    return new RouteListingPreference(in);
                }

                @Override
                public RouteListingPreference[] newArray(int size) {
                    return new RouteListingPreference[size];
                }
            };

    public static final class Builder {
        List<Item> mItems = Collections.emptyList();
        boolean mUseSystemOrdering = true;
        ComponentName mLinkedItemComponentName;

        public Builder() {}

        public Builder setItems(List<Item> items) {
            Objects.requireNonNull(items, "items");
            mItems = new ArrayList<Item>(items);
            return this;
        }

        public Builder setUseSystemOrdering(boolean useSystemOrdering) {
            mUseSystemOrdering = useSystemOrdering;
            return this;
        }

        public Builder setLinkedItemComponentName(ComponentName linkedItemComponentName) {
            mLinkedItemComponentName = linkedItemComponentName;
            return this;
        }

        public RouteListingPreference build() {
            return new RouteListingPreference(this);
        }
    }

    public static final class Item implements Parcelable {
        public static final int SELECTION_BEHAVIOR_NONE = 0;
        public static final int SELECTION_BEHAVIOR_TRANSFER = 1;
        public static final int SELECTION_BEHAVIOR_GO_TO_APP = 2;

        public static final int FLAG_ONGOING_SESSION = 1;
        public static final int FLAG_ONGOING_SESSION_MANAGED = 2;
        public static final int FLAG_SUGGESTED = 4;

        public static final int SUBTEXT_NONE = 0;
        public static final int SUBTEXT_ERROR_UNKNOWN = 1;
        public static final int SUBTEXT_SUBSCRIPTION_REQUIRED = 2;
        public static final int SUBTEXT_DOWNLOADED_CONTENT_ROUTING_DISALLOWED = 3;
        public static final int SUBTEXT_AD_ROUTING_DISALLOWED = 4;
        public static final int SUBTEXT_DEVICE_LOW_POWER = 5;
        public static final int SUBTEXT_UNAUTHORIZED = 6;
        public static final int SUBTEXT_TRACK_UNSUPPORTED = 7;
        public static final int SUBTEXT_CUSTOM = 10000;

        private final String mRouteId;
        private final int mSelectionBehavior;
        private final int mFlags;
        private final int mSubText;
        private final CharSequence mCustomSubtextMessage;

        private Item(Builder builder) {
            mRouteId = builder.mRouteId;
            mSelectionBehavior = builder.mSelectionBehavior;
            mFlags = builder.mFlags;
            mSubText = builder.mSubText;
            mCustomSubtextMessage = builder.mCustomSubtextMessage;
        }

        private Item(Parcel in) {
            mRouteId = in.readString();
            mSelectionBehavior = in.readInt();
            mFlags = in.readInt();
            mSubText = in.readInt();
            mCustomSubtextMessage = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        }

        public String getRouteId() {
            return mRouteId;
        }

        public int getSelectionBehavior() {
            return mSelectionBehavior;
        }

        public int getFlags() {
            return mFlags;
        }

        public int getSubText() {
            return mSubText;
        }

        public CharSequence getCustomSubtextMessage() {
            return mCustomSubtextMessage;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mRouteId);
            dest.writeInt(mSelectionBehavior);
            dest.writeInt(mFlags);
            dest.writeInt(mSubText);
            TextUtils.writeToParcel(mCustomSubtextMessage, dest, flags);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Item)) return false;
            Item that = (Item) other;
            return Objects.equals(mRouteId, that.mRouteId)
                    && mSelectionBehavior == that.mSelectionBehavior
                    && mFlags == that.mFlags
                    && mSubText == that.mSubText
                    && TextUtils.equals(mCustomSubtextMessage, that.mCustomSubtextMessage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    mRouteId,
                    Integer.valueOf(mSelectionBehavior),
                    Integer.valueOf(mFlags),
                    Integer.valueOf(mSubText),
                    mCustomSubtextMessage != null ? mCustomSubtextMessage.toString() : null);
        }

        public static final Parcelable.Creator<Item> CREATOR =
                new Parcelable.Creator<Item>() {
                    @Override
                    public Item createFromParcel(Parcel in) {
                        return new Item(in);
                    }

                    @Override
                    public Item[] newArray(int size) {
                        return new Item[size];
                    }
                };

        public static final class Builder {
            final String mRouteId;
            int mSelectionBehavior = SELECTION_BEHAVIOR_TRANSFER;
            int mFlags;
            int mSubText = SUBTEXT_NONE;
            CharSequence mCustomSubtextMessage;

            public Builder(String routeId) {
                if (TextUtils.isEmpty(routeId)) {
                    throw new IllegalArgumentException("routeId must not be empty");
                }
                mRouteId = routeId;
            }

            public Builder setSelectionBehavior(int selectionBehavior) {
                mSelectionBehavior = selectionBehavior;
                return this;
            }

            public Builder setFlags(int flags) {
                mFlags = flags;
                return this;
            }

            public Builder setSubText(int subText) {
                mSubText = subText;
                return this;
            }

            public Builder setCustomSubtextMessage(CharSequence customSubtextMessage) {
                mCustomSubtextMessage = customSubtextMessage;
                return this;
            }

            public Item build() {
                if (mSubText == SUBTEXT_CUSTOM && mCustomSubtextMessage == null) {
                    throw new IllegalArgumentException(
                            "Custom subtext message cannot be null if subtext is SUBTEXT_CUSTOM");
                }
                return new Item(this);
            }
        }
    }
}
