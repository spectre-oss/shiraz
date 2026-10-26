// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
import java.util.concurrent.atomic.AtomicReference;
public final class TextSmoke {
    static long descriptors() throws Exception {
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(java.nio.file.Paths.get("/proc/self/fd"))) { return files.count(); }
    }
    static String threadFamily(int size) { return size % 3 == 0 ? "Adwaita Sans" : "monospace"; }
    static void shapingReuse() {
        Bitmap bitmap=Bitmap.createBitmap(240,64,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        String text="office حافظ ۱۲۳";
        for(float size:new float[]{12,24,12.25f}) {
            paint.setTextSize(size);float width=paint.measureText(text);long expected=0;
            for(int repeat=0;repeat<4;repeat++) {
                bitmap.eraseColor(0);paint.setTextSize(size);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);
                paint.setColor(Color.WHITE);canvas.drawText(text,4,40,paint);
                paint.setStyle(Paint.Style.FILL);paint.setColor(Color.BLUE);canvas.drawText(text,4,40,paint);
                long hash=1;for(int y=0;y<64;y++)for(int x=0;x<240;x++)hash=hash*31+bitmap.getPixel(x,y);
                if(repeat==0)expected=hash;else if(hash!=expected)throw new AssertionError("Repeated shaping changed pixels");
                if(paint.measureText(text)!=width)throw new AssertionError("Repeated shaping changed advance");
                // Force a different face size between identical outlined labels.
                paint.setTextSize(size+7);paint.measureText("different");
            }
        }
        bitmap.recycle();
    }
    public static void main(String[] args) throws Exception {
        java.nio.file.Path fontFile = java.nio.file.Paths.get(System.getProperty("user.dir"), ".local/share/shiraz/fonts/NotoSans-Regular.ttf");
        if (!java.nio.file.Files.isRegularFile(fontFile)) throw new AssertionError("Missing Noto Sans fixture font");
        byte[] fontBytes = java.nio.file.Files.readAllBytes(fontFile);
        java.nio.ByteBuffer fontBuffer = java.nio.ByteBuffer.allocate(fontBytes.length); fontBuffer.put(fontBytes).flip();
        FontFamily memoryFamily = new FontFamily();
        if (!memoryFamily.addFontFromBuffer(fontBuffer, 0, null, 400, 0) || !memoryFamily.freeze()) throw new AssertionError("Memory font was rejected");
        Typeface memoryFace = Typeface.createFromFamiliesWithDefault(new FontFamily[]{memoryFamily}, 400, 0);
        Paint memoryPaint = new Paint(Paint.ANTI_ALIAS_FLAG); memoryPaint.setTextSize(32); memoryPaint.setTypeface(memoryFace);
        if (!(memoryPaint.measureText("H") > 0)) throw new AssertionError("Memory font did not measure");
        if (memoryFamily.addFontFromBuffer(fontBuffer, 1, null, 400, 0) || memoryFamily.addFontFromBuffer(fontBuffer, 0, new android.graphics.fonts.FontVariationAxis[]{new android.graphics.fonts.FontVariationAxis("wght", 400f)}, 400, 0)) throw new AssertionError("Unsupported font buffer accepted");
        FontFamily fileFamily = new FontFamily();
        if (!fileFamily.addFontFromAssetManager(null, fontFile.toString(), 0, false, 0, 400, 0, null) || !fileFamily.freeze()) throw new AssertionError("File font was rejected");
        Paint filePaint = new Paint(Paint.ANTI_ALIAS_FLAG); filePaint.setTextSize(32); filePaint.setTypeface(Typeface.createFromFamiliesWithDefault(new FontFamily[]{fileFamily}, 400, 0));
        if (!(filePaint.measureText("H") > 0)) throw new AssertionError("File font did not measure");
        if (fileFamily.addFontFromAssetManager(null, fontFile.toString(), 0, true, 0, 400, 0, null)) throw new AssertionError("Missing asset manager accepted");
        Typeface fileFace = Typeface.createFromFile(fontFile.toFile());
        Paint created = new Paint(Paint.ANTI_ALIAS_FLAG); created.setTextSize(32); created.setTypeface(fileFace);
        if (!(created.measureText("H") > 0)) throw new AssertionError("createFromFile did not measure");
        try { Typeface.createFromFile(fontFile.resolveSibling("missing.ttf").toFile()); throw new AssertionError("Missing font file accepted"); }
        catch (RuntimeException expected) { if (expected.getMessage() == null || !expected.getMessage().contains("Font not found")) throw expected; }
        shapingReuse();
        Bitmap ignored=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);ignored.eraseColor(Color.BLUE);Canvas ignoredCanvas=new Canvas(ignored);Paint ignoredPaint=new Paint();ignoredPaint.setColor(Color.RED);ignoredPaint.setUnderlineText(true);for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}){ignoredCanvas.drawText("text",invalid,8,ignoredPaint);ignoredCanvas.drawText("text",2,invalid,ignoredPaint);ignoredCanvas.save();ignoredCanvas.rotate(30);ignoredCanvas.drawText("text",invalid,8,ignoredPaint);ignoredCanvas.restore();}for(int y=0;y<16;y++)for(int x=0;x<16;x++)if(ignored.getPixel(x,y)!=Color.BLUE)throw new AssertionError("nonfinite text draw changed destination");try{ignoredCanvas.drawText((String)null,Float.NaN,0,ignoredPaint);throw new AssertionError("null text accepted");}catch(NullPointerException expected){}ignored.recycle();

        org.shiraz.fixture.TextDirectionFixture.check();
        org.shiraz.fixture.BoringLayoutFixture.check();
        org.shiraz.fixture.StaticLayoutFixture.check();
        org.shiraz.fixture.TextBoundsFixture.check();
        org.shiraz.fixture.TextScaleFixture.check();
        org.shiraz.fixture.HintingFixture.check();
        org.shiraz.fixture.TextSkewFixture.check();org.shiraz.fixture.FakeBoldFixture.check();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] workers = new Thread[4];
        for (int thread = 0; thread < workers.length; ++thread) {
            final int size = 10 + thread * 8;
            workers[thread] = new Thread(() -> {
                try {
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setTextSize(size); paint.setColor(0x8080ff40);
                    paint.setTypeface(Typeface.create(threadFamily(size), (size / 8) % 4));
                    float width = paint.measureText("café office");
                    if (!(width > 0) || !Float.isFinite(width)) throw new AssertionError("Text width");
                    Bitmap bitmap = Bitmap.createBitmap(160, 64, Bitmap.Config.ARGB_8888);
                    try {
                        Canvas canvas = new Canvas(bitmap);
                        for (int i = 0; i < 64; ++i) {
                            if (paint.measureText("café office") != width) throw new AssertionError("Shared font size changed another thread's metrics");
                            paint.setFakeBoldText((i&1)!=0); bitmap.eraseColor(0); canvas.drawText("O", 2, 48, paint);
                            int ink = 0;
                            for (int y = 0; y < 64; ++y) for (int x = 0; x < 160; ++x) {
                                int alpha = bitmap.getPixel(x, y) >>> 24;
                                if (alpha > 128) throw new AssertionError("Text alpha overflow");
                                if (alpha != 0) ++ink;
                            }
                            if (ink == 0) throw new AssertionError("No text pixels");
                        }
                    } finally { bitmap.recycle(); }
                } catch (Throwable error) { failure.compareAndSet(null, error); }
            });
            workers[thread].start();
        }
        for (Thread worker : workers) worker.join();
        if (failure.get() != null) throw new AssertionError(failure.get());
        Paint paint = new Paint(); paint.setTextSize(24);
        paint.setTypeface(Typeface.MONOSPACE); float stable = paint.measureText("office");
        for (int i = 0; i < 24; ++i) { paint.setTypeface(Typeface.create("Shiraz missing family " + i, i % 4)); paint.measureText("office"); }
        long open = descriptors();
        for (int i = 24; i < 88; ++i) { paint.setTypeface(Typeface.create("Shiraz missing family " + i, i % 4)); paint.measureText("office"); }
        if (descriptors() != open) throw new AssertionError("Evicted font descriptors leaked");
        paint.setTypeface(Typeface.MONOSPACE); if (paint.measureText("office") != stable) throw new AssertionError("Evicted font changed metrics");
        paint.setTypeface(Typeface.create("Uninstalled supplementary family \ud83d\ude80", 0));
        if (!(paint.measureText("office") > 0)) throw new AssertionError("Unicode family fallback");
        try { shiraz.runtime.NativeText.measureFont("x", 20, "bad\u0000family", 0); throw new AssertionError("NUL family accepted"); }
        catch (IllegalArgumentException expected) { }
        try { shiraz.runtime.NativeText.measureFont("x", 20, "bad\ud800", 0); throw new AssertionError("Malformed UTF-16 family accepted"); }
        catch (IllegalArgumentException expected) { }
        int[] pixel={0xff123456};
        if(shiraz.runtime.NativeText.measureFont("office",0,"monospace",0)!=0)throw new AssertionError("Native zero-size advance");
        for(float metric:shiraz.runtime.NativeText.metricsFont(0,"monospace",0))if(metric!=0)throw new AssertionError("Native zero-size metrics");
        shiraz.runtime.NativeText.drawFontClippedStyled(pixel,1,1,"office",0,0,0,0xffff0000,true,"monospace",0,0,0,1,1,true);
        if(pixel[0]!=0xff123456)throw new AssertionError("Native zero-size draw changed destination");
        try { shiraz.runtime.NativeText.drawFontClipped(pixel,1,1,"x",0,0,16,0xffff0000,true,"sans-serif",0,-1,0,1,1); throw new AssertionError("negative text clip accepted"); }
        catch (IllegalArgumentException expected) { }
        try { shiraz.runtime.NativeText.drawFontClipped(pixel,1,1,"x",0,0,16,0xffff0000,true,"sans-serif",0,0,0,2,1); throw new AssertionError("outside text clip accepted"); }
        catch (IllegalArgumentException expected) { }
        try { shiraz.runtime.NativeText.drawFontClippedStyled(pixel,1,1,"x",0,0,16,0xffff0000,true,"sans-serif",0,0,0,2,1,true); throw new AssertionError("outside bold text clip accepted"); }
        catch (IllegalArgumentException expected) { }
        if (pixel[0] != 0xff123456) throw new AssertionError("failed text clip mutated pixels");
        System.out.println("TEXT_CONCURRENCY_AND_FONT_SELECTION_OK");
    }
}
