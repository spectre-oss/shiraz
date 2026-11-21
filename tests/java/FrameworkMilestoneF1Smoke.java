// SPDX-License-Identifier: GPL-3.0-only
import android.Manifest;
import android.animation.Animator;
import android.animation.BidirectionalTypeConverter;
import android.animation.FloatArrayEvaluator;
import android.animation.IntArrayEvaluator;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PointFEvaluator;
import android.animation.PropertyValuesHolder;
import android.animation.RectEvaluator;
import android.animation.TimeAnimator;
import android.animation.ValueAnimator;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.crypto.hpke.HpkeSpi;
import android.crypto.hpke.XdhKeySpec;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Looper;
import android.sax.Element;
import android.sax.EndElementListener;
import android.sax.EndTextElementListener;
import android.sax.RootElement;
import android.sax.StartElementListener;
import android.system.Int64Ref;
import android.system.Os;
import android.system.StructCmsghdr;
import android.system.StructMsghdr;
import android.system.VmSocketAddress;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Base64;
import android.util.Base64DataException;
import android.util.Base64InputStream;
import android.util.CloseGuard;
import android.util.Config;
import android.util.DebugUtils;
import android.util.Dumpable;
import android.util.EventLog;
import android.util.EventLogTags;
import android.util.Half;
import android.util.IntProperty;
import android.util.LayoutDirection;
import android.util.Log;
import android.util.LogPrinter;
import android.util.MonthDisplayHelper;
import android.util.MutableInt;
import android.util.PrintStreamPrinter;
import android.util.PrintWriterPrinter;
import android.util.Rational;
import android.util.StatsLog;
import android.util.StringBuilderPrinter;
import android.util.TimeUtils;
import android.util.TimingLogger;
import android.util.proto.ProtoOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

@TargetApi(35)
@SuppressLint({"NewApi", "HardcodedText"})
public class FrameworkMilestoneF1Smoke {
    static void check(boolean condition, String detail) {
        if (!condition) {
            throw new AssertionError("Check failed: " + detail);
        }
    }

    @FlaggedApi("shiraz.framework.f1")
    static String annotatedEcho(@NonNull String prefix, @Nullable String suffix) {
        return suffix == null ? prefix : prefix + suffix;
    }

    static final class AnimTarget {
        int count;
        float progress;

        public int getCount() {
            return count;
        }

        public void setCount(int value) {
            count = value;
        }

        public float getProgress() {
            return progress;
        }

        public void setProgress(float value) {
            progress = value;
        }
    }

    static void testManifestAnnotationsCryptoSystem() throws Exception {
        check("android.permission.CAMERA".equals(Manifest.permission.CAMERA), "Manifest CAMERA");
        check("android.permission.POST_NOTIFICATIONS".equals(Manifest.permission.POST_NOTIFICATIONS), "Manifest POST_NOTIFICATIONS");
        check("android.permission-group.LOCATION".equals(Manifest.permission_group.LOCATION), "Manifest group LOCATION");
        check("ok!".equals(annotatedEcho("ok", "!")), "annotations");

        byte[] rawKey = new byte[32];
        for (int i = 0; i < rawKey.length; i++) {
            rawKey[i] = (byte) (i + 1);
        }
        XdhKeySpec spec = new XdhKeySpec(rawKey);
        rawKey[0] = 99;
        check(spec.getKey()[0] == 1, "XdhKeySpec defensive copy");
        check("RAW".equals(spec.getFormat()), "XdhKeySpec format");
        check(HpkeSpi.class.isInterface(), "HpkeSpi interface");

        // Verify android.system additions coexist with boot core-libart android.system.Os.
        check(Os.class.getName().equals("android.system.Os"), "boot Os class");
        if ("Dalvik".equals(System.getProperty("java.vm.name"))) {
            check(Os.getpid() > 0, "ART boot Os.getpid");
        }
        Int64Ref ref = new Int64Ref(42L);
        check(ref.value == 42L && ref.toString().contains("42"), "Int64Ref");
        StructCmsghdr cmsg = new StructCmsghdr(1, 2, (short) 0x0102);
        check(cmsg.cmsg_level == 1 && cmsg.cmsg_type == 2 && cmsg.cmsg_data.length == 2, "StructCmsghdr");
        StructMsghdr msg = new StructMsghdr(
                new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 8080),
                new ByteBuffer[] {ByteBuffer.allocate(4)},
                new StructCmsghdr[] {cmsg},
                0);
        check(msg.msg_iov.length == 1 && msg.msg_control.length == 1, "StructMsghdr");
        VmSocketAddress vmAddr = new VmSocketAddress(5555, 2);
        check(vmAddr.getSvmPort() == 5555 && vmAddr.getSvmCid() == 2, "VmSocketAddress");
    }

    static void testSax() throws Exception {
        RootElement root = new RootElement("feed");
        final List<String> titles = new ArrayList<>();
        final int[] itemCount = {0};
        Element item = root.requireChild("item");
        item.setStartElementListener(new StartElementListener() {
            @Override
            public void start(Attributes attributes) {
                if ("yes".equals(attributes.getValue("featured"))) {
                    itemCount[0] += 10;
                }
            }
        });
        item.setEndElementListener(new EndElementListener() {
            @Override
            public void end() {
                itemCount[0] += 1;
            }
        });
        item.requireChild("title").setEndTextElementListener(new EndTextElementListener() {
            @Override
            public void end(String body) {
                titles.add(body);
            }
        });

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setContentHandler(root.getContentHandler());
        reader.parse(new InputSource(new StringReader(
                "<feed><item featured=\"yes\"><title>First</title></item><item><title>Second</title></item></feed>")));
        check(itemCount[0] == 12, "SAX item start/end counts: " + itemCount[0]);
        check(Arrays.asList("First", "Second").equals(titles), "SAX parsed titles: " + titles);

        // Missing required child <title> must throw SAXException.
        boolean caughtMissing = false;
        try {
            reader.setContentHandler(root.getContentHandler());
            reader.parse(new InputSource(new StringReader("<feed><item></item></feed>")));
        } catch (SAXException expected) {
            caughtMissing = true;
        }
        check(caughtMissing, "SAX requireChild enforcement");

        // Mixed child element inside text-only element must throw SAXException.
        boolean caughtMixed = false;
        try {
            reader.setContentHandler(root.getContentHandler());
            reader.parse(new InputSource(new StringReader("<feed><item><title><b>bad</b></title></item></feed>")));
        } catch (SAXException expected) {
            caughtMixed = true;
        }
        check(caughtMixed, "SAX mixed content rejection");
    }

    static void testAnimation() {
        Looper.prepareMainLooper();
        try {
            // Evaluators
            float[] fa = new FloatArrayEvaluator().evaluate(0.25f, new float[] {0f, 100f}, new float[] {40f, 200f});
            check(Math.abs(fa[0] - 10f) < 1e-4f && Math.abs(fa[1] - 125f) < 1e-4f, "FloatArrayEvaluator");
            int[] ia = new IntArrayEvaluator().evaluate(0.5f, new int[] {10, 20}, new int[] {30, 60});
            check(ia[0] == 20 && ia[1] == 40, "IntArrayEvaluator");
            PointF pf = new PointFEvaluator().evaluate(0.5f, new PointF(2f, 4f), new PointF(10f, 20f));
            check(Math.abs(pf.x - 6f) < 1e-4f && Math.abs(pf.y - 12f) < 1e-4f, "PointFEvaluator");
            Rect rect = new RectEvaluator().evaluate(0.5f, new Rect(0, 0, 10, 20), new Rect(20, 40, 50, 60));
            check(rect.left == 10 && rect.top == 20 && rect.right == 30 && rect.bottom == 40, "RectEvaluator");

            // BidirectionalTypeConverter
            BidirectionalTypeConverter<Float, Integer> converter =
                    new BidirectionalTypeConverter<Float, Integer>(Float.class, Integer.class) {
                        @Override
                        public Integer convert(Float value) {
                            return Integer.valueOf(Math.round(value.floatValue() * 10f));
                        }

                        @Override
                        public Float convertBack(Integer value) {
                            return Float.valueOf(value.intValue() / 10f);
                        }
                    };
            check(converter.convert(Float.valueOf(2.5f)).intValue() == 25, "converter forward");
            check(Math.abs(converter.invert().convert(Integer.valueOf(25)).floatValue() - 2.5f) < 1e-4f, "converter invert");

            // Keyframes + IntProperty + Pause/Resume
            AnimTarget target = new AnimTarget();
            PropertyValuesHolder kfh = PropertyValuesHolder.ofKeyframe(
                    "progress",
                    Keyframe.ofFloat(0f, 0f),
                    Keyframe.ofFloat(0.5f, 80f),
                    Keyframe.ofFloat(1f, 100f));
            ObjectAnimator kfAnim = ObjectAnimator.ofPropertyValuesHolder(target, kfh);
            kfAnim.setInterpolator(new android.view.animation.LinearInterpolator());
            kfAnim.setDuration(100L);
            kfAnim.setCurrentFraction(0.25f);
            check(Math.abs(target.progress - 40f) < 1e-3f, "Keyframe 0.25 -> 40: " + target.progress);
            kfAnim.setCurrentFraction(0.75f);
            check(Math.abs(target.progress - 90f) < 1e-3f, "Keyframe 0.75 -> 90: " + target.progress);

            IntProperty<AnimTarget> countProp = new IntProperty<AnimTarget>("count") {
                @Override
                public void setValue(AnimTarget obj, int value) {
                    obj.count = value;
                }

                @Override
                public Integer get(AnimTarget obj) {
                    return Integer.valueOf(obj.count);
                }
            };
            ObjectAnimator intAnim = ObjectAnimator.ofInt(target, countProp, 0, 50);
            final int[] pauseEvents = {0, 0};
            intAnim.addPauseListener(new Animator.AnimatorPauseListener() {
                @Override
                public void onAnimationPause(Animator animation) {
                    pauseEvents[0]++;
                }

                @Override
                public void onAnimationResume(Animator animation) {
                    pauseEvents[1]++;
                }
            });
            intAnim.setDuration(100L);
            intAnim.start();
            intAnim.pause();
            check(intAnim.isPaused() && pauseEvents[0] == 1, "Animator paused");
            intAnim.resume();
            check(!intAnim.isPaused() && pauseEvents[1] == 1, "Animator resumed");
            intAnim.setCurrentFraction(0.5f);
            check(target.count == 25, "IntProperty animated count: " + target.count);
            intAnim.cancel();

            // DurationScaleChangeListener + TimeAnimator
            ValueAnimator.DurationScaleChangeListener scaleListener =
                    new ValueAnimator.DurationScaleChangeListener() {
                        @Override
                        public void onChanged(float scale) {}
                    };
            check(ValueAnimator.registerDurationScaleChangeListener(scaleListener), "register scale listener");
            check(!ValueAnimator.registerDurationScaleChangeListener(scaleListener), "duplicate scale listener");
            check(ValueAnimator.unregisterDurationScaleChangeListener(scaleListener), "unregister scale listener");

            TimeAnimator timeAnim = new TimeAnimator();
            final long[] seenTime = {-1L, -1L};
            timeAnim.setTimeListener(new TimeAnimator.TimeListener() {
                @Override
                public void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime) {
                    seenTime[0] = totalTime;
                    seenTime[1] = deltaTime;
                }
            });
            timeAnim.start();
            Looper.getMainLooper().shirazDispatchDue(50);
            timeAnim.cancel();
            check(seenTime[0] >= 0L && seenTime[1] >= 0L, "TimeAnimator callback invoked");
        } finally {
            Looper.shirazShutdownMain();
        }
    }

    static void testCollectionsAndMath() {
        // Boot MutableInt check + ArrayMap + ArraySet
        MutableInt mutable = new MutableInt(7);
        check(mutable.value == 7, "boot MutableInt");

        ArrayMap<String, Integer> map = new ArrayMap<>();
        map.put("beta", Integer.valueOf(2));
        map.put(null, Integer.valueOf(0));
        map.put("Aa", Integer.valueOf(11)); // "Aa" and "BB" collide in String.hashCode()!
        map.put("BB", Integer.valueOf(22));
        check(map.size() == 4, "ArrayMap size");
        check(map.get("Aa").intValue() == 11 && map.get("BB").intValue() == 22 && map.get(null).intValue() == 0, "ArrayMap collision/null lookup");
        HashMap<String, Integer> expectedMap = new HashMap<>(map);
        check(map.equals(expectedMap) && expectedMap.equals(map), "ArrayMap equals HashMap");
        map.remove("Aa");
        check(!map.containsKey("Aa") && map.get("BB").intValue() == 22, "ArrayMap remove collision peer");

        ArraySet<String> set = new ArraySet<>();
        check(set.add("Aa") && set.add("BB") && set.add(null) && !set.add("Aa"), "ArraySet add collision/null");
        check(set.equals(new HashSet<>(Arrays.asList("Aa", "BB", null))), "ArraySet equals HashSet");
        check(set.removeIf(new java.util.function.Predicate<String>() {
            @Override
            public boolean test(String s) {
                return "BB".equals(s);
            }
        }) && set.size() == 2, "ArraySet removeIf");

        // Half
        short hOnePointFive = Half.toHalf(1.5f);
        check(Half.toFloat(hOnePointFive) == 1.5f, "Half 1.5 roundtrip");
        check(Half.toFloat(Half.MAX_VALUE) == 65504.0f, "Half MAX_VALUE");
        check(Half.isNaN(Half.NaN) && !Half.isNormalized(Half.MIN_VALUE) && Half.isNormalized(Half.MIN_NORMAL), "Half classification");
        check(Half.toFloat(Half.MIN_VALUE) == Math.scalb(1.0f, -24), "Half subnormal MIN_VALUE");
        check(Half.round(Half.toHalf(-0.2f)) == Half.NEGATIVE_ZERO, "Half round preserves -0.0");
        check("0x1.8p0".equals(Half.toHexString(hOnePointFive)), "Half hex string: " + Half.toHexString(hOnePointFive));

        // Rational
        Rational r = new Rational(6, -8);
        check(r.getNumerator() == -3 && r.getDenominator() == 4, "Rational GCD reduction");
        check(Rational.parseRational("16:9").equals(new Rational(32, 18)), "Rational parseRational");
        check(Rational.NaN.compareTo(Rational.POSITIVE_INFINITY) > 0, "Rational NaN ordering");
    }

    static void testIoProtoTimeAndDiagnostics() throws Exception {
        // Base64InputStream
        byte[] payload = "Shiraz Framework API 35 — Base64 streaming test payload!".getBytes(StandardCharsets.UTF_8);
        String encoded = Base64.encodeToString(payload, Base64.DEFAULT);
        final boolean[] underlyingClosed = {false};
        ByteArrayInputStream rawIn = new ByteArrayInputStream(encoded.getBytes(StandardCharsets.US_ASCII)) {
            @Override
            public void close() {
                underlyingClosed[0] = true;
            }
        };
        ByteArrayOutputStream decodedOut = new ByteArrayOutputStream();
        try (Base64InputStream b64 = new Base64InputStream(rawIn, Base64.NO_CLOSE)) {
            byte[] buf = new byte[7];
            int n;
            while ((n = b64.read(buf)) >= 0) {
                decodedOut.write(buf, 0, n);
            }
        }
        check(!underlyingClosed[0], "Base64InputStream NO_CLOSE respected");
        check(Arrays.equals(payload, decodedOut.toByteArray()), "Base64InputStream roundtrip");

        boolean caughtCorruptB64 = false;
        try (Base64InputStream bad = new Base64InputStream(
                new ByteArrayInputStream("%%%".getBytes(StandardCharsets.US_ASCII)), Base64.DEFAULT)) {
            bad.read();
        } catch (Base64DataException expected) {
            caughtCorruptB64 = true;
        }
        check(caughtCorruptB64, "Base64InputStream throws Base64DataException");

        // ProtoOutputStream
        long fTitle = ProtoOutputStream.makeFieldId(1, ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_STRING);
        long fChild = ProtoOutputStream.makeFieldId(2, ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_MESSAGE);
        long fSint = ProtoOutputStream.makeFieldId(1, ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_SINT32);
        ProtoOutputStream proto = new ProtoOutputStream();
        proto.write(fTitle, "hi");
        long tok = proto.start(fChild);
        proto.write(fSint, -2); // zigzag(-2) = 3 -> tag (1<<3|0)=0x08, val=0x03
        proto.end(tok);
        byte[] wire = proto.getBytes();
        byte[] expectedWire = new byte[] {
            0x0a, 0x02, 'h', 'i', // field 1 string "hi"
            0x12, 0x02, 0x08, 0x03 // field 2 message { field 1 sint32 -2 }
        };
        check(Arrays.equals(expectedWire, wire), "ProtoOutputStream wire bytes: " + Arrays.toString(wire));

        // MonthDisplayHelper & TimeUtils
        MonthDisplayHelper febLeap = new MonthDisplayHelper(2024, Calendar.FEBRUARY, Calendar.SUNDAY);
        check(febLeap.getNumberOfDaysInMonth() == 29, "Leap Feb 2024 days");
        int row29 = febLeap.getRowOf(29);
        int col29 = febLeap.getColumnOf(29);
        check(febLeap.getDayAt(row29, col29) == 29 && febLeap.isWithinCurrentMonth(row29, col29), "MonthDisplayHelper day 29");

        check(TimeUtils.isTimeBetween(LocalTime.of(23, 30), LocalTime.of(22, 0), LocalTime.of(6, 0)), "TimeUtils midnight wrap true");
        check(!TimeUtils.isTimeBetween(LocalTime.of(12, 0), LocalTime.of(22, 0), LocalTime.of(6, 0)), "TimeUtils midnight wrap false");
        List<String> usZones = TimeUtils.getTimeZoneIdsForCountryCode("US");
        check(usZones != null && usZones.contains("America/New_York"), "TimeUtils US zones");
        TimeZone tokyo = TimeUtils.getTimeZone(9 * 3600 * 1000, false, 1700000000000L, "JP");
        check(tokyo != null && "Asia/Tokyo".equals(tokyo.getID()), "TimeUtils JP zone");
        check(TimeUtils.getTimeZoneDatabaseVersion() != null && !TimeUtils.getTimeZoneDatabaseVersion().isEmpty(), "tzdb version");

        // EventLog, EventLogTags, StatsLog, Printers, Diagnostics
        EventLogTags tags = new EventLogTags(new BufferedReader(new StringReader("# comment\n42424 shiraz_f1_event (payload|3)\n")));
        check(tags.get("shiraz_f1_event").mTag == 42424 && "shiraz_f1_event".equals(EventLog.getTagName(42424)), "EventLogTags");
        EventLog.writeEvent(42424, "milestone-f1");
        List<EventLog.Event> events = new ArrayList<>();
        EventLog.readEvents(new int[] {42424}, events);
        check(!events.isEmpty() && "milestone-f1".equals(events.get(events.size() - 1).getData()), "EventLog roundtrip");
        check(StatsLog.logEvent(7) && StatsLog.logStart(8) && StatsLog.logStop(8), "StatsLog");

        StringBuilder sb = new StringBuilder();
        new StringBuilderPrinter(sb).println("hello");
        check("hello\n".equals(sb.toString()), "StringBuilderPrinter");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        new PrintWriterPrinter(pw).println("pw-ok");
        pw.flush();
        check(sw.toString().contains("pw-ok"), "PrintWriterPrinter");
        ByteArrayOutputStream psBytes = new ByteArrayOutputStream();
        new PrintStreamPrinter(new PrintStream(psBytes)).println("ps-ok");
        check(psBytes.toString("UTF-8").contains("ps-ok"), "PrintStreamPrinter");
        new LogPrinter(Log.INFO, "F1Smoke").println("logprinter-ok");

        System.setProperty("log.tag.F1Timing", "VERBOSE");
        TimingLogger timer = new TimingLogger("F1Timing", "work");
        timer.addSplit("step1");
        timer.dumpToLog();

        CloseGuard guard = new CloseGuard();
        guard.open("close");
        guard.close();
        guard.warnIfOpen();

        check(!Config.DEBUG && Config.RELEASE && LayoutDirection.RTL == 1 && !DebugUtils.isObjectSelected(null), "Config/LayoutDirection/DebugUtils");
        Dumpable dumpable = new Dumpable() {
            @Override
            public void dump(PrintWriter writer, String[] args) {
                writer.print("dumped");
            }
        };
        check(dumpable.getDumpableName().contains("FrameworkMilestoneF1Smoke"), "Dumpable default method");
    }

    public static void main(String[] args) throws Exception {
        testManifestAnnotationsCryptoSystem();
        testSax();
        testAnimation();
        testCollectionsAndMath();
        testIoProtoTimeAndDiagnostics();
        System.out.println("FRAMEWORK_F1_OK");
    }
}
