// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.text.TextUtils;

/** SQLite-backed ContentProvider serving recent search query suggestions. */
public class SearchRecentSuggestionsProvider extends ContentProvider {
    public static final int DATABASE_MODE_QUERIES = 1;
    public static final int DATABASE_MODE_2LINES = 2;

    private static final String sSuggestions = "suggestions";
    private static final String sDatabaseName = "suggestions.db";
    private static final int URI_MATCH_SUGGEST = 1;
    private static final int DATABASE_VERSION = 2 * 256;

    private String mAuthority;
    private int mMode;
    private boolean mTwoLineDisplay;
    private SQLiteOpenHelper mOpenHelper;
    private Uri mSuggestionsUri;
    private UriMatcher mUriMatcher;
    private String mSuggestSuggestionClause;
    private String[] mSuggestionProjection;

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private final int mNewVersion;

        public DatabaseHelper(Context context, int newVersion) {
            super(context, sDatabaseName, null, newVersion);
            mNewVersion = newVersion;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            StringBuilder builder = new StringBuilder();
            builder.append("CREATE TABLE suggestions ("
                    + "_id INTEGER PRIMARY KEY"
                    + ",display1 TEXT UNIQUE ON CONFLICT REPLACE");
            if (0 != (mNewVersion & DATABASE_MODE_2LINES)) {
                builder.append(",display2 TEXT");
            }
            builder.append(",query TEXT"
                    + ",date LONG"
                    + ");");
            db.execSQL(builder.toString());
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS suggestions");
            onCreate(db);
        }
    }

    public SearchRecentSuggestionsProvider() {}

    protected void setupSuggestions(String authority, int mode) {
        if (TextUtils.isEmpty(authority) || ((mode & DATABASE_MODE_QUERIES) == 0)) {
            throw new IllegalArgumentException();
        }
        mTwoLineDisplay = (0 != (mode & DATABASE_MODE_2LINES));
        mAuthority = authority;
        mMode = mode;

        mSuggestionsUri = Uri.parse("content://" + mAuthority + "/suggestions");
        mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mUriMatcher.addURI(mAuthority, "search_suggest_query", URI_MATCH_SUGGEST);

        if (mTwoLineDisplay) {
            mSuggestSuggestionClause = "display1 LIKE ? OR display2 LIKE ?";
            mSuggestionProjection = new String[] {
                    "0 AS suggest_format",
                    "'android.resource://system/17301578' AS suggest_icon_1",
                    "display1 AS suggest_text_1",
                    "display2 AS suggest_text_2",
                    "query AS suggest_intent_query",
                    "_id"
            };
        } else {
            mSuggestSuggestionClause = "display1 LIKE ?";
            mSuggestionProjection = new String[] {
                    "0 AS suggest_format",
                    "'android.resource://system/17301578' AS suggest_icon_1",
                    "display1 AS suggest_text_1",
                    "query AS suggest_intent_query",
                    "_id"
            };
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final int length = uri.getPathSegments().size();
        if (length != 1) {
            throw new IllegalArgumentException("Unknown Uri");
        }
        final String base = uri.getPathSegments().get(0);
        if (!base.equals(sSuggestions)) {
            throw new IllegalArgumentException("Unknown Uri");
        }
        int count = db.delete(sSuggestions, selection, selectionArgs);
        if (getContext() != null && getContext().getContentResolver() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    @Override
    public String getType(Uri uri) {
        if (mUriMatcher != null && mUriMatcher.match(uri) == URI_MATCH_SUGGEST) {
            return "vnd.android.cursor.dir/vnd.android.search.suggest";
        }
        int length = uri.getPathSegments().size();
        if (length >= 1) {
            String base = uri.getPathSegments().get(0);
            if (base.equals(sSuggestions)) {
                if (length == 1) {
                    return "vnd.android.cursor.dir/suggestion";
                } else if (length == 2) {
                    return "vnd.android.cursor.item/suggestion";
                }
            }
        }
        throw new IllegalArgumentException("Unknown Uri");
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int length = uri.getPathSegments().size();
        if (length < 1) {
            throw new IllegalArgumentException("Unknown Uri");
        }
        long rowID = -1;
        String base = uri.getPathSegments().get(0);
        Uri newUri = null;
        if (base.equals(sSuggestions) && length == 1) {
            rowID = db.insert(sSuggestions, "query", values);
            if (rowID > 0) {
                newUri = Uri.withAppendedPath(mSuggestionsUri, String.valueOf(rowID));
            }
        }
        if (rowID < 0) {
            throw new IllegalArgumentException("Unknown Uri");
        }
        if (getContext() != null && getContext().getContentResolver() != null) {
            getContext().getContentResolver().notifyChange(newUri, null);
        }
        return newUri;
    }

    @Override
    public boolean onCreate() {
        if (mAuthority == null || mMode == 0) {
            throw new IllegalArgumentException("Provider not configured");
        }
        int mWorkingDbVersion = DATABASE_VERSION + mMode;
        mOpenHelper = new DatabaseHelper(getContext(), mWorkingDbVersion);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if (mUriMatcher != null && mUriMatcher.match(uri) == URI_MATCH_SUGGEST) {
            String suggestSelection;
            String[] myArgs;
            if (selectionArgs == null || selectionArgs.length == 0 || TextUtils.isEmpty(selectionArgs[0])) {
                suggestSelection = null;
                myArgs = null;
            } else {
                String like = "%" + selectionArgs[0] + "%";
                if (mTwoLineDisplay) {
                    myArgs = new String[] { like, like };
                } else {
                    myArgs = new String[] { like };
                }
                suggestSelection = mSuggestSuggestionClause;
            }
            return db.query(sSuggestions, mSuggestionProjection,
                    suggestSelection, myArgs, null, null, "date DESC", null);
        }

        int length = uri.getPathSegments().size();
        if (length != 1 && length != 2) {
            throw new IllegalArgumentException("Unknown Uri");
        }
        String base = uri.getPathSegments().get(0);
        if (!base.equals(sSuggestions)) {
            throw new IllegalArgumentException("Unknown Uri");
        }

        String[] useProjection = null;
        if (projection != null && projection.length > 0) {
            useProjection = new String[projection.length + 1];
            System.arraycopy(projection, 0, useProjection, 0, projection.length);
            useProjection[projection.length] = "_id AS _id";
        }

        StringBuilder whereClause = new StringBuilder(256);
        if (length == 2) {
            whereClause.append("(_id = ").append(uri.getPathSegments().get(1)).append(")");
        }
        if (selection != null && !selection.isEmpty()) {
            if (whereClause.length() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append('(').append(selection).append(')');
        }
        return db.query(base, useProjection, whereClause.toString(),
                selectionArgs, null, null, sortOrder, null);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
