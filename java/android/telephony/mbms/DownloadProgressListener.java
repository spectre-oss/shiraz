// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.mbms;

public class DownloadProgressListener {
    public DownloadProgressListener() {}

    public void onProgressUpdated(
            DownloadRequest request,
            FileInfo fileInfo,
            int currentDownloadSize,
            int fullDownloadSize,
            int currentDecodedSize,
            int fullDecodedSize) {}
}
