// SPDX-License-Identifier: GPL-3.0-only
package android.printservice;

import android.graphics.drawable.Icon;
import android.print.PrinterId;

public final class CustomPrinterIconCallback {
    private final PrinterId mPrinterId;
    private final PrinterDiscoverySession mSession;

    CustomPrinterIconCallback(PrinterId printerId, PrinterDiscoverySession session) {
        mPrinterId = printerId;
        mSession = session;
    }

    public boolean onCustomPrinterIconLoaded(Icon icon) {
        if (mSession == null || mSession.isDestroyed()) {
            return false;
        }
        return mSession.onCustomPrinterIconLoaded(mPrinterId, icon);
    }
}
