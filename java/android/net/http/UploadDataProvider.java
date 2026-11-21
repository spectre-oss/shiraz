// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class UploadDataProvider implements Closeable {
    public UploadDataProvider() {}

    public abstract long getLength() throws IOException;

    public abstract void read(UploadDataSink uploadDataSink, ByteBuffer byteBuffer)
            throws IOException;

    public abstract void rewind(UploadDataSink uploadDataSink) throws IOException;

    @Override
    public void close() throws IOException {}
}
