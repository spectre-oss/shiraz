// SPDX-License-Identifier: GPL-3.0-only
package android.provider;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.content.EntityIterator;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import java.util.NoSuchElementException;

/** The contract between the calendar provider and applications. */
public final class CalendarContract {
    private CalendarContract() {
    }

    public static final String ACTION_EVENT_REMINDER = "android.intent.action.EVENT_REMINDER";
    public static final String ACTION_HANDLE_CUSTOM_EVENT =
            "android.provider.calendar.action.HANDLE_CUSTOM_EVENT";
    public static final String ACTION_VIEW_MANAGED_PROFILE_CALENDAR_EVENT =
            "android.provider.calendar.action.VIEW_MANAGED_PROFILE_CALENDAR_EVENT";

    public static final String EXTRA_CUSTOM_APP_URI = "customAppUri";
    public static final String EXTRA_EVENT_BEGIN_TIME = "beginTime";
    public static final String EXTRA_EVENT_END_TIME = "endTime";
    public static final String EXTRA_EVENT_ALL_DAY = "allDay";
    public static final String EXTRA_EVENT_ID = "id";

    public static final String AUTHORITY = "com.android.calendar";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    public static final String CALLER_IS_SYNCADAPTER = "caller_is_syncadapter";
    public static final String ACCOUNT_TYPE_LOCAL = "LOCAL";

    public static boolean startViewCalendarEventInManagedProfile(
            Context context,
            long eventId,
            long startMs,
            long endMs,
            boolean allDay,
            int flags) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        return false;
    }

    static ContentValues cursorRowToContentValues(Cursor cursor) {
        ContentValues values = new ContentValues();
        String[] columns = cursor.getColumnNames();
        for (int i = 0; i < columns.length; i++) {
            String name = columns[i];
            int type = cursor.getType(i);
            if (type == Cursor.FIELD_TYPE_NULL) {
                values.putNull(name);
            } else if (type == Cursor.FIELD_TYPE_INTEGER) {
                values.put(name, cursor.getLong(i));
            } else if (type == Cursor.FIELD_TYPE_FLOAT) {
                values.put(name, cursor.getDouble(i));
            } else if (type == Cursor.FIELD_TYPE_BLOB) {
                values.put(name, cursor.getBlob(i));
            } else {
                values.put(name, cursor.getString(i));
            }
        }
        return values;
    }

    private abstract static class CursorEntityIterator implements EntityIterator {
        private final Cursor mCursor;
        private boolean mIsClosed;

        CursorEntityIterator(Cursor cursor) {
            if (cursor == null) {
                throw new NullPointerException("cursor");
            }
            mCursor = cursor;
            mCursor.moveToFirst();
        }

        public abstract Entity getEntityAndIncrementCursor(Cursor cursor) throws RemoteException;

        @Override
        public final boolean hasNext() {
            if (mIsClosed) {
                throw new IllegalStateException("calling hasNext() when the iterator is closed");
            }
            return !mCursor.isAfterLast();
        }

        @Override
        public Entity next() {
            if (mIsClosed) {
                throw new IllegalStateException("calling next() when the iterator is closed");
            }
            if (!hasNext()) {
                throw new NoSuchElementException("you may only call next() if hasNext() is true");
            }
            try {
                return getEntityAndIncrementCursor(mCursor);
            } catch (RemoteException e) {
                throw new RuntimeException("caught a remote exception, this process will die soon", e);
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove not supported by EntityIterators");
        }

        @Override
        public final void reset() {
            if (mIsClosed) {
                throw new IllegalStateException("calling reset() when the iterator is closed");
            }
            mCursor.moveToFirst();
        }

        @Override
        public final void close() {
            if (mIsClosed) {
                return;
            }
            mIsClosed = true;
            mCursor.close();
        }
    }

    protected interface CalendarSyncColumns {
        String CAL_SYNC1 = "cal_sync1";
        String CAL_SYNC2 = "cal_sync2";
        String CAL_SYNC3 = "cal_sync3";
        String CAL_SYNC4 = "cal_sync4";
        String CAL_SYNC5 = "cal_sync5";
        String CAL_SYNC6 = "cal_sync6";
        String CAL_SYNC7 = "cal_sync7";
        String CAL_SYNC8 = "cal_sync8";
        String CAL_SYNC9 = "cal_sync9";
        String CAL_SYNC10 = "cal_sync10";
    }

    protected interface SyncColumns extends CalendarSyncColumns {
        String ACCOUNT_NAME = "account_name";
        String ACCOUNT_TYPE = "account_type";
        String _SYNC_ID = "_sync_id";
        String DIRTY = "dirty";
        String MUTATORS = "mutators";
        String DELETED = "deleted";
        String CAN_PARTIALLY_UPDATE = "canPartiallyUpdate";
    }

    protected interface CalendarColumns {
        String CALENDAR_COLOR = "calendar_color";
        String CALENDAR_COLOR_KEY = "calendar_color_index";
        String CALENDAR_DISPLAY_NAME = "calendar_displayName";
        String CALENDAR_ACCESS_LEVEL = "calendar_access_level";
        int CAL_ACCESS_NONE = 0;
        int CAL_ACCESS_FREEBUSY = 100;
        int CAL_ACCESS_READ = 200;
        int CAL_ACCESS_RESPOND = 300;
        int CAL_ACCESS_OVERRIDE = 400;
        int CAL_ACCESS_CONTRIBUTOR = 500;
        int CAL_ACCESS_EDITOR = 600;
        int CAL_ACCESS_OWNER = 700;
        int CAL_ACCESS_ROOT = 800;

        String VISIBLE = "visible";
        String CALENDAR_TIME_ZONE = "calendar_timezone";
        String SYNC_EVENTS = "sync_events";
        String OWNER_ACCOUNT = "ownerAccount";
        String CAN_ORGANIZER_RESPOND = "canOrganizerRespond";
        String CAN_MODIFY_TIME_ZONE = "canModifyTimeZone";
        String MAX_REMINDERS = "maxReminders";
        String ALLOWED_REMINDERS = "allowedReminders";
        String ALLOWED_AVAILABILITY = "allowedAvailability";
        String ALLOWED_ATTENDEE_TYPES = "allowedAttendeeTypes";
        String IS_PRIMARY = "isPrimary";
    }

    public static final class CalendarEntity
            implements BaseColumns, SyncColumns, CalendarColumns {
        private CalendarEntity() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/calendar_entities");

        public static EntityIterator newEntityIterator(Cursor cursor) {
            return new CursorEntityIterator(cursor) {
                @Override
                public Entity getEntityAndIncrementCursor(Cursor c) {
                    Entity entity = new Entity(cursorRowToContentValues(c));
                    c.moveToNext();
                    return entity;
                }
            };
        }
    }

    public static final class Calendars
            implements BaseColumns, SyncColumns, CalendarColumns {
        private Calendars() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/calendars");
        public static final Uri ENTERPRISE_CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/enterprise/calendars");
        public static final String DEFAULT_SORT_ORDER = CALENDAR_DISPLAY_NAME;
        public static final String NAME = "name";
        public static final String CALENDAR_LOCATION = "calendar_location";
    }

    protected interface AttendeesColumns {
        String EVENT_ID = "event_id";
        String ATTENDEE_NAME = "attendeeName";
        String ATTENDEE_EMAIL = "attendeeEmail";
        String ATTENDEE_RELATIONSHIP = "attendeeRelationship";
        int RELATIONSHIP_NONE = 0;
        int RELATIONSHIP_ATTENDEE = 1;
        int RELATIONSHIP_ORGANIZER = 2;
        int RELATIONSHIP_PERFORMER = 3;
        int RELATIONSHIP_SPEAKER = 4;

        String ATTENDEE_TYPE = "attendeeType";
        int TYPE_NONE = 0;
        int TYPE_REQUIRED = 1;
        int TYPE_OPTIONAL = 2;
        int TYPE_RESOURCE = 3;

        String ATTENDEE_STATUS = "attendeeStatus";
        int ATTENDEE_STATUS_NONE = 0;
        int ATTENDEE_STATUS_ACCEPTED = 1;
        int ATTENDEE_STATUS_DECLINED = 2;
        int ATTENDEE_STATUS_INVITED = 3;
        int ATTENDEE_STATUS_TENTATIVE = 4;

        String ATTENDEE_IDENTITY = "attendeeIdentity";
        String ATTENDEE_ID_NAMESPACE = "attendeeIdNamespace";
    }

    public static final class Attendees
            implements BaseColumns, AttendeesColumns, EventsColumns {
        private Attendees() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/attendees");

        public static Cursor query(
                ContentResolver cr, long eventId, String[] projection) {
            return cr.query(
                    CONTENT_URI,
                    projection,
                    AttendeesColumns.EVENT_ID + "=?",
                    new String[] { Long.toString(eventId) },
                    null);
        }
    }

    protected interface EventsColumns {
        String CALENDAR_ID = "calendar_id";
        String TITLE = "title";
        String DESCRIPTION = "description";
        String EVENT_LOCATION = "eventLocation";
        String EVENT_COLOR = "eventColor";
        String EVENT_COLOR_KEY = "eventColor_index";
        String DISPLAY_COLOR = "displayColor";
        String STATUS = "eventStatus";
        int STATUS_TENTATIVE = 0;
        int STATUS_CONFIRMED = 1;
        int STATUS_CANCELED = 2;

        String SELF_ATTENDEE_STATUS = "selfAttendeeStatus";
        String SYNC_DATA1 = "sync_data1";
        String SYNC_DATA2 = "sync_data2";
        String SYNC_DATA3 = "sync_data3";
        String SYNC_DATA4 = "sync_data4";
        String SYNC_DATA5 = "sync_data5";
        String SYNC_DATA6 = "sync_data6";
        String SYNC_DATA7 = "sync_data7";
        String SYNC_DATA8 = "sync_data8";
        String SYNC_DATA9 = "sync_data9";
        String SYNC_DATA10 = "sync_data10";
        String LAST_SYNCED = "lastSynced";
        String DTSTART = "dtstart";
        String DTEND = "dtend";
        String DURATION = "duration";
        String EVENT_TIMEZONE = "eventTimezone";
        String EVENT_END_TIMEZONE = "eventEndTimezone";
        String ALL_DAY = "allDay";
        String ACCESS_LEVEL = "accessLevel";
        int ACCESS_DEFAULT = 0;
        int ACCESS_CONFIDENTIAL = 1;
        int ACCESS_PRIVATE = 2;
        int ACCESS_PUBLIC = 3;

        String AVAILABILITY = "availability";
        int AVAILABILITY_BUSY = 0;
        int AVAILABILITY_FREE = 1;
        int AVAILABILITY_TENTATIVE = 2;

        String HAS_ALARM = "hasAlarm";
        String HAS_EXTENDED_PROPERTIES = "hasExtendedProperties";
        String RRULE = "rrule";
        String RDATE = "rdate";
        String EXRULE = "exrule";
        String EXDATE = "exdate";
        String ORIGINAL_ID = "original_id";
        String ORIGINAL_SYNC_ID = "original_sync_id";
        String ORIGINAL_INSTANCE_TIME = "originalInstanceTime";
        String ORIGINAL_ALL_DAY = "originalAllDay";
        String LAST_DATE = "lastDate";
        String HAS_ATTENDEE_DATA = "hasAttendeeData";
        String GUESTS_CAN_MODIFY = "guestsCanModify";
        String GUESTS_CAN_INVITE_OTHERS = "guestsCanInviteOthers";
        String GUESTS_CAN_SEE_GUESTS = "guestsCanSeeGuests";
        String ORGANIZER = "organizer";
        String IS_ORGANIZER = "isOrganizer";
        String CAN_INVITE_OTHERS = "canInviteOthers";
        String CUSTOM_APP_PACKAGE = "customAppPackage";
        String CUSTOM_APP_URI = "customAppUri";
        String UID_2445 = "uid2445";
    }

    public static final class EventsEntity
            implements BaseColumns, SyncColumns, EventsColumns {
        private EventsEntity() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/event_entities");

        public static EntityIterator newEntityIterator(Cursor cursor, ContentResolver resolver) {
            return new EntityIteratorImpl(cursor, resolver, null);
        }

        public static EntityIterator newEntityIterator(
                Cursor cursor, ContentProviderClient provider) {
            return new EntityIteratorImpl(cursor, null, provider);
        }

        private static final class EntityIteratorImpl extends CursorEntityIterator {
            private final ContentResolver mResolver;
            private final ContentProviderClient mProvider;

            EntityIteratorImpl(
                    Cursor cursor, ContentResolver resolver, ContentProviderClient provider) {
                super(cursor);
                mResolver = resolver;
                mProvider = provider;
            }

            private Cursor querySub(Uri uri, long eventId) throws RemoteException {
                String selection = "event_id=?";
                String[] args = new String[] { Long.toString(eventId) };
                if (mResolver != null) {
                    return mResolver.query(uri, null, selection, args, null);
                }
                if (mProvider != null) {
                    return mProvider.query(uri, null, selection, args, null);
                }
                return null;
            }

            private void appendSubValues(Entity entity, Uri uri, long eventId)
                    throws RemoteException {
                Cursor sub = querySub(uri, eventId);
                if (sub != null) {
                    try {
                        while (sub.moveToNext()) {
                            entity.addSubValue(uri, cursorRowToContentValues(sub));
                        }
                    } finally {
                        sub.close();
                    }
                }
            }

            @Override
            public Entity getEntityAndIncrementCursor(Cursor cursor) throws RemoteException {
                ContentValues cv = cursorRowToContentValues(cursor);
                Entity entity = new Entity(cv);
                int idIdx = cursor.getColumnIndex(_ID);
                if (idIdx >= 0 && !cursor.isNull(idIdx)) {
                    long eventId = cursor.getLong(idIdx);
                    appendSubValues(entity, Reminders.CONTENT_URI, eventId);
                    appendSubValues(entity, Attendees.CONTENT_URI, eventId);
                    appendSubValues(entity, ExtendedProperties.CONTENT_URI, eventId);
                }
                cursor.moveToNext();
                return entity;
            }
        }
    }

    public static final class Events
            implements BaseColumns, SyncColumns, EventsColumns, CalendarColumns {
        private Events() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/events");
        public static final Uri ENTERPRISE_CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/enterprise/events");
        public static final Uri CONTENT_EXCEPTION_URI =
                Uri.parse("content://" + AUTHORITY + "/exception");
    }

    public static final class Instances
            implements BaseColumns, EventsColumns, CalendarColumns {
        private Instances() {
        }

        public static final Uri CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/instances/when");
        public static final Uri CONTENT_BY_DAY_URI =
                Uri.parse("content://" + AUTHORITY + "/instances/whenbyday");
        public static final Uri CONTENT_SEARCH_URI =
                Uri.parse("content://" + AUTHORITY + "/instances/search");
        public static final Uri CONTENT_SEARCH_BY_DAY_URI =
                Uri.parse("content://" + AUTHORITY + "/instances/searchbyday");
        public static final Uri ENTERPRISE_CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/enterprise/instances/when");
        public static final Uri ENTERPRISE_CONTENT_BY_DAY_URI =
                Uri.parse("content://" + AUTHORITY + "/enterprise/instances/whenbyday");
        public static final Uri ENTERPRISE_CONTENT_SEARCH_URI =
                Uri.parse("content://" + AUTHORITY + "/enterprise/instances/search");
        public static final Uri ENTERPRISE_CONTENT_SEARCH_BY_DAY_URI =
                Uri.parse("content://" + AUTHORITY + "/enterprise/instances/searchbyday");

        public static final String BEGIN = "begin";
        public static final String END = "end";
        public static final String EVENT_ID = "event_id";
        public static final String START_DAY = "startDay";
        public static final String END_DAY = "endDay";
        public static final String START_MINUTE = "startMinute";
        public static final String END_MINUTE = "endMinute";

        private static final String DEFAULT_SORT_ORDER = "begin ASC";

        public static Cursor query(
                ContentResolver cr, String[] projection, long begin, long end) {
            Uri uri = ContentUris.withAppendedId(
                    ContentUris.withAppendedId(CONTENT_URI, begin), end);
            return cr.query(uri, projection, VISIBLE + "=1", null, DEFAULT_SORT_ORDER);
        }

        public static Cursor query(
                ContentResolver cr,
                String[] projection,
                long begin,
                long end,
                String searchQuery) {
            Uri.Builder builder = CONTENT_SEARCH_URI.buildUpon();
            builder.appendPath(Long.toString(begin));
            builder.appendPath(Long.toString(end));
            builder.appendPath(searchQuery);
            return cr.query(builder.build(), projection, VISIBLE + "=1", null, DEFAULT_SORT_ORDER);
        }
    }

    protected interface CalendarCacheColumns {
        String KEY = "key";
        String VALUE = "value";
    }

    public static final class CalendarCache implements CalendarCacheColumns {
        private CalendarCache() {
        }

        public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/properties");
        public static final String KEY_TIMEZONE_TYPE = "timezoneType";
        public static final String KEY_TIMEZONE_INSTANCES = "timezoneInstances";
        public static final String KEY_TIMEZONE_INSTANCES_PREVIOUS = "timezoneInstancesPrevious";
        public static final String TIMEZONE_TYPE_AUTO = "auto";
        public static final String TIMEZONE_TYPE_HOME = "home";
    }

    protected interface EventDaysColumns {
        String STARTDAY = "startDay";
        String ENDDAY = "endDay";
    }

    public static final class EventDays implements EventDaysColumns {
        private EventDays() {
        }

        public static final Uri CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/instances/groupbyday");

        public static Cursor query(
                ContentResolver cr, int startDay, int numDays, String[] projection) {
            if (numDays < 1) {
                return null;
            }
            int endDay = startDay + numDays - 1;
            Uri.Builder builder = CONTENT_URI.buildUpon();
            builder.appendPath(Integer.toString(startDay));
            builder.appendPath(Integer.toString(endDay));
            return cr.query(
                    builder.build(), projection, "selected=1", null, STARTDAY);
        }
    }

    protected interface RemindersColumns {
        String EVENT_ID = "event_id";
        String MINUTES = "minutes";
        int MINUTES_DEFAULT = -1;

        String METHOD = "method";
        int METHOD_DEFAULT = 0;
        int METHOD_ALERT = 1;
        int METHOD_EMAIL = 2;
        int METHOD_SMS = 3;
        int METHOD_ALARM = 4;
    }

    public static final class Reminders
            implements BaseColumns, RemindersColumns, EventsColumns {
        private Reminders() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/reminders");

        public static Cursor query(
                ContentResolver cr, long eventId, String[] projection) {
            return cr.query(
                    CONTENT_URI,
                    projection,
                    RemindersColumns.EVENT_ID + "=?",
                    new String[] { Long.toString(eventId) },
                    null);
        }
    }

    protected interface CalendarAlertsColumns {
        String EVENT_ID = "event_id";
        String BEGIN = "begin";
        String END = "end";
        String ALARM_TIME = "alarmTime";
        String CREATION_TIME = "creationTime";
        String RECEIVED_TIME = "receivedTime";
        String NOTIFY_TIME = "notifyTime";
        String STATE = "state";
        int STATE_SCHEDULED = 0;
        int STATE_FIRED = 1;
        int STATE_DISMISSED = 2;

        String MINUTES = "minutes";
        String DEFAULT_SORT_ORDER = "begin ASC,title ASC";
    }

    public static final class CalendarAlerts
            implements BaseColumns, CalendarAlertsColumns, EventsColumns, CalendarColumns {
        private CalendarAlerts() {
        }

        public static final Uri CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/calendar_alerts");
        public static final Uri CONTENT_URI_BY_INSTANCE =
                Uri.parse("content://" + AUTHORITY + "/calendar_alerts/by_instance");
    }

    protected interface ColorsColumns extends SyncStateContract.Columns {
        String COLOR_TYPE = "color_type";
        int TYPE_CALENDAR = 0;
        int TYPE_EVENT = 1;

        String COLOR_KEY = "color_index";
        String COLOR = "color";
    }

    public static final class Colors implements ColorsColumns {
        private Colors() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/colors");
    }

    protected interface ExtendedPropertiesColumns {
        String EVENT_ID = "event_id";
        String NAME = "name";
        String VALUE = "value";
    }

    public static final class ExtendedProperties
            implements BaseColumns, ExtendedPropertiesColumns, EventsColumns {
        private ExtendedProperties() {
        }

        public static final Uri CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/extendedproperties");
    }

    public static final class SyncState implements SyncStateContract.Columns {
        private SyncState() {
        }

        public static final Uri CONTENT_URI =
                Uri.withAppendedPath(CalendarContract.CONTENT_URI, SyncStateContract.Constants.CONTENT_DIRECTORY);
    }
}
