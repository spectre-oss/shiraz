// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.content.Context;
import android.telephony.mbms.DownloadProgressListener;
import android.telephony.mbms.DownloadRequest;
import android.telephony.mbms.DownloadStatusListener;
import android.telephony.mbms.FileInfo;
import android.telephony.mbms.MbmsDownloadSessionCallback;
import android.telephony.mbms.MbmsErrors;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public class MbmsDownloadSession implements AutoCloseable {
    public static final String DEFAULT_TOP_LEVEL_TEMP_DIRECTORY = "androidMbmsTempFileRoot";
    public static final String EXTRA_MBMS_DOWNLOAD_RESULT =
            "android.telephony.extra.MBMS_DOWNLOAD_RESULT";
    public static final String EXTRA_MBMS_FILE_INFO =
            "android.telephony.extra.MBMS_FILE_INFO";
    public static final String EXTRA_MBMS_DOWNLOAD_REQUEST =
            "android.telephony.extra.MBMS_DOWNLOAD_REQUEST";
    public static final String EXTRA_MBMS_COMPLETED_FILE_URI =
            "android.telephony.extra.MBMS_COMPLETED_FILE_URI";

    public static final int RESULT_SUCCESSFUL = 1;
    public static final int RESULT_CANCELLED = 2;
    public static final int RESULT_EXPIRED = 3;
    public static final int RESULT_IO_ERROR = 4;
    public static final int RESULT_SERVICE_ID_NOT_DEFINED = 5;
    public static final int RESULT_DOWNLOAD_FAILURE = 6;
    public static final int RESULT_OUT_OF_STORAGE = 7;
    public static final int RESULT_FILE_ROOT_UNREACHABLE = 8;

    public static final int STATUS_UNKNOWN = 0;
    public static final int STATUS_ACTIVELY_DOWNLOADING = 1;
    public static final int STATUS_PENDING_DOWNLOAD = 2;
    public static final int STATUS_PENDING_REPAIR = 3;
    public static final int STATUS_PENDING_DOWNLOAD_WINDOW = 4;

    private static final int MAX_SERVICE_ANNOUNCEMENT_SIZE = 10240;

    private File mTempFileRootDir;

    private MbmsDownloadSession() {}

    public static MbmsDownloadSession create(
            Context context, Executor executor, MbmsDownloadSessionCallback callback) {
        return create(context, executor, SubscriptionManager.getDefaultSubscriptionId(), callback);
    }

    public static MbmsDownloadSession create(
            Context context,
            Executor executor,
            int subscriptionId,
            final MbmsDownloadSessionCallback callback) {
        Objects.requireNonNull(context, "Context must be non-null");
        Objects.requireNonNull(executor, "Executor must be non-null");
        Objects.requireNonNull(callback, "Callback must be non-null");
        executor.execute(new Runnable() {
            public void run() {
                callback.onError(
                        MbmsErrors.ERROR_NO_UNIQUE_MIDDLEWARE,
                        "No eMBMS download middleware available on host");
            }
        });
        return null;
    }

    public static int getMaximumServiceAnnouncementSize() {
        return MAX_SERVICE_ANNOUNCEMENT_SIZE;
    }

    public void requestUpdateFileServices(List<String> classList) {}

    public void addServiceAnnouncement(byte[] contents) {
        Objects.requireNonNull(contents, "contents must be non-null");
        if (contents.length > MAX_SERVICE_ANNOUNCEMENT_SIZE) {
            throw new IllegalArgumentException("File too large");
        }
    }

    public void setTempFileRootDirectory(File tempFileRootDirectory) {
        mTempFileRootDir = tempFileRootDirectory;
    }

    public File getTempFileRootDirectory() {
        return mTempFileRootDir;
    }

    public void download(DownloadRequest request) {}

    public List<DownloadRequest> listPendingDownloads() {
        return Collections.emptyList();
    }

    public void addStatusListener(
            DownloadRequest request, Executor executor, DownloadStatusListener listener) {}

    public void removeStatusListener(DownloadRequest request, DownloadStatusListener listener) {}

    public void addProgressListener(
            DownloadRequest request, Executor executor, DownloadProgressListener listener) {}

    public void removeProgressListener(
            DownloadRequest request, DownloadProgressListener listener) {}

    public void cancelDownload(DownloadRequest downloadRequest) {}

    public void requestDownloadState(DownloadRequest downloadRequest, FileInfo fileInfo) {}

    public void resetDownloadKnowledge(DownloadRequest downloadRequest) {}

    public void close() {}
}
