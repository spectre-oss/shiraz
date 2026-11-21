// SPDX-License-Identifier: GPL-3.0-only
package android.provider;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.content.EntityIterator;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Pair;
import android.view.View;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/** The contract between the contacts provider and applications. */
public final class ContactsContract {
    public ContactsContract() {
    }

    public static final String AUTHORITY = "com.android.contacts";
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    public static final String CALLER_IS_SYNCADAPTER = "caller_is_syncadapter";
    public static final String DIRECTORY_PARAM_KEY = "directory";
    public static final String LIMIT_PARAM_KEY = "limit";
    public static final String PRIMARY_ACCOUNT_NAME = "name_for_primary_account";
    public static final String PRIMARY_ACCOUNT_TYPE = "type_for_primary_account";
    public static final String STREQUENT_PHONE_ONLY = "strequent_phone_only";
    public static final String DEFERRED_SNIPPETING = "deferred_snippeting";
    public static final String DEFERRED_SNIPPETING_QUERY = "deferred_snippeting_query";
    public static final String REMOVE_DUPLICATE_ENTRIES = "remove_duplicate_entries";

    public static boolean isProfileId(long id) {
        return id >= Profile.MIN_ID;
    }

    static CharSequence resolveLabel(
            Resources res, int type, CharSequence customLabel, int resId, String fallback) {
        if (type == CommonDataKinds.BaseTypes.TYPE_CUSTOM
                && customLabel != null
                && customLabel.length() > 0) {
            return customLabel;
        }
        if (res != null && resId != 0) {
            try {
                CharSequence resolved = res.getText(resId);
                if (resolved != null && resolved.length() > 0) {
                    return resolved;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return fallback;
    }

    public static final class Directory implements BaseColumns {
        private Directory() {
        }

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "directories");
        public static final Uri ENTERPRISE_CONTENT_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "directories_enterprise");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/contact_directories";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/contact_directory";

        public static final long DEFAULT = 0L;
        public static final long LOCAL_INVISIBLE = 1L;
        public static final long ENTERPRISE_DEFAULT = 1000000000L;
        public static final long ENTERPRISE_LOCAL_INVISIBLE = 1000000001L;

        public static final String PACKAGE_NAME = "packageName";
        public static final String TYPE_RESOURCE_ID = "typeResourceId";
        public static final String DISPLAY_NAME = "displayName";
        public static final String DIRECTORY_AUTHORITY = "authority";
        public static final String ACCOUNT_TYPE = "accountType";
        public static final String ACCOUNT_NAME = "accountName";
        public static final String EXPORT_SUPPORT = "exportSupport";
        public static final int EXPORT_SUPPORT_NONE = 0;
        public static final int EXPORT_SUPPORT_SAME_ACCOUNT_ONLY = 1;
        public static final int EXPORT_SUPPORT_ANY_ACCOUNT = 2;

        public static final String SHORTCUT_SUPPORT = "shortcutSupport";
        public static final int SHORTCUT_SUPPORT_NONE = 0;
        public static final int SHORTCUT_SUPPORT_DATA_ITEMS_ONLY = 1;
        public static final int SHORTCUT_SUPPORT_FULL = 2;

        public static final String PHOTO_SUPPORT = "photoSupport";
        public static final int PHOTO_SUPPORT_NONE = 0;
        public static final int PHOTO_SUPPORT_THUMBNAIL_ONLY = 1;
        public static final int PHOTO_SUPPORT_FULL_SIZE_ONLY = 2;
        public static final int PHOTO_SUPPORT_FULL = 3;

        public static final String CALLER_PACKAGE_PARAM_KEY = "callerPackage";

        public static boolean isRemoteDirectoryId(long directoryId) {
            return directoryId != DEFAULT
                    && directoryId != LOCAL_INVISIBLE
                    && directoryId != ENTERPRISE_DEFAULT
                    && directoryId != ENTERPRISE_LOCAL_INVISIBLE;
        }

        public static boolean isEnterpriseDirectoryId(long directoryId) {
            return directoryId >= ENTERPRISE_DEFAULT;
        }

        public static void notifyDirectoryChange(ContentResolver resolver) {
            ContentValues emptyValues = new ContentValues();
            resolver.update(Directory.CONTENT_URI, emptyValues, null, null);
        }
    }

    public static final class SyncState implements SyncStateContract.Columns {
        private SyncState() {
        }

        public static final String CONTENT_DIRECTORY =
                SyncStateContract.Constants.CONTENT_DIRECTORY;
        public static final Uri CONTENT_URI =
                Uri.withAppendedPath(AUTHORITY_URI, CONTENT_DIRECTORY);

        public static byte[] get(ContentProviderClient provider, Account account)
                throws RemoteException {
            return SyncStateContract.Helpers.get(provider, CONTENT_URI, account);
        }

        public static Pair<Uri, byte[]> getWithUri(
                ContentProviderClient provider, Account account) throws RemoteException {
            return SyncStateContract.Helpers.getWithUri(provider, CONTENT_URI, account);
        }

        public static void set(ContentProviderClient provider, Account account, byte[] data)
                throws RemoteException {
            SyncStateContract.Helpers.set(provider, CONTENT_URI, account, data);
        }

        public static ContentProviderOperation newSetOperation(Account account, byte[] data) {
            return SyncStateContract.Helpers.newSetOperation(CONTENT_URI, account, data);
        }
    }

    public static final class ProfileSyncState implements SyncStateContract.Columns {
        private ProfileSyncState() {
        }

        public static final String CONTENT_DIRECTORY =
                SyncStateContract.Constants.CONTENT_DIRECTORY;
        public static final Uri CONTENT_URI =
                Uri.withAppendedPath(Profile.CONTENT_URI, CONTENT_DIRECTORY);

        public static byte[] get(ContentProviderClient provider, Account account)
                throws RemoteException {
            return SyncStateContract.Helpers.get(provider, CONTENT_URI, account);
        }

        public static Pair<Uri, byte[]> getWithUri(
                ContentProviderClient provider, Account account) throws RemoteException {
            return SyncStateContract.Helpers.getWithUri(provider, CONTENT_URI, account);
        }

        public static void set(ContentProviderClient provider, Account account, byte[] data)
                throws RemoteException {
            SyncStateContract.Helpers.set(provider, CONTENT_URI, account, data);
        }

        public static ContentProviderOperation newSetOperation(Account account, byte[] data) {
            return SyncStateContract.Helpers.newSetOperation(CONTENT_URI, account, data);
        }
    }

    protected interface BaseSyncColumns {
        String SYNC1 = "sync1";
        String SYNC2 = "sync2";
        String SYNC3 = "sync3";
        String SYNC4 = "sync4";
    }

    protected interface SyncColumns extends BaseSyncColumns {
        String ACCOUNT_NAME = "account_name";
        String ACCOUNT_TYPE = "account_type";
        String SOURCE_ID = "sourceid";
        String VERSION = "version";
        String DIRTY = "dirty";
    }

    protected interface ContactOptionsColumns {
        String TIMES_CONTACTED = "times_contacted";
        String LAST_TIME_CONTACTED = "last_time_contacted";
        String CUSTOM_RINGTONE = "custom_ringtone";
        String SEND_TO_VOICEMAIL = "send_to_voicemail";
        String STARRED = "starred";
        String PINNED = "pinned";
    }

    protected interface ContactsColumns {
        String DISPLAY_NAME = "display_name";
        String NAME_RAW_CONTACT_ID = "name_raw_contact_id";
        String PHOTO_ID = "photo_id";
        String PHOTO_FILE_ID = "photo_file_id";
        String PHOTO_URI = "photo_uri";
        String PHOTO_THUMBNAIL_URI = "photo_thumb_uri";
        String IN_VISIBLE_GROUP = "in_visible_group";
        String IN_DEFAULT_DIRECTORY = "in_default_directory";
        String IS_USER_PROFILE = "is_user_profile";
        String HAS_PHONE_NUMBER = "has_phone_number";
        String LOOKUP_KEY = "lookup";
        String CONTACT_LAST_UPDATED_TIMESTAMP = "contact_last_updated_timestamp";
    }

    protected interface ContactStatusColumns {
        String CONTACT_PRESENCE = "contact_presence";
        String CONTACT_CHAT_CAPABILITY = "contact_chat_capability";
        String CONTACT_STATUS = "contact_status";
        String CONTACT_STATUS_TIMESTAMP = "contact_status_ts";
        String CONTACT_STATUS_RES_PACKAGE = "contact_status_res_package";
        String CONTACT_STATUS_LABEL = "contact_status_label";
        String CONTACT_STATUS_ICON = "contact_status_icon";
    }

    public interface FullNameStyle {
        int UNDEFINED = 0;
        int WESTERN = 1;
        int CJK = 2;
        int CHINESE = 3;
        int JAPANESE = 4;
        int KOREAN = 5;
    }

    public interface PhoneticNameStyle {
        int UNDEFINED = 0;
        int PINYIN = 3;
        int JAPANESE = 4;
        int KOREAN = 5;
    }

    public interface DisplayNameSources {
        int UNDEFINED = 0;
        int EMAIL = 10;
        int PHONE = 20;
        int ORGANIZATION = 30;
        int NICKNAME = 35;
        int STRUCTURED_PHONETIC_NAME = 37;
        int STRUCTURED_NAME = 40;
    }

    protected interface ContactNameColumns {
        String DISPLAY_NAME_SOURCE = "display_name_source";
        String DISPLAY_NAME_PRIMARY = "display_name";
        String DISPLAY_NAME_ALTERNATIVE = "display_name_alt";
        String PHONETIC_NAME_STYLE = "phonetic_name_style";
        String PHONETIC_NAME = "phonetic_name";
        String SORT_KEY_PRIMARY = "sort_key";
        String SORT_KEY_ALTERNATIVE = "sort_key_alt";
    }

    public static class Contacts
            implements BaseColumns,
                    ContactsColumns,
                    ContactOptionsColumns,
                    ContactNameColumns,
                    ContactStatusColumns {
        private Contacts() {
        }

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "contacts");
        public static final Uri ENTERPRISE_CONTENT_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "contacts_enterprise");
        public static final Uri CONTENT_LOOKUP_URI = Uri.withAppendedPath(CONTENT_URI, "lookup");
        public static final Uri CONTENT_VCARD_URI = Uri.withAppendedPath(CONTENT_URI, "as_vcard");
        public static final String QUERY_PARAMETER_VCARD_NO_PHOTO = "no_photo";
        public static final Uri CONTENT_MULTI_VCARD_URI =
                Uri.withAppendedPath(CONTENT_URI, "as_multi_vcard");
        public static final Uri CONTENT_FILTER_URI = Uri.withAppendedPath(CONTENT_URI, "filter");
        public static final Uri ENTERPRISE_CONTENT_FILTER_URI =
                Uri.withAppendedPath(CONTENT_URI, "filter_enterprise");
        public static final Uri CONTENT_STREQUENT_URI =
                Uri.withAppendedPath(CONTENT_URI, "strequent");
        @Deprecated
        public static final Uri CONTENT_FREQUENT_URI =
                Uri.withAppendedPath(CONTENT_URI, "frequent");
        public static final Uri CONTENT_STREQUENT_FILTER_URI =
                Uri.withAppendedPath(CONTENT_STREQUENT_URI, "filter");
        public static final Uri CONTENT_GROUP_URI = Uri.withAppendedPath(CONTENT_URI, "group");

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/contact";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/contact";
        public static final String CONTENT_VCARD_TYPE = "text/x-vcard";

        public static final String EXTRA_ADDRESS_BOOK_INDEX =
                "android.provider.extra.ADDRESS_BOOK_INDEX";
        public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
        public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";

        private static final long ENTERPRISE_CONTACT_ID_BASE = 1000000000L;

        public static Uri getLookupUri(ContentResolver resolver, Uri contactUri) {
            Cursor c = resolver.query(
                    contactUri, new String[] { Contacts.LOOKUP_KEY, Contacts._ID }, null, null, null);
            if (c == null) {
                return null;
            }
            try {
                if (c.moveToFirst()) {
                    String lookupKey = c.getString(0);
                    long contactId = c.getLong(1);
                    return getLookupUri(contactId, lookupKey);
                }
            } finally {
                c.close();
            }
            return null;
        }

        public static Uri getLookupUri(long contactId, String lookupKey) {
            if (lookupKey == null || lookupKey.isEmpty()) {
                return null;
            }
            return ContentUris.withAppendedId(
                    Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey), contactId);
        }

        public static Uri lookupContact(ContentResolver resolver, Uri lookupUri) {
            if (lookupUri == null) {
                return null;
            }
            Cursor c = resolver.query(lookupUri, new String[] { Contacts._ID }, null, null, null);
            if (c == null) {
                return null;
            }
            try {
                if (c.moveToFirst()) {
                    long contactId = c.getLong(0);
                    return ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                }
            } finally {
                c.close();
            }
            return null;
        }

        @Deprecated
        public static void markAsContacted(ContentResolver resolver, long contactId) {
        }

        public static boolean isEnterpriseContactId(long contactId) {
            return contactId >= ENTERPRISE_CONTACT_ID_BASE && contactId < Profile.MIN_ID;
        }

        public static InputStream openContactPhotoInputStream(
                ContentResolver cr, Uri contactUri, boolean preferHighres) {
            if (contactUri == null) {
                throw new IllegalArgumentException("Uri must not be null");
            }
            if (preferHighres) {
                Uri displayPhotoUri = Uri.withAppendedPath(
                        contactUri, Contacts.Photo.DISPLAY_PHOTO);
                try {
                    AssetFileDescriptor fd = cr.openAssetFileDescriptor(displayPhotoUri, "r");
                    if (fd != null) {
                        return fd.createInputStream();
                    }
                } catch (IOException ignored) {
                }
            }
            Uri photoUri = Uri.withAppendedPath(contactUri, Photo.CONTENT_DIRECTORY);
            if (photoUri == null) {
                return null;
            }
            Cursor cursor = cr.query(
                    photoUri,
                    new String[] { CommonDataKinds.Photo.PHOTO },
                    null,
                    null,
                    null);
            if (cursor == null) {
                return null;
            }
            try {
                if (!cursor.moveToNext()) {
                    return null;
                }
                byte[] data = cursor.getBlob(0);
                if (data == null) {
                    return null;
                }
                return new ByteArrayInputStream(data);
            } finally {
                cursor.close();
            }
        }

        public static InputStream openContactPhotoInputStream(ContentResolver cr, Uri contactUri) {
            return openContactPhotoInputStream(cr, contactUri, false);
        }

        public static final class Data implements BaseColumns, DataColumns {
            private Data() {
            }

            public static final String CONTENT_DIRECTORY = "data";
        }

        public static final class Entity
                implements BaseColumns,
                        ContactsColumns,
                        ContactNameColumns,
                        RawContactsColumns,
                        BaseSyncColumns,
                        SyncColumns,
                        DataColumns,
                        StatusColumns,
                        ContactOptionsColumns,
                        ContactStatusColumns,
                        DataUsageStatColumns {
            private Entity() {
            }

            public static final String CONTENT_DIRECTORY = "entities";
            public static final String RAW_CONTACT_ID = "raw_contact_id";
            public static final String DATA_ID = "data_id";
        }

        public static final class AggregationSuggestions
                implements BaseColumns,
                        ContactsColumns,
                        ContactOptionsColumns,
                        ContactStatusColumns {
            private AggregationSuggestions() {
            }

            public static final String CONTENT_DIRECTORY = "suggestions";

            public static final class Builder {
                private long mContactId;
                private final ArrayList<String> mValues = new ArrayList<String>();
                private int mLimit;

                public Builder() {
                }

                public Builder setContactId(long contactId) {
                    mContactId = contactId;
                    return this;
                }

                public Builder addNameParameter(String name) {
                    if (name != null && !name.isEmpty()) {
                        mValues.add(name);
                    }
                    return this;
                }

                public Builder setLimit(int limit) {
                    mLimit = limit;
                    return this;
                }

                public Uri build() {
                    Uri.Builder builder = Contacts.CONTENT_URI.buildUpon();
                    builder.appendEncodedPath(String.valueOf(mContactId));
                    builder.appendPath(CONTENT_DIRECTORY);
                    if (mLimit != 0) {
                        builder.appendQueryParameter("limit", String.valueOf(mLimit));
                    }
                    for (int i = 0; i < mValues.size(); i++) {
                        builder.appendQueryParameter("query", "name:" + mValues.get(i));
                    }
                    return builder.build();
                }
            }
        }

        public static final class Photo implements BaseColumns, DataColumnsWithJoins {
            private Photo() {
            }

            public static final String CONTENT_DIRECTORY = "photo";
            public static final String DISPLAY_PHOTO = "display_photo";
            public static final String PHOTO_FILE_ID = DATA14;
            public static final String PHOTO = DATA15;
        }
    }

    public static final class Profile
            implements BaseColumns,
                    ContactsColumns,
                    ContactOptionsColumns,
                    ContactNameColumns,
                    ContactStatusColumns {
        private Profile() {
        }

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "profile");
        public static final Uri CONTENT_VCARD_URI = Uri.withAppendedPath(CONTENT_URI, "as_vcard");
        public static final Uri CONTENT_RAW_CONTACTS_URI =
                Uri.withAppendedPath(CONTENT_URI, "raw_contacts");
        public static final long MIN_ID = 9223372034707292160L;
    }

    protected interface DeletedContactsColumns {
        String CONTACT_ID = "contact_id";
        String CONTACT_DELETED_TIMESTAMP = "contact_deleted_timestamp";
    }

    public static final class DeletedContacts implements DeletedContactsColumns {
        private DeletedContacts() {
        }

        public static final Uri CONTENT_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "deleted_contacts");
        public static final long DAYS_KEPT_MILLISECONDS = 2592000000L;
    }

    protected interface RawContactsColumns {
        String CONTACT_ID = "contact_id";
        String AGGREGATION_MODE = "aggregation_mode";
        String DELETED = "deleted";
        String METADATA_DIRTY = "metadata_dirty";
        String RAW_CONTACT_IS_READ_ONLY = "raw_contact_is_read_only";
        String RAW_CONTACT_IS_USER_PROFILE = "raw_contact_is_user_profile";
        String DATA_SET = "data_set";
        String ACCOUNT_TYPE_AND_DATA_SET = "account_type_and_data_set";
        String BACKUP_ID = "backup_id";
    }

    public static final class RawContacts
            implements BaseColumns,
                    RawContactsColumns,
                    ContactOptionsColumns,
                    ContactNameColumns,
                    SyncColumns {
        private RawContacts() {
        }

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "raw_contacts");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/raw_contact";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/raw_contact";

        public static final int AGGREGATION_MODE_DEFAULT = 0;
        public static final int AGGREGATION_MODE_IMMEDIATE = 1;
        public static final int AGGREGATION_MODE_SUSPENDED = 2;
        public static final int AGGREGATION_MODE_DISABLED = 3;

        public static Uri getContactLookupUri(ContentResolver resolver, Uri rawContactUri) {
            Uri dataUri = Uri.withAppendedPath(rawContactUri, Data.CONTENT_DIRECTORY);
            Cursor cursor = resolver.query(
                    dataUri,
                    new String[] { RawContacts.CONTACT_ID, Contacts.LOOKUP_KEY },
                    null,
                    null,
                    null);
            if (cursor == null) {
                return null;
            }
            try {
                if (cursor.moveToFirst()) {
                    long contactId = cursor.getLong(0);
                    String lookupKey = cursor.getString(1);
                    return Contacts.getLookupUri(contactId, lookupKey);
                }
            } finally {
                cursor.close();
            }
            return null;
        }

        public static String getLocalAccountName(Context context) {
            return null;
        }

        public static String getLocalAccountType(Context context) {
            return null;
        }

        public static final class Data implements BaseColumns, DataColumns {
            private Data() {
            }

            public static final String CONTENT_DIRECTORY = "data";
        }

        public static final class Entity implements BaseColumns, DataColumns {
            private Entity() {
            }

            public static final String CONTENT_DIRECTORY = "entity";
            public static final String DATA_ID = "data_id";
        }

        public static final class DisplayPhoto {
            private DisplayPhoto() {
            }

            public static final String CONTENT_DIRECTORY = "display_photo";
        }

        public static EntityIterator newEntityIterator(Cursor cursor) {
            return new RawContactEntityIterator(cursor);
        }

        private static final class RawContactEntityIterator implements EntityIterator {
            private static final String[] DATA_KEYS = new String[] {
                DataColumns.DATA1, DataColumns.DATA2, DataColumns.DATA3, DataColumns.DATA4,
                DataColumns.DATA5, DataColumns.DATA6, DataColumns.DATA7, DataColumns.DATA8,
                DataColumns.DATA9, DataColumns.DATA10, DataColumns.DATA11, DataColumns.DATA12,
                DataColumns.DATA13, DataColumns.DATA14, DataColumns.DATA15,
                DataColumns.SYNC1, DataColumns.SYNC2, DataColumns.SYNC3, DataColumns.SYNC4,
                DataColumns.DATA_VERSION, DataColumns.IS_PRIMARY, DataColumns.IS_SUPER_PRIMARY,
                DataColumns.MIMETYPE, DataColumns.RES_PACKAGE, DataColumns.IS_READ_ONLY,
                DataColumns.CARRIER_PRESENCE
            };

            private final Cursor mCursor;
            private boolean mIsClosed;

            RawContactEntityIterator(Cursor cursor) {
                if (cursor == null) throw new NullPointerException("cursor");
                mCursor = cursor;
                mCursor.moveToFirst();
            }

            @Override
            public boolean hasNext() {
                if (mIsClosed) {
                    throw new IllegalStateException("calling hasNext() when the iterator is closed");
                }
                return !mCursor.isAfterLast();
            }

            @Override
            public android.content.Entity next() {
                if (mIsClosed) {
                    throw new IllegalStateException("calling next() when the iterator is closed");
                }
                if (!hasNext()) {
                    throw new NoSuchElementException("you may only call next() if hasNext() is true");
                }
                int dataIdIdx = mCursor.getColumnIndex(Entity.DATA_ID);
                int idIdx = mCursor.getColumnIndex(_ID);
                if (dataIdIdx < 0 || idIdx < 0) {
                    android.content.Entity simple =
                            new android.content.Entity(CalendarContract.cursorRowToContentValues(mCursor));
                    mCursor.moveToNext();
                    return simple;
                }

                long rawContactId = mCursor.getLong(idIdx);
                ContentValues header = new ContentValues();
                String[] cols = mCursor.getColumnNames();
                for (int i = 0; i < cols.length; i++) {
                    String col = cols[i];
                    if (Entity.DATA_ID.equals(col) || isDataColumn(col)) {
                        continue;
                    }
                    putColumn(mCursor, i, col, header);
                }
                android.content.Entity contact = new android.content.Entity(header);
                do {
                    if (rawContactId != mCursor.getLong(idIdx)) {
                        break;
                    }
                    if (!mCursor.isNull(dataIdIdx)) {
                        ContentValues dataValues = new ContentValues();
                        dataValues.put(_ID, mCursor.getLong(dataIdIdx));
                        for (int k = 0; k < DATA_KEYS.length; k++) {
                            String key = DATA_KEYS[k];
                            int idx = mCursor.getColumnIndex(key);
                            if (idx >= 0 && !mCursor.isNull(idx)) {
                                putColumn(mCursor, idx, key, dataValues);
                            }
                        }
                        contact.addSubValue(ContactsContract.Data.CONTENT_URI, dataValues);
                    }
                } while (mCursor.moveToNext());
                return contact;
            }

            private static boolean isDataColumn(String name) {
                for (int i = 0; i < DATA_KEYS.length; i++) {
                    if (DATA_KEYS[i].equals(name)) return true;
                }
                return false;
            }

            private static void putColumn(Cursor c, int idx, String key, ContentValues out) {
                int type = c.getType(idx);
                if (type == Cursor.FIELD_TYPE_NULL) out.putNull(key);
                else if (type == Cursor.FIELD_TYPE_INTEGER) out.put(key, c.getLong(idx));
                else if (type == Cursor.FIELD_TYPE_FLOAT) out.put(key, c.getDouble(idx));
                else if (type == Cursor.FIELD_TYPE_BLOB) out.put(key, c.getBlob(idx));
                else out.put(key, c.getString(idx));
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove not supported");
            }

            @Override
            public void reset() {
                if (mIsClosed) {
                    throw new IllegalStateException("calling reset() when the iterator is closed");
                }
                mCursor.moveToFirst();
            }

            @Override
            public void close() {
                if (!mIsClosed) {
                    mIsClosed = true;
                    mCursor.close();
                }
            }
        }
    }

    protected interface StatusColumns {
        String PRESENCE = "mode";
        @Deprecated String PRESENCE_STATUS = PRESENCE;

        int OFFLINE = 0;
        int INVISIBLE = 1;
        int AWAY = 2;
        int IDLE = 3;
        int DO_NOT_DISTURB = 4;
        int AVAILABLE = 5;

        String STATUS = "status";
        @Deprecated String PRESENCE_CUSTOM_STATUS = STATUS;

        String STATUS_TIMESTAMP = "status_ts";
        String STATUS_RES_PACKAGE = "status_res_package";
        String STATUS_LABEL = "status_label";
        String STATUS_ICON = "status_icon";
        String CHAT_CAPABILITY = "chat_capability";

        int CAPABILITY_HAS_VOICE = 1;
        int CAPABILITY_HAS_VIDEO = 2;
        int CAPABILITY_HAS_CAMERA = 4;
    }

    @Deprecated
    protected interface DataUsageStatColumns {
        @Deprecated String LAST_TIME_USED = "last_time_used";
        @Deprecated String TIMES_USED = "times_used";
    }

    protected interface DataColumns {
        String RES_PACKAGE = "res_package";
        String MIMETYPE = "mimetype";
        String RAW_CONTACT_ID = "raw_contact_id";
        String IS_PRIMARY = "is_primary";
        String IS_SUPER_PRIMARY = "is_super_primary";
        String IS_READ_ONLY = "is_read_only";
        String DATA_VERSION = "data_version";
        String PREFERRED_PHONE_ACCOUNT_COMPONENT_NAME = "preferred_phone_account_component_name";
        String PREFERRED_PHONE_ACCOUNT_ID = "preferred_phone_account_id";

        String DATA1 = "data1";
        String DATA2 = "data2";
        String DATA3 = "data3";
        String DATA4 = "data4";
        String DATA5 = "data5";
        String DATA6 = "data6";
        String DATA7 = "data7";
        String DATA8 = "data8";
        String DATA9 = "data9";
        String DATA10 = "data10";
        String DATA11 = "data11";
        String DATA12 = "data12";
        String DATA13 = "data13";
        String DATA14 = "data14";
        String DATA15 = "data15";

        String SYNC1 = "data_sync1";
        String SYNC2 = "data_sync2";
        String SYNC3 = "data_sync3";
        String SYNC4 = "data_sync4";

        String CARRIER_PRESENCE = "carrier_presence";
        int CARRIER_PRESENCE_VT_CAPABLE = 1;
    }

    protected interface DataColumnsWithJoins
            extends BaseColumns,
                    DataColumns,
                    StatusColumns,
                    RawContactsColumns,
                    ContactsColumns,
                    ContactNameColumns,
                    ContactOptionsColumns,
                    ContactStatusColumns,
                    DataUsageStatColumns {
    }

    public static final class Data implements DataColumnsWithJoins {
        private Data() {
        }

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "data");
        public static final String VISIBLE_CONTACTS_ONLY = "visible_contacts_only";
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/data";

        public static final String EXTRA_ADDRESS_BOOK_INDEX =
                "android.provider.extra.ADDRESS_BOOK_INDEX";
        public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
        public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";

        public static Uri getContactLookupUri(ContentResolver resolver, Uri dataUri) {
            Cursor cursor = resolver.query(
                    dataUri,
                    new String[] { RawContacts.CONTACT_ID, Contacts.LOOKUP_KEY },
                    null,
                    null,
                    null);
            if (cursor == null) {
                return null;
            }
            try {
                if (cursor.moveToFirst()) {
                    long contactId = cursor.getLong(0);
                    String lookupKey = cursor.getString(1);
                    return Contacts.getLookupUri(contactId, lookupKey);
                }
            } finally {
                cursor.close();
            }
            return null;
        }
    }

    public static final class RawContactsEntity
            implements BaseColumns, DataColumns, RawContactsColumns {
        private RawContactsEntity() {
        }

        public static final Uri CONTENT_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "raw_contact_entities");
        public static final Uri PROFILE_CONTENT_URI =
                Uri.withAppendedPath(Profile.CONTENT_URI, "raw_contact_entities");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/raw_contact_entity";
        public static final String DATA_ID = "data_id";
    }

    protected interface PhoneLookupColumns {
        String NUMBER = "number";
        String TYPE = "type";
        String LABEL = "label";
        String NORMALIZED_NUMBER = "normalized_number";
        String CONTACT_ID = "contact_id";
        String DATA_ID = "data_id";
    }

    public static final class PhoneLookup
            implements BaseColumns,
                    PhoneLookupColumns,
                    ContactsColumns,
                    ContactOptionsColumns,
                    ContactNameColumns {
        private PhoneLookup() {
        }

        public static final Uri CONTENT_FILTER_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "phone_lookup");
        public static final Uri ENTERPRISE_CONTENT_FILTER_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "phone_lookup_enterprise");
        public static final String QUERY_PARAMETER_SIP_ADDRESS = "sip";
    }

    protected interface PresenceColumns {
        String DATA_ID = "presence_data_id";
        String PROTOCOL = "protocol";
        String CUSTOM_PROTOCOL = "custom_protocol";
        String IM_HANDLE = "im_handle";
        String IM_ACCOUNT = "im_account";
    }

    public static class StatusUpdates implements StatusColumns, PresenceColumns {
        StatusUpdates() {
        }

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "status_updates");
        public static final Uri PROFILE_CONTENT_URI =
                Uri.withAppendedPath(Profile.CONTENT_URI, "status_updates");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/status-update";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/status-update";

        public static final int getPresenceIconResourceId(int status) {
            switch (status) {
                case AVAILABLE: return 0x0108006b;
                case IDLE:
                case AWAY: return 0x01080067;
                case DO_NOT_DISTURB: return 0x01080068;
                case INVISIBLE: return 0x01080069;
                case OFFLINE:
                default: return 0x0108006a;
            }
        }

        public static final int getPresencePrecedence(int status) {
            return status;
        }
    }

    @Deprecated
    public static final class Presence extends StatusUpdates {
        public Presence() {
        }
    }

    public static class SearchSnippets {
        public SearchSnippets() {
        }

        public static final String SNIPPET = "snippet";
        public static final String DEFERRED_SNIPPETING_KEY = "deferred_snippeting";
    }

    public static final class CommonDataKinds {
        private CommonDataKinds() {
        }

        public interface BaseTypes {
            int TYPE_CUSTOM = 0;
        }

        protected interface CommonColumns extends BaseTypes {
            String DATA = DataColumns.DATA1;
            String TYPE = DataColumns.DATA2;
            String LABEL = DataColumns.DATA3;
        }

        public static final class StructuredName implements DataColumnsWithJoins {
            private StructuredName() {
            }

            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/name";
            public static final String DISPLAY_NAME = DATA1;
            public static final String GIVEN_NAME = DATA2;
            public static final String FAMILY_NAME = DATA3;
            public static final String PREFIX = DATA4;
            public static final String MIDDLE_NAME = DATA5;
            public static final String SUFFIX = DATA6;
            public static final String PHONETIC_GIVEN_NAME = DATA7;
            public static final String PHONETIC_MIDDLE_NAME = DATA8;
            public static final String PHONETIC_FAMILY_NAME = DATA9;
            public static final String FULL_NAME_STYLE = DATA10;
            public static final String PHONETIC_NAME_STYLE = DATA11;

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";
        }

        public static final class Nickname implements DataColumnsWithJoins, CommonColumns {
            private Nickname() {
            }

            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/nickname";
            public static final int TYPE_DEFAULT = 1;
            public static final int TYPE_OTHER_NAME = 2;
            public static final int TYPE_MAIDEN_NAME = 3;
            @Deprecated public static final int TYPE_MAINDEN_NAME = 3;
            public static final int TYPE_SHORT_NAME = 4;
            public static final int TYPE_INITIALS = 5;
            public static final String NAME = DATA;

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";
        }

        public static final class Phone implements DataColumnsWithJoins, CommonColumns {
            private Phone() {
            }

            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/phone_v2";
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/phone_v2";
            public static final Uri CONTENT_URI =
                    Uri.withAppendedPath(Data.CONTENT_URI, "phones");
            public static final Uri ENTERPRISE_CONTENT_URI =
                    Uri.withAppendedPath(Data.CONTENT_URI, "phones_enterprise");
            public static final Uri CONTENT_FILTER_URI =
                    Uri.withAppendedPath(CONTENT_URI, "filter");
            public static final Uri ENTERPRISE_CONTENT_FILTER_URI =
                    Uri.withAppendedPath(CONTENT_URI, "filter_enterprise");
            public static final String SEARCH_DISPLAY_NAME_KEY = "search_display_name";
            public static final String SEARCH_PHONE_NUMBER_KEY = "search_phone_number";

            public static final int TYPE_HOME = 1;
            public static final int TYPE_MOBILE = 2;
            public static final int TYPE_WORK = 3;
            public static final int TYPE_FAX_WORK = 4;
            public static final int TYPE_FAX_HOME = 5;
            public static final int TYPE_PAGER = 6;
            public static final int TYPE_OTHER = 7;
            public static final int TYPE_CALLBACK = 8;
            public static final int TYPE_CAR = 9;
            public static final int TYPE_COMPANY_MAIN = 10;
            public static final int TYPE_ISDN = 11;
            public static final int TYPE_MAIN = 12;
            public static final int TYPE_OTHER_FAX = 13;
            public static final int TYPE_RADIO = 14;
            public static final int TYPE_TELEX = 15;
            public static final int TYPE_TTY_TDD = 16;
            public static final int TYPE_WORK_MOBILE = 17;
            public static final int TYPE_WORK_PAGER = 18;
            public static final int TYPE_ASSISTANT = 19;
            public static final int TYPE_MMS = 20;

            public static final String NUMBER = DATA;
            public static final String NORMALIZED_NUMBER = DATA4;

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";

            public static int getTypeLabelResource(int type) {
                if (type < 0 || type > TYPE_MMS) return 0x010402ba;
                return 0x010402ba + type;
            }

            public static CharSequence getTypeLabel(
                    Resources res, int type, CharSequence label) {
                String fallback;
                switch (type) {
                    case TYPE_HOME: fallback = "Home"; break;
                    case TYPE_MOBILE: fallback = "Mobile"; break;
                    case TYPE_WORK: fallback = "Work"; break;
                    case TYPE_FAX_WORK: fallback = "Work Fax"; break;
                    case TYPE_FAX_HOME: fallback = "Home Fax"; break;
                    case TYPE_PAGER: fallback = "Pager"; break;
                    case TYPE_OTHER: fallback = "Other"; break;
                    case TYPE_CALLBACK: fallback = "Callback"; break;
                    case TYPE_CAR: fallback = "Car"; break;
                    case TYPE_COMPANY_MAIN: fallback = "Company Main"; break;
                    case TYPE_ISDN: fallback = "ISDN"; break;
                    case TYPE_MAIN: fallback = "Main"; break;
                    case TYPE_OTHER_FAX: fallback = "Other Fax"; break;
                    case TYPE_RADIO: fallback = "Radio"; break;
                    case TYPE_TELEX: fallback = "Telex"; break;
                    case TYPE_TTY_TDD: fallback = "TTY TDD"; break;
                    case TYPE_WORK_MOBILE: fallback = "Work Mobile"; break;
                    case TYPE_WORK_PAGER: fallback = "Work Pager"; break;
                    case TYPE_ASSISTANT: fallback = "Assistant"; break;
                    case TYPE_MMS: fallback = "MMS"; break;
                    default: fallback = "Custom"; break;
                }
                return resolveLabel(res, type, label, getTypeLabelResource(type), fallback);
            }
        }

        public static final class Email implements DataColumnsWithJoins, CommonColumns {
            private Email() {
            }

            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/email_v2";
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/email_v2";
            public static final Uri CONTENT_URI = Uri.withAppendedPath(Data.CONTENT_URI, "emails");
            public static final Uri CONTENT_LOOKUP_URI =
                    Uri.withAppendedPath(CONTENT_URI, "lookup");
            public static final Uri ENTERPRISE_CONTENT_LOOKUP_URI =
                    Uri.withAppendedPath(CONTENT_URI, "lookup_enterprise");
            public static final Uri CONTENT_FILTER_URI =
                    Uri.withAppendedPath(CONTENT_URI, "filter");
            public static final Uri ENTERPRISE_CONTENT_FILTER_URI =
                    Uri.withAppendedPath(CONTENT_URI, "filter_enterprise");

            public static final String ADDRESS = DATA1;
            public static final int TYPE_HOME = 1;
            public static final int TYPE_WORK = 2;
            public static final int TYPE_OTHER = 3;
            public static final int TYPE_MOBILE = 4;
            public static final String DISPLAY_NAME = DATA4;

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";

            public static int getTypeLabelResource(int type) {
                if (type < 0 || type > TYPE_MOBILE) return 0x010402d0;
                return 0x010402d0 + type;
            }

            public static CharSequence getTypeLabel(
                    Resources res, int type, CharSequence label) {
                String fallback;
                switch (type) {
                    case TYPE_HOME: fallback = "Home"; break;
                    case TYPE_WORK: fallback = "Work"; break;
                    case TYPE_OTHER: fallback = "Other"; break;
                    case TYPE_MOBILE: fallback = "Mobile"; break;
                    default: fallback = "Custom"; break;
                }
                return resolveLabel(res, type, label, getTypeLabelResource(type), fallback);
            }
        }

        public static final class StructuredPostal
                implements DataColumnsWithJoins, CommonColumns {
            private StructuredPostal() {
            }

            public static final String CONTENT_ITEM_TYPE =
                    "vnd.android.cursor.item/postal-address_v2";
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/postal-address_v2";
            public static final Uri CONTENT_URI =
                    Uri.withAppendedPath(Data.CONTENT_URI, "postals");

            public static final int TYPE_HOME = 1;
            public static final int TYPE_WORK = 2;
            public static final int TYPE_OTHER = 3;

            public static final String FORMATTED_ADDRESS = DATA;
            public static final String STREET = DATA4;
            public static final String POBOX = DATA5;
            public static final String NEIGHBORHOOD = DATA6;
            public static final String CITY = DATA7;
            public static final String REGION = DATA8;
            public static final String POSTCODE = DATA9;
            public static final String COUNTRY = DATA10;

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";

            public static int getTypeLabelResource(int type) {
                if (type < 0 || type > TYPE_OTHER) return 0x010402d8;
                return 0x010402d8 + type;
            }

            public static CharSequence getTypeLabel(
                    Resources res, int type, CharSequence label) {
                String fallback;
                switch (type) {
                    case TYPE_HOME: fallback = "Home"; break;
                    case TYPE_WORK: fallback = "Work"; break;
                    case TYPE_OTHER: fallback = "Other"; break;
                    default: fallback = "Custom"; break;
                }
                return resolveLabel(res, type, label, getTypeLabelResource(type), fallback);
            }
        }

        public static final class Im implements DataColumnsWithJoins, CommonColumns {
            private Im() {
            }

            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/im";
            public static final int TYPE_HOME = 1;
            public static final int TYPE_WORK = 2;
            public static final int TYPE_OTHER = 3;

            public static final String PROTOCOL = DATA5;
            public static final String CUSTOM_PROTOCOL = DATA6;

            public static final int PROTOCOL_CUSTOM = -1;
            public static final int PROTOCOL_AIM = 0;
            public static final int PROTOCOL_MSN = 1;
            public static final int PROTOCOL_YAHOO = 2;
            public static final int PROTOCOL_SKYPE = 3;
            public static final int PROTOCOL_QQ = 4;
            public static final int PROTOCOL_GOOGLE_TALK = 5;
            public static final int PROTOCOL_ICQ = 6;
            public static final int PROTOCOL_JABBER = 7;
            public static final int PROTOCOL_NETMEETING = 8;

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";

            public static int getTypeLabelResource(int type) {
                if (type < 0 || type > TYPE_OTHER) return 0x010402e0;
                return 0x010402e0 + type;
            }

            public static CharSequence getTypeLabel(
                    Resources res, int type, CharSequence label) {
                String fallback;
                switch (type) {
                    case TYPE_HOME: fallback = "Home"; break;
                    case TYPE_WORK: fallback = "Work"; break;
                    case TYPE_OTHER: fallback = "Other"; break;
                    default: fallback = "Custom"; break;
                }
                return resolveLabel(res, type, label, getTypeLabelResource(type), fallback);
            }

            public static int getProtocolLabelResource(int type) {
                if (type < 0 || type > PROTOCOL_NETMEETING) return 0x010402e8;
                return 0x010402e9 + type;
            }

            public static CharSequence getProtocolLabel(
                    Resources res, int type, CharSequence label) {
                if (type == PROTOCOL_CUSTOM && label != null && label.length() > 0) {
                    return label;
                }
                String fallback;
                switch (type) {
                    case PROTOCOL_AIM: fallback = "AIM"; break;
                    case PROTOCOL_MSN: fallback = "Windows Live"; break;
                    case PROTOCOL_YAHOO: fallback = "Yahoo"; break;
                    case PROTOCOL_SKYPE: fallback = "Skype"; break;
                    case PROTOCOL_QQ: fallback = "QQ"; break;
                    case PROTOCOL_GOOGLE_TALK: fallback = "Hangouts"; break;
                    case PROTOCOL_ICQ: fallback = "ICQ"; break;
                    case PROTOCOL_JABBER: fallback = "Jabber"; break;
                    case PROTOCOL_NETMEETING: fallback = "NetMeeting"; break;
                    default: fallback = "Custom"; break;
                }
                return resolveLabel(
                        res,
                        type == PROTOCOL_CUSTOM ? TYPE_CUSTOM : 1,
                        label,
                        getProtocolLabelResource(type),
                        fallback);
            }
        }

        public static final class Organization implements DataColumnsWithJoins, CommonColumns {
            private Organization() {
            }

            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/organization";
            public static final int TYPE_WORK = 1;
            public static final int TYPE_OTHER = 2;

            public static final String COMPANY = DATA;
            public static final String TITLE = DATA4;
            public static final String DEPARTMENT = DATA5;
            public static final String JOB_DESCRIPTION = DATA6;
            public static final String SYMBOL = DATA7;
            public static final String PHONETIC_NAME = DATA8;
            public static final String OFFICE_LOCATION = DATA9;
            public static final String PHONETIC_NAME_STYLE = DATA10;

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";

            public static int getTypeLabelResource(int type) {
                if (type < 0 || type > TYPE_OTHER) return 0x010402f4;
                return 0x010402f4 + type;
            }

            public static CharSequence getTypeLabel(
                    Resources res, int type, CharSequence label) {
                String fallback;
                switch (type) {
                    case TYPE_WORK: fallback = "Work"; break;
                    case TYPE_OTHER: fallback = "Other"; break;
                    default: fallback = "Custom"; break;
                }
                return resolveLabel(res, type, label, getTypeLabelResource(type), fallback);
            }
        }

        public static final class Relation implements DataColumnsWithJoins, CommonColumns {
            private Relation() {
            }

            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/relation";
            public static final String NAME = DATA;

            public static final int TYPE_ASSISTANT = 1;
            public static final int TYPE_BROTHER = 2;
            public static final int TYPE_CHILD = 3;
            public static final int TYPE_DOMESTIC_PARTNER = 4;
            public static final int TYPE_FATHER = 5;
            public static final int TYPE_FRIEND = 6;
            public static final int TYPE_MANAGER = 7;
            public static final int TYPE_MOTHER = 8;
            public static final int TYPE_PARENT = 9;
            public static final int TYPE_PARTNER = 10;
            public static final int TYPE_REFERRED_BY = 11;
            public static final int TYPE_RELATIVE = 12;
            public static final int TYPE_SISTER = 13;
            public static final int TYPE_SPOUSE = 14;

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";

            public static int getTypeLabelResource(int type) {
                if (type < 0 || type > TYPE_SPOUSE) return 0x010402fa;
                return 0x010402fa + type;
            }

            public static CharSequence getTypeLabel(
                    Resources res, int type, CharSequence label) {
                String fallback;
                switch (type) {
                    case TYPE_ASSISTANT: fallback = "Assistant"; break;
                    case TYPE_BROTHER: fallback = "Brother"; break;
                    case TYPE_CHILD: fallback = "Child"; break;
                    case TYPE_DOMESTIC_PARTNER: fallback = "Domestic Partner"; break;
                    case TYPE_FATHER: fallback = "Father"; break;
                    case TYPE_FRIEND: fallback = "Friend"; break;
                    case TYPE_MANAGER: fallback = "Manager"; break;
                    case TYPE_MOTHER: fallback = "Mother"; break;
                    case TYPE_PARENT: fallback = "Parent"; break;
                    case TYPE_PARTNER: fallback = "Partner"; break;
                    case TYPE_REFERRED_BY: fallback = "Referred by"; break;
                    case TYPE_RELATIVE: fallback = "Relative"; break;
                    case TYPE_SISTER: fallback = "Sister"; break;
                    case TYPE_SPOUSE: fallback = "Spouse"; break;
                    default: fallback = "Custom"; break;
                }
                return resolveLabel(res, type, label, getTypeLabelResource(type), fallback);
            }
        }

        public static final class Event implements DataColumnsWithJoins, CommonColumns {
            private Event() {
            }

            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/contact_event";
            public static final int TYPE_ANNIVERSARY = 1;
            public static final int TYPE_OTHER = 2;
            public static final int TYPE_BIRTHDAY = 3;
            public static final String START_DATE = DATA;

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";

            public static int getTypeResource(Integer type) {
                if (type == null || type < 0 || type > TYPE_BIRTHDAY) return 0x01040310;
                return 0x01040310 + type;
            }

            public static CharSequence getTypeLabel(
                    Resources res, int type, CharSequence label) {
                String fallback;
                switch (type) {
                    case TYPE_ANNIVERSARY: fallback = "Anniversary"; break;
                    case TYPE_OTHER: fallback = "Other"; break;
                    case TYPE_BIRTHDAY: fallback = "Birthday"; break;
                    default: fallback = "Custom"; break;
                }
                return resolveLabel(res, type, label, getTypeResource(type), fallback);
            }
        }

        public static final class Photo implements DataColumnsWithJoins {
            private Photo() {
            }

            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/photo";
            public static final String PHOTO_FILE_ID = DATA14;
            public static final String PHOTO = DATA15;

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";
        }

        public static final class Note implements DataColumnsWithJoins {
            private Note() {
            }

            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/note";
            public static final String NOTE = DATA1;

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";
        }

        public static final class GroupMembership implements DataColumnsWithJoins {
            private GroupMembership() {
            }

            public static final String CONTENT_ITEM_TYPE =
                    "vnd.android.cursor.item/group_membership";
            public static final String GROUP_ROW_ID = DATA1;
            public static final String GROUP_SOURCE_ID = "group_sourceid";

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";
        }

        public static final class Website implements DataColumnsWithJoins, CommonColumns {
            private Website() {
            }

            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/website";
            public static final String URL = DATA;

            public static final int TYPE_HOMEPAGE = 1;
            public static final int TYPE_BLOG = 2;
            public static final int TYPE_PROFILE = 3;
            public static final int TYPE_HOME = 4;
            public static final int TYPE_WORK = 5;
            public static final int TYPE_FTP = 6;
            public static final int TYPE_OTHER = 7;

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";
        }

        public static final class SipAddress implements DataColumnsWithJoins, CommonColumns {
            private SipAddress() {
            }

            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/sip_address";
            public static final int TYPE_HOME = 1;
            public static final int TYPE_WORK = 2;
            public static final int TYPE_OTHER = 3;
            public static final String SIP_ADDRESS = DATA;

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";

            public static int getTypeLabelResource(int type) {
                if (type < 0 || type > TYPE_OTHER) return 0x01040318;
                return 0x01040318 + type;
            }

            public static CharSequence getTypeLabel(
                    Resources res, int type, CharSequence label) {
                String fallback;
                switch (type) {
                    case TYPE_HOME: fallback = "Home"; break;
                    case TYPE_WORK: fallback = "Work"; break;
                    case TYPE_OTHER: fallback = "Other"; break;
                    default: fallback = "Custom"; break;
                }
                return resolveLabel(res, type, label, getTypeLabelResource(type), fallback);
            }
        }

        public static final class Identity implements DataColumnsWithJoins {
            private Identity() {
            }

            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/identity";
            public static final String IDENTITY = DATA1;
            public static final String NAMESPACE = DATA2;

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";
        }

        public static final class Callable implements DataColumnsWithJoins, CommonColumns {
            public Callable() {
            }

            public static final Uri CONTENT_URI =
                    Uri.withAppendedPath(Data.CONTENT_URI, "callables");
            public static final Uri CONTENT_FILTER_URI =
                    Uri.withAppendedPath(CONTENT_URI, "filter");
            public static final Uri ENTERPRISE_CONTENT_FILTER_URI =
                    Uri.withAppendedPath(CONTENT_URI, "filter_enterprise");

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";
        }

        public static final class Contactables implements DataColumnsWithJoins, CommonColumns {
            public Contactables() {
            }

            public static final Uri CONTENT_URI =
                    Uri.withAppendedPath(Data.CONTENT_URI, "contactables");
            public static final Uri CONTENT_FILTER_URI =
                    Uri.withAppendedPath(CONTENT_URI, "filter");
            public static final String VISIBLE_CONTACTS_ONLY = "visible_contacts_only";

            public static final String EXTRA_ADDRESS_BOOK_INDEX =
                    "android.provider.extra.ADDRESS_BOOK_INDEX";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS =
                    "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";
        }
    }

    protected interface GroupsColumns {
        String DATA_SET = "data_set";
        String TITLE = "title";
        String RES_PACKAGE = "res_package";
        String TITLE_RES = "title_res";
        String NOTES = "notes";
        String SYSTEM_ID = "system_id";
        String SUMMARY_COUNT = "summ_count";
        String SUMMARY_WITH_PHONES = "summ_phones";
        String GROUP_VISIBLE = "group_visible";
        String DELETED = "deleted";
        String SHOULD_SYNC = "should_sync";
        String AUTO_ADD = "auto_add";
        String FAVORITES = "favorites";
        String GROUP_IS_READ_ONLY = "group_is_read_only";
    }

    public static final class Groups
            implements BaseColumns, GroupsColumns, SyncColumns {
        private Groups() {
        }

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "groups");
        public static final Uri CONTENT_SUMMARY_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "groups_summary");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/group";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/group";

        public static EntityIterator newEntityIterator(final Cursor cursor) {
            if (cursor == null) throw new NullPointerException("cursor");
            cursor.moveToFirst();
            return new EntityIterator() {
                private boolean mClosed;

                @Override
                public boolean hasNext() {
                    if (mClosed) throw new IllegalStateException("closed");
                    return !cursor.isAfterLast();
                }

                @Override
                public Entity next() {
                    if (mClosed) throw new IllegalStateException("closed");
                    if (!hasNext()) throw new NoSuchElementException();
                    Entity e = new Entity(CalendarContract.cursorRowToContentValues(cursor));
                    cursor.moveToNext();
                    return e;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void reset() {
                    if (mClosed) throw new IllegalStateException("closed");
                    cursor.moveToFirst();
                }

                @Override
                public void close() {
                    if (!mClosed) {
                        mClosed = true;
                        cursor.close();
                    }
                }
            };
        }
    }

    public static final class AggregationExceptions implements BaseColumns {
        private AggregationExceptions() {
        }

        public static final Uri CONTENT_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "aggregation_exceptions");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/aggregation_exception";
        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/aggregation_exception";

        public static final String TYPE = "type";
        public static final int TYPE_AUTOMATIC = 0;
        public static final int TYPE_KEEP_TOGETHER = 1;
        public static final int TYPE_KEEP_SEPARATE = 2;

        public static final String RAW_CONTACT_ID1 = "raw_contact_id1";
        public static final String RAW_CONTACT_ID2 = "raw_contact_id2";
    }

    protected interface SettingsColumns {
        String ACCOUNT_NAME = "account_name";
        String ACCOUNT_TYPE = "account_type";
        String DATA_SET = "data_set";
        String SHOULD_SYNC = "should_sync";
        String UNGROUPED_VISIBLE = "ungrouped_visible";
        String ANY_UNSYNCED = "any_unsynced";
        String UNGROUPED_COUNT = "summ_count";
        String UNGROUPED_WITH_PHONES = "summ_phones";
    }

    public static final class Settings implements SettingsColumns {
        private Settings() {
        }

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "settings");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/setting";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/setting";
        public static final String ACTION_SET_DEFAULT_ACCOUNT =
                "android.provider.action.SET_DEFAULT_ACCOUNT";

        public static Account getDefaultAccount(ContentResolver resolver) {
            if (resolver == null) throw new NullPointerException("resolver");
            Bundle response = null;
            try {
                response = resolver.call(AUTHORITY_URI, "getDefaultAccount", null, null);
            } catch (RuntimeException ignored) {
                return null;
            }
            if (response == null) {
                return null;
            }
            return response.getParcelable("key_default_account");
        }
    }

    public static final class ProviderStatus {
        private ProviderStatus() {
        }

        public static final Uri CONTENT_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "provider_status");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/provider_status";

        public static final String STATUS = "status";
        public static final int STATUS_NORMAL = 0;
        public static final int STATUS_BUSY = 1;
        public static final int STATUS_EMPTY = 2;
        public static final String DATABASE_CREATION_TIMESTAMP = "database_creation_timestamp";
    }

    public static final class QuickContact {
        public QuickContact() {
        }

        public static final String ACTION_QUICK_CONTACT = "android.provider.action.QUICK_CONTACT";
        public static final String EXTRA_MODE = "android.provider.extra.MODE";
        public static final String EXTRA_EXCLUDE_MIMES = "android.provider.extra.EXCLUDE_MIMES";
        public static final String EXTRA_PRIORITIZED_MIMETYPE =
                "android.provider.extra.PRIORITIZED_MIMETYPE";

        public static final int MODE_SMALL = 1;
        public static final int MODE_MEDIUM = 2;
        public static final int MODE_LARGE = 3;

        public static void showQuickContact(
                Context context,
                View target,
                Uri lookupUri,
                int mode,
                String[] excludeMimes) {
            Rect rect = new Rect();
            if (target != null) {
                int[] loc = new int[2];
                target.getLocationOnScreen(loc);
                rect.set(loc[0], loc[1], loc[0] + target.getWidth(), loc[1] + target.getHeight());
            }
            showQuickContact(context, rect, lookupUri, mode, excludeMimes);
        }

        public static void showQuickContact(
                Context context,
                Rect target,
                Uri lookupUri,
                int mode,
                String[] excludeMimes) {
            Intent intent = new Intent(ACTION_QUICK_CONTACT);
            intent.setData(lookupUri);
            intent.setSourceBounds(target);
            intent.putExtra(EXTRA_MODE, mode);
            intent.putExtra(EXTRA_EXCLUDE_MIMES, excludeMimes);
            context.startActivity(intent);
        }

        public static void showQuickContact(
                Context context,
                View target,
                Uri lookupUri,
                String[] excludeMimes,
                String prioritizedMimeType) {
            Rect rect = new Rect();
            if (target != null) {
                int[] loc = new int[2];
                target.getLocationOnScreen(loc);
                rect.set(loc[0], loc[1], loc[0] + target.getWidth(), loc[1] + target.getHeight());
            }
            showQuickContact(context, rect, lookupUri, excludeMimes, prioritizedMimeType);
        }

        public static void showQuickContact(
                Context context,
                Rect target,
                Uri lookupUri,
                String[] excludeMimes,
                String prioritizedMimeType) {
            Intent intent = new Intent(ACTION_QUICK_CONTACT);
            intent.setData(lookupUri);
            intent.setSourceBounds(target);
            intent.putExtra(EXTRA_MODE, MODE_LARGE);
            intent.putExtra(EXTRA_EXCLUDE_MIMES, excludeMimes);
            intent.putExtra(EXTRA_PRIORITIZED_MIMETYPE, prioritizedMimeType);
            context.startActivity(intent);
        }
    }

    public static final class DisplayPhoto {
        private DisplayPhoto() {
        }

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "display_photo");
        public static final Uri CONTENT_MAX_DIMENSIONS_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "photo_dimensions");
        public static final String DISPLAY_MAX_DIM = "display_max_dim";
        public static final String THUMBNAIL_MAX_DIM = "thumbnail_max_dim";
    }

    @Deprecated
    public static final class DataUsageFeedback {
        @Deprecated
        public DataUsageFeedback() {
        }

        @Deprecated
        public static final Uri FEEDBACK_URI =
                Uri.withAppendedPath(Data.CONTENT_URI, "usagefeedback");
        @Deprecated
        public static final Uri DELETE_USAGE_URI =
                Uri.withAppendedPath(Contacts.CONTENT_URI, "delete_usage");
        @Deprecated public static final String USAGE_TYPE = "type";
        @Deprecated public static final String USAGE_TYPE_CALL = "call";
        @Deprecated public static final String USAGE_TYPE_LONG_TEXT = "long_text";
        @Deprecated public static final String USAGE_TYPE_SHORT_TEXT = "short_text";
    }

    public static final class PinnedPositions {
        public PinnedPositions() {
        }

        public static final int UNPINNED = 0;
        public static final int DEMOTED = -1;

        public static void undemote(ContentResolver contentResolver, long contactId) {
            try {
                contentResolver.call(
                        AUTHORITY_URI, "undemote", String.valueOf(contactId), null);
            } catch (RuntimeException ignored) {
            }
        }

        public static void pin(ContentResolver contentResolver, long contactId, int pinnedPosition) {
            Uri uri = Uri.withAppendedPath(Contacts.CONTENT_URI, String.valueOf(contactId));
            ContentValues values = new ContentValues();
            values.put(Contacts.PINNED, pinnedPosition);
            contentResolver.update(uri, values, null, null);
        }
    }

    public static final class SimAccount implements Parcelable {
        public static final int UNKNOWN_EF_TYPE = 0;
        public static final int ADN_EF_TYPE = 1;
        public static final int FDN_EF_TYPE = 2;
        public static final int SDN_EF_TYPE = 3;

        private final String mAccountName;
        private final String mAccountType;
        private final int mSimSlotIndex;
        private final int mEfType;

        SimAccount(String accountName, String accountType, int simSlotIndex, int efType) {
            mAccountName = accountName;
            mAccountType = accountType;
            mSimSlotIndex = simSlotIndex;
            mEfType = efType;
        }

        public String getAccountName() {
            return mAccountName;
        }

        public String getAccountType() {
            return mAccountType;
        }

        public int getSimSlotIndex() {
            return mSimSlotIndex;
        }

        public int getEfType() {
            return mEfType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mAccountName, mAccountType, mSimSlotIndex, mEfType);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof SimAccount)) return false;
            SimAccount other = (SimAccount) obj;
            return mSimSlotIndex == other.mSimSlotIndex
                    && mEfType == other.mEfType
                    && Objects.equals(mAccountName, other.mAccountName)
                    && Objects.equals(mAccountType, other.mAccountType);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mAccountName);
            dest.writeString(mAccountType);
            dest.writeInt(mSimSlotIndex);
            dest.writeInt(mEfType);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<SimAccount> CREATOR =
                new Parcelable.Creator<SimAccount>() {
                    @Override
                    public SimAccount createFromParcel(Parcel source) {
                        String accountName = source.readString();
                        String accountType = source.readString();
                        int simSlot = source.readInt();
                        int efType = source.readInt();
                        return new SimAccount(accountName, accountType, simSlot, efType);
                    }

                    @Override
                    public SimAccount[] newArray(int size) {
                        return new SimAccount[size];
                    }
                };
    }

    public static final class SimContacts {
        private SimContacts() {
        }

        public static final String ACTION_SIM_ACCOUNTS_CHANGED =
                "android.provider.action.SIM_ACCOUNTS_CHANGED";

        public static List<SimAccount> getSimAccounts(ContentResolver contentResolver) {
            if (contentResolver == null) throw new NullPointerException("contentResolver");
            try {
                Bundle response = contentResolver.call(
                        AUTHORITY_URI, "querySimAccounts", null, null);
                if (response != null) {
                    List<SimAccount> list = response.getParcelableArrayList("key_sim_accounts");
                    if (list != null) {
                        return list;
                    }
                }
            } catch (RuntimeException ignored) {
            }
            return Collections.emptyList();
        }
    }

    public static final class Intents {
        public Intents() {
        }

        public static final String SEARCH_SUGGESTION_CLICKED =
                "android.provider.Contacts.SEARCH_SUGGESTION_CLICKED";
        public static final String SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED =
                "android.provider.Contacts.SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED";
        public static final String SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED =
                "android.provider.Contacts.SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED";
        public static final String ATTACH_IMAGE = "com.android.contacts.action.ATTACH_IMAGE";
        public static final String INVITE_CONTACT = "com.android.contacts.action.INVITE_CONTACT";
        public static final String SHOW_OR_CREATE_CONTACT =
                "com.android.contacts.action.SHOW_OR_CREATE_CONTACT";
        public static final String ACTION_VOICE_SEND_MESSAGE_TO_CONTACTS =
                "android.provider.action.VOICE_SEND_MESSAGE_TO_CONTACTS";
        public static final String METADATA_MIMETYPE = "android.provider.mimetype";
        public static final String METADATA_ACCOUNT_TYPE = "android.provider.account_type";
        public static final String EXTRA_RECIPIENT_CONTACT_URI =
                "android.provider.extra.RECIPIENT_CONTACT_URI";
        public static final String EXTRA_RECIPIENT_CONTACT_CHAT_ID =
                "android.provider.extra.RECIPIENT_CONTACT_CHAT_ID";
        public static final String EXTRA_RECIPIENT_CONTACT_NAME =
                "android.provider.extra.RECIPIENT_CONTACT_NAME";
        public static final String EXTRA_FORCE_CREATE = "com.android.contacts.action.FORCE_CREATE";
        public static final String EXTRA_CREATE_DESCRIPTION =
                "com.android.contacts.action.CREATE_DESCRIPTION";
        public static final String CONTACTS_DATABASE_CREATED =
                "android.provider.Contacts.DATABASE_CREATED";

        public static final class Insert {
            public Insert() {
            }

            public static final String ACTION = "android.intent.action.INSERT";
            public static final String FULL_MODE = "full_mode";
            public static final String NAME = "name";
            public static final String PHONETIC_NAME = "phonetic_name";
            public static final String COMPANY = "company";
            public static final String JOB_TITLE = "job_title";
            public static final String NOTES = "notes";
            public static final String PHONE = "phone";
            public static final String PHONE_TYPE = "phone_type";
            public static final String PHONE_ISPRIMARY = "phone_isprimary";
            public static final String SECONDARY_PHONE = "secondary_phone";
            public static final String SECONDARY_PHONE_TYPE = "secondary_phone_type";
            public static final String TERTIARY_PHONE = "tertiary_phone";
            public static final String TERTIARY_PHONE_TYPE = "tertiary_phone_type";
            public static final String EMAIL = "email";
            public static final String EMAIL_TYPE = "email_type";
            public static final String EMAIL_ISPRIMARY = "email_isprimary";
            public static final String SECONDARY_EMAIL = "secondary_email";
            public static final String SECONDARY_EMAIL_TYPE = "secondary_email_type";
            public static final String TERTIARY_EMAIL = "tertiary_email";
            public static final String TERTIARY_EMAIL_TYPE = "tertiary_email_type";
            public static final String POSTAL = "postal";
            public static final String POSTAL_TYPE = "postal_type";
            public static final String POSTAL_ISPRIMARY = "postal_isprimary";
            public static final String IM_HANDLE = "im_handle";
            public static final String IM_PROTOCOL = "im_protocol";
            public static final String IM_ISPRIMARY = "im_isprimary";
            public static final String DATA = "data";
            public static final String EXTRA_ACCOUNT = "android.provider.extra.ACCOUNT";
            public static final String EXTRA_DATA_SET = "android.provider.extra.DATA_SET";
        }
    }
}
