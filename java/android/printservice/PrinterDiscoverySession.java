// SPDX-License-Identifier: GPL-3.0-only
package android.printservice;

import android.graphics.drawable.Icon;
import android.os.CancellationSignal;
import android.print.PrinterId;
import android.print.PrinterInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class PrinterDiscoverySession {
    private final Map<PrinterId, PrinterInfo> mPrinters = new LinkedHashMap<PrinterId, PrinterInfo>();
    private final List<PrinterId> mTrackedPrinters = new ArrayList<PrinterId>();
    private final Map<PrinterId, Icon> mCustomIcons = new LinkedHashMap<PrinterId, Icon>();
    private boolean mIsDestroyed;
    private boolean mIsDiscoveryStarted;

    public PrinterDiscoverySession() {
    }

    public final List<PrinterInfo> getPrinters() {
        if (mIsDestroyed) {
            return Collections.emptyList();
        }
        return new ArrayList<PrinterInfo>(mPrinters.values());
    }

    public final void addPrinters(List<PrinterInfo> printers) {
        if (mIsDestroyed || printers == null) {
            return;
        }
        for (int i = 0; i < printers.size(); i++) {
            PrinterInfo printer = printers.get(i);
            if (printer != null && printer.getId() != null) {
                mPrinters.put(printer.getId(), printer);
            }
        }
    }

    public final void removePrinters(List<PrinterId> printerIds) {
        if (mIsDestroyed || printerIds == null) {
            return;
        }
        for (int i = 0; i < printerIds.size(); i++) {
            PrinterId printerId = printerIds.get(i);
            if (printerId != null) {
                mPrinters.remove(printerId);
                mCustomIcons.remove(printerId);
            }
        }
    }

    public abstract void onStartPrinterDiscovery(List<PrinterId> priorityList);

    public abstract void onStopPrinterDiscovery();

    public abstract void onValidatePrinters(List<PrinterId> printerIds);

    public abstract void onStartPrinterStateTracking(PrinterId printerId);

    public void onRequestCustomPrinterIcon(
            PrinterId printerId,
            CancellationSignal cancellationSignal,
            CustomPrinterIconCallback callback) {
    }

    public abstract void onStopPrinterStateTracking(PrinterId printerId);

    public final List<PrinterId> getTrackedPrinters() {
        if (mIsDestroyed) {
            return Collections.emptyList();
        }
        return new ArrayList<PrinterId>(mTrackedPrinters);
    }

    public abstract void onDestroy();

    public final boolean isDestroyed() {
        return mIsDestroyed;
    }

    public final boolean isPrinterDiscoveryStarted() {
        return mIsDiscoveryStarted;
    }

    boolean onCustomPrinterIconLoaded(PrinterId printerId, Icon icon) {
        if (mIsDestroyed || printerId == null) {
            return false;
        }
        if (icon != null) {
            mCustomIcons.put(printerId, icon);
        } else {
            mCustomIcons.remove(printerId);
        }
        return true;
    }

    void startPrinterDiscovery(List<PrinterId> priorityList) {
        if (!mIsDestroyed && !mIsDiscoveryStarted) {
            mIsDiscoveryStarted = true;
            List<PrinterId> list = priorityList == null
                    ? Collections.<PrinterId>emptyList()
                    : new ArrayList<PrinterId>(priorityList);
            onStartPrinterDiscovery(list);
        }
    }

    void stopPrinterDiscovery() {
        if (!mIsDestroyed && mIsDiscoveryStarted) {
            mIsDiscoveryStarted = false;
            onStopPrinterDiscovery();
        }
    }

    void startPrinterStateTracking(PrinterId printerId) {
        if (!mIsDestroyed && printerId != null && !mTrackedPrinters.contains(printerId)) {
            mTrackedPrinters.add(printerId);
            onStartPrinterStateTracking(printerId);
        }
    }

    void stopPrinterStateTracking(PrinterId printerId) {
        if (!mIsDestroyed && printerId != null && mTrackedPrinters.remove(printerId)) {
            onStopPrinterStateTracking(printerId);
        }
    }

    void destroy() {
        if (!mIsDestroyed) {
            if (mIsDiscoveryStarted) {
                stopPrinterDiscovery();
            }
            List<PrinterId> tracked = new ArrayList<PrinterId>(mTrackedPrinters);
            for (int i = 0; i < tracked.size(); i++) {
                stopPrinterStateTracking(tracked.get(i));
            }
            mIsDestroyed = true;
            mPrinters.clear();
            mCustomIcons.clear();
            onDestroy();
        }
    }
}
