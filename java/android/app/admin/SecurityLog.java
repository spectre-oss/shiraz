// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public class SecurityLog {
    public static final int LEVEL_INFO = 1;
    public static final int LEVEL_WARNING = 2;
    public static final int LEVEL_ERROR = 3;

    public static final int TAG_ADB_SHELL_INTERACTIVE = 210001;
    public static final int TAG_ADB_SHELL_CMD = 210002;
    public static final int TAG_SYNC_RECV_FILE = 210003;
    public static final int TAG_SYNC_SEND_FILE = 210004;
    public static final int TAG_APP_PROCESS_START = 210005;
    public static final int TAG_KEYGUARD_DISMISSED = 210006;
    public static final int TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT = 210007;
    public static final int TAG_KEYGUARD_SECURED = 210008;
    public static final int TAG_OS_STARTUP = 210009;
    public static final int TAG_OS_SHUTDOWN = 210010;
    public static final int TAG_LOGGING_STARTED = 210011;
    public static final int TAG_LOGGING_STOPPED = 210012;
    public static final int TAG_MEDIA_MOUNT = 210013;
    public static final int TAG_MEDIA_UNMOUNT = 210014;
    public static final int TAG_LOG_BUFFER_SIZE_CRITICAL = 210015;
    public static final int TAG_PASSWORD_EXPIRATION_SET = 210016;
    public static final int TAG_PASSWORD_COMPLEXITY_SET = 210017;
    public static final int TAG_PASSWORD_HISTORY_LENGTH_SET = 210018;
    public static final int TAG_MAX_SCREEN_LOCK_TIMEOUT_SET = 210019;
    public static final int TAG_MAX_PASSWORD_ATTEMPTS_SET = 210020;
    public static final int TAG_KEYGUARD_DISABLED_FEATURES_SET = 210021;
    public static final int TAG_REMOTE_LOCK = 210022;
    public static final int TAG_WIPE_FAILURE = 210023;
    public static final int TAG_KEY_GENERATED = 210024;
    public static final int TAG_KEY_IMPORT = 210025;
    public static final int TAG_KEY_DESTRUCTION = 210026;
    public static final int TAG_USER_RESTRICTION_ADDED = 210027;
    public static final int TAG_USER_RESTRICTION_REMOVED = 210028;
    public static final int TAG_CERT_AUTHORITY_INSTALLED = 210029;
    public static final int TAG_CERT_AUTHORITY_REMOVED = 210030;
    public static final int TAG_CRYPTO_SELF_TEST_COMPLETED = 210031;
    public static final int TAG_KEY_INTEGRITY_VIOLATION = 210032;
    public static final int TAG_CERT_VALIDATION_FAILURE = 210033;
    public static final int TAG_CAMERA_POLICY_SET = 210034;
    public static final int TAG_PASSWORD_COMPLEXITY_REQUIRED = 210035;
    public static final int TAG_PASSWORD_CHANGED = 210036;
    public static final int TAG_WIFI_CONNECTION = 210037;
    public static final int TAG_WIFI_DISCONNECTION = 210038;
    public static final int TAG_BLUETOOTH_CONNECTION = 210039;
    public static final int TAG_BLUETOOTH_DISCONNECTION = 210040;
    public static final int TAG_PACKAGE_INSTALLED = 210041;
    public static final int TAG_PACKAGE_UPDATED = 210042;
    public static final int TAG_PACKAGE_UNINSTALLED = 210043;
    public static final int TAG_BACKUP_SERVICE_TOGGLED = 210044;

    public SecurityLog() {
    }

    public static final class SecurityEvent implements Parcelable {
        private final long mId;
        private final long mTimeNanos;
        private final int mTag;
        private final String mData;
        private final int mLevel;

        public SecurityEvent(long id, long timeNanos, int tag, String data, int level) {
            mId = id;
            mTimeNanos = timeNanos;
            mTag = tag;
            mData = data;
            mLevel = level;
        }

        SecurityEvent(Parcel source) {
            mId = source.readLong();
            mTimeNanos = source.readLong();
            mTag = source.readInt();
            mData = source.readString();
            mLevel = source.readInt();
        }

        public long getTimeNanos() {
            return mTimeNanos;
        }

        public int getTag() {
            return mTag;
        }

        public Object getData() {
            return mData;
        }

        public long getId() {
            return mId;
        }

        public int getLogLevel() {
            return mLevel;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(mId);
            dest.writeLong(mTimeNanos);
            dest.writeInt(mTag);
            dest.writeString(mData);
            dest.writeInt(mLevel);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SecurityEvent)) return false;
            SecurityEvent other = (SecurityEvent) o;
            return mId == other.mId
                    && mTimeNanos == other.mTimeNanos
                    && mTag == other.mTag
                    && mLevel == other.mLevel
                    && Objects.equals(mData, other.mData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Long.valueOf(mId),
                    Long.valueOf(mTimeNanos),
                    Integer.valueOf(mTag),
                    mData,
                    Integer.valueOf(mLevel));
        }

        public static final Parcelable.Creator<SecurityEvent> CREATOR =
                new Parcelable.Creator<SecurityEvent>() {
                    @Override
                    public SecurityEvent createFromParcel(Parcel source) {
                        return new SecurityEvent(source);
                    }

                    @Override
                    public SecurityEvent[] newArray(int size) {
                        return new SecurityEvent[size];
                    }
                };
    }
}
