// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import dalvik.system.CloseGuard;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import shiraz.runtime.NativeFd;
import shiraz.runtime.NativeSound;

/** Short effects decoded by libsndfile and mixed to a Linux ALSA PCM. */
public class SoundPool {
    public interface OnLoadCompleteListener { void onLoadComplete(SoundPool pool, int sampleId, int status); }
    private static final Set<SoundPool> POOLS = Collections.newSetFromMap(new WeakHashMap<SoundPool, Boolean>());
    private final Set<Integer> samples = new HashSet<Integer>();
    private final ThreadPoolExecutor loader;
    private final Object releaseLock = new Object();
    private final CloseGuard guard = CloseGuard.get();
    private long handle;
    private long nextSample = 1;
    private int pendingBytes;
    private Handler handler;
    private OnLoadCompleteListener listener;
    private SoundPool(int streams, AudioAttributes attributes) {
        Objects.requireNonNull(attributes, "attributes");
        if (attributes.getFlags() != 0) throw new UnsupportedOperationException("SoundPool output flags are not implemented");
        loader = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(8), new ThreadFactory() {
            public Thread newThread(Runnable task) { return new Thread(task, "Shiraz-SoundPool-loader"); }
        });
        handle = NativeSound.create(streams); guard.open("release");
        synchronized (POOLS) { POOLS.add(this); }
    }
    public SoundPool(int maxStreams, int streamType, int srcQuality) {
        this(maxStreams, new AudioAttributes.Builder().setLegacyStreamType(streamType).build());
    }
    public static class Builder {
        private int streams = 1;
        private AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build();
        public Builder() { }
        public Builder setMaxStreams(int count) { if (count < 1 || count > 64) throw new IllegalArgumentException("SoundPool supports 1–64 streams"); streams = count; return this; }
        public Builder setAudioAttributes(AudioAttributes value) { attributes = Objects.requireNonNull(value); return this; }
        public SoundPool build() { return new SoundPool(streams, attributes); }
    }
    private void requireOpen() { if (handle == 0) throw new IllegalStateException("SoundPool is released"); }
    public synchronized void setOnLoadCompleteListener(OnLoadCompleteListener value) {
        requireOpen(); listener = value;
        Looper looper = Looper.myLooper(); if (looper == null) looper = Looper.getMainLooper();
        handler = value == null || looper == null ? null : new Handler(looper);
    }
    private synchronized int enqueue(final byte[] encoded) {
        requireOpen();
        if (encoded.length == 0 || encoded.length > 16 * 1024 * 1024 || pendingBytes > 32 * 1024 * 1024 - encoded.length
                || samples.size() >= 64 || nextSample > Integer.MAX_VALUE) return 0;
        final int id = (int)nextSample++; final long owner = handle;
        if (!NativeSound.reserve(owner, id)) return 0;
        samples.add(id); pendingBytes += encoded.length;
        try {
            loader.execute(new Runnable() { public void run() {
                boolean success = false;
                try { success = NativeSound.load(owner, id, encoded); }
                catch (IllegalArgumentException error) { System.err.println("Shiraz audio: sound decode failed: " + error.getMessage()); }
                catch (IllegalStateException error) { synchronized (SoundPool.this) { if (handle != 0) throw error; } }
                finally {
                    synchronized (SoundPool.this) { pendingBytes -= encoded.length; }
                }
                synchronized (SoundPool.this) {
                    if (handle == 0 || !samples.contains(id)) return;
                    if (!success) NativeSound.unload(owner, id);
                    final int status = success ? 0 : 1;
                    if (handler != null) handler.post(new Runnable() { public void run() {
                        OnLoadCompleteListener target;
                        synchronized (SoundPool.this) { target = handle != 0 && samples.contains(id) ? listener : null; }
                        if (target != null) target.onLoadComplete(SoundPool.this, id, status);
                    } });
                }
            } });
        } catch (RejectedExecutionException rejected) {
            samples.remove(id); pendingBytes -= encoded.length; NativeSound.unload(owner, id); return 0;
        }
        return id;
    }
    public int load(AssetFileDescriptor asset, int priority) {
        Objects.requireNonNull(asset, "asset");
        try { return enqueue(asset.shirazReadRegion()); }
        catch (IOException error) { System.err.println("Shiraz audio: cannot read sound asset: " + error.getMessage()); return 0; }
    }
    public int load(FileDescriptor descriptor, long offset, long length, int priority) {
        Objects.requireNonNull(descriptor, "descriptor");
        try {
            synchronized (descriptor) {
                dalvik.system.BlockGuard.getThreadPolicy().onReadFromDisk();
                return enqueue(NativeFd.readRegion(descriptor, offset, length));
            }
        } catch (IOException error) { System.err.println("Shiraz audio: cannot read sound region: " + error.getMessage()); return 0; }
    }
    public int load(String path, int priority) {
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
                AssetFileDescriptor asset = new AssetFileDescriptor(fd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)) {
            return load(asset, priority);
        } catch (IOException error) { System.err.println("Shiraz audio: cannot open sound: " + error.getMessage()); return 0; }
    }
    public int load(Context context, int resourceId, int priority) {
        Objects.requireNonNull(context, "context");
        try (java.io.InputStream in = context.getResources().openRawResource(resourceId)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return enqueue(out.toByteArray());
        } catch (IOException | android.content.res.Resources.NotFoundException error) {
            System.err.println("Shiraz audio: cannot read raw sound: " + error.getMessage());
            return 0;
        }
    }
    public synchronized boolean unload(int sound) { requireOpen(); if (!samples.remove(sound)) return false; NativeSound.unload(handle, sound); return true; }
    public synchronized int play(int sound, float left, float right, int priority, int loop, float rate) {
        requireOpen(); return NativeSound.play(handle, sound, left, right, priority, loop, rate);
    }
    private synchronized void control(int stream, int operation, float a, float b, int value) { requireOpen(); NativeSound.control(handle, stream, operation, a, b, value); }
    public final void stop(int stream) { control(stream, 0, 0, 0, 0); }
    public final void pause(int stream) { control(stream, 1, 0, 0, 0); }
    public final void resume(int stream) { control(stream, 2, 0, 0, 0); }
    public final void autoPause() { control(0, 3, 0, 0, 0); }
    public final void autoResume() { control(0, 4, 0, 0, 0); }
    public final void setVolume(int stream, float left, float right) { control(stream, 5, left, right, 0); }
    public final void setRate(int stream, float rate) { control(stream, 6, rate, 0, 0); }
    public final void setLoop(int stream, int loops) { control(stream, 7, 0, 0, loops); }
    public final void setPriority(int stream, int priority) { control(stream, 8, 0, 0, priority); }
    public final void release() { synchronized (releaseLock) { releaseOwned(); } }
    private void releaseOwned() {
        long owner;
        synchronized (this) {
            if (handle == 0) return;
            owner = handle; handle = 0; listener = null;
            if (handler != null) handler.removeCallbacksAndMessages(null);
            handler = null; samples.clear(); guard.close();
        }
        synchronized (POOLS) { POOLS.remove(this); }
        loader.shutdownNow();
        try { NativeSound.release(owner); }
        finally {
            boolean interrupted = false;
            for (;;) {
                try { if (loader.awaitTermination(100, TimeUnit.MILLISECONDS)) break; }
                catch (InterruptedException error) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
    @Override protected void finalize() throws Throwable {
        try { if (guard != null) guard.warnIfOpen(); if (handle != 0) release(); }
        finally { super.finalize(); }
    }
    public static void shirazShutdown() {
        SoundPool[] active; synchronized (POOLS) { active = POOLS.toArray(new SoundPool[0]); }
        for (SoundPool pool : active) pool.release();
    }
}
