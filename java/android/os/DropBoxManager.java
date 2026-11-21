// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/** Bounded diagnostic event log storing timestamped text and binary entries. */
public class DropBoxManager {
    public static final String ACTION_DROPBOX_ENTRY_ADDED =
            "android.intent.action.DROPBOX_ENTRY_ADDED";
    public static final String EXTRA_TAG = "tag";
    public static final String EXTRA_TIME = "time";
    public static final String EXTRA_DROPPED_COUNT = "android.os.extra.DROPPED_COUNT";

    public static final int IS_EMPTY = 1;
    public static final int IS_TEXT = 2;
    public static final int IS_GZIPPED = 4;

    private static final int MAX_ENTRIES = 256;

    public static class Entry implements Parcelable, Closeable {
        public static final Parcelable.Creator<Entry> CREATOR =
                new Parcelable.Creator<Entry>() {
                    @Override
                    public Entry createFromParcel(Parcel in) {
                        String tag = in.readString();
                        long millis = in.readLong();
                        int flags = in.readInt();
                        int kind = in.readInt();
                        if (kind == 1) {
                            return new Entry(tag, millis, in.createByteArray(), flags);
                        }
                        if (kind == 2) {
                            return new Entry(tag, millis, in.readFileDescriptor(), flags);
                        }
                        return new Entry(tag, millis);
                    }

                    @Override
                    public Entry[] newArray(int size) {
                        return new Entry[size];
                    }
                };

        private final String tag;
        private final long timeMillis;
        private final byte[] data;
        private final ParcelFileDescriptor fileDescriptor;
        private final int flags;

        public Entry(String tag, long millis) {
            this.tag = Objects.requireNonNull(tag, "tag == null");
            this.timeMillis = millis;
            this.data = null;
            this.fileDescriptor = null;
            this.flags = IS_EMPTY;
        }

        public Entry(String tag, long millis, String text) {
            this.tag = Objects.requireNonNull(tag, "tag == null");
            Objects.requireNonNull(text, "text == null");
            this.timeMillis = millis;
            this.data = text.getBytes(StandardCharsets.UTF_8);
            this.fileDescriptor = null;
            this.flags = IS_TEXT;
        }

        public Entry(String tag, long millis, byte[] data, int flags) {
            this.tag = Objects.requireNonNull(tag, "tag == null");
            if (((flags & IS_EMPTY) != 0) != (data == null)) {
                throw new IllegalArgumentException("Bad flags: " + flags);
            }
            this.timeMillis = millis;
            this.data = data == null ? null : data.clone();
            this.fileDescriptor = null;
            this.flags = flags;
        }

        public Entry(String tag, long millis, ParcelFileDescriptor data, int flags) {
            this.tag = Objects.requireNonNull(tag, "tag == null");
            if (((flags & IS_EMPTY) != 0) != (data == null)) {
                throw new IllegalArgumentException("Bad flags: " + flags);
            }
            this.timeMillis = millis;
            this.data = null;
            this.fileDescriptor = data;
            this.flags = flags;
        }

        public Entry(String tag, long millis, File data, int flags) throws IOException {
            this.tag = Objects.requireNonNull(tag, "tag == null");
            if ((flags & IS_EMPTY) != 0) {
                throw new IllegalArgumentException("Bad flags: " + flags);
            }
            this.timeMillis = millis;
            this.data = null;
            this.fileDescriptor = ParcelFileDescriptor.open(data, ParcelFileDescriptor.MODE_READ_ONLY);
            this.flags = flags;
        }

        @Override
        public void close() {
            if (fileDescriptor != null) {
                FileUtils.closeQuietly(fileDescriptor);
            }
        }

        public String getTag() {
            return tag;
        }

        public long getTimeMillis() {
            return timeMillis;
        }

        public int getFlags() {
            return flags & ~IS_GZIPPED;
        }

        public String getText(int maxBytes) {
            if ((flags & IS_TEXT) == 0) {
                return null;
            }
            if (maxBytes < 0) {
                return null;
            }
            InputStream is = null;
            try {
                is = getInputStream();
                if (is == null) {
                    return null;
                }
                byte[] buf = new byte[Math.min(maxBytes, 8192)];
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int remaining = maxBytes;
                while (remaining > 0) {
                    int n = is.read(buf, 0, Math.min(buf.length, remaining));
                    if (n < 0) {
                        break;
                    }
                    out.write(buf, 0, n);
                    remaining -= n;
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return null;
            } finally {
                FileUtils.closeQuietly(is);
            }
        }

        public InputStream getInputStream() throws IOException {
            InputStream is;
            if (data != null) {
                is = new ByteArrayInputStream(data);
            } else if (fileDescriptor != null) {
                is = new ParcelFileDescriptor.AutoCloseInputStream(fileDescriptor.dup());
            } else {
                return null;
            }
            return (flags & IS_GZIPPED) != 0 ? new GZIPInputStream(is) : is;
        }

        @Override
        public int describeContents() {
            return fileDescriptor != null ? CONTENTS_FILE_DESCRIPTOR : 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeString(tag);
            out.writeLong(timeMillis);
            out.writeInt(this.flags);
            if (data != null) {
                out.writeInt(1);
                out.writeByteArray(data);
            } else if (fileDescriptor != null) {
                out.writeInt(2);
                out.writeFileDescriptor(fileDescriptor.getFileDescriptor());
            } else {
                out.writeInt(0);
            }
        }
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private long lastTimestamp;

    protected DropBoxManager() {
    }

    private synchronized long nextTimestamp() {
        long now = System.currentTimeMillis();
        if (now <= lastTimestamp) {
            now = lastTimestamp + 1;
        }
        lastTimestamp = now;
        return now;
    }

    private synchronized void append(Entry entry) {
        if (entries.size() >= MAX_ENTRIES) {
            Entry evicted = entries.remove(0);
            evicted.close();
        }
        entries.add(entry);
    }

    public void addText(String tag, String data) {
        Objects.requireNonNull(tag, "tag == null");
        Objects.requireNonNull(data, "data == null");
        append(new Entry(tag, nextTimestamp(), data));
    }

    public void addData(String tag, byte[] data, int flags) {
        Objects.requireNonNull(tag, "tag == null");
        Objects.requireNonNull(data, "data == null");
        append(new Entry(tag, nextTimestamp(), data, flags & ~IS_EMPTY));
    }

    public void addFile(String tag, File file, int flags) throws IOException {
        Objects.requireNonNull(tag, "tag == null");
        Objects.requireNonNull(file, "file == null");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileInputStream fis = new FileInputStream(file);
        try {
            FileUtils.copy(fis, out);
        } finally {
            fis.close();
        }
        append(new Entry(tag, nextTimestamp(), out.toByteArray(), flags & ~IS_EMPTY));
    }

    public boolean isTagEnabled(String tag) {
        Objects.requireNonNull(tag, "tag == null");
        return true;
    }

    public synchronized Entry getNextEntry(String tag, long msec) {
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.getTimeMillis() > msec && (tag == null || tag.equals(e.getTag()))) {
                return new Entry(e.tag, e.timeMillis, e.data, e.flags);
            }
        }
        return null;
    }
}
