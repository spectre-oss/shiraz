// SPDX-License-Identifier: GPL-3.0-only
import android.animation.Animator;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.slice.Slice;
import android.app.slice.SliceItem;
import android.app.slice.SliceSpec;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextParams;
import android.content.Intent;
import android.credentials.CreateCredentialRequest;
import android.credentials.Credential;
import android.credentials.CredentialOption;
import android.credentials.GetCredentialException;
import android.credentials.GetCredentialRequest;
import android.drm.DrmConvertedStatus;
import android.drm.DrmInfo;
import android.drm.DrmInfoRequest;
import android.drm.DrmRights;
import android.drm.DrmStore;
import android.drm.DrmSupportInfo;
import android.drm.DrmUtils;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.GesturePoint;
import android.gesture.GestureStroke;
import android.gesture.GestureUtils;
import android.gesture.OrientedBoundingBox;
import android.gesture.Prediction;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.hardware.SyncFence;
import android.hardware.usb.UsbConstants;
import android.mtp.MtpConstants;
import android.mtp.MtpObjectInfo;
import android.net.Uri;
import android.opengl.ETC1;
import android.opengl.ETC1Util;
import android.opengl.GLU;
import android.opengl.Matrix;
import android.opengl.Visibility;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentInfo;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.print.pdf.PrintedPdfDocument;
import android.speech.AlternativeSpan;
import android.speech.AlternativeSpans;
import android.speech.RecognitionPart;
import android.speech.RecognitionSupport;
import android.transition.ArcMotion;
import android.transition.AutoTransition;
import android.transition.ChangeBounds;
import android.transition.ChangeClipBounds;
import android.transition.ChangeScroll;
import android.transition.ChangeTransform;
import android.transition.CircularPropagation;
import android.transition.Fade;
import android.transition.PatternPathMotion;
import android.transition.Scene;
import android.transition.SidePropagation;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.TransitionValues;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.window.BackEvent;
import android.window.InputTransferToken;
import android.window.TrustedPresentationThresholds;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public final class FrameworkMilestoneF3Smoke {
    private static Context createSmokeContext(final File tempDir) {
        return new android.content.ContextWrapper(null) {
            private final android.content.res.Resources.Theme theme =
                    android.content.res.Resources.getSystem().newTheme();

            @Override
            public android.content.res.Resources getResources() {
                return android.content.res.Resources.getSystem();
            }

            @Override
            public android.content.res.Resources.Theme getTheme() {
                return theme;
            }

            @Override
            public String getPackageName() {
                return "org.shiraz.smoke";
            }

            @Override
            public File getFilesDir() {
                return tempDir;
            }

            @Override
            public File getCacheDir() {
                return tempDir;
            }
        };
    }
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T extends Parcelable> T roundTripParcel(T input, Parcelable.Creator<T> creator) {
        Parcel p = Parcel.obtain();
        try {
            input.writeToParcel(p, 0);
            p.setDataPosition(0);
            return creator.createFromParcel(p);
        } finally {
            p.recycle();
        }
    }

    private static void verifyTransitions(Context ctx) {
        ArcMotion arc = new ArcMotion();
        arc.setMinimumHorizontalAngle(15f);
        arc.setMinimumVerticalAngle(10f);
        arc.setMaximumAngle(70f);
        Path arcPath = arc.getPath(10f, 20f, 110f, 120f);
        PathMeasure pm = new PathMeasure(arcPath, false);
        check(pm.getLength() > 140f, "ArcMotion curved path length");
        float[] endPos = new float[2];
        pm.getPosTan(pm.getLength(), endPos, null);
        check(Math.abs(endPos[0] - 110f) < 1f && Math.abs(endPos[1] - 120f) < 1f, "ArcMotion end");

        Path template = new Path();
        template.moveTo(0f, 0f);
        template.lineTo(0.5f, 0.25f);
        template.lineTo(1f, 0f);
        PatternPathMotion pattern = new PatternPathMotion(template);
        Path mapped = pattern.getPath(0f, 0f, 200f, 0f);
        PathMeasure pm2 = new PathMeasure(mapped, false);
        check(pm2.getLength() > 200f, "PatternPathMotion scaled path");

        FrameLayout root = new FrameLayout(ctx);
        root.layout(0, 0, 400, 300);
        View child = new View(ctx);
        child.setId(42);
        child.setTransitionName("hero");
        root.addView(child);
        child.layout(10, 20, 110, 80);
        child.setScrollX(5);
        child.setScrollY(10);
        child.setScaleX(1.0f);
        child.setRotation(0f);
        child.setClipBounds(new Rect(0, 0, 50, 30));

        ChangeBounds cb = new ChangeBounds();
        cb.setResizeClip(true);
        TransitionValues sBounds = new TransitionValues(child);
        cb.captureStartValues(sBounds);
        child.layout(50, 60, 250, 180);
        child.setClipBounds(new Rect(0, 0, 100, 60));
        TransitionValues eBounds = new TransitionValues(child);
        cb.captureEndValues(eBounds);
        Animator cbAnim = cb.createAnimator(root, sBounds, eBounds);
        check(cbAnim != null, "ChangeBounds animator created");

        ChangeClipBounds ccb = new ChangeClipBounds();
        Animator ccbAnim = ccb.createAnimator(root, sBounds, eBounds);
        check(ccbAnim == null, "ChangeClipBounds uses own keys");
        TransitionValues sc = new TransitionValues(child);
        child.setClipBounds(new Rect(0, 0, 20, 20));
        ccb.captureStartValues(sc);
        child.setClipBounds(new Rect(0, 0, 80, 80));
        TransitionValues ec = new TransitionValues(child);
        ccb.captureEndValues(ec);
        check(ccb.createAnimator(root, sc, ec) != null, "ChangeClipBounds animator created");

        ChangeScroll cs = new ChangeScroll();
        TransitionValues ss = new TransitionValues(child);
        cs.captureStartValues(ss);
        child.setScrollX(45);
        child.setScrollY(90);
        TransitionValues es = new TransitionValues(child);
        cs.captureEndValues(es);
        check(cs.createAnimator(root, ss, es) != null, "ChangeScroll animator created");

        ChangeTransform ct = new ChangeTransform();
        TransitionValues st = new TransitionValues(child);
        ct.captureStartValues(st);
        child.setScaleX(2.0f);
        child.setRotation(45f);
        TransitionValues et = new TransitionValues(child);
        ct.captureEndValues(et);
        check(ct.createAnimator(root, st, et) != null, "ChangeTransform animator created");

        Fade fade = new Fade(Fade.IN | Fade.OUT);
        fade.setEpicenterCallback(new Transition.EpicenterCallback() {
            @Override
            public Rect onGetEpicenter(Transition transition) {
                return new Rect(0, 0, 20, 20);
            }
        });
        CircularPropagation cp = new CircularPropagation();
        cp.captureValues(et);
        long delay = cp.getStartDelay(root, fade, null, et);
        check(delay >= 0L, "CircularPropagation start delay");

        SidePropagation sp = new SidePropagation();
        sp.setSide(Gravity.BOTTOM);
        sp.captureValues(et);
        check(sp.getStartDelay(root, new Slide(Gravity.BOTTOM), null, et) >= 0L, "SidePropagation delay");

        AutoTransition auto = new AutoTransition();
        check(auto.getTransitionCount() == 3, "AutoTransition count == 3");

        final AtomicInteger sceneEntered = new AtomicInteger(0);
        Scene scene = new Scene(root);
        scene.setEnterAction(new Runnable() {
            @Override
            public void run() {
                sceneEntered.incrementAndGet();
            }
        });
        scene.enter();
        check(sceneEntered.get() == 1 && Scene.getCurrentScene(root) == scene, "Scene enter & current");
    }

    private static void verifyOpenGlMathAndEtc1() throws Exception {
        float[] proj = new float[16];
        float[] view = new float[16];
        float[] mvp = new float[16];
        float[] inv = new float[16];
        float[] prod = new float[16];

        Matrix.perspectiveM(proj, 0, 60f, 16f / 9f, 0.5f, 100f);
        Matrix.setLookAtM(view, 0, 0f, 1f, 5f, 0f, 0f, 0f, 0f, 1f, 0f);
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0);
        check(Matrix.invertM(inv, 0, mvp, 0), "Matrix.invertM invertible");
        Matrix.multiplyMM(prod, 0, mvp, 0, inv, 0);
        check(Math.abs(prod[0] - 1f) < 1e-4f && Math.abs(prod[5] - 1f) < 1e-4f
                && Math.abs(prod[10] - 1f) < 1e-4f && Math.abs(prod[15] - 1f) < 1e-4f,
                "M * M^-1 == Identity");

        int[] viewport = new int[] {0, 0, 1920, 1080};
        float[] win = new float[3];
        float[] unproj = new float[3];
        check(GLU.gluProject(1.25f, -0.75f, -3.5f, view, 0, proj, 0, viewport, 0, win, 0) == 1,
                "GLU.gluProject success");
        check(GLU.gluUnProject(win[0], win[1], win[2], view, 0, proj, 0, viewport, 0, unproj, 0) == 1,
                "GLU.gluUnProject success");
        check(Math.abs(unproj[0] - 1.25f) < 1e-3f
                && Math.abs(unproj[1] + 0.75f) < 1e-3f
                && Math.abs(unproj[2] + 3.5f) < 1e-3f,
                "GLU project/unproject round-trip");

        float[] verts = new float[] {
            -0.5f, -0.5f, 0f,
             0.5f, -0.5f, 0f,
             0.0f,  0.5f, 0f
        };
        float[] sphere = new float[4];
        Visibility.computeBoundingSphere(verts, 0, 3, sphere, 0);
        check(sphere[3] > 0.49f && sphere[3] < 0.75f, "Bounding sphere radius");

        float[] spheres = new float[] {
            0f, 0f, 0f, 0.5f,      // visible in front of camera at z=5 looking toward z=0
            0f, 0f, 25f, 0.5f      // behind camera (eyeZ=5 looking toward -Z)
        };
        int[] culled = new int[2];
        int visCount = Visibility.frustumCullSpheres(mvp, 0, spheres, 0, 2, culled, 0, 2);
        check(visCount == 1 && culled[0] == 0, "frustumCullSpheres keeps front sphere, culls behind");

        char[] triIdx = new char[] {0, 1, 2};
        int triVis = Visibility.visibilityTest(mvp, 0, verts, 0, triIdx, 0, 3);
        check(triVis == 2, "visibilityTest triangle completely inside frustum");

        // ETC1 + ETC1Util round-trip
        int w = 8, h = 8;
        ByteBuffer rawRgb = ByteBuffer.allocateDirect(w * h * 3).order(ByteOrder.nativeOrder());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                rawRgb.put((byte) 200);
                rawRgb.put((byte) 100);
                rawRgb.put((byte) 50);
            }
        }
        rawRgb.position(0);
        ETC1Util.ETC1Texture tex = ETC1Util.compressTexture(rawRgb, w, h, 3, w * 3);
        check(tex.getWidth() == 8 && tex.getHeight() == 8, "ETC1Texture dimensions");
        ByteArrayOutputStream pkmOut = new ByteArrayOutputStream();
        ETC1Util.writeTexture(tex, pkmOut);
        byte[] pkmBytes = pkmOut.toByteArray();
        check(pkmBytes.length == ETC1.ETC_PKM_HEADER_SIZE + ETC1.getEncodedDataSize(w, h), "PKM byte size");

        ETC1Util.ETC1Texture loaded = ETC1Util.createTexture(new ByteArrayInputStream(pkmBytes));
        check(loaded.getWidth() == 8 && loaded.getHeight() == 8, "PKM loaded dimensions");
        ByteBuffer decoded = ByteBuffer.allocateDirect(w * h * 3).order(ByteOrder.nativeOrder());
        ETC1.decodeImage(loaded.getData(), decoded, w, h, 3, w * 3);
        int dr = decoded.get(0) & 0xFF;
        int dg = decoded.get(1) & 0xFF;
        int db = decoded.get(2) & 0xFF;
        check(Math.abs(dr - 200) < 20 && Math.abs(dg - 100) < 20 && Math.abs(db - 50) < 20,
                "ETC1 decoded RGB fidelity");
    }

    private static void verifyWaveW1(Context ctx) throws Exception {
        BackEvent back = new BackEvent(120f, 340f, 0.45f, BackEvent.EDGE_LEFT);
        check(back.getTouchX() == 120f && back.getTouchY() == 340f
                && Math.abs(back.getProgress() - 0.45f) < 1e-5f
                && back.getSwipeEdge() == BackEvent.EDGE_LEFT, "BackEvent getters");

        InputTransferToken itt = new InputTransferToken();
        InputTransferToken ittOut = roundTripParcel(itt, InputTransferToken.CREATOR);
        check(ittOut != null, "InputTransferToken Parcel round-trip");

        TrustedPresentationThresholds tpt = new TrustedPresentationThresholds(0.5f, 0.8f, 250);
        TrustedPresentationThresholds tptOut = roundTripParcel(tpt, TrustedPresentationThresholds.CREATOR);
        check(Math.abs(tptOut.getMinAlpha() - 0.5f) < 1e-5f
                && Math.abs(tptOut.getMinFractionRendered() - 0.8f) < 1e-5f
                && tptOut.getStabilityRequirementMillis() == 250,
                "TrustedPresentationThresholds Parcel round-trip");

        ColorSpace srgb = ColorSpace.get(ColorSpace.Named.SRGB);
        ColorSpace.Rgb p3 = new ColorSpace.Rgb(
                "Display P3",
                new float[] {0.680f, 0.320f, 0.265f, 0.690f, 0.150f, 0.060f},
                ColorSpace.ILLUMINANT_D65,
                new ColorSpace.Rgb.TransferParameters(
                        1.0 / 1.055, 0.055 / 1.055, 1.0 / 12.92, 0.04045, 2.4));
        ColorSpace.Connector connector = ColorSpace.connect(srgb, p3);
        float[] transformed = connector.transform(1.0f, 0.0f, 0.0f);
        check(transformed.length == 3 && transformed[0] > 0.75f && transformed[0] <= 1.01f,
                "ColorSpace.Connector SRGB->DisplayP3 transform");

        final AtomicInteger widgetUpdates = new AtomicInteger(0);
        AppWidgetProvider provider = new AppWidgetProvider() {
            @Override
            public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
                widgetUpdates.addAndGet(appWidgetIds.length);
            }
        };
        Intent updateIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {101, 102, 103});
        provider.onReceive(ctx, updateIntent);
        check(widgetUpdates.get() == 3, "AppWidgetProvider dispatches ACTION_APPWIDGET_UPDATE");

        AppWidgetProviderInfo awInfo = new AppWidgetProviderInfo();
        awInfo.minWidth = 180;
        awInfo.minHeight = 110;
        awInfo.updatePeriodMillis = 1800000;
        AppWidgetProviderInfo awInfoOut = roundTripParcel(awInfo, AppWidgetProviderInfo.CREATOR);
        check(awInfoOut.minWidth == 180 && awInfoOut.minHeight == 110, "AppWidgetProviderInfo Parcel");

        check(MtpConstants.isAbstractObject(MtpConstants.FORMAT_ABSTRACT_AUDIO_ALBUM), "MtpConstants abstract");
        check(!MtpConstants.isAbstractObject(MtpConstants.FORMAT_EXIF_JPEG), "MtpConstants non-abstract");
        MtpObjectInfo mtpObj = new MtpObjectInfo.Builder()
                .setName("photo.jpg")
                .setFormat(MtpConstants.FORMAT_EXIF_JPEG)
                .setCompressedSize(4096L)
                .setImagePixWidth(1920L)
                .setImagePixHeight(1080L)
                .build();
        check("photo.jpg".equals(mtpObj.getName()) && mtpObj.getCompressedSizeLong() == 4096L,
                "MtpObjectInfo.Builder");
        check(UsbConstants.USB_CLASS_MASS_STORAGE == 8, "UsbConstants mass storage");

        SyncFence signaledFence = SyncFence.createSignaled(123456L);
        SyncFence copiedFence = new SyncFence(roundTripParcel(signaledFence, SyncFence.CREATOR));
        check(copiedFence.isValid() && copiedFence.awaitForever(), "SyncFence Parcel & awaitForever");
        copiedFence.close();
        signaledFence.close();
    }

    private static void verifyWaveW2(File tempDir) throws Exception {
        // Real 2D gesture creation, persistence, and cosine recognition
        ArrayList<GesturePoint> horizPts = new ArrayList<GesturePoint>();
        for (int i = 0; i <= 20; i++) {
            horizPts.add(new GesturePoint(i * 10f, 50f, i * 16L));
        }
        Gesture horizGesture = new Gesture();
        horizGesture.addStroke(new GestureStroke(horizPts));

        ArrayList<GesturePoint> circlePts = new ArrayList<GesturePoint>();
        for (int i = 0; i <= 24; i++) {
            double ang = (2.0 * Math.PI * i) / 24.0;
            circlePts.add(new GesturePoint(
                    100f + 60f * (float) Math.cos(ang),
                    100f + 60f * (float) Math.sin(ang),
                    i * 16L));
        }
        Gesture circleGesture = new Gesture();
        circleGesture.addStroke(new GestureStroke(circlePts));

        OrientedBoundingBox obb = GestureUtils.computeOrientedBoundingBox(horizPts);
        check(obb != null && obb.width > 100f, "GestureUtils.computeOrientedBoundingBox");

        Bitmap gBmp = horizGesture.toBitmap(64, 64, 4, Color.GREEN);
        check(gBmp != null && gBmp.getWidth() == 64 && gBmp.getHeight() == 64, "Gesture.toBitmap");

        Gesture parcelGesture = roundTripParcel(horizGesture, Gesture.CREATOR);
        check(parcelGesture.getStrokesCount() == 1 && parcelGesture.getLength() > 190f, "Gesture Parcel");

        File libFile = new File(tempDir, "gestures.bin");
        GestureLibrary libWrite = GestureLibraries.fromFile(libFile);
        libWrite.addGesture("line", horizGesture);
        libWrite.addGesture("circle", circleGesture);
        check(libWrite.save(), "GestureLibrary.save");

        GestureLibrary libRead = GestureLibraries.fromFile(libFile);
        check(libRead.load(), "GestureLibrary.load");
        check(libRead.getGestureEntries().contains("line") && libRead.getGestureEntries().contains("circle"),
                "GestureLibrary entries");

        ArrayList<GesturePoint> queryPts = new ArrayList<GesturePoint>();
        for (int i = 0; i <= 20; i++) {
            queryPts.add(new GesturePoint(i * 9.8f, 52f + (i % 2), i * 15L));
        }
        Gesture query = new Gesture();
        query.addStroke(new GestureStroke(queryPts));
        ArrayList<Prediction> preds = libRead.recognize(query);
        check(preds != null && !preds.isEmpty() && "line".equals(preds.get(0).name),
                "GestureLibrary recognizes horizontal line as top prediction");

        AttributionSource attr = new AttributionSource.Builder(1001)
                .setPackageName("org.shiraz.smoke")
                .setAttributionTag("mic")
                .build();
        AttributionSource attrOut = roundTripParcel(attr, AttributionSource.CREATOR);
        check(attrOut.getUid() == 1001 && "org.shiraz.smoke".equals(attrOut.getPackageName())
                && "mic".equals(attrOut.getAttributionTag()), "AttributionSource Parcel");

        ContextParams cp = new ContextParams.Builder()
                .setAttributionTag("audio")
                .setNextAttributionSource(attr)
                .build();
        check("audio".equals(cp.getAttributionTag()) && cp.getNextAttributionSource() != null,
                "ContextParams.Builder");

        RecognitionPart part = new RecognitionPart.Builder("hello")
                .setFormattedText("Hello,")
                .setTimestampMillis(120L)
                .setConfidenceLevel(RecognitionPart.CONFIDENCE_LEVEL_HIGH)
                .build();
        RecognitionPart partOut = roundTripParcel(part, RecognitionPart.CREATOR);
        check("hello".equals(partOut.getRawText()) && "Hello,".equals(partOut.getFormattedText()),
                "RecognitionPart Parcel");

        RecognitionSupport support = new RecognitionSupport.Builder()
                .setInstalledOnDeviceLanguages(new ArrayList<String>(Arrays.asList("en-US", "fa-IR")))
                .build();
        RecognitionSupport supportOut = roundTripParcel(support, RecognitionSupport.CREATOR);
        check(supportOut.getInstalledOnDeviceLanguages().contains("fa-IR"), "RecognitionSupport Parcel");

        AlternativeSpan span = new AlternativeSpan(0, 5, new ArrayList<String>(Collections.singletonList("world")));
        AlternativeSpans spans = new AlternativeSpans(Collections.singletonList(span));
        AlternativeSpans spansOut = roundTripParcel(spans, AlternativeSpans.CREATOR);
        check(spansOut.getSpans().size() == 1 && spansOut.getSpans().get(0).getEndPosition() == 5,
                "AlternativeSpans Parcel");
    }

    private static void verifyWaveW3(Context ctx, File tempDir) throws Exception {
        PrintAttributes attrs = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
                .setResolution(new PrintAttributes.Resolution("r1", "300dpi", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build();
        PrintAttributes attrsOut = roundTripParcel(attrs, PrintAttributes.CREATOR);
        check(PrintAttributes.MediaSize.NA_LETTER.equals(attrsOut.getMediaSize()), "PrintAttributes Parcel");

        PrintedPdfDocument pdfDoc = new PrintedPdfDocument(ctx, attrs);
        check(pdfDoc.getPageWidth() == 612 && pdfDoc.getPageHeight() == 792, "NA_LETTER 612x792 pt");
        android.graphics.pdf.PdfDocument.Page page = pdfDoc.startPage(0);
        Canvas canvas = page.getCanvas();
        Paint white = new Paint();
        white.setColor(Color.WHITE);
        canvas.drawRect(0, 0, 612, 792, white);
        Paint red = new Paint();
        red.setColor(Color.RED);
        canvas.drawRect(50, 50, 300, 300, red);
        pdfDoc.finishPage(page);

        File pdfFile = new File(tempDir, "test_print.pdf");
        FileOutputStream fos = new FileOutputStream(pdfFile);
        try {
            pdfDoc.writeTo(fos);
        } finally {
            fos.close();
            pdfDoc.close();
        }
        byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());
        String header = new String(pdfBytes, 0, Math.min(8, pdfBytes.length), StandardCharsets.US_ASCII);
        String tail = new String(
                pdfBytes, Math.max(0, pdfBytes.length - 32), Math.min(32, pdfBytes.length), StandardCharsets.US_ASCII);
        check(header.startsWith("%PDF-1.") && tail.contains("%%EOF"), "Valid PDF-1.x header and %%EOF");

        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
        PdfRenderer renderer = new PdfRenderer(pfd);
        try {
            check(renderer.getPageCount() == 1, "PdfRenderer pageCount == 1");
            PdfRenderer.Page rPage = renderer.openPage(0);
            try {
                check(rPage.getWidth() == 612 && rPage.getHeight() == 792, "PdfRenderer page 612x792");
                Bitmap rendered = Bitmap.createBitmap(612, 792, Bitmap.Config.ARGB_8888);
                rPage.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                int centerPixel = rendered.getPixel(150, 150);
                check(Color.red(centerPixel) > 200 && Color.green(centerPixel) < 40 && Color.blue(centerPixel) < 40,
                        "PdfRenderer decoded red rectangle accurately");
            } finally {
                rPage.close();
            }
        } finally {
            renderer.close();
        }

        PageRange pr = new PageRange(1, 5);
        PageRange prOut = roundTripParcel(pr, PageRange.CREATOR);
        check(prOut.getStart() == 1 && prOut.getEnd() == 5, "PageRange Parcel");

        PrintDocumentInfo docInfo = new PrintDocumentInfo.Builder("invoice.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(3)
                .build();
        PrintDocumentInfo docInfoOut = roundTripParcel(docInfo, PrintDocumentInfo.CREATOR);
        check("invoice.pdf".equals(docInfoOut.getName()) && docInfoOut.getPageCount() == 3,
                "PrintDocumentInfo Parcel");

        PrinterId pid = roundTripParcel(
                createPrinterInfoSample().getId(), PrinterId.CREATOR);
        check("printer-1".equals(pid.getLocalId()), "PrinterId Parcel");
    }

    private static PrinterInfo createPrinterInfoSample() {
        ComponentName cn = new ComponentName("org.shiraz.print", "org.shiraz.print.Svc");
        // PrinterId has package-private constructor in AOSP, exposed via PrintService or PrinterInfo; check viaCapabilities:
        PrinterId id = createPrinterIdViaParcel(cn, "printer-1");
        PrinterCapabilitiesInfo caps = new PrinterCapabilitiesInfo.Builder(id)
                .addMediaSize(PrintAttributes.MediaSize.NA_LETTER, true)
                .addResolution(new PrintAttributes.Resolution("r1", "300dpi", 300, 300), true)
                .setColorModes(PrintAttributes.COLOR_MODE_COLOR, PrintAttributes.COLOR_MODE_COLOR)
                .build();
        return new PrinterInfo.Builder(id, "Shiraz Virtual PDF Printer", PrinterInfo.STATUS_IDLE)
                .setCapabilities(caps)
                .build();
    }

    private static PrinterId createPrinterIdViaParcel(ComponentName service, String localId) {
        Parcel p = Parcel.obtain();
        try {
            p.writeString(service.getPackageName());
            p.writeString(service.getClassName());
            p.writeString(localId);
            p.setDataPosition(0);
            return PrinterId.CREATOR.createFromParcel(p);
        } finally {
            p.recycle();
        }
    }

    private static void verifyWaveW4(Context ctx, File tempDir) throws Exception {
        RemoteInput ri = new RemoteInput.Builder("reply_text")
                .setLabel("Reply")
                .setAllowFreeFormInput(true)
                .build();
        RemoteInput riOut = roundTripParcel(ri, RemoteInput.CREATOR);
        check("reply_text".equals(riOut.getResultKey()), "RemoteInput Parcel");

        Intent replyIntent = new Intent("org.shiraz.REPLY");
        Bundle replyBundle = new Bundle();
        replyBundle.putCharSequence("reply_text", "Sounds good!");
        RemoteInput.addResultsToIntent(new RemoteInput[] {ri}, replyIntent, replyBundle);
        Bundle extracted = RemoteInput.getResultsFromIntent(replyIntent);
        check(extracted != null && "Sounds good!".equals(String.valueOf(extracted.getCharSequence("reply_text"))),
                "RemoteInput.addResultsToIntent / getResultsFromIntent");

        Uri sliceUri = Uri.parse("content://org.shiraz.slice/status");
        SliceSpec spec = new SliceSpec("shiraz.status", 2);
        Slice slice = new Slice.Builder(sliceUri, spec)
                .addText("Online", "status", Collections.singletonList("title"))
                .addInt(99, "battery", Collections.<String>emptyList())
                .build();
        Slice sliceOut = roundTripParcel(slice, Slice.CREATOR);
        check(sliceOut.getItems().size() == 2
                && SliceItem.FORMAT_TEXT.equals(sliceOut.getItems().get(0).getFormat()),
                "Slice Parcel round-trip");

        Bundle credData = new Bundle();
        credData.putString("androidx.credentials.BUNDLE_KEY_ID", "alice@example.com");
        Credential cred = new Credential(Credential.TYPE_PASSWORD_CREDENTIAL, credData);
        Credential credOut = roundTripParcel(cred, Credential.CREATOR);
        check(Credential.TYPE_PASSWORD_CREDENTIAL.equals(credOut.getType()), "Credential Parcel");

        CredentialOption opt = new CredentialOption.Builder(
                Credential.TYPE_PASSWORD_CREDENTIAL, new Bundle(), new Bundle())
                .setIsSystemProviderRequired(false)
                .build();
        GetCredentialRequest getReq = new GetCredentialRequest.Builder(new Bundle())
                .addCredentialOption(opt)
                .build();
        GetCredentialRequest getReqOut = roundTripParcel(getReq, GetCredentialRequest.CREATOR);
        check(getReqOut.getCredentialOptions().size() == 1, "GetCredentialRequest Parcel");

        CreateCredentialRequest createReq = new CreateCredentialRequest.Builder(
                Credential.TYPE_PASSWORD_CREDENTIAL, credData, new Bundle())
                .setAlwaysSendAppInfoToProvider(true)
                .build();
        CreateCredentialRequest createReqOut = roundTripParcel(createReq, CreateCredentialRequest.CREATOR);
        check(Credential.TYPE_PASSWORD_CREDENTIAL.equals(createReqOut.getType()), "CreateCredentialRequest Parcel");

        GetCredentialException gce = new GetCredentialException(
                GetCredentialException.TYPE_NO_CREDENTIAL, "none");
        check(GetCredentialException.TYPE_NO_CREDENTIAL.equals(gce.getType()), "GetCredentialException type");

        // DRM classes
        DrmSupportInfo support = new DrmSupportInfo();
        support.addMimeType("application/vnd.oma.drm.message");
        support.addFileSuffix(".dm");
        support.setDescription("OMA DRM v1");
        check("OMA DRM v1".equals(support.getDescription()), "DrmSupportInfo description");

        DrmInfoRequest infoReq = new DrmInfoRequest(DrmInfoRequest.TYPE_RIGHTS_ACQUISITION_INFO, "video/mp4");
        infoReq.put("content_id", "cid-42");
        check("cid-42".equals(infoReq.get("content_id")), "DrmInfoRequest put/get");

        DrmInfo drmInfo = new DrmInfo(
                DrmInfoRequest.TYPE_RIGHTS_ACQUISITION_INFO,
                "payload".getBytes(StandardCharsets.UTF_8),
                "video/mp4");
        check(drmInfo.getData().length == 7, "DrmInfo bytes");

        File rightsFile = new File(tempDir, "rights.dr");
        Files.write(rightsFile.toPath(), "RIGHTS_XML".getBytes(StandardCharsets.UTF_8));
        DrmRights rights = new DrmRights(rightsFile, "application/vnd.oma.drm.rights+xml");
        check(rights.getData().length == 10 && DrmStore.RightsStatus.RIGHTS_VALID == 0, "DrmRights file load");

        byte[] extMeta = new byte[] {
            3, 5, 'k', 'e', 'y', 'v', 'a', 'l', 'u', 'e'
        };
        DrmUtils.ExtendedMetadataParser parser = DrmUtils.getExtendedMetadataParser(extMeta);
        check("value".equals(parser.get("key")), "DrmUtils.ExtendedMetadataParser");

        DrmConvertedStatus conv = new DrmConvertedStatus(
                DrmConvertedStatus.STATUS_OK, new byte[] {1, 2, 3}, 0);
        check(conv.statusCode == DrmConvertedStatus.STATUS_OK && conv.convertedData.length == 3,
                "DrmConvertedStatus");
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        File tempDir = Files.createTempDirectory("shiraz-f3-smoke").toFile();
        Context ctx = createSmokeContext(tempDir);
        try {
            verifyTransitions(ctx);
            verifyOpenGlMathAndEtc1();
            verifyWaveW1(ctx);
            verifyWaveW2(tempDir);
            verifyWaveW3(ctx, tempDir);
            verifyWaveW4(ctx, tempDir);
            System.out.println("FRAMEWORK_F3_OK");
        } finally {
            File[] children = tempDir.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    children[i].delete();
                }
            }
            tempDir.delete();
        }
    }
}
