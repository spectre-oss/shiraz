// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Stream, compression, and OpenGL ES upload utilities for {@link ETC1} textures. */
public class ETC1Util {
    public ETC1Util() {}

    /** Container for a compressed {@link ETC1} texture payload and pixel dimensions. */
    public static class ETC1Texture {
        private final int width;
        private final int height;
        private final ByteBuffer data;

        public ETC1Texture(int width, int height, ByteBuffer data) {
            this.width = width;
            this.height = height;
            this.data = data;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public ByteBuffer getData() {
            return data;
        }
    }

    public static void loadTexture(
            int target,
            int level,
            int border,
            int fallbackFormat,
            int fallbackType,
            InputStream input)
            throws IOException {
        loadTexture(target, level, border, fallbackFormat, fallbackType, createTexture(input));
    }

    public static void loadTexture(
            int target,
            int level,
            int border,
            int fallbackFormat,
            int fallbackType,
            ETC1Texture texture) {
        if (fallbackFormat != GLES20.GL_RGB) {
            throw new IllegalArgumentException("fallbackFormat must be GL_RGB");
        }
        if (fallbackType != GLES20.GL_UNSIGNED_SHORT_5_6_5
                && fallbackType != GLES20.GL_UNSIGNED_BYTE) {
            throw new IllegalArgumentException("Unsupported fallbackType");
        }
        int width = texture.getWidth();
        int height = texture.getHeight();
        ByteBuffer data = texture.getData();
        boolean useShorts = fallbackType != GLES20.GL_UNSIGNED_BYTE;
        int pixelSize = useShorts ? 2 : 3;
        int stride = pixelSize * width;
        ByteBuffer decodedData =
                ByteBuffer.allocateDirect(stride * height).order(ByteOrder.nativeOrder());
        ETC1.decodeImage(data, decodedData, width, height, pixelSize, stride);
        GLES20.glTexImage2D(
                target, level, fallbackFormat, width, height, border, fallbackFormat, fallbackType, decodedData);
    }

    public static boolean isETC1Supported() {
        try {
            String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
            return extensions != null && extensions.contains("GL_OES_compressed_ETC1_RGB8_texture");
        } catch (Throwable unavailable) {
            return false;
        }
    }

    public static ETC1Texture createTexture(InputStream input) throws IOException {
        byte[] ioBuffer = new byte[4096];
        if (readFully(input, ioBuffer, 0, ETC1.ETC_PKM_HEADER_SIZE) != ETC1.ETC_PKM_HEADER_SIZE) {
            throw new IOException("Unable to read PKM file header.");
        }
        ByteBuffer headerBuffer =
                ByteBuffer.allocateDirect(ETC1.ETC_PKM_HEADER_SIZE).order(ByteOrder.nativeOrder());
        headerBuffer.put(ioBuffer, 0, ETC1.ETC_PKM_HEADER_SIZE).position(0);
        if (!ETC1.isValid(headerBuffer)) {
            throw new IOException("Not a PKM file.");
        }
        int width = ETC1.getWidth(headerBuffer);
        int height = ETC1.getHeight(headerBuffer);
        int encodedSize = ETC1.getEncodedDataSize(width, height);
        ByteBuffer dataBuffer = ByteBuffer.allocateDirect(encodedSize).order(ByteOrder.nativeOrder());
        int remaining = encodedSize;
        while (remaining > 0) {
            int chunk = Math.min(remaining, ioBuffer.length);
            int bytesRead = readFully(input, ioBuffer, 0, chunk);
            if (bytesRead != chunk) {
                throw new IOException("Unable to read PKM file data.");
            }
            dataBuffer.put(ioBuffer, 0, bytesRead);
            remaining -= bytesRead;
        }
        dataBuffer.position(0);
        return new ETC1Texture(width, height, dataBuffer);
    }

    private static int readFully(InputStream in, byte[] buf, int offset, int length)
            throws IOException {
        int total = 0;
        while (total < length) {
            int n = in.read(buf, offset + total, length - total);
            if (n < 0) {
                break;
            }
            total += n;
        }
        return total;
    }

    public static ETC1Texture compressTexture(
            Buffer input, int width, int height, int pixelSize, int stride) {
        int encodedImageSize = ETC1.getEncodedDataSize(width, height);
        ByteBuffer compressedImage =
                ByteBuffer.allocateDirect(encodedImageSize).order(ByteOrder.nativeOrder());
        ETC1.encodeImage(input, width, height, pixelSize, stride, compressedImage);
        compressedImage.position(0);
        return new ETC1Texture(width, height, compressedImage);
    }

    public static void writeTexture(ETC1Texture texture, OutputStream output) throws IOException {
        ByteBuffer dataBuffer = texture.getData();
        int originalPosition = dataBuffer.position();
        try {
            int width = texture.getWidth();
            int height = texture.getHeight();
            ByteBuffer header =
                    ByteBuffer.allocateDirect(ETC1.ETC_PKM_HEADER_SIZE).order(ByteOrder.nativeOrder());
            ETC1.formatHeader(header, width, height);
            byte[] ioBuffer = new byte[4096];
            header.get(ioBuffer, 0, ETC1.ETC_PKM_HEADER_SIZE);
            output.write(ioBuffer, 0, ETC1.ETC_PKM_HEADER_SIZE);
            int encodedSize = ETC1.getEncodedDataSize(width, height);
            int written = 0;
            while (written < encodedSize) {
                int chunk = Math.min(ioBuffer.length, encodedSize - written);
                dataBuffer.get(ioBuffer, 0, chunk);
                output.write(ioBuffer, 0, chunk);
                written += chunk;
            }
        } finally {
            dataBuffer.position(originalPosition);
        }
    }
}
