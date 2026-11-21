// SPDX-License-Identifier: GPL-3.0-only
import android.adservices.AdServicesState;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.topics.Topic;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.ImageFormat;
import android.graphics.Matrix44;
import android.graphics.Paint;
import android.graphics.ParcelableColorSpace;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.graphics.YuvImage;
import android.graphics.text.LineBreaker;
import android.graphics.text.MeasuredText;
import android.hardware.DataSpace;
import android.hardware.GeomagneticField;
import android.hardware.camera2.params.TonemapCurve;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.InsertRecordsResponse;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.RecordIdFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogTokenResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.ExerciseRoute;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.units.Length;
import android.media.ImageReader;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.os.Parcel;
import android.os.Parcelable;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.FieldPacker;
import android.renderscript.Float3;
import android.renderscript.Matrix4f;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicColorMatrix;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public final class FrameworkMilestoneF5Smoke {
    private static final Executor DIRECT_EXECUTOR = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    private static Context makeContext(final File tempDir) {
        return new ContextWrapper(null) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public String getPackageName() {
                return "shiraz.smoke.f5";
            }

            @Override
            public Resources getResources() {
                return Resources.getSystem();
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

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError("Assertion failed: " + msg);
        }
    }

    private static void assertNear(double actual, double expected, double eps, String msg) {
        if (Math.abs(actual - expected) > eps) {
            throw new AssertionError(msg + ": expected " + expected + " +/- " + eps + " got " + actual);
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

    private static void testRenderScript(Context ctx) {
        Matrix4f m = new Matrix4f();
        m.loadTranslate(3.0f, 4.0f, 5.0f);
        m.scale(2.0f, 2.0f, 2.0f);
        Matrix4f copy = new Matrix4f(m.getArray());
        assertTrue(copy.inverse(), "Matrix4f inverse should succeed");
        m.multiply(copy);
        assertNear(m.get(0, 0), 1.0, 1e-4, "Matrix4f identity (0,0)");
        assertNear(m.get(1, 1), 1.0, 1e-4, "Matrix4f identity (1,1)");

        FieldPacker fp = new FieldPacker(64);
        fp.addI32(0x12345678);
        fp.addF32(new Float3(1.5f, -2.5f, 4.0f));
        Float3 f3 = fp.subFloat3();
        assertNear(f3.x, 1.5, 1e-5, "FieldPacker Float3.x");
        assertNear(f3.y, -2.5, 1e-5, "FieldPacker Float3.y");
        assertNear(f3.z, 4.0, 1e-5, "FieldPacker Float3.z");
        assertTrue(fp.subI32() == 0x12345678, "FieldPacker subI32 round-trip");

        RenderScript rs = RenderScript.create(ctx);
        try {
            Bitmap srcBmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
            srcBmp.setPixel(0, 0, 0xFFFF0000);
            srcBmp.setPixel(1, 0, 0xFF00FF00);
            srcBmp.setPixel(0, 1, 0xFF0000FF);
            srcBmp.setPixel(1, 1, 0xFFFFFFFF);
            Bitmap dstBmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);

            Allocation inAlloc = Allocation.createFromBitmap(rs, srcBmp);
            Allocation outAlloc = Allocation.createFromBitmap(rs, dstBmp);
            ScriptIntrinsicColorMatrix cm = ScriptIntrinsicColorMatrix.create(rs, Element.U8_4(rs));
            cm.setGreyscale();
            cm.forEach(inAlloc, outAlloc);
            outAlloc.copyTo(dstBmp);
            int whiteOut = dstBmp.getPixel(1, 1);
            int r = (whiteOut >> 16) & 0xFF;
            int g = (whiteOut >> 8) & 0xFF;
            int b = whiteOut & 0xFF;
            assertTrue(r > 240 && Math.abs(r - g) <= 2 && Math.abs(g - b) <= 2,
                    "ScriptIntrinsicColorMatrix greyscale white pixel: " + Integer.toHexString(whiteOut));
            inAlloc.destroy();
            outAlloc.destroy();
        } finally {
            rs.destroy();
        }
    }

    private static void testGraphics() {
        Matrix44 m44 = new Matrix44();
        assertTrue(m44.isIdentity(), "Matrix44 starts as identity");
        m44.translate(10.0f, 20.0f, 30.0f);
        m44.scale(2.0f, 4.0f, 5.0f);
        float[] mapped = m44.map(1.0f, 2.0f, 3.0f, 1.0f);
        assertNear(mapped[0], 12.0, 1e-4, "Matrix44 map x");
        assertNear(mapped[1], 28.0, 1e-4, "Matrix44 map y");
        assertNear(mapped[2], 45.0, 1e-4, "Matrix44 map z");
        assertTrue(m44.invert(), "Matrix44 invert succeeds");
        float[] back = m44.map(mapped[0], mapped[1], mapped[2], mapped[3]);
        assertNear(back[0], 1.0, 1e-4, "Matrix44 inverse x");
        assertNear(back[1], 2.0, 1e-4, "Matrix44 inverse y");
        assertNear(back[2], 3.0, 1e-4, "Matrix44 inverse z");

        ColorSpace srgb = ColorSpace.get(ColorSpace.Named.SRGB);
        assertTrue(ParcelableColorSpace.isParcelable(srgb), "sRGB is parcelable");
        ParcelableColorSpace pcs = roundTripParcel(
                new ParcelableColorSpace(srgb), ParcelableColorSpace.CREATOR);
        assertTrue(srgb.equals(pcs.getColorSpace()), "ParcelableColorSpace round-trips sRGB");

        byte[] nv21 = new byte[4 * 4 * 3 / 2];
        Arrays.fill(nv21, 0, 16, (byte) 180);
        Arrays.fill(nv21, 16, nv21.length, (byte) 128);
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, 4, 4, null);
        ByteArrayOutputStream jpegOut = new ByteArrayOutputStream();
        assertTrue(yuv.compressToJpeg(new Rect(0, 0, 4, 4), 85, jpegOut),
                "YuvImage.compressToJpeg succeeds");
        byte[] jpegBytes = jpegOut.toByteArray();
        assertTrue(jpegBytes.length > 4 && (jpegBytes[0] & 0xFF) == 0xFF && (jpegBytes[1] & 0xFF) == 0xD8,
                "YuvImage output starts with JPEG SOI marker");

        RenderNode node = new RenderNode("smokeNode");
        node.setPosition(0, 0, 120, 80);
        node.setAlpha(0.75f);
        assertTrue(node.getWidth() == 120 && node.getHeight() == 80, "RenderNode bounds");
        assertNear(node.getAlpha(), 0.75, 1e-5, "RenderNode alpha");
        node.beginRecording(120, 80);
        node.endRecording();
        assertTrue(node.hasDisplayList(), "RenderNode records display list");

        char[] chars = "Hello Shiraz text layout engine".toCharArray();
        Paint paint = new Paint();
        paint.setTextSize(16.0f);
        MeasuredText mt = new MeasuredText.Builder(chars)
                .appendStyleRun(paint, chars.length, false)
                .build();
        assertTrue(mt.getWidth(0, chars.length) > 0.0f, "MeasuredText measures positive width");
        LineBreaker breaker = new LineBreaker.Builder()
                .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
                .build();
        LineBreaker.ParagraphConstraints constraints = new LineBreaker.ParagraphConstraints();
        constraints.setWidth(80.0f);
        LineBreaker.Result res = breaker.computeLineBreaks(mt, constraints, 0);
        assertTrue(res.getLineCount() >= 1, "LineBreaker computes at least one line");
        assertTrue(res.getLineBreakOffset(res.getLineCount() - 1) == chars.length,
                "LineBreaker final line reaches end of text");
    }

    private static void testHardwareAndAdServices() {
        GeomagneticField geo = new GeomagneticField(37.7749f, -122.4194f, 10.0f, 1700000000000L);
        assertTrue(geo.getFieldStrength() > 20000.0f && geo.getFieldStrength() < 70000.0f,
                "GeomagneticField strength in San Francisco is realistic nT: " + geo.getFieldStrength());

        int packed = DataSpace.pack(DataSpace.STANDARD_BT709, DataSpace.TRANSFER_SRGB, DataSpace.RANGE_FULL);
        assertTrue(DataSpace.getStandard(packed) == DataSpace.STANDARD_BT709, "DataSpace standard");
        assertTrue(DataSpace.getTransfer(packed) == DataSpace.TRANSFER_SRGB, "DataSpace transfer");
        assertTrue(DataSpace.getRange(packed) == DataSpace.RANGE_FULL, "DataSpace range");

        float[] identityCurve = new float[] {0.0f, 0.0f, 1.0f, 1.0f};
        TonemapCurve tc = new TonemapCurve(identityCurve, identityCurve, identityCurve);
        assertTrue(tc.getPointCount(TonemapCurve.CHANNEL_RED) == 2, "TonemapCurve point count");
        PointF pt = tc.getPoint(TonemapCurve.CHANNEL_GREEN, 1);
        assertNear(pt.x, 1.0, 1e-5, "TonemapCurve point x");
        assertNear(pt.y, 1.0, 1e-5, "TonemapCurve point y");

        ImageReader reader = ImageReader.newInstance(64, 48, ImageFormat.YUV_420_888, 2);
        try {
            assertTrue(reader.getWidth() == 64 && reader.getHeight() == 48, "ImageReader dimensions");
            assertTrue(reader.getMaxImages() == 2, "ImageReader maxImages");
        } finally {
            reader.close();
        }

        assertTrue(!AdServicesState.isAdServicesStateEnabled() || AdServicesState.isAdServicesStateEnabled(),
                "AdServicesState query executes cleanly");
        Topic topic = new Topic(4L, 2L, 10001);
        assertTrue(topic.getTopicId() == 10001 && topic.getTaxonomyVersion() == 4L, "Topic getters");

        AdTechIdentifier seller = AdTechIdentifier.fromString("ads.example.org");
        AdSelectionConfig adCfg = new AdSelectionConfig.Builder()
                .setSeller(seller)
                .setDecisionLogicUri(Uri.parse("https://ads.example.org/logic.js"))
                .setCustomAudienceBuyers(Collections.singletonList(seller))
                .setAdSelectionSignals(AdSelectionSignals.fromString("{\"bidFloor\":1.0}"))
                .setSellerSignals(AdSelectionSignals.EMPTY)
                .setTrustedScoringSignalsUri(Uri.parse("https://ads.example.org/score"))
                .build();
        AdSelectionConfig adCfgOut = roundTripParcel(adCfg, AdSelectionConfig.CREATOR);
        assertTrue(seller.equals(adCfgOut.getSeller()), "AdSelectionConfig Parcel round-trip");
    }

    private static void testHealthConnect() {
        HealthConnectManager hcm = new HealthConnectManager();
        DataOrigin origin = new DataOrigin.Builder().setPackageName("shiraz.smoke.f5").build();
        Metadata meta1 = new Metadata.Builder()
                .setDataOrigin(origin)
                .setClientRecordId("steps-1")
                .build();
        Metadata meta2 = new Metadata.Builder()
                .setDataOrigin(origin)
                .setClientRecordId("steps-2")
                .build();
        Metadata metaHr = new Metadata.Builder()
                .setDataOrigin(origin)
                .setClientRecordId("hr-1")
                .build();

        final ChangeLogTokenResponse[] tokenHolder = new ChangeLogTokenResponse[1];
        hcm.getChangeLogToken(
                new ChangeLogTokenRequest.Builder().addRecordType(StepsRecord.class).build(),
                DIRECT_EXECUTOR,
                new OutcomeReceiver<ChangeLogTokenResponse, HealthConnectException>() {
                    @Override
                    public void onResult(ChangeLogTokenResponse result) {
                        tokenHolder[0] = result;
                    }
                });
        assertTrue(tokenHolder[0] != null && !tokenHolder[0].getToken().isEmpty(),
                "HealthConnectManager issued change log token");

        Instant t0 = Instant.parse("2026-05-01T10:00:00Z");
        Instant t1 = Instant.parse("2026-05-01T10:30:00Z");
        Instant t2 = Instant.parse("2026-05-01T11:00:00Z");

        StepsRecord s1 = new StepsRecord.Builder(meta1, t0, t1, 1200L)
                .setStartZoneOffset(ZoneOffset.UTC)
                .setEndZoneOffset(ZoneOffset.UTC)
                .build();
        StepsRecord s2 = new StepsRecord.Builder(meta2, t1, t2, 1800L)
                .setStartZoneOffset(ZoneOffset.UTC)
                .setEndZoneOffset(ZoneOffset.UTC)
                .build();
        List<HeartRateRecord.HeartRateSample> hrSamples = new ArrayList<HeartRateRecord.HeartRateSample>();
        hrSamples.add(new HeartRateRecord.HeartRateSample(70L, t0.plusSeconds(60)));
        hrSamples.add(new HeartRateRecord.HeartRateSample(90L, t0.plusSeconds(120)));
        HeartRateRecord hr = new HeartRateRecord.Builder(metaHr, t0, t1, hrSamples).build();

        final InsertRecordsResponse[] insertHolder = new InsertRecordsResponse[1];
        List<Record> toInsert = Arrays.<Record>asList(s1, s2, hr);
        hcm.insertRecords(toInsert, DIRECT_EXECUTOR,
                new OutcomeReceiver<InsertRecordsResponse, HealthConnectException>() {
                    @Override
                    public void onResult(InsertRecordsResponse result) {
                        insertHolder[0] = result;
                    }
                });
        assertTrue(insertHolder[0] != null && insertHolder[0].getRecords().size() == 3,
                "HealthConnectManager inserted 3 records");

        final ReadRecordsResponse<StepsRecord>[] readHolder = new ReadRecordsResponse[1];
        ReadRecordsRequestUsingFilters<StepsRecord> readReq =
                new ReadRecordsRequestUsingFilters.Builder<StepsRecord>(StepsRecord.class)
                        .setTimeRangeFilter(new TimeInstantRangeFilter.Builder()
                                .setStartTime(t0.minusSeconds(10))
                                .setEndTime(t2.plusSeconds(10))
                                .build())
                        .build();
        hcm.readRecords(readReq, DIRECT_EXECUTOR,
                new OutcomeReceiver<ReadRecordsResponse<StepsRecord>, HealthConnectException>() {
                    @Override
                    public void onResult(ReadRecordsResponse<StepsRecord> result) {
                        readHolder[0] = result;
                    }
                });
        assertTrue(readHolder[0] != null && readHolder[0].getRecords().size() == 2,
                "HealthConnectManager read 2 StepsRecords");

        final AggregateRecordsResponse<Long>[] aggHolder = new AggregateRecordsResponse[1];
        AggregateRecordsRequest<Long> aggReq = new AggregateRecordsRequest.Builder<Long>(
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(t0.minusSeconds(10))
                        .setEndTime(t2.plusSeconds(10))
                        .build())
                .addAggregationType(StepsRecord.STEPS_COUNT_TOTAL)
                .addAggregationType(HeartRateRecord.BPM_AVG)
                .addAggregationType(HeartRateRecord.BPM_MAX)
                .build();
        hcm.aggregate(aggReq, DIRECT_EXECUTOR,
                new OutcomeReceiver<AggregateRecordsResponse<Long>, HealthConnectException>() {
                    @Override
                    public void onResult(AggregateRecordsResponse<Long> result) {
                        aggHolder[0] = result;
                    }
                });
        assertTrue(aggHolder[0] != null, "HealthConnectManager aggregate returned response");
        assertTrue(Long.valueOf(3000L).equals(aggHolder[0].get(StepsRecord.STEPS_COUNT_TOTAL)),
                "Steps total = 3000");
        assertTrue(Long.valueOf(80L).equals(aggHolder[0].get(HeartRateRecord.BPM_AVG)),
                "HeartRate BPM_AVG = 80");
        assertTrue(Long.valueOf(90L).equals(aggHolder[0].get(HeartRateRecord.BPM_MAX)),
                "HeartRate BPM_MAX = 90");

        String secondStepsId = insertHolder[0].getRecords().get(1).getMetadata().getId();
        hcm.deleteRecords(
                Collections.singletonList(RecordIdFilter.fromId(StepsRecord.class, secondStepsId)),
                DIRECT_EXECUTOR,
                new OutcomeReceiver<Void, HealthConnectException>() {
                    @Override
                    public void onResult(Void result) {
                    }
                });

        final ChangeLogsResponse[] logsHolder = new ChangeLogsResponse[1];
        hcm.getChangeLogs(
                new ChangeLogsRequest.Builder(tokenHolder[0].getToken()).build(),
                DIRECT_EXECUTOR,
                new OutcomeReceiver<ChangeLogsResponse, HealthConnectException>() {
                    @Override
                    public void onResult(ChangeLogsResponse result) {
                        logsHolder[0] = result;
                    }
                });
        assertTrue(logsHolder[0] != null, "getChangeLogs returned response");
        assertTrue(logsHolder[0].getUpsertedRecords().size() == 1,
                "getChangeLogs reports 1 remaining upserted StepsRecord");
        assertTrue(logsHolder[0].getDeletedLogs().size() == 1
                        && secondStepsId.equals(logsHolder[0].getDeletedLogs().get(0).getDeletedRecordId()),
                "getChangeLogs reports deleted StepsRecord ID");

        ExerciseRoute.Location loc = new ExerciseRoute.Location.Builder(t0, 37.7749, -122.4194)
                .setAltitude(Length.fromMeters(15.0))
                .build();
        ExerciseRoute route = new ExerciseRoute(Collections.singletonList(loc));
        ExerciseRoute routeOut = roundTripParcel(route, ExerciseRoute.CREATOR);
        assertTrue(route.equals(routeOut), "ExerciseRoute Parcel round-trip");
    }

    private static void testServiceAndIcuNumber() {
        String formatted = android.icu.number.NumberFormatter.withLocale(java.util.Locale.US)
                .precision(android.icu.number.Precision.fixedFraction(2))
                .scale(android.icu.number.Scale.powerOfTen(2))
                .format(12.3456)
                .toString();
        assertTrue("1,234.56".equals(formatted), "NumberFormatter formatted value: " + formatted);

        String range = android.icu.number.NumberRangeFormatter.withLocale(java.util.Locale.US)
                .formatRange(10, 25)
                .toString();
        assertTrue(range.contains("10") && range.contains("25"), "NumberRangeFormatter output: " + range);

        int[] smsLen = android.telephony.SmsMessage.calculateLength("Hello Shiraz GSM 7-bit", false);
        assertTrue(smsLen[0] == 1 && smsLen[1] == 22, "SmsMessage.calculateLength 1 segment");

        android.view.autofill.AutofillId afId = new android.view.autofill.AutofillId(42);
        android.service.autofill.RegexValidator rx =
                new android.service.autofill.RegexValidator(afId, java.util.regex.Pattern.compile("^SHIRAZ-\\d+$"));
        android.service.autofill.RegexValidator rxOut =
                roundTripParcel(rx, android.service.autofill.RegexValidator.CREATOR);
        assertTrue(rxOut != null, "RegexValidator Parcel round-trip");

        android.view.textservice.SuggestionsInfo si =
                new android.view.textservice.SuggestionsInfo(
                        android.view.textservice.SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY,
                        new String[] {"shiraz", "framework"});
        android.view.textservice.SuggestionsInfo siOut =
                roundTripParcel(si, android.view.textservice.SuggestionsInfo.CREATOR);
        assertTrue(siOut.getSuggestionsCount() == 2 && "shiraz".equals(siOut.getSuggestionAt(0)),
                "SuggestionsInfo Parcel round-trip");
    }

    public static void main(String[] args) throws Exception {
        File tempDir = Files.createTempDirectory("shiraz-f5-smoke").toFile();
        tempDir.deleteOnExit();
        Context ctx = makeContext(tempDir);

        testRenderScript(ctx);
        testGraphics();
        testHardwareAndAdServices();
        testHealthConnect();
        testServiceAndIcuNumber();

        System.out.println("FRAMEWORK_F5_OK");
    }
}
