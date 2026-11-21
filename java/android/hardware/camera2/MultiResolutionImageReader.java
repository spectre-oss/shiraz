// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2;

import android.hardware.camera2.params.MultiResolutionStreamInfo;
import android.hardware.camera2.params.OutputConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.view.Surface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class MultiResolutionImageReader implements AutoCloseable {
    private final Map<ImageReader, MultiResolutionStreamInfo> mReaders =
            new LinkedHashMap<ImageReader, MultiResolutionStreamInfo>();
    private final int mFormat;
    private final int mMaxImages;
    private boolean mClosed;

    public MultiResolutionImageReader(
            Collection<MultiResolutionStreamInfo> streams,
            int format,
            int maxImages) {
        if (streams == null) {
            throw new IllegalArgumentException("streams must not be null");
        }
        if (streams.size() <= 1) {
            throw new IllegalArgumentException("The streams info collection must contain at least 2 entries");
        }
        if (maxImages < 1) {
            throw new IllegalArgumentException("Maximum image count must be at least 1");
        }
        mFormat = format;
        mMaxImages = maxImages;
        for (MultiResolutionStreamInfo info : streams) {
            if (info == null) {
                throw new IllegalArgumentException("MultiResolutionStreamInfo must not be null");
            }
            ImageReader reader = ImageReader.newInstance(
                    info.getWidth(), info.getHeight(), format, maxImages);
            mReaders.put(reader, info);
        }
    }

    public void setOnImageAvailableListener(
            final ImageReader.OnImageAvailableListener listener,
            final Executor executor) {
        if (listener != null && executor == null) {
            throw new IllegalArgumentException("Executor must not be null when listener is non-null");
        }
        for (ImageReader reader : mReaders.keySet()) {
            if (listener == null) {
                reader.setOnImageAvailableListener(null, null);
            } else {
                reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(final ImageReader r) {
                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                listener.onImageAvailable(r);
                            }
                        });
                    }
                }, null);
            }
        }
    }

    @Override
    public void close() {
        if (mClosed) return;
        mClosed = true;
        for (ImageReader reader : mReaders.keySet()) {
            reader.close();
        }
    }

    @Override
    protected void finalize() {
        try {
            close();
        } finally {
            try {
                super.finalize();
            } catch (Throwable ignored) {
            }
        }
    }

    public void flush() {
        for (ImageReader reader : mReaders.keySet()) {
            reader.discardFreeBuffers();
            Image img = reader.acquireLatestImage();
            if (img != null) {
                img.close();
            }
        }
    }

    public Surface getSurface() {
        for (ImageReader reader : mReaders.keySet()) {
            return reader.getSurface();
        }
        throw new IllegalStateException("No underlying ImageReaders");
    }

    public MultiResolutionStreamInfo getStreamInfoForImageReader(ImageReader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("ImageReader is null");
        }
        MultiResolutionStreamInfo info = mReaders.get(reader);
        if (info == null) {
            throw new IllegalArgumentException("ImageReader doesn't belong to this MultiResolutionImageReader");
        }
        return info;
    }

    public Collection<OutputConfiguration> shirazCreateOutputConfigurations() {
        int groupId = Math.abs(System.identityHashCode(this)) | 1;
        List<OutputConfiguration> out = new ArrayList<OutputConfiguration>(mReaders.size());
        for (Map.Entry<ImageReader, MultiResolutionStreamInfo> e : mReaders.entrySet()) {
            OutputConfiguration cfg = new OutputConfiguration(groupId, e.getKey().getSurface());
            cfg.setPhysicalCameraId(e.getValue().getPhysicalCameraId());
            out.add(cfg);
        }
        return out;
    }
}
