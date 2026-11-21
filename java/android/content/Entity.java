// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import android.net.Uri;
import java.util.ArrayList;

/** A representation of an item using ContentValues, plus any number of sub-values. */
public final class Entity {
    private final ContentValues mValues;
    private final ArrayList<NamedContentValues> mSubValues = new ArrayList<NamedContentValues>();

    public Entity(ContentValues values) {
        mValues = values;
    }

    public ContentValues getEntityValues() {
        return mValues;
    }

    public ArrayList<NamedContentValues> getSubValues() {
        return mSubValues;
    }

    public void addSubValue(Uri uri, ContentValues values) {
        mSubValues.add(new NamedContentValues(uri, values));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Entity: ").append(getEntityValues());
        for (NamedContentValues namedValue : getSubValues()) {
            sb.append("\n  ").append(namedValue.uri);
            sb.append("\n  -> ").append(namedValue.values);
        }
        return sb.toString();
    }

    public static class NamedContentValues {
        public final Uri uri;
        public final ContentValues values;

        public NamedContentValues(Uri uri, ContentValues values) {
            this.uri = uri;
            this.values = values;
        }
    }
}
