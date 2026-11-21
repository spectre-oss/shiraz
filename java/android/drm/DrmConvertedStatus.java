// SPDX-License-Identifier: GPL-3.0-only
package android.drm;

/** Status and converted byte payload returned by DRM data conversion operations. */
@Deprecated
public class DrmConvertedStatus {
    public static final int STATUS_OK = 1;
    public static final int STATUS_INPUTDATA_ERROR = 2;
    public static final int STATUS_ERROR = 3;

    public final int statusCode;
    public final byte[] convertedData;
    public final int offset;

    public DrmConvertedStatus(int statusCode, byte[] convertedData, int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Invalid offset: " + offset);
        }
        if (!isValidStatusCode(statusCode)) {
            throw new IllegalArgumentException("Unsupported status code: " + statusCode);
        }
        this.statusCode = statusCode;
        this.convertedData = convertedData;
        this.offset = offset;
    }

    private static boolean isValidStatusCode(int statusCode) {
        return statusCode == STATUS_OK
                || statusCode == STATUS_INPUTDATA_ERROR
                || statusCode == STATUS_ERROR;
    }
}
