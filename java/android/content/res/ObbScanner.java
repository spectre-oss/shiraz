// SPDX-License-Identifier: GPL-3.0-only
package android.content.res;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Scanner reading the AOSP OBB v1 footer signature and metadata block from OBB files. */
public class ObbScanner {
    private static final int OBB_SIGNATURE = 0x01059983;
    private static final int FOOTER_TAG_SIZE = 8;

    private ObbScanner() {}

    public static ObbInfo getObbInfo(String filePath) throws IOException {
        if (filePath == null) {
            throw new IllegalArgumentException("file path cannot be null");
        }
        File obbFile = new File(filePath);
        if (!obbFile.exists()) {
            throw new IllegalArgumentException("OBB file does not exist: " + filePath);
        }
        String canonical = obbFile.getCanonicalPath();
        RandomAccessFile raf = new RandomAccessFile(obbFile, "r");
        try {
            long fileLength = raf.length();
            if (fileLength < FOOTER_TAG_SIZE) {
                throw new IOException("Not a valid OBB file (too small): " + canonical);
            }
            raf.seek(fileLength - FOOTER_TAG_SIZE);
            byte[] tag = new byte[FOOTER_TAG_SIZE];
            raf.readFully(tag);
            ByteBuffer tagBuf = ByteBuffer.wrap(tag).order(ByteOrder.LITTLE_ENDIAN);
            int footerSize = tagBuf.getInt();
            int footerMagic = tagBuf.getInt();
            if (footerMagic != OBB_SIGNATURE || footerSize < 24 || footerSize > fileLength) {
                throw new IOException("Invalid OBB signature in: " + canonical);
            }
            raf.seek(fileLength - footerSize);
            byte[] footer = new byte[footerSize - FOOTER_TAG_SIZE];
            raf.readFully(footer);
            ByteBuffer buf = ByteBuffer.wrap(footer).order(ByteOrder.LITTLE_ENDIAN);
            int footerVersion = buf.getInt();
            if (footerVersion != 1) {
                throw new IOException("Unsupported OBB footer version: " + footerVersion);
            }
            int packageVersion = buf.getInt();
            int flags = buf.getInt();
            byte[] salt = new byte[8];
            buf.get(salt);
            int pkgNameLen = buf.getInt();
            if (pkgNameLen < 0 || pkgNameLen > buf.remaining()) {
                throw new IOException("Corrupt OBB package name length: " + pkgNameLen);
            }
            byte[] pkgBytes = new byte[pkgNameLen];
            buf.get(pkgBytes);
            int actualLen = pkgNameLen;
            while (actualLen > 0 && pkgBytes[actualLen - 1] == 0) {
                actualLen--;
            }
            ObbInfo info = new ObbInfo();
            info.filename = canonical;
            info.packageName = new String(pkgBytes, 0, actualLen, StandardCharsets.UTF_8);
            info.version = packageVersion;
            info.flags = flags;
            info.salt = salt;
            return info;
        } finally {
            raf.close();
        }
    }
}
