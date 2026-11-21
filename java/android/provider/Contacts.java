// SPDX-License-Identifier: GPL-3.0-only
package android.provider;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Legacy Contacts provider contract superseded by {@link ContactsContract}.
 */
@Deprecated
public class Contacts {
    private Contacts() {
    }

    @Deprecated public static final String AUTHORITY = "contacts";
    @Deprecated public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    @Deprecated public static final int KIND_EMAIL = 1;
    @Deprecated public static final int KIND_POSTAL = 2;
    @Deprecated public static final int KIND_IM = 3;
    @Deprecated public static final int KIND_ORGANIZATION = 4;
    @Deprecated public static final int KIND_PHONE = 5;

    @Deprecated
    public interface SettingsColumns {
        @Deprecated String _SYNC_ACCOUNT = "_sync_account";
        @Deprecated String _SYNC_ACCOUNT_TYPE = "_sync_account_type";
        @Deprecated String KEY = "key";
        @Deprecated String VALUE = "value";
    }

    @Deprecated
    public static final class Settings implements BaseColumns, SettingsColumns {
        private Settings() {
        }

        @Deprecated public static final Uri CONTENT_URI = Uri.parse("content://contacts/settings");
        @Deprecated public static final String CONTENT_DIRECTORY = "settings";
        @Deprecated public static final String DEFAULT_SORT_ORDER = "key ASC";
        @Deprecated public static final String SYNC_EVERYTHING = "syncEverything";

        @Deprecated
        public static String getSetting(ContentResolver cr, String account, String key) {
            String selectString = (account == null)
                    ? "_sync_account IS NULL AND _sync_account_type IS NULL AND key=?"
                    : "_sync_account=? AND key=?";
            String[] selectArgs = (account == null)
                    ? new String[] { key }
                    : new String[] { account, key };
            Cursor cursor = cr.query(
                    Settings.CONTENT_URI, new String[] { VALUE }, selectString, selectArgs, null);
            if (cursor == null) return null;
            try {
                return cursor.moveToNext() ? cursor.getString(0) : null;
            } finally {
                cursor.close();
            }
        }

        @Deprecated
        public static void setSetting(
                ContentResolver cr, String account, String key, String value) {
            ContentValues values = new ContentValues();
            values.put(KEY, key);
            values.put(VALUE, value);
            cr.update(Settings.CONTENT_URI, values, null, null);
        }
    }

    @Deprecated
    public interface PeopleColumns {
        @Deprecated String NAME = "name";
        @Deprecated String PHONETIC_NAME = "phonetic_name";
        @Deprecated String DISPLAY_NAME = "display_name";
        @Deprecated String NOTES = "notes";
        @Deprecated String TIMES_CONTACTED = "times_contacted";
        @Deprecated String LAST_TIME_CONTACTED = "last_time_contacted";
        @Deprecated String CUSTOM_RINGTONE = "custom_ringtone";
        @Deprecated String SEND_TO_VOICEMAIL = "send_to_voicemail";
        @Deprecated String STARRED = "starred";
        @Deprecated String PHOTO_VERSION = "photo_version";
    }

    @Deprecated
    public static final class People
            implements BaseColumns, PeopleColumns, PhonesColumns, PresenceColumns {
        private People() {
        }

        @Deprecated public static final Uri CONTENT_URI = Uri.parse("content://contacts/people");
        @Deprecated public static final Uri CONTENT_FILTER_URI =
                Uri.parse("content://contacts/people/filter");
        @Deprecated public static final Uri DELETED_CONTENT_URI =
                Uri.parse("content://contacts/deleted_people");
        @Deprecated public static final String CONTENT_TYPE = "vnd.android.cursor.dir/person";
        @Deprecated public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/person";
        @Deprecated public static final String DEFAULT_SORT_ORDER = People.NAME + " ASC";
        @Deprecated public static final String PRIMARY_PHONE_ID = "primary_phone";
        @Deprecated public static final String PRIMARY_EMAIL_ID = "primary_email";
        @Deprecated public static final String PRIMARY_ORGANIZATION_ID = "primary_organization";

        @Deprecated
        public static void markAsContacted(ContentResolver resolver, long personId) {
        }

        @Deprecated
        public static Uri addToMyContactsGroup(ContentResolver resolver, long personId) {
            return addToGroup(resolver, personId, Groups.GROUP_MY_CONTACTS);
        }

        @Deprecated
        public static Uri addToGroup(ContentResolver resolver, long personId, String groupName) {
            long groupId = 0;
            Cursor groupsCursor = resolver.query(
                    Groups.CONTENT_URI,
                    new String[] { Groups._ID },
                    Groups.NAME + "=?",
                    new String[] { groupName },
                    null);
            if (groupsCursor != null) {
                try {
                    if (groupsCursor.moveToFirst()) {
                        groupId = groupsCursor.getLong(0);
                    }
                } finally {
                    groupsCursor.close();
                }
            }
            if (groupId == 0) {
                throw new IllegalStateException("Failed to find the My Contacts group");
            }
            return addToGroup(resolver, personId, groupId);
        }

        @Deprecated
        public static Uri addToGroup(ContentResolver resolver, long personId, long groupId) {
            ContentValues values = new ContentValues();
            values.put(GroupMembership.PERSON_ID, personId);
            values.put(GroupMembership.GROUP_ID, groupId);
            return resolver.insert(GroupMembership.CONTENT_URI, values);
        }

        @Deprecated
        public static Uri createPersonInMyContactsGroup(
                ContentResolver resolver, ContentValues values) {
            Uri contactUri = resolver.insert(People.CONTENT_URI, values);
            if (contactUri == null) {
                return null;
            }
            if (addToMyContactsGroup(resolver, ContentUris.parseId(contactUri)) == null) {
                resolver.delete(contactUri, null, null);
                return null;
            }
            return contactUri;
        }

        @Deprecated
        public static Cursor queryGroups(ContentResolver resolver, long person) {
            return resolver.query(
                    GroupMembership.CONTENT_URI,
                    null,
                    "person=?",
                    new String[] { String.valueOf(person) },
                    Groups.DEFAULT_SORT_ORDER);
        }

        @Deprecated
        public static void setPhotoData(ContentResolver cr, Uri person, byte[] data) {
            Uri photoUri = Uri.withAppendedPath(person, Photos.CONTENT_DIRECTORY);
            ContentValues values = new ContentValues();
            values.put(Photos.DATA, data);
            cr.update(photoUri, values, null, null);
        }

        @Deprecated
        public static InputStream openContactPhotoInputStream(ContentResolver cr, Uri person) {
            if (person == null) return null;
            Uri photoUri = Uri.withAppendedPath(person, Photos.CONTENT_DIRECTORY);
            Cursor cursor = cr.query(photoUri, new String[] { Photos.DATA }, null, null, null);
            if (cursor == null) return null;
            try {
                if (!cursor.moveToNext()) return null;
                byte[] data = cursor.getBlob(0);
                return data == null ? null : new ByteArrayInputStream(data);
            } finally {
                cursor.close();
            }
        }

        @Deprecated
        public static Bitmap loadContactPhoto(
                Context context,
                Uri person,
                int placeholderImageResource,
                BitmapFactory.Options options) {
            if (person == null) {
                return placeholderImageResource != 0 && context != null
                        ? BitmapFactory.decodeResource(
                                context.getResources(), placeholderImageResource, options)
                        : null;
            }
            InputStream stream = openContactPhotoInputStream(context.getContentResolver(), person);
            Bitmap bm = stream != null ? BitmapFactory.decodeStream(stream, null, options) : null;
            if (bm == null && placeholderImageResource != 0 && context != null) {
                bm = BitmapFactory.decodeResource(
                        context.getResources(), placeholderImageResource, options);
            }
            return bm;
        }

        @Deprecated
        public static final class Phones implements BaseColumns, PhonesColumns, PeopleColumns {
            private Phones() {
            }

            @Deprecated public static final String CONTENT_DIRECTORY = "phones";
            @Deprecated public static final String DEFAULT_SORT_ORDER = "number ASC";
        }

        @Deprecated
        public static final class ContactMethods
                implements BaseColumns, ContactMethodsColumns, PeopleColumns {
            private ContactMethods() {
            }

            @Deprecated public static final String CONTENT_DIRECTORY = "contact_methods";
            @Deprecated public static final String DEFAULT_SORT_ORDER = "data ASC";
        }

        @Deprecated
        public static class Extensions implements BaseColumns, ExtensionsColumns {
            private Extensions() {
            }

            @Deprecated public static final String CONTENT_DIRECTORY = "extensions";
            @Deprecated public static final String DEFAULT_SORT_ORDER = "name ASC";
            @Deprecated public static final String PERSON_ID = "person";
        }
    }

    @Deprecated
    public interface GroupsColumns {
        @Deprecated String NAME = "name";
        @Deprecated String NOTES = "notes";
        @Deprecated String SHOULD_SYNC = "should_sync";
        @Deprecated String SYSTEM_ID = "system_id";
    }

    @Deprecated
    public static final class Groups implements BaseColumns, GroupsColumns {
        private Groups() {
        }

        @Deprecated public static final Uri CONTENT_URI = Uri.parse("content://contacts/groups");
        @Deprecated public static final Uri DELETED_CONTENT_URI =
                Uri.parse("content://contacts/deleted_groups");
        @Deprecated public static final String CONTENT_TYPE = "vnd.android.cursor.dir/contactsgroup";
        @Deprecated public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/contactsgroup";
        @Deprecated public static final String DEFAULT_SORT_ORDER = NAME + " ASC";
        @Deprecated public static final String GROUP_ANDROID_STARRED = "Starred in Android";
        @Deprecated public static final String GROUP_MY_CONTACTS = "Contacts";
    }

    @Deprecated
    public interface PhonesColumns {
        @Deprecated String TYPE = "type";
        @Deprecated int TYPE_CUSTOM = 0;
        @Deprecated int TYPE_HOME = 1;
        @Deprecated int TYPE_MOBILE = 2;
        @Deprecated int TYPE_WORK = 3;
        @Deprecated int TYPE_FAX_WORK = 4;
        @Deprecated int TYPE_FAX_HOME = 5;
        @Deprecated int TYPE_PAGER = 6;
        @Deprecated int TYPE_OTHER = 7;

        @Deprecated String LABEL = "label";
        @Deprecated String NUMBER = "number";
        @Deprecated String NUMBER_KEY = "number_key";
        @Deprecated String ISPRIMARY = "isprimary";
    }

    @Deprecated
    public static final class Phones implements BaseColumns, PhonesColumns, PeopleColumns {
        private Phones() {
        }

        @Deprecated public static final Uri CONTENT_URI = Uri.parse("content://contacts/phones");
        @Deprecated public static final Uri CONTENT_FILTER_URL =
                Uri.parse("content://contacts/phones/filter");
        @Deprecated public static final String CONTENT_TYPE = "vnd.android.cursor.dir/phone";
        @Deprecated public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/phone";
        @Deprecated public static final String DEFAULT_SORT_ORDER = "name ASC";
        @Deprecated public static final String PERSON_ID = "person";

        @Deprecated
        public static CharSequence getDisplayLabel(
                Context context, int type, CharSequence label, CharSequence[] labelArray) {
            if (type == TYPE_CUSTOM) {
                return label != null ? label : "";
            }
            if (labelArray != null && type > 0 && type <= labelArray.length) {
                return labelArray[type - 1];
            }
            switch (type) {
                case TYPE_HOME: return "Home";
                case TYPE_MOBILE: return "Mobile";
                case TYPE_WORK: return "Work";
                case TYPE_FAX_WORK: return "Work Fax";
                case TYPE_FAX_HOME: return "Home Fax";
                case TYPE_PAGER: return "Pager";
                case TYPE_OTHER: return "Other";
                default: return label != null ? label : "";
            }
        }

        @Deprecated
        public static CharSequence getDisplayLabel(
                Context context, int type, CharSequence label) {
            return getDisplayLabel(context, type, label, null);
        }
    }

    @Deprecated
    public static final class GroupMembership implements BaseColumns, GroupsColumns {
        private GroupMembership() {
        }

        @Deprecated public static final Uri CONTENT_URI =
                Uri.parse("content://contacts/groupmembership");
        @Deprecated public static final Uri RAW_CONTENT_URI =
                Uri.parse("content://contacts/groupmembershipraw");
        @Deprecated public static final String CONTENT_DIRECTORY = "groupmembership";
        @Deprecated public static final String CONTENT_TYPE =
                "vnd.android.cursor.dir/contactsgroupmembership";
        @Deprecated public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/contactsgroupmembership";
        @Deprecated public static final String DEFAULT_SORT_ORDER = "group_id ASC";
        @Deprecated public static final String GROUP_ID = "group_id";
        @Deprecated public static final String GROUP_SYNC_ID = "group_sync_id";
        @Deprecated public static final String GROUP_SYNC_ACCOUNT = "group_sync_account";
        @Deprecated public static final String GROUP_SYNC_ACCOUNT_TYPE = "group_sync_account_type";
        @Deprecated public static final String PERSON_ID = "person";
    }

    @Deprecated
    public interface ContactMethodsColumns {
        @Deprecated String KIND = "kind";
        @Deprecated String TYPE = "type";
        @Deprecated int TYPE_CUSTOM = 0;
        @Deprecated int TYPE_HOME = 1;
        @Deprecated int TYPE_WORK = 2;
        @Deprecated int TYPE_OTHER = 3;

        @Deprecated String LABEL = "label";
        @Deprecated String DATA = "data";
        @Deprecated String AUX_DATA = "aux_data";
        @Deprecated String ISPRIMARY = "isprimary";
    }

    @Deprecated
    public static final class ContactMethods
            implements BaseColumns, ContactMethodsColumns, PeopleColumns {
        private ContactMethods() {
        }

        @Deprecated public static final String POSTAL_LOCATION_LATITUDE = DATA;
        @Deprecated public static final String POSTAL_LOCATION_LONGITUDE = AUX_DATA;
        @Deprecated public static final int PROTOCOL_AIM = 0;
        @Deprecated public static final int PROTOCOL_MSN = 1;
        @Deprecated public static final int PROTOCOL_YAHOO = 2;
        @Deprecated public static final int PROTOCOL_SKYPE = 3;
        @Deprecated public static final int PROTOCOL_QQ = 4;
        @Deprecated public static final int PROTOCOL_GOOGLE_TALK = 5;
        @Deprecated public static final int PROTOCOL_ICQ = 6;
        @Deprecated public static final int PROTOCOL_JABBER = 7;

        @Deprecated public static final Uri CONTENT_URI =
                Uri.parse("content://contacts/contact_methods");
        @Deprecated public static final Uri CONTENT_EMAIL_URI =
                Uri.parse("content://contacts/contact_methods/email");
        @Deprecated public static final String CONTENT_TYPE =
                "vnd.android.cursor.dir/contact-methods";
        @Deprecated public static final String CONTENT_EMAIL_TYPE =
                "vnd.android.cursor.dir/email";
        @Deprecated public static final String CONTENT_POSTAL_TYPE =
                "vnd.android.cursor.dir/postal-address";
        @Deprecated public static final String CONTENT_EMAIL_ITEM_TYPE =
                "vnd.android.cursor.item/email";
        @Deprecated public static final String CONTENT_POSTAL_ITEM_TYPE =
                "vnd.android.cursor.item/postal-address";
        @Deprecated public static final String CONTENT_IM_ITEM_TYPE =
                "vnd.android.cursor.item/jabber-im";
        @Deprecated public static final String DEFAULT_SORT_ORDER = "name ASC";
        @Deprecated public static final String PERSON_ID = "person";

        @Deprecated
        public static String encodePredefinedImProtocol(int protocol) {
            return "pre:" + protocol;
        }

        @Deprecated
        public static String encodeCustomImProtocol(String protocolString) {
            return "custom:" + protocolString;
        }

        @Deprecated
        public static Object decodeImProtocol(String encodedString) {
            if (encodedString == null) {
                return null;
            }
            if (encodedString.startsWith("pre:")) {
                return Integer.parseInt(encodedString.substring(4));
            }
            if (encodedString.startsWith("custom:")) {
                return encodedString.substring(7);
            }
            throw new IllegalArgumentException(
                    "the value is not a valid encoded protocol, " + encodedString);
        }

        @Deprecated
        public static CharSequence getDisplayLabel(
                Context context, int kind, int type, CharSequence label) {
            if (type == TYPE_CUSTOM) {
                return label != null ? label : "";
            }
            switch (type) {
                case TYPE_HOME: return "Home";
                case TYPE_WORK: return "Work";
                case TYPE_OTHER: return "Other";
                default: return label != null ? label : "";
            }
        }

        @Deprecated
        public void addPostalLocation(
                Context context, long postalId, double latitude, double longitude) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues(2);
            values.put(POSTAL_LOCATION_LATITUDE, latitude);
            values.put(POSTAL_LOCATION_LONGITUDE, longitude);
            Uri loc = resolver.insert(CONTENT_URI, values);
            if (loc != null) {
                long locId = ContentUris.parseId(loc);
                values.clear();
                values.put(AUX_DATA, locId);
                resolver.update(ContentUris.withAppendedId(CONTENT_URI, postalId), values, null, null);
            }
        }
    }

    @Deprecated
    public interface PresenceColumns {
        @Deprecated String PRIORITY = "priority";
        @Deprecated String PRESENCE_STATUS = "mode";
        @Deprecated int OFFLINE = 0;
        @Deprecated int INVISIBLE = 1;
        @Deprecated int AWAY = 2;
        @Deprecated int IDLE = 3;
        @Deprecated int DO_NOT_DISTURB = 4;
        @Deprecated int AVAILABLE = 5;

        @Deprecated String PRESENCE_CUSTOM_STATUS = "status";
        @Deprecated String IM_PROTOCOL = "im_protocol";
        @Deprecated String IM_HANDLE = "im_handle";
        @Deprecated String IM_ACCOUNT = "im_account";
    }

    @Deprecated
    public interface OrganizationColumns {
        @Deprecated String TYPE = "type";
        @Deprecated int TYPE_CUSTOM = 0;
        @Deprecated int TYPE_WORK = 1;
        @Deprecated int TYPE_OTHER = 2;

        @Deprecated String LABEL = "label";
        @Deprecated String COMPANY = "company";
        @Deprecated String TITLE = "title";
        @Deprecated String PERSON_ID = "person";
        @Deprecated String ISPRIMARY = "isprimary";
    }

    @Deprecated
    public static final class Organizations implements BaseColumns, OrganizationColumns {
        private Organizations() {
        }

        @Deprecated public static final Uri CONTENT_URI =
                Uri.parse("content://contacts/organizations");
        @Deprecated public static final String CONTENT_DIRECTORY = "organizations";
        @Deprecated public static final String DEFAULT_SORT_ORDER = "company, title, isprimary ASC";

        @Deprecated
        public static CharSequence getDisplayLabel(
                Context context, int type, CharSequence label) {
            if (type == TYPE_CUSTOM) {
                return label != null ? label : "";
            }
            switch (type) {
                case TYPE_WORK: return "Work";
                case TYPE_OTHER: return "Other";
                default: return label != null ? label : "";
            }
        }
    }

    @Deprecated
    public interface PhotosColumns {
        @Deprecated String LOCAL_VERSION = "local_version";
        @Deprecated String PERSON_ID = "person";
        @Deprecated String DOWNLOAD_REQUIRED = "download_required";
        @Deprecated String EXISTS_ON_SERVER = "exists_on_server";
        @Deprecated String SYNC_ERROR = "sync_error";
        @Deprecated String DATA = "data";
    }

    @Deprecated
    public static final class Photos implements BaseColumns, PhotosColumns {
        private Photos() {
        }

        @Deprecated public static final Uri CONTENT_URI = Uri.parse("content://contacts/photos");
        @Deprecated public static final String CONTENT_DIRECTORY = "photo";
        @Deprecated public static final String DEFAULT_SORT_ORDER = "person ASC";
    }

    @Deprecated
    public interface ExtensionsColumns {
        @Deprecated String NAME = "name";
        @Deprecated String VALUE = "value";
    }

    @Deprecated
    public static final class Extensions implements BaseColumns, ExtensionsColumns {
        private Extensions() {
        }

        @Deprecated public static final Uri CONTENT_URI =
                Uri.parse("content://contacts/extensions");
        @Deprecated public static final String CONTENT_TYPE =
                "vnd.android.cursor.dir/contact_extensions";
        @Deprecated public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/contact_extensions";
        @Deprecated public static final String DEFAULT_SORT_ORDER = "person, name ASC";
        @Deprecated public static final String PERSON_ID = "person";
    }

    @Deprecated
    public static final class Intents {
        @Deprecated
        public Intents() {
        }

        @Deprecated public static final String SEARCH_SUGGESTION_CLICKED =
                "android.provider.Contacts.SEARCH_SUGGESTION_CLICKED";
        @Deprecated public static final String SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED =
                "android.provider.Contacts.SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED";
        @Deprecated public static final String SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED =
                "android.provider.Contacts.SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED";
        @Deprecated public static final String ATTACH_IMAGE =
                "com.android.contacts.action.ATTACH_IMAGE";
        @Deprecated public static final String SHOW_OR_CREATE_CONTACT =
                "com.android.contacts.action.SHOW_OR_CREATE_CONTACT";
        @Deprecated public static final String EXTRA_FORCE_CREATE =
                "com.android.contacts.action.FORCE_CREATE";
        @Deprecated public static final String EXTRA_CREATE_DESCRIPTION =
                "com.android.contacts.action.CREATE_DESCRIPTION";

        @Deprecated
        public static final class UI {
            @Deprecated
            public UI() {
            }

            @Deprecated public static final String LIST_DEFAULT =
                    "com.android.contacts.action.LIST_DEFAULT";
            @Deprecated public static final String LIST_GROUP_ACTION =
                    "com.android.contacts.action.LIST_GROUP";
            @Deprecated public static final String GROUP_NAME_EXTRA_KEY =
                    "com.android.contacts.extra.GROUP";
            @Deprecated public static final String LIST_ALL_CONTACTS_ACTION =
                    "com.android.contacts.action.LIST_ALL_CONTACTS";
            @Deprecated public static final String LIST_CONTACTS_WITH_PHONES_ACTION =
                    "com.android.contacts.action.LIST_CONTACTS_WITH_PHONES";
            @Deprecated public static final String LIST_STARRED_ACTION =
                    "com.android.contacts.action.LIST_STARRED";
            @Deprecated public static final String LIST_FREQUENT_ACTION =
                    "com.android.contacts.action.LIST_FREQUENT";
            @Deprecated public static final String LIST_STREQUENT_ACTION =
                    "com.android.contacts.action.LIST_STREQUENT";
            @Deprecated public static final String TITLE_EXTRA_KEY =
                    "com.android.contacts.extra.TITLE_EXTRA";
            @Deprecated public static final String FILTER_CONTACTS_ACTION =
                    "com.android.contacts.action.FILTER_CONTACTS";
            @Deprecated public static final String FILTER_TEXT_EXTRA_KEY =
                    "com.android.contacts.extra.FILTER_TEXT";
        }

        @Deprecated
        public static final class Insert {
            @Deprecated
            public Insert() {
            }

            @Deprecated public static final String ACTION = "android.intent.action.INSERT";
            @Deprecated public static final String FULL_MODE = "full_mode";
            @Deprecated public static final String NAME = "name";
            @Deprecated public static final String PHONETIC_NAME = "phonetic_name";
            @Deprecated public static final String COMPANY = "company";
            @Deprecated public static final String JOB_TITLE = "job_title";
            @Deprecated public static final String NOTES = "notes";
            @Deprecated public static final String PHONE = "phone";
            @Deprecated public static final String PHONE_TYPE = "phone_type";
            @Deprecated public static final String PHONE_ISPRIMARY = "phone_isprimary";
            @Deprecated public static final String SECONDARY_PHONE = "secondary_phone";
            @Deprecated public static final String SECONDARY_PHONE_TYPE = "secondary_phone_type";
            @Deprecated public static final String TERTIARY_PHONE = "tertiary_phone";
            @Deprecated public static final String TERTIARY_PHONE_TYPE = "tertiary_phone_type";
            @Deprecated public static final String EMAIL = "email";
            @Deprecated public static final String EMAIL_TYPE = "email_type";
            @Deprecated public static final String EMAIL_ISPRIMARY = "email_isprimary";
            @Deprecated public static final String SECONDARY_EMAIL = "secondary_email";
            @Deprecated public static final String SECONDARY_EMAIL_TYPE = "secondary_email_type";
            @Deprecated public static final String TERTIARY_EMAIL = "tertiary_email";
            @Deprecated public static final String TERTIARY_EMAIL_TYPE = "tertiary_email_type";
            @Deprecated public static final String POSTAL = "postal";
            @Deprecated public static final String POSTAL_TYPE = "postal_type";
            @Deprecated public static final String POSTAL_ISPRIMARY = "postal_isprimary";
            @Deprecated public static final String IM_HANDLE = "im_handle";
            @Deprecated public static final String IM_PROTOCOL = "im_protocol";
            @Deprecated public static final String IM_ISPRIMARY = "im_isprimary";
        }
    }
}
