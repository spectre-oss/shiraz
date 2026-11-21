// SPDX-License-Identifier: GPL-3.0-only
package android.printservice;

import android.os.ParcelFileDescriptor;
import android.print.PrintDocumentInfo;
import java.io.File;
import java.io.IOException;

public final class PrintDocument {
    private final PrintDocumentInfo mInfo;
    private final File mDataFile;

    PrintDocument(PrintDocumentInfo info, File dataFile) {
        mInfo = info;
        mDataFile = dataFile;
    }

    public PrintDocumentInfo getInfo() {
        return mInfo;
    }

    public ParcelFileDescriptor getData() {
        if (mDataFile == null || !mDataFile.exists()) {
            return null;
        }
        try {
            return ParcelFileDescriptor.open(mDataFile, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException e) {
            return null;
        }
    }
}
