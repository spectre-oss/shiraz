// SPDX-License-Identifier: GPL-3.0-only
import android.app.ActivityOptions;
import android.app.ApplicationErrorReport;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Person;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.ClipData;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.AudioPresentation;
import android.media.ExifInterface;
import android.media.Session2Command;
import android.media.Session2Token;
import android.media.ThumbnailUtils;
import android.media.VolumeShaper;
import android.media.audiofx.Equalizer;
import android.media.metrics.LogSessionId;
import android.media.metrics.PlaybackMetrics;
import android.media.midi.MidiReceiver;
import android.media.midi.MidiSender;
import android.media.projection.MediaProjectionConfig;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Rational;
import android.view.ContentInfo;
import android.view.FocusFinder;
import android.view.KeyboardShortcutGroup;
import android.view.KeyboardShortcutInfo;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.SelectGesture;
import android.view.inspector.IntFlagMapping;
import android.view.textclassifier.TextClassificationManager;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextLinks;
import android.widget.FrameLayout;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.function.Predicate;

public final class FrameworkMilestoneF7Smoke {
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Assertion failed: " + message);
        }
    }

    private static byte[] buildMinimalExifJpeg(int width, int height, int orientation) throws IOException {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        DataOutputStream td = new DataOutputStream(tiff);
        // Big-endian TIFF ("MM", 0x002A, IFD0 offset = 8)
        td.writeByte('M');
        td.writeByte('M');
        td.writeShort(0x002A);
        td.writeInt(8);
        // IFD0 with 3 entries: ImageWidth (0x0100), ImageLength (0x0101), Orientation (0x0112)
        td.writeShort(3);
        // Entry 1: ImageWidth (SHORT = 3, count = 1)
        td.writeShort(0x0100);
        td.writeShort(3);
        td.writeInt(1);
        td.writeShort(width);
        td.writeShort(0);
        // Entry 2: ImageLength (SHORT = 3, count = 1)
        td.writeShort(0x0101);
        td.writeShort(3);
        td.writeInt(1);
        td.writeShort(height);
        td.writeShort(0);
        // Entry 3: Orientation (SHORT = 3, count = 1)
        td.writeShort(0x0112);
        td.writeShort(3);
        td.writeInt(1);
        td.writeShort(orientation);
        td.writeShort(0);
        // Next IFD offset = 0
        td.writeInt(0);
        td.flush();
        byte[] tiffBytes = tiff.toByteArray();

        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        DataOutputStream jd = new DataOutputStream(jpeg);
        jd.writeShort(0xFFD8); // SOI
        jd.writeShort(0xFFE1); // APP1
        jd.writeShort(2 + 6 + tiffBytes.length);
        jd.writeBytes("Exif");
        jd.writeByte(0);
        jd.writeByte(0);
        jd.write(tiffBytes);
        jd.writeShort(0xFFD9); // EOI
        jd.flush();
        return jpeg.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        final File tmpDir = new File(System.getProperty("java.io.tmpdir", "/tmp"));
        final Resources.Theme theme = Resources.getSystem().newTheme();
        Context context = new ContextWrapper(null) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public String getPackageName() {
                return "shiraz.smoke.f7";
            }

            @Override
            public Resources getResources() {
                return Resources.getSystem();
            }

            @Override
            public Resources.Theme getTheme() {
                return theme;
            }

            @Override
            public File getFilesDir() {
                return tmpDir;
            }

            @Override
            public File getCacheDir() {
                return tmpDir;
            }
        };

        // 1. android.media (ExifInterface, ThumbnailUtils, VolumeShaper, AudioPresentation, Equalizer, MIDI, TV, Metrics)
        byte[] jpeg = buildMinimalExifJpeg(640, 480, ExifInterface.ORIENTATION_ROTATE_90);
        ExifInterface exif = new ExifInterface(new ByteArrayInputStream(jpeg));
        require(exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0) == 640, "Exif width");
        require(exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0) == 480, "Exif height");
        require(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0) == ExifInterface.ORIENTATION_ROTATE_90, "Exif orientation");

        Bitmap srcBmp = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888);
        Bitmap thumb = ThumbnailUtils.extractThumbnail(srcBmp, 24, 24);
        require(thumb != null && thumb.getWidth() == 24 && thumb.getHeight() == 24, "ThumbnailUtils.extractThumbnail");

        VolumeShaper.Configuration shaperCfg = new VolumeShaper.Configuration.Builder()
                .setDuration(1000L)
                .setCurve(new float[]{0f, 1f}, new float[]{0f, 1f})
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                .build();
        Parcel p = Parcel.obtain();
        shaperCfg.writeToParcel(p, 0);
        p.setDataPosition(0);
        VolumeShaper.Configuration shaperOut = VolumeShaper.Configuration.CREATOR.createFromParcel(p);
        require(shaperOut.getDuration() == 1000L, "VolumeShaper.Configuration parcel");
        p.recycle();

        AudioPresentation ap = new AudioPresentation.Builder(7)
                .setMasteringIndication(AudioPresentation.MASTERED_FOR_SURROUND)
                .setHasDialogueEnhancement(true)
                .build();
        p = Parcel.obtain();
        ap.writeToParcel(p, 0);
        p.setDataPosition(0);
        AudioPresentation apOut = AudioPresentation.CREATOR.createFromParcel(p);
        require(apOut.getPresentationId() == 7 && apOut.hasDialogueEnhancement(), "AudioPresentation parcel");
        p.recycle();

        Equalizer.Settings eqSettings = new Equalizer.Settings("Equalizer;curPreset=2;numBands=2;band1Level=-300;band2Level=450");
        require(eqSettings.curPreset == 2 && eqSettings.numBands == 2 && eqSettings.bandLevels[1] == 450, "Equalizer.Settings parse");

        final int[] midiCount = new int[1];
        MidiSender sender = new MidiSender() {
            private MidiReceiver mTarget;

            @Override
            public void onConnect(MidiReceiver receiver) {
                mTarget = receiver;
            }

            @Override
            public void onDisconnect(MidiReceiver receiver) {
                if (mTarget == receiver) {
                    mTarget = null;
                }
            }
        };
        MidiReceiver receiver = new MidiReceiver() {
            @Override
            public void onSend(byte[] msg, int offset, int count, long timestamp) {
                midiCount[0] += count;
            }
        };
        sender.connect(receiver);
        receiver.send(new byte[]{(byte) 0x90, 60, 100}, 0, 3);
        require(midiCount[0] == 3, "MidiReceiver.send -> onSend");

        TvContentRating rating = TvContentRating.createRating("com.android.tv", "US_TV", "US_TV_PG", "US_TV_D", "US_TV_L");
        TvContentRating unflattened = TvContentRating.unflattenFromString(rating.flattenToString());
        require("US_TV_PG".equals(unflattened.getMainRating()) && unflattened.getSubRatings().size() == 2, "TvContentRating round-trip");
        Uri chUri = TvContract.buildChannelUri(42L);
        require(TvContract.isChannelUri(chUri), "TvContract.buildChannelUri");

        PlaybackMetrics pm = new PlaybackMetrics.Builder()
                .setMediaDurationMillis(120000L)
                .setStreamType(PlaybackMetrics.STREAM_TYPE_DASH)
                .setPlaybackType(PlaybackMetrics.PLAYBACK_TYPE_VOD)
                .build();
        require(pm.getMediaDurationMillis() == 120000L, "PlaybackMetrics.Builder");
        require(LogSessionId.LOG_SESSION_ID_NONE.getStringId().isEmpty(), "LogSessionId.NONE");
        require(MediaProjectionConfig.createConfigForDefaultDisplay() != null, "MediaProjectionConfig");
        require(new Session2Command(10000).getCommandCode() == 10000, "Session2Command predefined");
        Session2Command customCmd = new Session2Command("shiraz.cmd.PLAY", Bundle.EMPTY);
        require(customCmd.getCommandCode() == Session2Command.COMMAND_CODE_CUSTOM
                && "shiraz.cmd.PLAY".equals(customCmd.getCustomAction()), "Session2Command custom");
        require(Session2Token.TYPE_SESSION == 0, "Session2Token constant");

        // Verify real WAV container demuxing in MediaExtractor & MediaParser + explicit DRM/CAS boundary errors
        ByteArrayOutputStream wavOut = new ByteArrayOutputStream();
        DataOutputStream wd = new DataOutputStream(wavOut);
        wd.writeBytes("RIFF");
        wd.writeInt(Integer.reverseBytes(36 + 64));
        wd.writeBytes("WAVE");
        wd.writeBytes("fmt ");
        wd.writeInt(Integer.reverseBytes(16));
        wd.writeShort(Short.reverseBytes((short) 1));
        wd.writeShort(Short.reverseBytes((short) 1));
        wd.writeInt(Integer.reverseBytes(16000));
        wd.writeInt(Integer.reverseBytes(32000));
        wd.writeShort(Short.reverseBytes((short) 2));
        wd.writeShort(Short.reverseBytes((short) 16));
        wd.writeBytes("data");
        wd.writeInt(Integer.reverseBytes(64));
        wd.write(new byte[64]);
        wd.flush();
        final byte[] wavBytes = wavOut.toByteArray();

        android.media.MediaExtractor extractor = new android.media.MediaExtractor();
        extractor.setDataSource(new android.media.MediaDataSource() {
            @Override
            public int readAt(long position, byte[] buffer, int offset, int size) {
                if (position < 0 || position >= wavBytes.length) return -1;
                int n = (int) Math.min(size, wavBytes.length - position);
                System.arraycopy(wavBytes, (int) position, buffer, offset, n);
                return n;
            }
            @Override
            public long getSize() {
                return wavBytes.length;
            }
            @Override
            public void close() {
            }
        });
        require(extractor.getTrackCount() == 1, "MediaExtractor WAV trackCount");
        extractor.selectTrack(0);
        java.nio.ByteBuffer sampleBuf = java.nio.ByteBuffer.allocate(128);
        require(extractor.readSampleData(sampleBuf, 0) == 64, "MediaExtractor WAV readSampleData");
        extractor.release();

        require(!android.media.MediaDrm.isCryptoSchemeSupported(new java.util.UUID(1L, 2L)), "MediaDrm unsupported scheme");
        require(!android.media.MediaCas.isSystemIdSupported(0x0B00), "MediaCas unsupported systemId");

        // 2. android.view (FocusFinder, ContentInfo, WindowInsets, IntFlagMapping, TextClassifier, HandwritingGesture)
        FrameLayout root = new FrameLayout(context);
        View topBtn = new View(context);
        topBtn.setFocusable(true);
        View bottomBtn = new View(context);
        bottomBtn.setFocusable(true);
        root.addView(topBtn);
        root.addView(bottomBtn);
        root.layout(0, 0, 200, 200);
        topBtn.layout(10, 10, 110, 50);
        bottomBtn.layout(10, 80, 110, 120);
        View nextFocus = FocusFinder.getInstance().findNextFocus(root, topBtn, View.FOCUS_DOWN);
        require(nextFocus == bottomBtn, "FocusFinder 2D directional FOCUS_DOWN");

        ClipData clip = ClipData.newPlainText("items", "alpha");
        clip.addItem(new ClipData.Item(Uri.parse("https://shiraz.example")));
        ContentInfo contentInfo = new ContentInfo.Builder(clip, ContentInfo.SOURCE_CLIPBOARD).build();
        android.util.Pair<ContentInfo, ContentInfo> split = contentInfo.partition(new Predicate<ClipData.Item>() {
            @Override
            public boolean test(ClipData.Item item) {
                return item.getUri() != null;
            }
        });
        require(split.first != null && split.second != null, "ContentInfo.partition");

        WindowInsets builtInsets = new WindowInsets.Builder()
                .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 48, 0, 0))
                .build();
        require(builtInsets.getInsets(WindowInsets.Type.statusBars()).top == 48, "WindowInsets.Builder + Type.statusBars");

        IntFlagMapping flagMapping = new IntFlagMapping();
        flagMapping.add(0x3, 0x1, "LEFT");
        flagMapping.add(0x3, 0x2, "RIGHT");
        Set<String> mappedFlags = flagMapping.get(0x1);
        require(mappedFlags.contains("LEFT") && !mappedFlags.contains("RIGHT"), "IntFlagMapping");

        TextClassificationManager tcm = new TextClassificationManager();
        TextClassifier classifier = tcm.getTextClassifier();
        TextLinks links = classifier.generateLinks(
                new TextLinks.Request.Builder("Visit https://shiraz.dev today").build());
        require(!links.getLinks().isEmpty(), "TextClassifier.generateLinks URL detection");

        SelectGesture selectGesture = new SelectGesture.Builder()
                .setGranularity(SelectGesture.GRANULARITY_WORD)
                .setSelectionArea(new RectF(0f, 0f, 100f, 30f))
                .setFallbackText("fallback")
                .build();
        p = Parcel.obtain();
        selectGesture.writeToParcel(p, 0);
        p.setDataPosition(0);
        SelectGesture selectOut = SelectGesture.CREATOR.createFromParcel(p);
        require("fallback".equals(selectOut.getFallbackText()), "SelectGesture parcel");
        p.recycle();

        KeyboardShortcutGroup ksg = new KeyboardShortcutGroup("Edit");
        ksg.addItem(new KeyboardShortcutInfo("Copy", KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON));
        require(ksg.getItems().size() == 1, "KeyboardShortcutGroup");

        // 3. android.app (ActivityOptions, PictureInPictureParams, Notification.MessagingStyle, Policy, CrashInfo)
        Bundle optsBundle = ActivityOptions.makeBasic().toBundle();
        require(optsBundle != null, "ActivityOptions.makeBasic");

        PictureInPictureParams pip = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9))
                .setAutoEnterEnabled(true)
                .build();
        p = Parcel.obtain();
        pip.writeToParcel(p, 0);
        p.setDataPosition(0);
        PictureInPictureParams pipOut = PictureInPictureParams.CREATOR.createFromParcel(p);
        require(new Rational(16, 9).equals(pipOut.getAspectRatio()), "PictureInPictureParams parcel");
        p.recycle();

        Person senderPerson = new Person.Builder().setName("Darius").build();
        Notification.MessagingStyle msgStyle = new Notification.MessagingStyle(senderPerson)
                .setConversationTitle("Team")
                .addMessage(new Notification.MessagingStyle.Message("Ship 100%!", 1000L, senderPerson));
        Notification notif = new Notification.Builder(context, "general")
                .setContentTitle("Messages")
                .setStyle(msgStyle)
                .build();
        require(notif != null && msgStyle.getMessages().size() == 1, "Notification.MessagingStyle");

        NotificationManager.Policy policy = new NotificationManager.Policy(
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS, 0, 0);
        p = Parcel.obtain();
        policy.writeToParcel(p, 0);
        p.setDataPosition(0);
        NotificationManager.Policy policyOut = NotificationManager.Policy.CREATOR.createFromParcel(p);
        require(policyOut.priorityCategories == NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS, "NotificationManager.Policy parcel");
        p.recycle();

        ApplicationErrorReport.CrashInfo crashInfo = new ApplicationErrorReport.CrashInfo(
                new IllegalArgumentException("sample crash"));
        require(IllegalArgumentException.class.getName().equals(crashInfo.exceptionClassName), "CrashInfo");
        require(RemoteAction.CREATOR.newArray(1).length == 1, "RemoteAction CREATOR");

        System.out.println("FRAMEWORK_F7_100_PERCENT_OK");
    }
}
