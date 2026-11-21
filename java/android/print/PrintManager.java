// SPDX-License-Identifier: GPL-3.0-only
package android.print;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PrintManager {
    private final Context mContext;
    private final Map<PrintJobId, JobRecord> mJobs = new LinkedHashMap<PrintJobId, JobRecord>();

    public PrintManager() {
        this.mContext = null;
    }

    public PrintManager(Context context) {
        this.mContext = context;
    }

    public List<PrintJob> getPrintJobs() {
        synchronized (this) {
            ArrayList<PrintJob> list = new ArrayList<PrintJob>(mJobs.size());
            for (JobRecord rec : mJobs.values()) {
                list.add(new PrintJob(new PrintJobInfo(rec.info), this));
            }
            return Collections.unmodifiableList(list);
        }
    }

    public PrintJob print(
            String printJobName,
            PrintDocumentAdapter documentAdapter,
            PrintAttributes attributes) {
        if (printJobName == null || printJobName.isEmpty()) {
            throw new IllegalArgumentException("printJobName cannot be empty");
        }
        Objects.requireNonNull(documentAdapter, "documentAdapter cannot be null");

        PrintAttributes resolvedAttributes = normalizeAttributes(attributes);
        PrintJobId jobId = new PrintJobId();
        PrintJobInfo info = new PrintJobInfo();
        info.setId(jobId);
        info.setLabel(printJobName);
        info.setCreationTime(System.currentTimeMillis());
        info.setState(PrintJobInfo.STATE_QUEUED);
        info.setAttributesInternal(resolvedAttributes);
        info.setPagesInternal(new PageRange[] { PageRange.ALL_PAGES });

        JobRecord record = new JobRecord(info, documentAdapter, attributes, resolvedAttributes);
        synchronized (this) {
            mJobs.put(jobId, record);
        }

        executeAdapterLifecycle(record);
        synchronized (this) {
            return new PrintJob(new PrintJobInfo(record.info), this);
        }
    }

    public boolean isPrintServiceEnabled(ComponentName service) {
        Objects.requireNonNull(service, "service cannot be null");
        return false;
    }

    synchronized PrintJobInfo getPrintJobInfo(PrintJobId jobId) {
        JobRecord rec = mJobs.get(jobId);
        return rec == null ? null : new PrintJobInfo(rec.info);
    }

    void cancelPrintJob(PrintJobId jobId) {
        CancellationSignal sig = null;
        synchronized (this) {
            JobRecord rec = mJobs.get(jobId);
            if (rec == null) {
                return;
            }
            int state = rec.info.getState();
            if (state == PrintJobInfo.STATE_CREATED
                    || state == PrintJobInfo.STATE_QUEUED
                    || state == PrintJobInfo.STATE_STARTED
                    || state == PrintJobInfo.STATE_BLOCKED) {
                rec.info.setState(PrintJobInfo.STATE_CANCELED);
                sig = rec.activeSignal;
            }
        }
        if (sig != null) {
            sig.cancel();
        }
    }

    void restartPrintJob(PrintJobId jobId) {
        JobRecord rec;
        synchronized (this) {
            rec = mJobs.get(jobId);
            if (rec == null) {
                return;
            }
            int state = rec.info.getState();
            if (state != PrintJobInfo.STATE_FAILED && state != PrintJobInfo.STATE_BLOCKED) {
                return;
            }
            rec.info.setState(PrintJobInfo.STATE_QUEUED);
        }
        executeAdapterLifecycle(rec);
    }

    private void executeAdapterLifecycle(final JobRecord record) {
        final CancellationSignal layoutSignal = new CancellationSignal();
        synchronized (this) {
            if (record.info.getState() == PrintJobInfo.STATE_CANCELED) {
                return;
            }
            record.activeSignal = layoutSignal;
            record.finished = false;
            record.info.setState(PrintJobInfo.STATE_STARTED);
        }

        record.adapter.onStart();
        Bundle extras = new Bundle();
        extras.putBoolean(PrintDocumentAdapter.EXTRA_PRINT_PREVIEW, false);

        PrintDocumentAdapter.LayoutResultCallback layoutCallback =
                new PrintDocumentAdapter.LayoutResultCallback() {
                    @Override
                    public void onLayoutFinished(PrintDocumentInfo docInfo, boolean changed) {
                        Objects.requireNonNull(docInfo, "info cannot be null!");
                        synchronized (PrintManager.this) {
                            if (record.info.getState() == PrintJobInfo.STATE_CANCELED) {
                                finishAdapterOnce(record);
                                return;
                            }
                            record.documentInfo = docInfo;
                        }
                        if (docInfo.getPageCount() == 0) {
                            synchronized (PrintManager.this) {
                                record.info.setState(PrintJobInfo.STATE_COMPLETED);
                                record.activeSignal = null;
                            }
                            finishAdapterOnce(record);
                            return;
                        }
                        performWrite(record);
                    }

                    @Override
                    public void onLayoutFailed(CharSequence error) {
                        synchronized (PrintManager.this) {
                            record.info.setState(PrintJobInfo.STATE_FAILED);
                            record.activeSignal = null;
                        }
                        finishAdapterOnce(record);
                    }

                    @Override
                    public void onLayoutCancelled() {
                        synchronized (PrintManager.this) {
                            record.info.setState(PrintJobInfo.STATE_CANCELED);
                            record.activeSignal = null;
                        }
                        finishAdapterOnce(record);
                    }
                };

        record.adapter.onLayout(
                record.oldAttributes,
                record.resolvedAttributes,
                layoutSignal,
                layoutCallback,
                extras);
    }

    private void performWrite(final JobRecord record) {
        final CancellationSignal writeSignal = new CancellationSignal();
        synchronized (this) {
            if (record.info.getState() == PrintJobInfo.STATE_CANCELED) {
                finishAdapterOnce(record);
                return;
            }
            record.activeSignal = writeSignal;
        }

        final File outFile;
        final ParcelFileDescriptor pfd;
        try {
            File dir = (mContext != null && mContext.getCacheDir() != null)
                    ? mContext.getCacheDir() : null;
            outFile = File.createTempFile("shiraz_print_job_", ".pdf", dir);
            outFile.deleteOnExit();
            pfd = ParcelFileDescriptor.open(
                    outFile,
                    ParcelFileDescriptor.MODE_READ_WRITE
                            | ParcelFileDescriptor.MODE_CREATE
                            | ParcelFileDescriptor.MODE_TRUNCATE);
            record.outputFile = outFile;
        } catch (IOException e) {
            synchronized (this) {
                record.info.setState(PrintJobInfo.STATE_FAILED);
                record.activeSignal = null;
            }
            finishAdapterOnce(record);
            return;
        }

        PageRange[] requestedPages = new PageRange[] { PageRange.ALL_PAGES };
        PrintDocumentAdapter.WriteResultCallback writeCallback =
                new PrintDocumentAdapter.WriteResultCallback() {
                    @Override
                    public void onWriteFinished(PageRange[] pages) {
                        closeQuietly(pfd);
                        if (pages == null || pages.length == 0) {
                            synchronized (PrintManager.this) {
                                record.info.setState(PrintJobInfo.STATE_FAILED);
                                record.activeSignal = null;
                            }
                            finishAdapterOnce(record);
                            return;
                        }
                        synchronized (PrintManager.this) {
                            if (record.documentInfo != null && outFile.exists()) {
                                record.documentInfo.setDataSize(outFile.length());
                            }
                            record.info.setPagesInternal(pages);
                            if (record.info.getState() != PrintJobInfo.STATE_CANCELED) {
                                record.info.setState(PrintJobInfo.STATE_COMPLETED);
                            }
                            record.activeSignal = null;
                        }
                        finishAdapterOnce(record);
                    }

                    @Override
                    public void onWriteFailed(CharSequence error) {
                        closeQuietly(pfd);
                        synchronized (PrintManager.this) {
                            record.info.setState(PrintJobInfo.STATE_FAILED);
                            record.activeSignal = null;
                        }
                        finishAdapterOnce(record);
                    }

                    @Override
                    public void onWriteCancelled() {
                        closeQuietly(pfd);
                        synchronized (PrintManager.this) {
                            record.info.setState(PrintJobInfo.STATE_CANCELED);
                            record.activeSignal = null;
                        }
                        finishAdapterOnce(record);
                    }
                };

        record.adapter.onWrite(requestedPages, pfd, writeSignal, writeCallback);
    }

    private void finishAdapterOnce(JobRecord record) {
        boolean shouldFinish = false;
        synchronized (this) {
            if (!record.finished) {
                record.finished = true;
                shouldFinish = true;
            }
        }
        if (shouldFinish) {
            record.adapter.onFinish();
        }
    }

    private static void closeQuietly(ParcelFileDescriptor pfd) {
        if (pfd != null) {
            try {
                pfd.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static PrintAttributes normalizeAttributes(PrintAttributes attrs) {
        PrintAttributes.Builder builder = new PrintAttributes.Builder();
        builder.setMediaSize(attrs != null && attrs.getMediaSize() != null
                ? attrs.getMediaSize() : PrintAttributes.MediaSize.NA_LETTER);
        builder.setResolution(attrs != null && attrs.getResolution() != null
                ? attrs.getResolution()
                : new PrintAttributes.Resolution("pdf", "PDF", 300, 300));
        builder.setMinMargins(attrs != null && attrs.getMinMargins() != null
                ? attrs.getMinMargins() : PrintAttributes.Margins.NO_MARGINS);
        builder.setColorMode(attrs != null && attrs.getColorMode() != 0
                ? attrs.getColorMode() : PrintAttributes.COLOR_MODE_COLOR);
        builder.setDuplexMode(attrs != null && attrs.getDuplexMode() != 0
                ? attrs.getDuplexMode() : PrintAttributes.DUPLEX_MODE_NONE);
        return builder.build();
    }

    private static final class JobRecord {
        final PrintJobInfo info;
        final PrintDocumentAdapter adapter;
        final PrintAttributes oldAttributes;
        final PrintAttributes resolvedAttributes;
        PrintDocumentInfo documentInfo;
        File outputFile;
        CancellationSignal activeSignal;
        boolean finished;

        JobRecord(
                PrintJobInfo info,
                PrintDocumentAdapter adapter,
                PrintAttributes oldAttributes,
                PrintAttributes resolvedAttributes) {
            this.info = info;
            this.adapter = adapter;
            this.oldAttributes = oldAttributes;
            this.resolvedAttributes = resolvedAttributes;
        }
    }
}
