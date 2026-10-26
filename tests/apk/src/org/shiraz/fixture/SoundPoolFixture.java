// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Looper;

final class SoundPoolFixture {
    static void start(Context context) throws Exception {
        final SoundPool pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        final int[] expected = new int[2];
        final boolean[] completed = new boolean[2];
        pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            public void onLoadComplete(SoundPool source, int id, int status) {
                if (source != pool || Looper.myLooper() != Looper.getMainLooper()) throw new AssertionError("Sound callback thread/owner");
                if (id == expected[0]) {
                    if (status != 0 || completed[0]) throw new AssertionError("Sound decode completion");
                    int stream = pool.play(id, .5f, .25f, 2, 1, 1);
                    if (stream <= 0) throw new AssertionError("Explicit test PCM rejected playback");
                    pool.pause(stream); pool.setVolume(stream, .25f, .5f); pool.setRate(stream, 2); pool.setLoop(stream, 0);
                    pool.setPriority(stream, 3); pool.resume(stream); pool.autoPause(); pool.autoResume(); pool.stop(stream);
                    if (pool.play(Integer.MAX_VALUE, 1, 1, 0, 0, 1) != 0) throw new AssertionError("Unknown sound accepted");
                    completed[0] = true;
                } else if (id == expected[1]) {
                    if (status == 0 || completed[1] || pool.play(id, 1, 1, 0, 0, 1) != 0) throw new AssertionError("Malformed sound accepted");
                    completed[1] = true;
                } else throw new AssertionError("Unexpected sound callback");
                if (completed[0] && completed[1]) {
                    if (!pool.unload(expected[0]) || pool.unload(expected[0])) throw new AssertionError("Sound unload ownership");
                    pool.release(); pool.release();
                    try { pool.play(id, 1, 1, 0, 0, 1); throw new AssertionError("Released SoundPool accepted playback"); }
                    catch (IllegalStateException good) { }
                    System.out.println("SHIRAZ_FIXTURE_SOUNDPOOL_OK");
                }
            }
        });
        try (AssetFileDescriptor sound = context.getAssets().openFd("effect.wav")) { expected[0] = pool.load(sound, 1); }
        try (AssetFileDescriptor invalid = context.getAssets().openFd("region.txt")) { expected[1] = pool.load(invalid, 1); }
        if (expected[0] <= 0 || expected[1] <= 0 || expected[0] == expected[1]) throw new AssertionError("Sound sample IDs");
        // No listener is needed to load; teardown must still cancel/join active loaders.
        SoundPool orphan = new SoundPool.Builder().build();
        try (AssetFileDescriptor sound = context.getAssets().openFd("effect.wav")) {
            int sample = orphan.load(sound, 1); if (sample <= 0 || !orphan.unload(sample)) throw new AssertionError("Pending sound unload");
        }
        orphan.release();
    }
}
