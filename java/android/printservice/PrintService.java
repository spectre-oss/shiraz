// SPDX-License-Identifier: GPL-3.0-only
package android.printservice;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.print.PrinterId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class PrintService extends Service {
    public static final String SERVICE_INTERFACE = "android.printservice.PrintService";
    public static final String SERVICE_META_DATA = "android.printservice";
    public static final String EXTRA_PRINT_JOB_INFO = "android.intent.extra.print.PRINT_JOB_INFO";
    public static final String EXTRA_PRINTER_INFO = "android.intent.extra.print.EXTRA_PRINTER_INFO";
    public static final String EXTRA_PRINT_DOCUMENT_INFO =
            "android.printservice.extra.PRINT_DOCUMENT_INFO";
    public static final String EXTRA_CAN_SELECT_PRINTER =
            "android.printservice.extra.CAN_SELECT_PRINTER";
    public static final String EXTRA_SELECT_PRINTER =
            "android.printservice.extra.SELECT_PRINTER";

    private final List<PrintJob> mJobs = new ArrayList<PrintJob>();
    private PrinterDiscoverySession mDiscoverySession;
    private IBinder mBinder;

    public PrintService() {
    }

    @Override
    protected final void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    protected void onConnected() {
    }

    protected void onDisconnected() {
    }

    protected abstract PrinterDiscoverySession onCreatePrinterDiscoverySession();

    protected abstract void onRequestCancelPrintJob(PrintJob printJob);

    protected abstract void onPrintJobQueued(PrintJob printJob);

    public final List<PrintJob> getActivePrintJobs() {
        if (mJobs.isEmpty()) {
            return Collections.emptyList();
        }
        List<PrintJob> active = new ArrayList<PrintJob>();
        for (int i = 0; i < mJobs.size(); i++) {
            PrintJob job = mJobs.get(i);
            if (job.isQueued() || job.isStarted() || job.isBlocked()) {
                active.add(job);
            }
        }
        return active;
    }

    public final PrinterId generatePrinterId(String localId) {
        Objects.requireNonNull(localId, "localId cannot be null");
        String pkg = null;
        if (getBaseContext() != null) {
            try {
                pkg = getPackageName();
            } catch (RuntimeException ignored) {
                pkg = null;
            }
        }
        if (pkg == null) {
            Package p = getClass().getPackage();
            pkg = (p != null && !p.getName().isEmpty()) ? p.getName() : "android.printservice";
        }
        String cls = getClass().getName();
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeString(pkg);
            parcel.writeString(cls);
            parcel.writeString(localId);
            parcel.setDataPosition(0);
            return PrinterId.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    @Override
    public final IBinder onBind(Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            if (mBinder == null) {
                PrintServiceBinder binder = new PrintServiceBinder(this);
                mBinder = binder;
            }
            return mBinder;
        }
        return null;
    }

    void enqueueJob(PrintJob job) {
        if (job != null) {
            mJobs.add(job);
            onPrintJobQueued(job);
        }
    }

    PrinterDiscoverySession ensureDiscoverySession() {
        if (mDiscoverySession == null || mDiscoverySession.isDestroyed()) {
            mDiscoverySession = onCreatePrinterDiscoverySession();
        }
        return mDiscoverySession;
    }

    private static final class PrintServiceBinder extends Binder implements IInterface {
        private final PrintService mService;

        PrintServiceBinder(PrintService service) {
            mService = service;
            attachInterface(this, SERVICE_INTERFACE);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        PrintService getService() {
            return mService;
        }
    }
}
