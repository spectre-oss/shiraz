// SPDX-License-Identifier: GPL-3.0-only

package android.provider;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import java.nio.charset.StandardCharsets;

public final class SimPhonebookContract {
    public static final String AUTHORITY = "com.android.simphonebook";
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    private SimPhonebookContract() {
    }

    private static String efPathSegment(int efType) {
        switch (efType) {
            case ElementaryFiles.EF_ADN:
                return "adn";
            case ElementaryFiles.EF_FDN:
                return "fdn";
            case ElementaryFiles.EF_SDN:
                return "sdn";
            default:
                throw new IllegalArgumentException("Unsupported EfType " + efType);
        }
    }

    public static final class ElementaryFiles {
        public static final Uri CONTENT_URI =
                AUTHORITY_URI.buildUpon().appendPath("elementary_files").build();
        public static final String CONTENT_TYPE =
                "vnd.android.cursor.dir/sim-elementary-file";
        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/sim-elementary-file";

        public static final String SLOT_INDEX = "slot_index";
        public static final String SUBSCRIPTION_ID = "subscription_id";
        public static final String EF_TYPE = "ef_type";
        public static final String MAX_RECORDS = "max_records";
        public static final String RECORD_COUNT = "record_count";
        public static final String NAME_MAX_LENGTH = "name_max_length";
        public static final String PHONE_NUMBER_MAX_LENGTH = "phone_number_max_length";

        public static final int EF_UNKNOWN = 0;
        public static final int EF_ADN = 1;
        public static final int EF_FDN = 2;
        public static final int EF_SDN = 3;

        private ElementaryFiles() {
        }

        public static Uri getItemUri(int subscriptionId, int efType) {
            return CONTENT_URI.buildUpon()
                    .appendPath("subid")
                    .appendPath(String.valueOf(subscriptionId))
                    .appendPath(efPathSegment(efType))
                    .build();
        }
    }

    public static final class SimRecords {
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/sim-contact_v2";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/sim-contact_v2";

        public static final String SUBSCRIPTION_ID = "subscription_id";
        public static final String ELEMENTARY_FILE_TYPE = "elementary_file_type";
        public static final String RECORD_NUMBER = "record_number";
        public static final String NAME = "name";
        public static final String PHONE_NUMBER = "phone_number";

        public static final int ERROR_NAME_UNSUPPORTED = -1;

        private SimRecords() {
        }

        public static Uri getContentUri(int subscriptionId, int efType) {
            return AUTHORITY_URI.buildUpon()
                    .appendPath("subid")
                    .appendPath(String.valueOf(subscriptionId))
                    .appendPath(efPathSegment(efType))
                    .build();
        }

        public static Uri getItemUri(int subscriptionId, int efType, int recordNumber) {
            if (recordNumber <= 0) {
                throw new IllegalArgumentException("Invalid recordNumber: " + recordNumber);
            }
            return getContentUri(subscriptionId, efType).buildUpon()
                    .appendPath(String.valueOf(recordNumber))
                    .build();
        }

        public static int getEncodedNameLength(ContentResolver resolver, String name) {
            if (resolver == null) {
                throw new NullPointerException("resolver must not be null");
            }
            if (name == null) {
                throw new NullPointerException("name must not be null");
            }
            if (name.isEmpty()) {
                return 0;
            }
            try {
                Bundle out = resolver.call(AUTHORITY_URI, "get_encoded_name_length", name, null);
                if (out != null && out.containsKey("android.provider.extra.ENCODED_NAME_LENGTH")) {
                    return out.getInt("android.provider.extra.ENCODED_NAME_LENGTH");
                }
            } catch (RuntimeException ignored) {
            }
            boolean asciiGsm = true;
            for (int i = 0; i < name.length(); i++) {
                char ch = name.charAt(i);
                if (ch > 0x7F) {
                    asciiGsm = false;
                    break;
                }
            }
            if (asciiGsm) {
                return name.length();
            }
            return 1 + name.getBytes(StandardCharsets.UTF_16BE).length;
        }
    }
}
