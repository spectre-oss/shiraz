// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.Objects;

public final class Person implements Parcelable {
    private final CharSequence mName;
    private final Icon mIcon;
    private final String mUri;
    private final String mKey;
    private final boolean mIsBot;
    private final boolean mIsImportant;

    private Person(Builder builder) {
        mName = builder.mName;
        mIcon = builder.mIcon;
        mUri = builder.mUri;
        mKey = builder.mKey;
        mIsBot = builder.mIsBot;
        mIsImportant = builder.mIsImportant;
    }

    Person(Parcel in) {
        mName = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        mIcon = in.readTypedObject(Icon.CREATOR);
        mUri = in.readString();
        mKey = in.readString();
        mIsImportant = in.readInt() != 0;
        mIsBot = in.readInt() != 0;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public String getUri() {
        return mUri;
    }

    public CharSequence getName() {
        return mName;
    }

    public Icon getIcon() {
        return mIcon;
    }

    public String getKey() {
        return mKey;
    }

    public boolean isBot() {
        return mIsBot;
    }

    public boolean isImportant() {
        return mIsImportant;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Person)) return false;
        Person other = (Person) obj;
        String thisName = mName == null ? null : mName.toString();
        String otherName = other.mName == null ? null : other.mName.toString();
        return Objects.equals(thisName, otherName)
                && Objects.equals(mIcon, other.mIcon)
                && Objects.equals(mUri, other.mUri)
                && Objects.equals(mKey, other.mKey)
                && mIsBot == other.mIsBot
                && mIsImportant == other.mIsImportant;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mName == null ? null : mName.toString(),
                mIcon,
                mUri,
                mKey,
                Boolean.valueOf(mIsBot),
                Boolean.valueOf(mIsImportant));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        TextUtils.writeToParcel(mName, dest, 0);
        dest.writeTypedObject(mIcon, 0);
        dest.writeString(mUri);
        dest.writeString(mKey);
        dest.writeInt(mIsImportant ? 1 : 0);
        dest.writeInt(mIsBot ? 1 : 0);
    }

    public static final Parcelable.Creator<Person> CREATOR = new Parcelable.Creator<Person>() {
        @Override
        public Person createFromParcel(Parcel in) {
            return new Person(in);
        }

        @Override
        public Person[] newArray(int size) {
            return new Person[size];
        }
    };

    public static class Builder {
        private CharSequence mName;
        private Icon mIcon;
        private String mUri;
        private String mKey;
        private boolean mIsBot;
        private boolean mIsImportant;

        public Builder() {
        }

        private Builder(Person person) {
            mName = person.mName;
            mIcon = person.mIcon;
            mUri = person.mUri;
            mKey = person.mKey;
            mIsBot = person.mIsBot;
            mIsImportant = person.mIsImportant;
        }

        public Builder setName(CharSequence name) {
            mName = name;
            return this;
        }

        public Builder setIcon(Icon icon) {
            mIcon = icon;
            return this;
        }

        public Builder setUri(String uri) {
            mUri = uri;
            return this;
        }

        public Builder setKey(String key) {
            mKey = key;
            return this;
        }

        public Builder setImportant(boolean isImportant) {
            mIsImportant = isImportant;
            return this;
        }

        public Builder setBot(boolean isBot) {
            mIsBot = isBot;
            return this;
        }

        public Person build() {
            return new Person(this);
        }
    }
}
