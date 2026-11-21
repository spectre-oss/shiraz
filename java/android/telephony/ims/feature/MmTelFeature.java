// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.ims.feature;

public class MmTelFeature {
    MmTelFeature() {}

    public static class MmTelCapabilities {
        public static final int CAPABILITY_TYPE_VOICE = 1 << 0;
        public static final int CAPABILITY_TYPE_VIDEO = 1 << 1;
        public static final int CAPABILITY_TYPE_UT = 1 << 2;
        public static final int CAPABILITY_TYPE_SMS = 1 << 3;
        public static final int CAPABILITY_TYPE_CALL_COMPOSER = 1 << 4;
        public static final int CAPABILITY_TYPE_CALL_COMPOSER_BUSINESS_ONLY = 1 << 5;

        private final int mCapabilities;

        MmTelCapabilities() {
            this(0);
        }

        MmTelCapabilities(int capabilities) {
            mCapabilities = capabilities;
        }

        public final boolean isCapable(int capabilities) {
            return (mCapabilities & capabilities) == capabilities;
        }

        public String toString() {
            StringBuilder builder = new StringBuilder("MmTel Capabilities - [");
            builder.append("Voice: ").append(isCapable(CAPABILITY_TYPE_VOICE));
            builder.append(" Video: ").append(isCapable(CAPABILITY_TYPE_VIDEO));
            builder.append(" UT: ").append(isCapable(CAPABILITY_TYPE_UT));
            builder.append(" SMS: ").append(isCapable(CAPABILITY_TYPE_SMS));
            builder.append(" CALL_COMPOSER: ").append(isCapable(CAPABILITY_TYPE_CALL_COMPOSER));
            builder.append(" CALL_COMPOSER_BUSINESS_ONLY: ")
                    .append(isCapable(CAPABILITY_TYPE_CALL_COMPOSER_BUSINESS_ONLY));
            builder.append("]");
            return builder.toString();
        }

        public int hashCode() {
            return mCapabilities;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MmTelCapabilities)) return false;
            return mCapabilities == ((MmTelCapabilities) o).mCapabilities;
        }
    }
}
