// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.biometrics;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PromptVerticalListContentView implements PromptContentView, Parcelable {
    static final int MAX_ITEM_COUNT = 20;
    static final int MAX_EACH_ITEM_CHARACTER_NUMBER = 225;
    static final int MAX_DESCRIPTION_CHARACTER_NUMBER = 225;

    private static final int ITEM_TYPE_PLAIN = 1;
    private static final int ITEM_TYPE_BULLETED = 2;

    private final List<PromptContentItem> mContentList;
    private final String mDescription;

    private PromptVerticalListContentView(
            List<PromptContentItem> contentList, String description) {
        mContentList = Collections.unmodifiableList(
                new ArrayList<PromptContentItem>(contentList));
        mDescription = description;
    }

    private PromptVerticalListContentView(Parcel in) {
        mDescription = in.readString();
        int count = in.readInt();
        ArrayList<PromptContentItem> items = new ArrayList<PromptContentItem>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            int type = in.readInt();
            if (type == ITEM_TYPE_PLAIN) {
                items.add(PromptContentItemPlainText.CREATOR.createFromParcel(in));
            } else if (type == ITEM_TYPE_BULLETED) {
                items.add(PromptContentItemBulletedText.CREATOR.createFromParcel(in));
            } else {
                throw new IllegalArgumentException("Unknown PromptContentItem type " + type);
            }
        }
        mContentList = Collections.unmodifiableList(items);
    }

    public static int getMaxItemCount() {
        return MAX_ITEM_COUNT;
    }

    public static int getMaxEachItemCharacterNumber() {
        return MAX_EACH_ITEM_CHARACTER_NUMBER;
    }

    public String getDescription() {
        return mDescription;
    }

    public List<PromptContentItem> getListItems() {
        return mContentList;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mDescription);
        dest.writeInt(mContentList.size());
        for (int i = 0; i < mContentList.size(); i++) {
            PromptContentItem item = mContentList.get(i);
            if (item instanceof PromptContentItemPlainText) {
                dest.writeInt(ITEM_TYPE_PLAIN);
                ((PromptContentItemPlainText) item).writeToParcel(dest, flags);
            } else if (item instanceof PromptContentItemBulletedText) {
                dest.writeInt(ITEM_TYPE_BULLETED);
                ((PromptContentItemBulletedText) item).writeToParcel(dest, flags);
            } else {
                throw new IllegalArgumentException("Unsupported PromptContentItem " + item);
            }
        }
    }

    public static final Parcelable.Creator<PromptVerticalListContentView> CREATOR =
            new Parcelable.Creator<PromptVerticalListContentView>() {
                @Override
                public PromptVerticalListContentView createFromParcel(Parcel in) {
                    return new PromptVerticalListContentView(in);
                }

                @Override
                public PromptVerticalListContentView[] newArray(int size) {
                    return new PromptVerticalListContentView[size];
                }
            };

    public static final class Builder {
        private final List<PromptContentItem> mContentList = new ArrayList<PromptContentItem>();
        private String mDescription;

        public Builder() {
        }

        public Builder setDescription(String description) {
            if (description != null && description.length() > MAX_DESCRIPTION_CHARACTER_NUMBER) {
                throw new IllegalArgumentException(
                        "The character number of description exceeds "
                                + MAX_DESCRIPTION_CHARACTER_NUMBER);
            }
            mDescription = description;
            return this;
        }

        public Builder addListItem(PromptContentItem listItem) {
            validateItem(listItem);
            mContentList.add(listItem);
            if (mContentList.size() > MAX_ITEM_COUNT) {
                throw new IllegalArgumentException(
                        "The number of list items exceeds " + MAX_ITEM_COUNT);
            }
            return this;
        }

        public Builder addListItem(PromptContentItem listItem, int index) {
            validateItem(listItem);
            mContentList.add(index, listItem);
            if (mContentList.size() > MAX_ITEM_COUNT) {
                throw new IllegalArgumentException(
                        "The number of list items exceeds " + MAX_ITEM_COUNT);
            }
            return this;
        }

        private static void validateItem(PromptContentItem item) {
            Objects.requireNonNull(item, "listItem");
            String text = null;
            if (item instanceof PromptContentItemPlainText) {
                text = ((PromptContentItemPlainText) item).getText();
            } else if (item instanceof PromptContentItemBulletedText) {
                text = ((PromptContentItemBulletedText) item).getText();
            }
            if (text != null && text.length() > MAX_EACH_ITEM_CHARACTER_NUMBER) {
                throw new IllegalArgumentException(
                        "The character number of list item exceeds "
                                + MAX_EACH_ITEM_CHARACTER_NUMBER);
            }
        }

        public PromptVerticalListContentView build() {
            return new PromptVerticalListContentView(mContentList, mDescription);
        }
    }
}
