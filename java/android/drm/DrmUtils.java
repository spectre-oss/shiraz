// SPDX-License-Identifier: GPL-3.0-only
package android.drm;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;

/** Utility helpers for DRM file I/O and binary extended metadata parsing. */
@Deprecated
public class DrmUtils {
    public DrmUtils() {
    }

    static byte[] readBytes(String path) throws IOException {
        if (path == null) {
            throw new IOException("path is null");
        }
        return readBytes(new File(path));
    }

    static byte[] readBytes(File file) throws IOException {
        FileInputStream inputStream = new FileInputStream(file);
        BufferedInputStream bufferedStream = new BufferedInputStream(inputStream);
        try {
            long fileLen = file.length();
            if (fileLen <= 0 || fileLen > Integer.MAX_VALUE) {
                return null;
            }
            byte[] data = new byte[(int) fileLen];
            int offset = 0;
            while (offset < data.length) {
                int n = bufferedStream.read(data, offset, data.length - offset);
                if (n < 0) {
                    break;
                }
                offset += n;
            }
            if (offset < data.length) {
                byte[] trimmed = new byte[offset];
                System.arraycopy(data, 0, trimmed, 0, offset);
                return trimmed;
            }
            return data;
        } finally {
            try {
                bufferedStream.close();
            } catch (IOException ignored) {
            }
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    static void writeToFile(String path, byte[] data) throws IOException {
        if (path != null && data != null) {
            FileOutputStream outputStream = new FileOutputStream(path);
            try {
                outputStream.write(data);
            } finally {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    static void removeFile(String path) throws IOException {
        if (path != null) {
            File file = new File(path);
            file.delete();
        }
    }

    public static ExtendedMetadataParser getExtendedMetadataParser(byte[] extendedMetadata) {
        return new ExtendedMetadataParser(extendedMetadata);
    }

    public static class ExtendedMetadataParser {
        private final LinkedHashMap<String, String> mMap = new LinkedHashMap<String, String>();

        private ExtendedMetadataParser(byte[] constraintData) {
            if (constraintData == null) {
                return;
            }
            int index = 0;
            while (index < constraintData.length) {
                int keyLength = constraintData[index] & 0xFF;
                index++;
                if (keyLength == 0 || index >= constraintData.length) {
                    break;
                }
                int valueLength = constraintData[index] & 0xFF;
                index++;
                if (index + keyLength + valueLength > constraintData.length) {
                    break;
                }
                String strKey =
                        new String(constraintData, index, keyLength, StandardCharsets.UTF_8);
                index += keyLength;
                String strValue =
                        new String(constraintData, index, valueLength, StandardCharsets.UTF_8);
                if (" ".equals(strValue)) {
                    strValue = "";
                }
                index += valueLength;
                mMap.put(strKey, strValue);
            }
        }

        public Iterator<String> iterator() {
            return mMap.values().iterator();
        }

        public Iterator<String> keyIterator() {
            return mMap.keySet().iterator();
        }

        public String get(String key) {
            return mMap.get(key);
        }
    }
}
