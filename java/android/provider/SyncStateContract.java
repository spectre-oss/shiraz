// SPDX-License-Identifier: GPL-3.0-only

package android.provider;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Pair;

public class SyncStateContract {
    public SyncStateContract() {
    }

    public interface Columns extends BaseColumns {
        String ACCOUNT_NAME = "account_name";
        String ACCOUNT_TYPE = "account_type";
        String DATA = "data";
    }

    public static class Constants implements Columns {
        public static final String CONTENT_DIRECTORY = "syncstate";

        public Constants() {
        }
    }

    public static final class Helpers {
        private static final String[] DATA_PROJECTION = new String[] {Columns.DATA, Columns._ID};
        private static final String SELECT_BY_ACCOUNT =
                Columns.ACCOUNT_NAME + "=? AND " + Columns.ACCOUNT_TYPE + "=?";

        public Helpers() {
        }

        public static byte[] get(
                ContentProviderClient provider, Uri uri, Account account) throws RemoteException {
            Cursor c = provider.query(
                    uri,
                    DATA_PROJECTION,
                    SELECT_BY_ACCOUNT,
                    new String[] {account.name, account.type},
                    null);
            if (c == null) {
                throw new RemoteException();
            }
            try {
                if (c.moveToNext()) {
                    return c.getBlob(c.getColumnIndexOrThrow(Columns.DATA));
                }
                return null;
            } finally {
                c.close();
            }
        }

        public static void set(
                ContentProviderClient provider, Uri uri, Account account, byte[] data)
                throws RemoteException {
            ContentValues values = new ContentValues();
            values.put(Columns.DATA, data);
            values.put(Columns.ACCOUNT_NAME, account.name);
            values.put(Columns.ACCOUNT_TYPE, account.type);
            provider.insert(uri, values);
        }

        public static Uri insert(
                ContentProviderClient provider, Uri uri, Account account, byte[] data)
                throws RemoteException {
            ContentValues values = new ContentValues();
            values.put(Columns.DATA, data);
            values.put(Columns.ACCOUNT_NAME, account.name);
            values.put(Columns.ACCOUNT_TYPE, account.type);
            return provider.insert(uri, values);
        }

        public static void update(ContentProviderClient provider, Uri uri, byte[] data)
                throws RemoteException {
            ContentValues values = new ContentValues();
            values.put(Columns.DATA, data);
            provider.update(uri, values, null, null);
        }

        public static Pair<Uri, byte[]> getWithUri(
                ContentProviderClient provider, Uri uri, Account account) throws RemoteException {
            Cursor c = provider.query(
                    uri,
                    DATA_PROJECTION,
                    SELECT_BY_ACCOUNT,
                    new String[] {account.name, account.type},
                    null);
            if (c == null) {
                throw new RemoteException();
            }
            try {
                if (c.moveToNext()) {
                    long rowId = c.getLong(1);
                    byte[] blob = c.getBlob(c.getColumnIndexOrThrow(Columns.DATA));
                    return Pair.create(ContentUris.withAppendedId(uri, rowId), blob);
                }
                return null;
            } finally {
                c.close();
            }
        }

        public static ContentProviderOperation newSetOperation(
                Uri uri, Account account, byte[] data) {
            ContentValues values = new ContentValues();
            values.put(Columns.DATA, data);
            return ContentProviderOperation.newInsert(uri)
                    .withValue(Columns.ACCOUNT_NAME, account.name)
                    .withValue(Columns.ACCOUNT_TYPE, account.type)
                    .withValues(values)
                    .build();
        }

        public static ContentProviderOperation newUpdateOperation(Uri uri, byte[] data) {
            ContentValues values = new ContentValues();
            values.put(Columns.DATA, data);
            return ContentProviderOperation.newUpdate(uri).withValues(values).build();
        }
    }
}
