// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public abstract class DrmInitData {
    DrmInitData() {
    }

    public abstract SchemeInitData get(UUID schemeUuid);

    public int getSchemeInitDataCount() {
        return 0;
    }

    public SchemeInitData getSchemeInitDataAt(int index) {
        throw new IndexOutOfBoundsException("index=" + index);
    }

    public static final class SchemeInitData {
        public static final UUID UUID_NIL = new UUID(0L, 0L);

        public final UUID uuid;
        public final String mimeType;
        public final byte[] data;

        public SchemeInitData(UUID uuid, String mimeType, byte[] data) {
            this.uuid = Objects.requireNonNull(uuid, "uuid");
            this.mimeType = Objects.requireNonNull(mimeType, "mimeType");
            this.data = Objects.requireNonNull(data, "data");
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SchemeInitData)) {
                return false;
            }
            if (obj == this) {
                return true;
            }
            SchemeInitData other = (SchemeInitData) obj;
            return uuid.equals(other.uuid)
                    && mimeType.equals(other.mimeType)
                    && Arrays.equals(data, other.data);
        }

        @Override
        public int hashCode() {
            return uuid.hashCode() + 31 * (mimeType.hashCode() + 31 * Arrays.hashCode(data));
        }
    }
}
