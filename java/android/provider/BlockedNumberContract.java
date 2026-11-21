// SPDX-License-Identifier: GPL-3.0-only

package android.provider;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

public class BlockedNumberContract {
    public static final String AUTHORITY = "com.android.blockednumber";
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    private static final String METHOD_IS_BLOCKED = "is_blocked";
    private static final String METHOD_UNBLOCK = "unblock";
    private static final String METHOD_CAN_CURRENT_USER_BLOCK_NUMBERS =
            "can_current_user_block_numbers";
    private static final String RES_NUMBER_IS_BLOCKED = "blocked";
    private static final String RES_NUM_ROWS_DELETED = "num_deleted";
    private static final String RES_CAN_BLOCK_NUMBERS = "can_block";

    private BlockedNumberContract() {
    }

    public static class BlockedNumbers {
        private BlockedNumbers() {
        }

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "blocked");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/blocked_number";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/blocked_number";
        public static final String COLUMN_ID = "_id";
        public static final String COLUMN_ORIGINAL_NUMBER = "original_number";
        public static final String COLUMN_E164_NUMBER = "e164_number";
    }

    public static boolean isBlocked(Context context, String phoneNumber) {
        if (context == null) {
            throw new NullPointerException("context must not be null");
        }
        try {
            Bundle res = context.getContentResolver().call(
                    AUTHORITY_URI, METHOD_IS_BLOCKED, phoneNumber, null);
            return res != null && res.getBoolean(RES_NUMBER_IS_BLOCKED, false);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static int unblock(Context context, String phoneNumber) {
        if (context == null) {
            throw new NullPointerException("context must not be null");
        }
        try {
            Bundle res = context.getContentResolver().call(
                    AUTHORITY_URI, METHOD_UNBLOCK, phoneNumber, null);
            return res == null ? 0 : res.getInt(RES_NUM_ROWS_DELETED, 0);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public static boolean canCurrentUserBlockNumbers(Context context) {
        if (context == null) {
            throw new NullPointerException("context must not be null");
        }
        try {
            Bundle res = context.getContentResolver().call(
                    AUTHORITY_URI, METHOD_CAN_CURRENT_USER_BLOCK_NUMBERS, null, null);
            return res != null && res.getBoolean(RES_CAN_BLOCK_NUMBERS, false);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
