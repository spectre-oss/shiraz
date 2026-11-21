// SPDX-License-Identifier: GPL-3.0-only
package android.drm;

/** Constants and column definitions used across the Android DRM framework. */
@Deprecated
public class DrmStore {
    public interface ConstraintsColumns {
        public static final String MAX_REPEAT_COUNT = "max_repeat_count";
        public static final String REMAINING_REPEAT_COUNT = "remaining_repeat_count";
        public static final String LICENSE_START_TIME = "license_start_time";
        public static final String LICENSE_EXPIRY_TIME = "license_expiry_time";
        public static final String LICENSE_AVAILABLE_TIME = "license_available_time";
        public static final String EXTENDED_METADATA = "extended_metadata";
    }

    public static class DrmObjectType {
        public static final int UNKNOWN = 0x00;
        public static final int CONTENT = 0x01;
        public static final int RIGHTS_OBJECT = 0x02;
        public static final int TRIGGER_OBJECT = 0x03;

        @Deprecated
        public DrmObjectType() {
        }
    }

    public static class Playback {
        public static final int START = 0x00;
        public static final int STOP = 0x01;
        public static final int PAUSE = 0x02;
        public static final int RESUME = 0x03;

        static boolean isValid(int playbackStatus) {
            switch (playbackStatus) {
                case START:
                case STOP:
                case PAUSE:
                case RESUME:
                    return true;
                default:
                    return false;
            }
        }

        @Deprecated
        public Playback() {
        }
    }

    public static class Action {
        public static final int DEFAULT = 0x00;
        public static final int PLAY = 0x01;
        public static final int RINGTONE = 0x02;
        public static final int TRANSFER = 0x03;
        public static final int OUTPUT = 0x04;
        public static final int PREVIEW = 0x05;
        public static final int EXECUTE = 0x06;
        public static final int DISPLAY = 0x07;

        static boolean isValid(int action) {
            switch (action) {
                case DEFAULT:
                case PLAY:
                case RINGTONE:
                case TRANSFER:
                case OUTPUT:
                case PREVIEW:
                case EXECUTE:
                case DISPLAY:
                    return true;
                default:
                    return false;
            }
        }

        @Deprecated
        public Action() {
        }
    }

    public static class RightsStatus {
        public static final int RIGHTS_VALID = 0x00;
        public static final int RIGHTS_INVALID = 0x01;
        public static final int RIGHTS_EXPIRED = 0x02;
        public static final int RIGHTS_NOT_ACQUIRED = 0x03;

        @Deprecated
        public RightsStatus() {
        }
    }

    @Deprecated
    public DrmStore() {
    }
}
