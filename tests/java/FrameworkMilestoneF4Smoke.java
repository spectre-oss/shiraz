// SPDX-License-Identifier: GPL-3.0-only
import android.accessibilityservice.AccessibilityGestureEvent;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.MagnificationConfig;
import android.accessibilityservice.TouchInteractionController;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanSettings;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.virtual.VirtualDevice;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Path;
import android.inputmethodservice.ExtractEditText;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.location.Address;
import android.location.Criteria;
import android.location.GnssCapabilities;
import android.location.Location;
import android.location.LocationRequest;
import android.location.altitude.AltitudeConverter;
import android.location.provider.ProviderProperties;
import android.net.MacAddress;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiSsid;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.security.AppUriAuthenticationPolicy;
import android.security.ConfirmationNotAvailableException;
import android.security.ConfirmationPrompt;
import android.security.KeyChain;
import android.security.KeyStoreException;
import android.security.identity.AccessControlProfile;
import android.security.identity.AccessControlProfileId;
import android.security.identity.PersonalizationData;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.telecom.CallAttributes;
import android.telecom.CallEndpoint;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.VideoProfile;
import android.util.Size;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.SurroundingText;
import android.view.inputmethod.TextAttribute;
import android.widget.inline.InlinePresentationSpec;
import android.webkit.DateSorter;
import android.webkit.TracingConfig;
import android.webkit.TracingController;
import android.webkit.WebMessage;
import android.webkit.WebMessagePort;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

public final class FrameworkMilestoneF4Smoke {
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

    private static void verifyNfc() throws Exception {
        NdefRecord uriRec = NdefRecord.createUri("https://shiraz.spectre.org/docs");
        NdefRecord mimeRec = NdefRecord.createMime(
                "application/vnd.shiraz+json", "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        NdefRecord textRec = NdefRecord.createTextRecord("en", "Hello NFC");
        NdefRecord appRec = NdefRecord.createApplicationRecord("org.shiraz.app");

        NdefMessage msg = new NdefMessage(new NdefRecord[] { uriRec, mimeRec, textRec, appRec });
        byte[] wire = msg.toByteArray();
        check(wire.length > 20, "NdefMessage binary framing size");

        NdefMessage parsed = new NdefMessage(wire);
        NdefRecord[] recs = parsed.getRecords();
        check(recs.length == 4, "NdefMessage record count");
        check(Uri.parse("https://shiraz.spectre.org/docs").equals(recs[0].toUri()), "NDEF RTD_URI decode");
        check("application/vnd.shiraz+json".equals(recs[1].toMimeType()), "NDEF MIME type decode");

        NdefMessage parcelMsg = roundTripParcel(msg, NdefMessage.CREATOR);
        check(parcelMsg.getRecords().length == 4, "NdefMessage Parcel round-trip");
    }

    private static void verifyLocation(Context ctx) throws Exception {
        float[] results = new float[3];
        // San Francisco (37.7749, -122.4194) to Los Angeles (34.0522, -118.2437) ~ 559 km
        Location.distanceBetween(37.7749, -122.4194, 34.0522, -118.2437, results);
        check(results[0] > 550000f && results[0] < 570000f, "WGS-84 SF->LA distance ~559km: " + results[0]);

        String dms = Location.convert(-122.4194, Location.FORMAT_SECONDS);
        double parsedDeg = Location.convert(dms);
        check(Math.abs(parsedDeg - (-122.4194)) < 0.01, "Location DMS convert round-trip");

        Location loc = new Location("gps");
        loc.setLatitude(51.5074);
        loc.setLongitude(-0.1278);
        loc.setAltitude(100.0);
        loc.setVerticalAccuracyMeters(2.5f);
        AltitudeConverter altConv = new AltitudeConverter();
        altConv.addMslAltitudeToLocation(ctx, loc);
        check(loc.hasMslAltitude(), "AltitudeConverter EGM96 MSL altitude computed");

        Location locOut = roundTripParcel(loc, Location.CREATOR);
        check(Math.abs(locOut.getLatitude() - 51.5074) < 1e-6, "Location Parcel round-trip");

        Address addr = new Address(Locale.US);
        addr.setLocality("San Francisco");
        addr.setPostalCode("94103");
        addr.setLatitude(37.7749);
        addr.setLongitude(-122.4194);
        Address addrOut = roundTripParcel(addr, Address.CREATOR);
        check("San Francisco".equals(addrOut.getLocality()) && "94103".equals(addrOut.getPostalCode()),
                "Address Parcel round-trip");

        Criteria crit = new Criteria();
        crit.setAccuracy(Criteria.ACCURACY_FINE);
        Criteria critOut = roundTripParcel(crit, Criteria.CREATOR);
        check(critOut.getAccuracy() == Criteria.ACCURACY_FINE, "Criteria Parcel");

        LocationRequest req = new LocationRequest.Builder(5000L)
                .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                .setMaxUpdates(10)
                .build();
        LocationRequest reqOut = roundTripParcel(req, LocationRequest.CREATOR);
        check(reqOut.getIntervalMillis() == 5000L && reqOut.getMaxUpdates() == 10, "LocationRequest Parcel");

        GnssCapabilities caps = new GnssCapabilities.Builder().setHasMeasurements(true).build();
        GnssCapabilities capsOut = roundTripParcel(caps, GnssCapabilities.CREATOR);
        check(capsOut.hasMeasurements(), "GnssCapabilities Parcel");

        ProviderProperties props = new ProviderProperties.Builder()
                .setHasAltitudeSupport(true)
                .setAccuracy(ProviderProperties.ACCURACY_FINE)
                .build();
        ProviderProperties propsOut = roundTripParcel(props, ProviderProperties.CREATOR);
        check(propsOut.hasAltitudeSupport(), "ProviderProperties Parcel");
    }

    private static void verifyTelecom() {
        PhoneAccountHandle handle = new PhoneAccountHandle(
                new ComponentName("org.shiraz.dialer", "org.shiraz.dialer.ConnSvc"), "acct-1");
        PhoneAccountHandle handleOut = roundTripParcel(handle, PhoneAccountHandle.CREATOR);
        check("acct-1".equals(handleOut.getId()), "PhoneAccountHandle Parcel");

        CallAttributes attrs = new CallAttributes.Builder(
                handle, CallAttributes.DIRECTION_OUTGOING, "Alice", Uri.parse("tel:+15551234567"))
                .setCallType(CallAttributes.AUDIO_CALL)
                .build();
        CallAttributes attrsOut = roundTripParcel(attrs, CallAttributes.CREATOR);
        check("Alice".equals(String.valueOf(attrsOut.getDisplayName())), "CallAttributes Parcel");

        CallEndpoint ep = new CallEndpoint(
                "Speaker", CallEndpoint.TYPE_SPEAKER, new android.os.ParcelUuid(UUID.randomUUID()));
        CallEndpoint epOut = roundTripParcel(ep, CallEndpoint.CREATOR);
        check(epOut.getEndpointType() == CallEndpoint.TYPE_SPEAKER, "CallEndpoint Parcel");

        DisconnectCause dc = new DisconnectCause(DisconnectCause.LOCAL, "Ended", "Call ended", "USER_HANGUP");
        DisconnectCause dcOut = roundTripParcel(dc, DisconnectCause.CREATOR);
        check(dcOut.getCode() == DisconnectCause.LOCAL, "DisconnectCause Parcel");

        VideoProfile vp = new VideoProfile(VideoProfile.STATE_BIDIRECTIONAL, VideoProfile.QUALITY_HIGH);
        VideoProfile vpOut = roundTripParcel(vp, VideoProfile.CREATOR);
        check(vpOut.getVideoState() == VideoProfile.STATE_BIDIRECTIONAL, "VideoProfile Parcel");
    }

    private static void verifyAccessibilityAndIme(Context ctx) {
        Path swipePath = new Path();
        swipePath.moveTo(50f, 100f);
        swipePath.lineTo(250f, 100f);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(swipePath, 10L, 200L);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        check(gesture.getStrokeCount() == 1
                && gesture.getStroke(0).getStartTime() == 10L
                && gesture.getStroke(0).getDuration() == 200L, "GestureDescription stroke");

        MagnificationConfig mag = new MagnificationConfig.Builder()
                .setMode(MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN)
                .setScale(2.0f)
                .setCenterX(240f)
                .setCenterY(400f)
                .build();
        MagnificationConfig magOut = roundTripParcel(mag, MagnificationConfig.CREATOR);
        check(Math.abs(magOut.getScale() - 2.0f) < 1e-4f, "MagnificationConfig Parcel");

        AccessibilityGestureEvent age = new AccessibilityGestureEvent(
                1, 0, Collections.<MotionEvent>emptyList());
        AccessibilityGestureEvent ageOut = roundTripParcel(age, AccessibilityGestureEvent.CREATOR);
        check(ageOut.getGestureId() == 1, "AccessibilityGestureEvent Parcel");

        check(AccessibilityServiceInfo.feedbackTypeToString(AccessibilityServiceInfo.FEEDBACK_SPOKEN) != null,
                "AccessibilityServiceInfo.feedbackTypeToString");
        check(TouchInteractionController.stateToString(TouchInteractionController.STATE_CLEAR) != null,
                "TouchInteractionController.stateToString");

        AccessibilityWindowInfo winInfo = new AccessibilityWindowInfo();
        winInfo.recycle();

        AccessibilityNodeInfo.RangeInfo range = AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0f, 100f, 42f);
        check(Math.abs(range.getCurrent() - 42f) < 1e-4f, "AccessibilityNodeInfo.RangeInfo");

        SurroundingText st = new SurroundingText("hello world", 0, 5, 10);
        SurroundingText stOut = roundTripParcel(st, SurroundingText.CREATOR);
        check("hello world".equals(String.valueOf(stOut.getText())) && stOut.getOffset() == 10,
                "SurroundingText Parcel");

        TextAttribute ta = new TextAttribute.Builder()
                .setTextConversionSuggestions(Collections.singletonList("helo"))
                .build();
        TextAttribute taOut = roundTripParcel(ta, TextAttribute.CREATOR);
        check(taOut.getTextConversionSuggestions().size() == 1, "TextAttribute Parcel");

        // InputMethodService + Keyboard + ExtractEditText + CompletionInfo
        CompletionInfo ci = new CompletionInfo(7L, 1, "shiraz", "Shiraz");
        CompletionInfo ciOut = roundTripParcel(ci, CompletionInfo.CREATOR);
        check(ciOut.getId() == 7L && "shiraz".equals(String.valueOf(ciOut.getText())), "CompletionInfo Parcel");

        android.widget.inline.InlinePresentationSpec ips =
                new android.widget.inline.InlinePresentationSpec.Builder(
                        new Size(80, 32), new Size(240, 48)).build();
        android.widget.inline.InlinePresentationSpec ipsOut =
                roundTripParcel(ips, android.widget.inline.InlinePresentationSpec.CREATOR);
        check(ipsOut.getMinSize().getWidth() == 80 && ipsOut.getMaxSize().getHeight() == 48,
                "InlinePresentationSpec Parcel");

        Keyboard kb = new Keyboard(ctx, 0, "abcdef", 3, 0);
        check(kb.getKeys().size() == 6 && kb.getHeight() > 0, "Keyboard char layout");
        KeyboardView kbView = new KeyboardView(ctx, null);
        kbView.setKeyboard(kb);
        check(kbView.setShifted(true) && kbView.isShifted(), "KeyboardView shift state");

        ExtractEditText eet = new ExtractEditText(ctx);
        ExtractedText ext = new ExtractedText();
        ext.text = "IME buffer";
        ext.selectionStart = 0;
        ext.selectionEnd = 3;
        eet.setExtractedText(ext);
        check("IME buffer".equals(eet.getText().toString()) && eet.isInputMethodTarget(),
                "ExtractEditText extracted text");

        InputMethodService ims = new InputMethodService();
        check("Search".equals(String.valueOf(ims.getTextForImeAction(EditorInfo.IME_ACTION_SEARCH))),
                "InputMethodService.getTextForImeAction");
    }

    private static void verifyWebkitAndSecurity(Context ctx) throws Exception {
        DateSorter sorter = new DateSorter(ctx);
        check(sorter.getIndex(System.currentTimeMillis()) == 0
                && "Today".equals(sorter.getLabel(0)), "DateSorter Today bucket");

        WebResourceResponse resp = new WebResourceResponse("application/json", "UTF-8", null);
        resp.setStatusCodeAndReasonPhrase(201, "Created");
        check(resp.getStatusCode() == 201 && "Created".equals(resp.getReasonPhrase()),
                "WebResourceResponse 201 Created");

        WebView wv = new WebView(ctx);
        wv.getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        check(wv.getSettings().getLayoutAlgorithm() == WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING,
                "WebSettings.LayoutAlgorithm");

        final AtomicReference<String> msgReceived = new AtomicReference<String>();
        WebMessagePort[] channel = wv.createWebMessageChannel();
        channel[1].setWebMessageCallback(new WebMessagePort.WebMessageCallback() {
            @Override
            public void onMessage(WebMessagePort port, WebMessage message) {
                msgReceived.set(message.getData());
            }
        });
        channel[0].postMessage(new WebMessage("ping-over-port"));
        check("ping-over-port".equals(msgReceived.get()), "WebMessagePort entangled delivery");
        channel[0].close();
        channel[1].close();

        WebViewDatabase db = WebViewDatabase.getInstance(ctx);
        db.setHttpAuthUsernamePassword("api.example.com", "realm1", "bob", "s3cr3t");
        String[] creds = db.getHttpAuthUsernamePassword("api.example.com", "realm1");
        check(creds != null && "bob".equals(creds[0]) && "s3cr3t".equals(creds[1]),
                "WebViewDatabase HTTP auth store");
        db.clearHttpAuthUsernamePassword();

        TracingConfig tc = new TracingConfig.Builder()
                .addCategories(TracingConfig.CATEGORIES_WEB_DEVELOPER)
                .addCategories("blink.user_timing")
                .build();
        TracingController controller = TracingController.getInstance();
        controller.start(tc);
        check(controller.isTracing(), "TracingController started");
        ByteArrayOutputStream traceOut = new ByteArrayOutputStream();
        Executor direct = new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
        check(controller.stop(traceOut, direct) && !controller.isTracing() && traceOut.size() > 0,
                "TracingController stop output");

        // android.security
        AppUriAuthenticationPolicy policy = new AppUriAuthenticationPolicy.Builder()
                .addAppAndUriMapping("org.shiraz.app", Uri.parse("https://auth.example.com"), "cert-1")
                .build();
        AppUriAuthenticationPolicy policyOut = roundTripParcel(policy, AppUriAuthenticationPolicy.CREATOR);
        check(policy.equals(policyOut), "AppUriAuthenticationPolicy Parcel");

        check(KeyChain.isKeyAlgorithmSupported("RSA") && KeyChain.isKeyAlgorithmSupported("EC")
                && !KeyChain.isKeyAlgorithmSupported("RC4"), "KeyChain.isKeyAlgorithmSupported");
        check(!ConfirmationPrompt.isSupported(ctx), "ConfirmationPrompt.isSupported == false");
        try {
            new ConfirmationPrompt.Builder(ctx)
                    .setPromptText("Approve $10")
                    .setExtraData(new byte[] { 1, 2 })
                    .build()
                    .presentPrompt(direct, new android.security.ConfirmationCallback() {});
            throw new AssertionError("Expected ConfirmationNotAvailableException");
        } catch (ConfirmationNotAvailableException expected) {
        }

        KeyStoreException kse = new KeyStoreException(
                -68, "auth", KeyStoreException.ERROR_USER_AUTHENTICATION_REQUIRED,
                KeyStoreException.RETRY_NEVER);
        check(kse.requiresUserAuthentication() && !kse.isTransientFailure(), "KeyStoreException flags");

        KeyProtection kp = new KeyProtection.Builder(
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build();
        check(kp.isDigestsSpecified() && kp.getBlockModes().length == 1, "KeyProtection.Builder");

        AccessControlProfileId acpId = new AccessControlProfileId(1);
        AccessControlProfile acp = new AccessControlProfile.Builder(acpId)
                .setUserAuthenticationRequired(false)
                .build();
        PersonalizationData pd = new PersonalizationData.Builder()
                .addAccessControlProfile(acp)
                .putEntry("org.iso.18013.5.1", "given_name",
                        Collections.singletonList(acpId), "Shiraz".getBytes(StandardCharsets.UTF_8))
                .build();
        check(pd != null, "PersonalizationData.Builder");
    }

    private static void verifyBluetooth() {
        check(BluetoothAdapter.checkBluetoothAddress("00:11:22:AA:BB:CC"),
                "Valid Bluetooth MAC accepted");
        check(!BluetoothAdapter.checkBluetoothAddress("00:11:22:GG:BB:CC")
                && !BluetoothAdapter.checkBluetoothAddress("invalid"),
                "Invalid Bluetooth MAC rejected");

        Parcel bcParcel = Parcel.obtain();
        BluetoothClass btClass;
        try {
            // AUDIO_VIDEO_WEARABLE_HEADSET (0x0404) | Service.AUDIO (0x200000)
            bcParcel.writeInt(0x200404);
            bcParcel.setDataPosition(0);
            btClass = BluetoothClass.CREATOR.createFromParcel(bcParcel);
        } finally {
            bcParcel.recycle();
        }
        check(btClass.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO
                && btClass.getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET
                && btClass.hasService(BluetoothClass.Service.AUDIO),
                "BluetoothClass 24-bit CoD bitmask extraction");

        BluetoothGattCharacteristic ch = new BluetoothGattCharacteristic(
                UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb"),
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        ch.setValue(72, BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        Integer hr = ch.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        check(hr != null && hr.intValue() == 72, "BluetoothGattCharacteristic UINT16 pack/unpack");

        ScanSettings ss = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        ScanSettings ssOut = roundTripParcel(ss, ScanSettings.CREATOR);
        check(ssOut.getScanMode() == ScanSettings.SCAN_MODE_LOW_LATENCY, "ScanSettings Parcel");

        AdvertiseSettings as = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .build();
        AdvertiseSettings asOut = roundTripParcel(as, AdvertiseSettings.CREATOR);
        check(asOut.getMode() == AdvertiseSettings.ADVERTISE_MODE_BALANCED && asOut.isConnectable(),
                "AdvertiseSettings Parcel");
        check(BluetoothStatusCodes.SUCCESS == 0, "BluetoothStatusCodes.SUCCESS == 0");
    }

    private static void verifyCompanionAndWifi() {
        MacAddress mac = MacAddress.fromString("02:1A:2B:3C:4D:5E");
        check(mac.getAddressType() == MacAddress.TYPE_UNICAST && mac.isLocallyAssigned(),
                "MacAddress locally-assigned unicast flags");
        MacAddress macOut = roundTripParcel(mac, MacAddress.CREATOR);
        check(mac.equals(macOut) && "02:1a:2b".equals(macOut.toOuiString()), "MacAddress Parcel");

        WifiSsid ssid = WifiSsid.fromBytes("Shiraz-5G".getBytes(StandardCharsets.UTF_8));
        WifiSsid ssidOut = roundTripParcel(ssid, WifiSsid.CREATOR);
        check(ssid.equals(ssidOut), "WifiSsid Parcel");

        check(ScanResult.convertChannelToFrequencyMhzIfSupported(36, ScanResult.WIFI_BAND_5_GHZ) == 5180,
                "ScanResult 5GHz channel 36 -> 5180 MHz");
        check(ScanResult.convertFrequencyMhzToChannelIfSupported(2437) == 6,
                "ScanResult 2437 MHz -> channel 6");

        BluetoothDeviceFilter btFilter = new BluetoothDeviceFilter.Builder()
                .setAddress("00:11:22:AA:BB:CC")
                .build();
        AssociationRequest assocReq = new AssociationRequest.Builder()
                .addDeviceFilter(btFilter)
                .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                .setDisplayName("Pixel Watch")
                .setSingleDevice(true)
                .build();
        AssociationRequest assocOut = roundTripParcel(assocReq, AssociationRequest.CREATOR);
        check(AssociationRequest.DEVICE_PROFILE_WATCH.equals(assocOut.getDeviceProfile())
                && assocOut.isSingleDevice()
                && "Pixel Watch".equals(String.valueOf(assocOut.getDisplayName())),
                "AssociationRequest Parcel");

        VirtualDevice vd = new VirtualDevice(
                7, "companion:7", "ShirazVirtual", "Shiraz Display", new int[] { 2 }, true);
        VirtualDevice vdOut = roundTripParcel(vd, VirtualDevice.CREATOR);
        check(vdOut.getDeviceId() == 7 && vdOut.hasCustomSensorSupport()
                && vdOut.getDisplayIds().length == 1 && vdOut.getDisplayIds()[0] == 2,
                "VirtualDevice Parcel");
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        File tempDir = Files.createTempDirectory("shiraz-f4-smoke").toFile();
        Context ctx = createSmokeContext(tempDir);
        try {
            verifyNfc();
            verifyLocation(ctx);
            verifyTelecom();
            verifyAccessibilityAndIme(ctx);
            verifyWebkitAndSecurity(ctx);
            verifyBluetooth();
            verifyCompanionAndWifi();
            System.out.println("FRAMEWORK_F4_OK");
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
