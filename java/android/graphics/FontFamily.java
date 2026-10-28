// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import android.content.res.AssetManager;
import android.graphics.fonts.FontVariationAxis;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
/** One in-memory font face for the API 26 TypefaceCompat probe. */
public final class FontFamily {
    static { System.loadLibrary("shiraz-text"); }
    private int id;
    private boolean frozen;
    public FontFamily() {}
    public boolean addFontFromAssetManager(AssetManager manager, String path, int cookie, boolean isAsset, int ttcIndex, int weight, int italic, FontVariationAxis[] axes) {
        if (frozen || id != 0 || path == null || cookie < 0 || ttcIndex != 0 || axes != null) return false;
        if (isAsset && manager == null) return false;
        try {
            byte[] bytes = isAsset ? readStream(manager.open(path)) : manager != null ? readStream(manager.openNonAsset(cookie,path,AssetManager.ACCESS_BUFFER)) : readFile(path);
            if (bytes == null) return false;
            id = nativeRegister(bytes);
            return id > 0;
        } catch (IOException | IllegalArgumentException failure) {
            return false;
        }
    }
    private static byte[] readFile(String path) throws IOException {
        if (path != null && path.startsWith("/system/fonts/")) {
            String dir = System.getenv("SHIRAZ_FONT_DIR");
            if (dir == null || dir.isEmpty()) dir = System.getenv("SHIRAZ_ANDROID_FONT_DIR");
            if (dir != null && !dir.isEmpty()) path = dir + "/" + path.substring("/system/fonts/".length());
        }
        Path file = Paths.get(path);
        if (!Files.isRegularFile(file)) return null;
        long size = Files.size(file);
        if (size < 1 || size > 16 * 1024 * 1024) return null;
        return Files.readAllBytes(file);
    }
    private static byte[] readStream(InputStream stream) throws IOException {
        try (InputStream input = stream) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) >= 0) {
                if (output.size() > 16 * 1024 * 1024 - count) return null;
                output.write(chunk, 0, count);
            }
            return output.size() == 0 ? null : output.toByteArray();
        }
    }
    public boolean addFontFromBuffer(ByteBuffer buffer, int ttcIndex, FontVariationAxis[] axes, int weight, int italic) {
        if (frozen || id != 0 || buffer == null || ttcIndex != 0 || axes != null) return false;
        ByteBuffer copy = buffer.duplicate();
        int count = copy.remaining();
        if (count < 1 || count > 16 * 1024 * 1024) return false;
        byte[] bytes = new byte[count];
        copy.get(bytes);
        id = nativeRegister(bytes);
        return id > 0;
    }
    public boolean freeze() { if (id <= 0 || frozen) return false; frozen = true; return true; }
    public void abortCreation() { frozen = false; id = 0; }
    int frozenId() { return frozen ? id : 0; }
    private static native int nativeRegister(byte[] bytes);
}
