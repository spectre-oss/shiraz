// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;

public abstract class UploadDataSink {
    public UploadDataSink() {}

    public abstract void onReadSucceeded(boolean finalChunk);

    public abstract void onReadError(Exception exception);

    public abstract void onRewindSucceeded();

    public abstract void onRewindError(Exception exception);
}
