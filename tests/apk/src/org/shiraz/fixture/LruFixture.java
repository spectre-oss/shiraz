// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.util.LruCache;
import android.graphics.Bitmap;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Stock-SDK cache tests, including app-style resource weights and concurrent creators. */
public final class LruFixture {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void await(CountDownLatch latch) { try { if (!latch.await(3,TimeUnit.SECONDS)) throw new AssertionError("cache creation deadlocked"); } catch (InterruptedException error) { throw new AssertionError(error); } }
    private static class Weighted extends LruCache<String,String> {
        final List<String> removed = new ArrayList<String>();
        Weighted(int maximum) { super(maximum); }
        @Override protected int sizeOf(String key, String value) { return value.length(); }
        @Override protected void entryRemoved(boolean evicted, String key, String oldValue, String newValue) { check(!Thread.holdsLock(this), "removal callback under cache monitor"); removed.add(evicted + ":" + key + ":" + oldValue + ":" + newValue); }
    }
    public static void check() throws Exception {
        Weighted cache = new Weighted(4); cache.put("a","aa"); cache.put("b","bb"); check("aa".equals(cache.get("a")), "cached value"); cache.put("c","cc");
        check(cache.get("b") == null && new ArrayList<String>(cache.snapshot().keySet()).equals(Arrays.asList("a","c")), "LRU eviction order");
        check(cache.size() == 4 && cache.hitCount() == 1 && cache.missCount() == 1 && cache.putCount() == 3 && cache.evictionCount() == 1, "weighted cache accounting");
        Map<String,String> snapshot = cache.snapshot(); snapshot.clear(); check(cache.size() == 4, "detached snapshot");
        cache.resize(2); check(cache.get("a") == null && cache.size() == 2 && cache.maxSize() == 2, "resize evicts oldest weight");
        check("cc".equals(cache.put("c","x")) && cache.size() == 1 && cache.removed.contains("false:c:cc:x"), "replacement notification");
        check("x".equals(cache.remove("c")) && cache.size() == 0 && cache.removed.contains("false:c:x:null"), "explicit removal");
        cache.put("too large","oversized"); check(cache.size() == 0 && cache.snapshot().isEmpty(), "oversize item is immediately evicted");
        try { cache.put(null,"x"); throw new AssertionError("null cache key"); } catch (NullPointerException expected) { }
        try { cache.put("x",null); throw new AssertionError("null cache value"); } catch (NullPointerException expected) { }
        try { cache.resize(0); throw new AssertionError("nonpositive cache size"); } catch (IllegalArgumentException expected) { }
        check(cache.maxSize() == 2, "failed resize changed budget");
        LruCache<String,Bitmap> images = new LruCache<String,Bitmap>(16) { @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); } };
        Bitmap first = Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888), second = Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888), full = Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);
        images.put("first",first); images.put("second",second); images.get("first"); images.put("full",full);
        check(images.size() == 16 && images.snapshot().size() == 1 && images.get("full") == full && images.evictionCount() == 2, "bitmap byte budget evicts prior images");
        images.evictAll(); first.recycle(); second.recycle(); full.recycle();
        final LruCache<String,String> reentrant = new LruCache<String,String>(2) {
            @Override protected void entryRemoved(boolean evicted, String key, String oldValue, String newValue) { if (evicted && key.equals("a")) put("d","d"); }
        };
        reentrant.put("a","a"); reentrant.put("b","b"); reentrant.put("c","c");
        check(new ArrayList<String>(reentrant.snapshot().keySet()).equals(Arrays.asList("c","d")) && reentrant.evictionCount() == 2, "removal callback can reenter and trim the cache");
        LruCache<String,String> zero = new LruCache<String,String>(1) { @Override protected int sizeOf(String key, String value) { return 0; } };
        zero.put("a","a"); zero.put("b","b"); zero.trimToSize(0); check(zero.snapshot().size() == 2, "zero-weight trim"); zero.evictAll(); check(zero.snapshot().isEmpty() && zero.evictionCount() == 2, "evictAll includes zero-weight values");
        LruCache<String,String> negative = new LruCache<String,String>(2) { @Override protected int sizeOf(String key, String value) { return value.equals("bad") ? -1 : 1; } };
        negative.put("healthy","good"); try { negative.put("rejected","bad"); throw new AssertionError("negative cache weight"); } catch (IllegalStateException expected) { }
        check(negative.size() == 1 && !negative.snapshot().containsKey("rejected"), "failed weighting partially published value");
        LruCache<String,Integer> wide = new LruCache<String,Integer>(Integer.MAX_VALUE) { @Override protected int sizeOf(String key, Integer value) { return value; } };
        wide.put("huge",Integer.MAX_VALUE); wide.put("small",1);
        check(wide.size() == 1 && wide.evictionCount() == 1 && wide.snapshot().containsKey("small"), "weighted addition must not wrap before trimming");
        final AtomicInteger dynamic = new AtomicInteger(1); LruCache<String,String> inconsistent = new LruCache<String,String>(2) { @Override protected int sizeOf(String key, String value) { return dynamic.get(); } };
        inconsistent.put("changed","value"); dynamic.set(2); try { inconsistent.evictAll(); throw new AssertionError("changing cached size accepted"); } catch (IllegalStateException expected) { }
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1); final AtomicReference<String> result = new AtomicReference<String>(); final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final Weighted racing = new Weighted(16) { @Override protected String create(String key) { check(!Thread.holdsLock(this), "create callback under cache monitor"); entered.countDown(); await(release); return "created"; } };
        Thread worker = new Thread(new Runnable() { public void run() { try { result.set(racing.get("race")); } catch (Throwable problem) { error.set(problem); } } }); worker.start();
        try { await(entered); racing.put("race","winner"); } finally { release.countDown(); }
        worker.join(3000); check(!worker.isAlive() && error.get() == null && "winner".equals(result.get()), "create/put conflict returns the cached winner");
        check(racing.createCount() == 1 && racing.putCount() == 1 && racing.removed.contains("false:race:created:winner"), "discarded created value callback");
        final CountDownLatch both = new CountDownLatch(2), gate = new CountDownLatch(1); final AtomicInteger serial = new AtomicInteger(); final List<String> answers = Collections.synchronizedList(new ArrayList<String>());
        final LruCache<String,String> creators = new LruCache<String,String>(2) {
            @Override protected String create(String key) { int id = serial.incrementAndGet(); both.countDown(); await(gate); return "created-" + id; }
        };
        Runnable load = new Runnable() { public void run() { try { answers.add(creators.get("same")); } catch (Throwable problem) { error.set(problem); } } };
        Thread one = new Thread(load), two = new Thread(load); one.start(); two.start();
        try { await(both); } finally { gate.countDown(); } one.join(3000); two.join(3000);
        check(!one.isAlive() && !two.isAlive() && error.get() == null && answers.size() == 2 && answers.get(0).equals(answers.get(1)) && creators.createCount() == 2 && creators.missCount() == 2 && creators.size() == 1, "concurrent creators share one retained value");
        final AssertionError marker = new AssertionError("cache callback failure");
        LruCache<String,String> failing = new LruCache<String,String>(2) { @Override protected void entryRemoved(boolean evicted, String key, String oldValue, String newValue) { throw marker; } };
        failing.put("key","old"); try { failing.put("key","new"); throw new AssertionError("cache callback swallowed"); } catch (AssertionError expected) { check(expected == marker, "original removal exception"); }
        check("new".equals(failing.get("key")) && failing.size() == 1, "callback failure preserves committed cache state");
        LruCache<String,String> counted = new LruCache<String,String>(1); counted.put("a","a"); counted.get("a"); counted.get("missing");
        check("LruCache[maxSize=1,hits=1,misses=1,hitRate=50%]".equals(counted.toString()), "cache statistics rendering");
        System.out.println("SHIRAZ_FIXTURE_LRU_CACHE_OK");
    }
}
