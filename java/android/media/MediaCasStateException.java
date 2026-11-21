// SPDX-License-Identifier: GPL-3.0-only
package android.media;

public class MediaCasStateException extends IllegalStateException {
    private final int mErrorCode;
    private final String mDiagnosticInfo;

    MediaCasStateException(int err, String msg, String diagnosticInfo) {
        super(msg);
        mErrorCode = err;
        mDiagnosticInfo = diagnosticInfo != null ? diagnosticInfo : ("android.media.MediaCasStateException_" + err);
    }

    int getErrorCode() {
        return mErrorCode;
    }

    public String getDiagnosticInfo() {
        return mDiagnosticInfo;
    }
}
