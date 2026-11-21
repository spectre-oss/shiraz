// SPDX-License-Identifier: GPL-3.0-only
package android.drm;

/** Status and processed data resulting from processing a {@link DrmInfo}. */
@Deprecated
public class DrmInfoStatus {
    public static final int STATUS_OK = 1;
    public static final int STATUS_ERROR = 2;

    public final int statusCode;
    public final int infoType;
    public final ProcessedData data;
    public final String mimeType;

    public DrmInfoStatus(int statusCode, int infoType, ProcessedData data, String mimeType) {
        if (!DrmInfoRequest.isValidType(infoType)) {
            throw new IllegalArgumentException("infoType: " + infoType);
        }
        if (!isValidStatusCode(statusCode)) {
            throw new IllegalArgumentException("Unsupported status code: " + statusCode);
        }
        if (mimeType == null || mimeType.isEmpty()) {
            throw new IllegalArgumentException("mimeType is null or an empty string");
        }
        this.statusCode = statusCode;
        this.infoType = infoType;
        this.data = data;
        this.mimeType = mimeType;
    }

    private static boolean isValidStatusCode(int statusCode) {
        return statusCode == STATUS_OK || statusCode == STATUS_ERROR;
    }
}
