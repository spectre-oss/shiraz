// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.Application;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.io.File;
import android.os.Process;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import android.os.Build;
import android.util.Log;
import java.net.UnknownHostException;

public final class FixtureApplication extends Application {
    private static final File initialPrimary = android.os.Environment.getExternalStorageDirectory();
    static boolean created;
    @Override public void onCreate() {
        super.onCreate();
        PermissionFixture.check(this);
        BuildFixture.check(Build.VERSION.INCREMENTAL);
        if (!initialPrimary.equals(android.os.Environment.getExternalStorageDirectory())) throw new AssertionError("Storage before Application class initialization");
        if (Build.VERSION.SDK_INT < 27 || Build.VERSION.CODENAME == null) throw new AssertionError("Minimum SDK profile");
        if (!"org.shiraz.fixture".equals(getPackageName()) || getApplicationContext() != this)
            throw new AssertionError("Application context identity");
        if (!getFilesDir().isDirectory() || !getCacheDir().isDirectory())
            throw new AssertionError("Application directories");
        System.setProperty("log.tag.ShirazFixture", "DEBUG");
        if (!Log.isLoggable("ShirazFixture", Log.DEBUG) || Log.isLoggable("ShirazFixture", Log.VERBOSE))
            throw new AssertionError("Log filtering");
        if (!Log.getStackTraceString(new UnknownHostException("fixture")).isEmpty())
            throw new AssertionError("UnknownHostException trace suppression");
        if (!Log.getStackTraceString(new IllegalArgumentException("fixture trace")).contains("fixture trace"))
            throw new AssertionError("Throwable log trace");
        Log.v("ShirazFixture", "SHIRAZ_FIXTURE_LOG_FILTERED");
        Log.d("ShirazFixture", "SHIRAZ_FIXTURE_LOG_OK");
        if (Process.myPid() <= 0 || Process.myTid() <= 0 || Process.myUid() < 0 || Process.getElapsedCpuTime() < 0)
            throw new AssertionError("Host process identity/clock");
        final AtomicInteger threadId = new AtomicInteger();
        Thread worker = new Thread(new Runnable() { @Override public void run() { threadId.set(Process.myTid()); } });
        worker.start();
        try { worker.join(); } catch (InterruptedException error) { throw new AssertionError(error); }
        if (threadId.get() <= 0 || threadId.get() == Process.myTid()) throw new AssertionError("Distinct native thread IDs");
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != getSystemService(Context.ACTIVITY_SERVICE)) throw new AssertionError("Service identity");
        List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        boolean own = false;
        for (ActivityManager.RunningAppProcessInfo info : processes)
            if (info.pid == Process.myPid() && info.uid == Process.myUid() && info.processName.equals(getPackageName())) own = true;
        if (!own) throw new AssertionError("Loaded application process info");
        System.out.println("SHIRAZ_FIXTURE_PROCESS_OK");
        String requestedLocale = System.getenv("SHIRAZ_FIXTURE_LOCALE");
        if (requestedLocale != null) java.util.Locale.setDefault(java.util.Locale.forLanguageTag(requestedLocale));
        PackageManager packages = getPackageManager();
        try {
            PackageInfo info = packages.getPackageInfo(getPackageName(), 0);
            if(info.firstInstallTime<=0||info.lastUpdateTime<info.firstInstallTime)throw new AssertionError("Host package timestamp metadata");
            if (!"1.0".equals(info.versionName) || info.versionCode != 1 || !info.packageName.equals(getPackageName()))
                throw new AssertionError("APK version metadata");
            info.versionName = "changed";
            if (!"1.0".equals(packages.getPackageInfo(getPackageName(), 0).versionName)) throw new AssertionError("Metadata snapshot aliases state");
            ApplicationInfo applicationInfo = getApplicationInfo();
            if (!applicationInfo.sourceDir.equals(getPackageCodePath()) || !applicationInfo.publicSourceDir.equals(getPackageResourcePath()) ||
                !new File(applicationInfo.sourceDir).isFile() || !new File(applicationInfo.dataDir).equals(getFilesDir().getParentFile()) ||
                applicationInfo.uid != Process.myUid() || applicationInfo.minSdkVersion != 27 || applicationInfo.targetSdkVersion != 35 ||
                (applicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) == 0 || !applicationInfo.enabled)
                throw new AssertionError("Application metadata paths/profile");
            if (!"Shiraz activity fixture – café".contentEquals(packages.getApplicationLabel(applicationInfo))) throw new AssertionError("UTF-8 application label");
            try { packages.getPackageInfo("org.shiraz.fixture.absent", 0); throw new AssertionError("Unknown package accepted"); }
            catch (PackageManager.NameNotFoundException expected) { }
            try { packages.getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES); throw new AssertionError("Unsupported signature query accepted"); }
            catch (UnsupportedOperationException expected) { }
            PackageInfo wide = new PackageInfo();
            wide.setLongVersionCode(0x2ffffffffL);
            if (wide.versionCode != -1 || wide.getLongVersionCode() != 0x2ffffffffL) throw new AssertionError("Wide package version");
        } catch (PackageManager.NameNotFoundException error) { throw new AssertionError(error); }
        System.out.println("SHIRAZ_FIXTURE_PACKAGE_OK");
        android.content.res.Resources resources = getResources();
        if (resources.getIdentifier("welcome", "string", getPackageName()) != R.string.welcome
                || resources.getIdentifier("string/welcome", "integer", getPackageName()) != R.string.welcome
                || resources.getIdentifier(getPackageName() + ":string/welcome", null, null) != R.string.welcome
                || resources.getIdentifier(getPackageName() + ":string/welcome", "integer", "absent.package") != R.string.welcome
                || resources.getIdentifier("welcome", "integer", getPackageName()) != R.integer.welcome
                || resources.getIdentifier("welcome_alias", "string", getPackageName()) != R.string.welcome_alias
                || resources.getIdentifier(Integer.toString(R.string.welcome), null, null) != R.string.welcome
                || resources.getIdentifier("123", null, null) != 123 || resources.getIdentifier("-1", null, null) != -1)
            throw new AssertionError("Resource name/default/decimal identifier lookup");
        if (resources.getIdentifier("missing", "string", getPackageName()) != 0
                || resources.getIdentifier("welcome", "missing", getPackageName()) != 0
                || resources.getIdentifier("welcome", "string", "absent.package") != 0
                || resources.getIdentifier("welcome", null, null) != 0
                || resources.getIdentifier("", "string", getPackageName()) != 0
                || resources.getIdentifier("welcome\u0000suffix", "string", getPackageName()) != 0)
            throw new AssertionError("Missing resource identifier");
        try { resources.getIdentifier(null, "string", getPackageName()); throw new AssertionError("Null name accepted"); }
        catch (NullPointerException expected) { }
        if (!"Hello café 🌍".equals(getString(R.string.welcome)) || !getString(R.string.welcome).equals(getString(R.string.welcome_alias)))
            throw new AssertionError("UTF-16 string/reference resources");
        if (!"Ada has 3 maps".equals(getString(R.string.formatted, "Ada", 3))) throw new AssertionError("Formatted string");
        String localized = java.util.Locale.getDefault().getLanguage().equals("fr") ? "Langue française" : "Default language";
        if (!localized.equals(getString(R.string.localized))) throw new AssertionError("Configured locale resource");
        for (int id : new int[]{0, 0x7fffffff}) {
            try { getString(id); throw new AssertionError("Invalid string resource accepted"); }
            catch (android.content.res.Resources.NotFoundException expected) { }
        }
        if (!"42".equals(getString(R.integer.not_string)) || !"true".equals(getString(R.bool.flag)) ||
            !"#ff123456".equals(getString(R.color.accent)) || !"12.5dip".equals(getString(R.dimen.distance)))
            throw new AssertionError("Scalar resource text coercion");
        System.out.println("SHIRAZ_FIXTURE_RESOURCES_OK");
        ConfigurationFixture.check(getResources());
        DateFormatFixture.check(this);
        ThemeFixture.check(this);
        ColorResourceFixture.check(this);
        BundleFixture.check();
        try { SQLiteFixture.check(this); } catch (Exception error) { throw new AssertionError(error); }
        try { XmlFixture.check(); } catch (Exception error) { throw new AssertionError(error); }
        try { BroadcastFixture.check(this); } catch (Exception error) { throw new AssertionError(error); }
        NotificationFixture.check(this);
        LocationFixture.check();
        SensorFixture.check(this);
        KeyboardFixture.stateChecks(this);KeyboardFixture.clusterXml(this);
        try { LruFixture.check(); } catch (Exception error) { throw new AssertionError(error); }
        DisplayFixture.check(this);
        TypefaceFixture.check();
        StrokeFixture.check();
        BitmapFixture.check();
        DrawableFixture.check();
        DrawableFixture.checkView(this);
        ImageViewFixture.check(this);
        ImageViewFixture.checkQueued(this);
        TextViewFixture.check(this);TextViewFixture.checkXml(this);FrameLayoutFixture.check(this);FrameLayoutFixture.checkForegroundXml(this);LinearLayoutFixture.check(this);LinearLayoutFixture.checkXml(this);
        LayoutListenerFixture.check(this);
        TreeObserverFixture.check(this);
        InflaterFixture.check(this);
        ComponentFixture.check(this);
        ComponentFixture.live(this);
        ColorFilterFixture.check();
        ColorMatrixFixture.check();
        ColorFilterFixture.checkView(this);
        BleValuesFixture.check();
        try { CompiledXmlFixture.check(getResources()); } catch(Exception error) {throw new AssertionError(error);}
        SystemResourcesFixture.check(getResources());
        TypedResourceFixture.check(getResources());
        try { PngFixture.check(getResources()); } catch (Exception error) { throw new AssertionError(error); }
        PngFixture.checkContext(this);
        PathFixture.check();
        DashFixture.check();
        RoundedFixture.check();
        PathMeasureFixture.check();
        VectorFixture.check(getResources());
        TextPaintFixture.check();
        TextSkewFixture.check();FakeBoldFixture.check();
        LayerFixture.check(getResources());
        AudioAttributesFixture.check();
        MediaSessionFixture.check(this);
        try { AssetFixture.check(this); SoundPoolFixture.start(this); } catch (Exception error) { throw new AssertionError(error); }
        try { StorageFixture.check(this); StrictFixture.check(this); QueueFixture.check(); } catch (Exception error) { throw new AssertionError(error); }
        try{ServiceMetadataFixture.check(this);ActivityInfoFixture.check(this);StyledAttributesFixture.check(this);}catch(Exception error){throw new AssertionError(error);}
        TextDirectionFixture.check();
        org.shiraz.fixture.BoringLayoutFixture.check();
        org.shiraz.fixture.StaticLayoutFixture.check();
        org.shiraz.fixture.TextBoundsFixture.check();
        TextScaleFixture.check();
        HintingFixture.check();
        ConfigurationMergeFixture.check(getResources());
        ConfigurationContextFixture.check(this);
        SavedStateFixture.check(this);
        WindowFixture.check(this);
        ServiceFixture.start(this);
        created = true;
        System.out.println("SHIRAZ_FIXTURE_APPLICATION_OK");
    }
}
