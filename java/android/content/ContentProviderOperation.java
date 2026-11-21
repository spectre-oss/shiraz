// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Represents a single operation to be performed as part of a batch of operations on a ContentProvider. */
public class ContentProviderOperation implements Parcelable {
    private static final int TYPE_INSERT = 1;
    private static final int TYPE_UPDATE = 2;
    private static final int TYPE_DELETE = 3;
    private static final int TYPE_ASSERT = 4;
    private static final int TYPE_CALL = 5;

    private final int mType;
    private final Uri mUri;
    private final String mMethod;
    private final String mArg;
    private final ContentValues mValues;
    private final ContentValues mValuesBackReferences;
    private final LinkedHashMap<String, String> mValueBackRefExtras;
    private final Bundle mExtras;
    private final LinkedHashMap<String, Integer> mExtrasBackReferences;
    private final LinkedHashMap<String, String> mExtrasBackRefKeys;
    private final String mSelection;
    private final String[] mSelectionArgs;
    private final LinkedHashMap<Integer, Integer> mSelectionArgsBackReferences;
    private final LinkedHashMap<Integer, String> mSelectionArgsBackRefKeys;
    private final Integer mExpectedCount;
    private final boolean mYieldAllowed;
    private final boolean mExceptionAllowed;

    ContentProviderOperation(Builder b) {
        mType = b.mType;
        mUri = b.mUri;
        mMethod = b.mMethod;
        mArg = b.mArg;
        mValues = b.mValues != null ? new ContentValues(b.mValues) : null;
        mValuesBackReferences = b.mValuesBackReferences != null
                ? new ContentValues(b.mValuesBackReferences) : null;
        mValueBackRefExtras = new LinkedHashMap<String, String>(b.mValueBackRefExtras);
        mExtras = b.mExtras != null ? new Bundle(b.mExtras) : null;
        mExtrasBackReferences = new LinkedHashMap<String, Integer>(b.mExtrasBackReferences);
        mExtrasBackRefKeys = new LinkedHashMap<String, String>(b.mExtrasBackRefKeys);
        mSelection = b.mSelection;
        mSelectionArgs = b.mSelectionArgs != null ? b.mSelectionArgs.clone() : null;
        mSelectionArgsBackReferences = new LinkedHashMap<Integer, Integer>(b.mSelectionArgsBackReferences);
        mSelectionArgsBackRefKeys = new LinkedHashMap<Integer, String>(b.mSelectionArgsBackRefKeys);
        mExpectedCount = b.mExpectedCount;
        mYieldAllowed = b.mYieldAllowed;
        mExceptionAllowed = b.mExceptionAllowed;
    }

    ContentProviderOperation(Parcel source) {
        mType = source.readInt();
        mUri = source.readTypedObject(Uri.CREATOR);
        mMethod = source.readString();
        mArg = source.readString();
        mValues = source.readTypedObject(ContentValues.CREATOR);
        mValuesBackReferences = source.readTypedObject(ContentValues.CREATOR);
        mValueBackRefExtras = new LinkedHashMap<String, String>();
        mExtras = source.readBundle(ContentProviderOperation.class.getClassLoader());
        mExtrasBackReferences = new LinkedHashMap<String, Integer>();
        mExtrasBackRefKeys = new LinkedHashMap<String, String>();
        mSelection = source.readString();
        int saLen = source.readInt();
        if (saLen >= 0) {
            mSelectionArgs = new String[saLen];
            for (int i = 0; i < saLen; i++) mSelectionArgs[i] = source.readString();
        } else {
            mSelectionArgs = null;
        }
        mSelectionArgsBackReferences = new LinkedHashMap<Integer, Integer>();
        mSelectionArgsBackRefKeys = new LinkedHashMap<Integer, String>();
        mExpectedCount = source.readInt() != 0 ? Integer.valueOf(source.readInt()) : null;
        mYieldAllowed = source.readInt() != 0;
        mExceptionAllowed = source.readInt() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeTypedObject(mUri, flags);
        dest.writeString(mMethod);
        dest.writeString(mArg);
        dest.writeTypedObject(mValues, flags);
        dest.writeTypedObject(mValuesBackReferences, flags);
        dest.writeBundle(mExtras);
        dest.writeString(mSelection);
        if (mSelectionArgs != null) {
            dest.writeInt(mSelectionArgs.length);
            for (int i = 0; i < mSelectionArgs.length; i++) dest.writeString(mSelectionArgs[i]);
        } else {
            dest.writeInt(-1);
        }
        if (mExpectedCount != null) {
            dest.writeInt(1);
            dest.writeInt(mExpectedCount.intValue());
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(mYieldAllowed ? 1 : 0);
        dest.writeInt(mExceptionAllowed ? 1 : 0);
    }

    public static Builder newInsert(Uri uri) {
        return new Builder(TYPE_INSERT, uri, null, null);
    }

    public static Builder newUpdate(Uri uri) {
        return new Builder(TYPE_UPDATE, uri, null, null);
    }

    public static Builder newDelete(Uri uri) {
        return new Builder(TYPE_DELETE, uri, null, null);
    }

    public static Builder newAssertQuery(Uri uri) {
        return new Builder(TYPE_ASSERT, uri, null, null);
    }

    public static Builder newCall(Uri uri, String method, String arg) {
        return new Builder(TYPE_CALL, uri, method, arg);
    }

    public Uri getUri() {
        return mUri;
    }

    public boolean isYieldAllowed() {
        return mYieldAllowed;
    }

    public boolean isExceptionAllowed() {
        return mExceptionAllowed;
    }

    public boolean isInsert() {
        return mType == TYPE_INSERT;
    }

    public boolean isDelete() {
        return mType == TYPE_DELETE;
    }

    public boolean isUpdate() {
        return mType == TYPE_UPDATE;
    }

    public boolean isAssertQuery() {
        return mType == TYPE_ASSERT;
    }

    public boolean isCall() {
        return mType == TYPE_CALL;
    }

    public boolean isWriteOperation() {
        return mType == TYPE_DELETE || mType == TYPE_INSERT || mType == TYPE_UPDATE;
    }

    public boolean isReadOperation() {
        return mType == TYPE_ASSERT;
    }

    private static long backRefToValue(
            ContentProviderResult[] backRefs, int numBackRefs, int index, String resultKey) {
        if (index < 0 || index >= numBackRefs) {
            throw new ArrayIndexOutOfBoundsException("Back-reference index out of bounds: " + index);
        }
        ContentProviderResult res = backRefs[index];
        if (resultKey != null && res.extras != null) {
            Object v = res.extras.get(resultKey);
            if (v instanceof Number) return ((Number) v).longValue();
            if (v != null) return Long.parseLong(v.toString());
        }
        if (res.uri != null) {
            String last = res.uri.getLastPathSegment();
            return Long.parseLong(last);
        }
        if (res.count != null) {
            return res.count.longValue();
        }
        throw new IllegalArgumentException("Unable to resolve back-reference from " + res);
    }

    public ContentProviderResult apply(
            ContentProvider provider, ContentProviderResult[] backRefs, int numBackRefs)
            throws OperationApplicationException {
        if (mExceptionAllowed) {
            try {
                return applyInternal(provider, backRefs, numBackRefs);
            } catch (Exception e) {
                return new ContentProviderResult(e);
            }
        }
        return applyInternal(provider, backRefs, numBackRefs);
    }

    private ContentProviderResult applyInternal(
            ContentProvider provider, ContentProviderResult[] backRefs, int numBackRefs)
            throws OperationApplicationException {
        ContentValues values = resolveValueBackReferences(backRefs, numBackRefs);
        String[] selectionArgs = resolveSelectionArgsBackReferences(backRefs, numBackRefs);
        if (mType == TYPE_INSERT) {
            Uri newUri = provider.insert(mUri, values);
            if (newUri == null) {
                throw new OperationApplicationException("Insert into " + mUri + " returned no result");
            }
            return new ContentProviderResult(newUri);
        }
        if (mType == TYPE_CALL) {
            Bundle extras = resolveExtrasBackReferences(backRefs, numBackRefs);
            Bundle res = provider.call(mMethod, mArg, extras);
            return new ContentProviderResult(res != null ? res : Bundle.EMPTY);
        }
        int numRows;
        if (mType == TYPE_DELETE) {
            numRows = provider.delete(mUri, mSelection, selectionArgs);
        } else if (mType == TYPE_UPDATE) {
            numRows = provider.update(mUri, values, mSelection, selectionArgs);
        } else if (mType == TYPE_ASSERT) {
            String[] projection = null;
            if (values != null && values.size() > 0) {
                projection = values.keySet().toArray(new String[values.size()]);
            }
            Cursor cursor = provider.query(mUri, projection, mSelection, selectionArgs, null);
            try {
                numRows = cursor != null ? cursor.getCount() : 0;
                if (projection != null && cursor != null) {
                    while (cursor.moveToNext()) {
                        for (int i = 0; i < projection.length; i++) {
                            String cursorVal = cursor.getString(i);
                            Object expObj = values.get(projection[i]);
                            String expVal = expObj != null ? expObj.toString() : null;
                            if ((cursorVal == null && expVal != null)
                                    || (cursorVal != null && !cursorVal.equals(expVal))) {
                                throw new OperationApplicationException(
                                        "Found value " + cursorVal + " when expected " + expVal
                                                + " for column " + projection[i]);
                            }
                        }
                    }
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        } else {
            throw new IllegalStateException("bad type, " + mType);
        }
        if (mExpectedCount != null && mExpectedCount.intValue() != numRows) {
            throw new OperationApplicationException(
                    "Expected " + mExpectedCount + " rows but actual " + numRows);
        }
        return new ContentProviderResult(numRows);
    }

    public ContentValues resolveValueBackReferences(
            ContentProviderResult[] backRefs, int numBackRefs) {
        if (mValuesBackReferences == null) {
            return mValues;
        }
        ContentValues values = mValues != null ? new ContentValues(mValues) : new ContentValues();
        for (String key : mValuesBackReferences.keySet()) {
            Integer backRefIndex = mValuesBackReferences.getAsInteger(key);
            if (backRefIndex == null) {
                throw new IllegalArgumentException("values backref " + key + " is not an integer");
            }
            String fromKey = mValueBackRefExtras.get(key);
            values.put(key, Long.valueOf(backRefToValue(
                    backRefs, numBackRefs, backRefIndex.intValue(), fromKey)));
        }
        return values;
    }

    public Bundle resolveExtrasBackReferences(
            ContentProviderResult[] backRefs, int numBackRefs) {
        if (mExtrasBackReferences.isEmpty()) {
            return mExtras;
        }
        Bundle extras = mExtras != null ? new Bundle(mExtras) : new Bundle();
        for (Map.Entry<String, Integer> e : mExtrasBackReferences.entrySet()) {
            String key = e.getKey();
            int idx = e.getValue().intValue();
            String fromKey = mExtrasBackRefKeys.get(key);
            extras.putLong(key, backRefToValue(backRefs, numBackRefs, idx, fromKey));
        }
        return extras;
    }

    public String[] resolveSelectionArgsBackReferences(
            ContentProviderResult[] backRefs, int numBackRefs) {
        if (mSelectionArgsBackReferences.isEmpty()) {
            return mSelectionArgs;
        }
        String[] newArgs = mSelectionArgs != null ? mSelectionArgs.clone() : new String[0];
        for (Map.Entry<Integer, Integer> e : mSelectionArgsBackReferences.entrySet()) {
            int argIndex = e.getKey().intValue();
            int backRefIndex = e.getValue().intValue();
            String fromKey = mSelectionArgsBackRefKeys.get(Integer.valueOf(argIndex));
            newArgs[argIndex] = String.valueOf(
                    backRefToValue(backRefs, numBackRefs, backRefIndex, fromKey));
        }
        return newArgs;
    }

    @Override
    public String toString() {
        return "ContentProviderOperation(type=" + mType + " uri=" + mUri + ")";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<ContentProviderOperation> CREATOR =
            new Parcelable.Creator<ContentProviderOperation>() {
                @Override
                public ContentProviderOperation createFromParcel(Parcel source) {
                    return new ContentProviderOperation(source);
                }

                @Override
                public ContentProviderOperation[] newArray(int size) {
                    return new ContentProviderOperation[size];
                }
            };

    public static class Builder {
        final int mType;
        final Uri mUri;
        final String mMethod;
        final String mArg;
        ContentValues mValues;
        ContentValues mValuesBackReferences;
        final LinkedHashMap<String, String> mValueBackRefExtras = new LinkedHashMap<String, String>();
        Bundle mExtras;
        final LinkedHashMap<String, Integer> mExtrasBackReferences = new LinkedHashMap<String, Integer>();
        final LinkedHashMap<String, String> mExtrasBackRefKeys = new LinkedHashMap<String, String>();
        String mSelection;
        String[] mSelectionArgs;
        final LinkedHashMap<Integer, Integer> mSelectionArgsBackReferences = new LinkedHashMap<Integer, Integer>();
        final LinkedHashMap<Integer, String> mSelectionArgsBackRefKeys = new LinkedHashMap<Integer, String>();
        Integer mExpectedCount;
        boolean mYieldAllowed;
        boolean mExceptionAllowed;

        Builder(int type, Uri uri, String method, String arg) {
            if (uri == null) throw new IllegalArgumentException("uri must not be null");
            mType = type;
            mUri = uri;
            mMethod = method;
            mArg = arg;
        }

        public ContentProviderOperation build() {
            return new ContentProviderOperation(this);
        }

        public Builder withValues(ContentValues values) {
            if (mValues == null) mValues = new ContentValues();
            if (values != null) mValues.putAll(values);
            return this;
        }

        public Builder withValue(String key, Object value) {
            if (mValues == null) mValues = new ContentValues();
            if (value == null) mValues.putNull(key);
            else if (value instanceof String) mValues.put(key, (String) value);
            else if (value instanceof Byte) mValues.put(key, (Byte) value);
            else if (value instanceof Short) mValues.put(key, (Short) value);
            else if (value instanceof Integer) mValues.put(key, (Integer) value);
            else if (value instanceof Long) mValues.put(key, (Long) value);
            else if (value instanceof Float) mValues.put(key, (Float) value);
            else if (value instanceof Double) mValues.put(key, (Double) value);
            else if (value instanceof Boolean) mValues.put(key, (Boolean) value);
            else if (value instanceof byte[]) mValues.put(key, (byte[]) value);
            else throw new IllegalArgumentException("bad value type: " + value.getClass().getName());
            return this;
        }

        public Builder withValueBackReferences(ContentValues backReferences) {
            mValuesBackReferences = backReferences != null ? new ContentValues(backReferences) : null;
            return this;
        }

        public Builder withValueBackReference(String key, int fromIndex) {
            if (mValuesBackReferences == null) mValuesBackReferences = new ContentValues();
            mValuesBackReferences.put(key, Integer.valueOf(fromIndex));
            return this;
        }

        public Builder withValueBackReference(String key, int fromIndex, String fromKey) {
            withValueBackReference(key, fromIndex);
            if (fromKey != null) mValueBackRefExtras.put(key, fromKey);
            return this;
        }

        public Builder withExtras(Bundle extras) {
            if (mExtras == null) mExtras = new Bundle();
            if (extras != null) mExtras.putAll(extras);
            return this;
        }

        public Builder withExtra(String key, Object value) {
            if (mExtras == null) mExtras = new Bundle();
            if (value == null) mExtras.putString(key, null);
            else if (value instanceof String) mExtras.putString(key, (String) value);
            else if (value instanceof Integer) mExtras.putInt(key, ((Integer) value).intValue());
            else if (value instanceof Long) mExtras.putLong(key, ((Long) value).longValue());
            else if (value instanceof Boolean) mExtras.putBoolean(key, ((Boolean) value).booleanValue());
            else if (value instanceof Float) mExtras.putFloat(key, ((Float) value).floatValue());
            else if (value instanceof Double) mExtras.putDouble(key, ((Double) value).doubleValue());
            else if (value instanceof Parcelable) mExtras.putParcelable(key, (Parcelable) value);
            else mExtras.putString(key, value.toString());
            return this;
        }

        public Builder withExtraBackReference(String key, int fromIndex) {
            mExtrasBackReferences.put(key, Integer.valueOf(fromIndex));
            return this;
        }

        public Builder withExtraBackReference(String key, int fromIndex, String fromKey) {
            mExtrasBackReferences.put(key, Integer.valueOf(fromIndex));
            if (fromKey != null) mExtrasBackRefKeys.put(key, fromKey);
            return this;
        }

        public Builder withSelection(String selection, String[] selectionArgs) {
            mSelection = selection;
            mSelectionArgs = selectionArgs != null ? selectionArgs.clone() : null;
            return this;
        }

        public Builder withSelectionBackReference(int selectionArgIndex, int fromIndex) {
            mSelectionArgsBackReferences.put(
                    Integer.valueOf(selectionArgIndex), Integer.valueOf(fromIndex));
            return this;
        }

        public Builder withSelectionBackReference(
                int selectionArgIndex, int fromIndex, String fromKey) {
            withSelectionBackReference(selectionArgIndex, fromIndex);
            if (fromKey != null) {
                mSelectionArgsBackRefKeys.put(Integer.valueOf(selectionArgIndex), fromKey);
            }
            return this;
        }

        public Builder withExpectedCount(int count) {
            mExpectedCount = Integer.valueOf(count);
            return this;
        }

        public Builder withYieldAllowed(boolean yieldAllowed) {
            mYieldAllowed = yieldAllowed;
            return this;
        }

        public Builder withExceptionAllowed(boolean exceptionAllowed) {
            mExceptionAllowed = exceptionAllowed;
            return this;
        }
    }
}
