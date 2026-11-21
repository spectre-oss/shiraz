// SPDX-License-Identifier: GPL-3.0-only
package android.app.backup;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** File backup helper relative to Context.getFilesDir(). */
public class FileBackupHelper implements BackupHelper {
    private final Context mContext;
    private final File mFilesDir;
    private final String[] mFiles;

    public FileBackupHelper(Context context, String... files) {
        if (context == null || files == null) {
            throw new NullPointerException("file backup");
        }
        if (files.length > 1024) {
            throw new IllegalArgumentException("File backup limit");
        }
        mContext = context;
        mFilesDir = context.getFilesDir();
        mFiles = files.clone();
        for (String f : mFiles) {
            if (f == null || f.isEmpty()) {
                throw new IllegalArgumentException("Invalid backup file name");
            }
        }
    }

    @Override
    public void performBackup(
            ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        if (mFilesDir == null) {
            throw new UnsupportedOperationException("Android backup transport unavailable");
        }
        byte[] buf = new byte[8192];
        for (String rel : mFiles) {
            File file = new File(mFilesDir, rel);
            if (!file.isFile()) {
                continue;
            }
            long len = file.length();
            if (len > Integer.MAX_VALUE) {
                continue;
            }
            try {
                data.writeEntityHeader(rel, (int) len);
                FileInputStream in = new FileInputStream(file);
                try {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        data.writeEntityData(buf, n);
                    }
                } finally {
                    in.close();
                }
            } catch (IOException e) {
                throw new RuntimeException("Backup failed for " + rel, e);
            }
        }
    }

    @Override
    public void restoreEntity(BackupDataInputStream data) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        String key = data.getKey();
        boolean allowed = false;
        for (String rel : mFiles) {
            if (rel.equals(key)) {
                allowed = true;
                break;
            }
        }
        if (!allowed || mFilesDir == null) {
            return;
        }
        File target = new File(mFilesDir, key);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        byte[] buf = new byte[8192];
        try {
            FileOutputStream out = new FileOutputStream(target);
            try {
                int n;
                while ((n = data.read(buf, 0, buf.length)) > 0) {
                    out.write(buf, 0, n);
                }
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Restore failed for " + key, e);
        }
    }

    @Override
    public void writeNewStateDescription(ParcelFileDescriptor newState) {
    }
}
