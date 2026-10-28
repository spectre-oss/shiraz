// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/** Initial host activity lifecycle and programmatic View startup frame. */
public final class ActivityRunner {
    static final class HostContext extends Context {
        private final String name;
        private final TestLocationConfig testLocation;
        private final CapturePermissions capture;
        private final Properties permissionMetadata=new Properties();
        private final Application application;
        private ActivityManager activities;
        private android.media.AudioManager audio;
        private HostWindowManager windows;
        private HostSensorManager sensors;
        private android.hardware.display.DisplayManager displays;
        private android.content.ClipboardManager clipboard;
        private android.telecom.TelecomManager telecom;
        private android.os.UserManager users;
        private android.app.job.JobScheduler jobs;
        private android.app.AlarmManager alarms;
        private android.app.DownloadManager downloads;
        private android.os.storage.StorageManager storageManager;
        private static final java.util.Set<String> ABSENT_SERVICES = new java.util.HashSet<String>(java.util.Arrays.asList("phone", "wifip2p", "bluetooth", "vibrator", "vibrator_manager", "usb", "nfc", "camera", "keyguard", "power", "batterymanager", "storage", "uimode", "media_session", "media_router", "mediaprojection", "device_policy", "launcherapps", "restrictions", "servicediscovery", "wallpaper", "search", "textservices", "textclassification", "captioning", "input", "usagestats", "role", "print", "dropbox", "consumer_ir", "tv_input", "locale"));
        private android.view.accessibility.AccessibilityManager accessibility;
        private android.app.NotificationManager notifications;
        private android.content.pm.ShortcutManager shortcuts;
        private HostBroadcasts broadcasts;
        final HostServices services;
        private final ActivityLaunchQueue launches;
        private final HostPackageManager packages;
        private final int width, height, densityDpi;
        private android.content.res.Resources resources;
        private android.content.res.Resources.Theme theme;
        private final HostPreferences preferences;
        private final HostStorage storage;
        private boolean foreground = true;
        HostContext(String name, File data, HostStorage storage, Application application, Properties metadata, int width, int height, int densityDpi) {
            this.densityDpi=densityDpi;
            testLocation=TestLocationConfig.fromEnvironment();permissionMetadata.putAll(metadata);
            capture=new CapturePermissions(data,metadata.getProperty("label",name),metadata);CapturePermissions.install(capture);
            this.width = width; this.height = height; this.name = name;
            this.application = application; this.packages = new HostPackageManager(metadata, this);
            this.preferences = new HostPreferences(new File(data, "preferences"));
            this.services = new HostServices(this,application,metadata);
            this.launches = new ActivityLaunchQueue(new ActivityLaunchResolver(name,packages));
            this.storage = storage;
        }
        private boolean ownSignaturePermission(String permission){int count=Integer.parseInt(permissionMetadata.getProperty("ownSignaturePermission.count","0"));if(count<0||count>1024)throw new IllegalArgumentException("Own permission count");if(!permission.startsWith(name+"."))return false;for(int i=0;i<count;i++)if(permission.equals(permissionMetadata.getProperty("ownSignaturePermission."+i)))return true;return false;}
        private boolean declaredNormalPermission(String permission){
            if(!"android.permission.INTERNET".equals(permission)&&!"android.permission.ACCESS_NETWORK_STATE".equals(permission))return false;
            int count=Integer.parseInt(permissionMetadata.getProperty("permission.count","0"));
            if(count<0||count>1024)throw new IllegalArgumentException("Permission count");
            for(int i=0;i<count;i++)if(permission.equals(permissionMetadata.getProperty("permission."+i)))return true;
            return false;
        }
        @Override public int checkPermission(String permission,int pid,int uid){
            if(permission==null)throw new IllegalArgumentException("permission");
            return pid==android.os.Process.myPid()&&uid==android.os.Process.myUid()&&(ownSignaturePermission(permission)||declaredNormalPermission(permission)||capture.granted(permission)||testLocation!=null&&testLocation.grants(permission,permissionMetadata))?PackageManager.PERMISSION_GRANTED:PackageManager.PERMISSION_DENIED;
        }
        @Override public void startActivity(android.content.Intent intent,android.os.Bundle options){if(options!=null&&!options.isEmpty())throw new UnsupportedOperationException("Activity options");launches.enqueue(intent);}
        @Override protected ActivityLaunchQueue shirazActivityLaunches(){return launches;}
        @Override protected HostServices shirazServices(){return services;}
        @Override protected synchronized HostBroadcasts shirazBroadcasts() { if (broadcasts == null) broadcasts = new HostBroadcasts(name); return broadcasts; }
        private android.view.inputmethod.InputMethodManager inputMethod;
        private android.view.autofill.AutofillManager autofill;
        private android.location.LocationManager locations;
        private android.net.ConnectivityManager connectivity;
        private android.net.wifi.WifiManager wifi;
        private android.view.LayoutInflater inflater;
        private android.os.PowerManager power;
        private android.app.KeyguardManager keyguard;
        @Override public synchronized Object getSystemService(String service) {
            if(STORAGE_SERVICE.equals(service)){if(storageManager==null)storageManager=android.os.storage.StorageManager.shirazForStorage(storage);return storageManager;}
            if(DOWNLOAD_SERVICE.equals(service)){if(downloads==null)downloads=android.app.DownloadManager.shirazForHost(this);return downloads;}
            if(VIBRATOR_SERVICE.equals(service))return new android.os.Vibrator();
            if("uimode".equals(service))return new android.app.UiModeManager(this);
            if(AUDIO_SERVICE.equals(service)){if(audio==null)audio=android.media.AudioManager.shirazForProcess();return audio;}
            if(POWER_SERVICE.equals(service)){if(power==null)power=new android.os.PowerManager();return power;}
            if(KEYGUARD_SERVICE.equals(service)){if(keyguard==null)keyguard=new android.app.KeyguardManager();return keyguard;}
            if(NFC_SERVICE.equals(service))return new android.nfc.NfcManager(this);
            if(ACCOUNT_SERVICE.equals(service))return android.accounts.AccountManager.get(this);
            if(TELEPHONY_SERVICE.equals(service))return new android.telephony.TelephonyManager();
            if(APP_OPS_SERVICE.equals(service))return new android.app.AppOpsManager(this);
            if(WIFI_SERVICE.equals(service)){capture.wifiAccess("service:requested");if(wifi==null)wifi=android.net.wifi.WifiManager.shirazForHost(capture);return wifi;}
            if(CONNECTIVITY_SERVICE.equals(service)){if(connectivity==null)connectivity=android.net.ConnectivityManager.shirazForHost();return connectivity;}
            if(INPUT_SERVICE.equals(service))return android.hardware.input.InputManager.getInstance();
            if(INPUT_METHOD_SERVICE.equals(service)){if(inputMethod==null)inputMethod=android.view.inputmethod.InputMethodManager.shirazHost(new android.view.inputmethod.InputMethodManager.KeyboardController(){public boolean setVisible(boolean visible){return NativeSurface.requestTextInput(visible);}});return inputMethod;}
            if(AUTOFILL_SERVICE.equals(service)){if(autofill==null)autofill=android.view.autofill.AutofillManager.shirazUnavailable();return autofill;}
            if(LOCATION_SERVICE.equals(service)){if(locations==null)locations=android.location.LocationManager.shirazForTest(this,testLocation);locations.shirazSetForeground(foreground);return locations;}
            if(SHORTCUT_SERVICE.equals(service)){if(shortcuts==null)shortcuts=android.content.pm.ShortcutManager.shirazForProcess(name);return shortcuts;}
            if(ACCESSIBILITY_SERVICE.equals(service)){if(accessibility==null)accessibility=android.view.accessibility.AccessibilityManager.shirazCreate();return accessibility;}
            if(LAYOUT_INFLATER_SERVICE.equals(service)){if(inflater==null)inflater=new HostLayoutInflater(this);return inflater;}
            if (NOTIFICATION_SERVICE.equals(service)) {
                if (notifications == null) {
                    android.os.StrictMode.ThreadPolicy old = android.os.StrictMode.allowThreadDiskWrites();
                    try { notifications = android.app.NotificationManager.shirazForProcess(name, storage.notificationPolicy()); }
                    finally { android.os.StrictMode.setThreadPolicy(old); }
                }
                return notifications;
            }
            if (SENSOR_SERVICE.equals(service)) {
                if (sensors == null) sensors = new HostSensorManager();
                return sensors;
            }
            if (CLIPBOARD_SERVICE.equals(service)) {
                if (clipboard == null) clipboard = new android.content.ClipboardManager();
                return clipboard;
            }
            if (DISPLAY_SERVICE.equals(service)) {
                if (displays == null) displays = new android.hardware.display.DisplayManager(getResources());
                return displays;
            }
            if (WINDOW_SERVICE.equals(service)) {
                if (windows == null) windows = new HostWindowManager(getResources());
                return windows;
            }
            if (USER_SERVICE.equals(service)) {
                if (users == null) users = new android.os.UserManager();
                return users;
            }
            if (ALARM_SERVICE.equals(service)) {
                if (alarms == null) alarms = new android.app.AlarmManager();
                return alarms;
            }
            if (JOB_SCHEDULER_SERVICE.equals(service)) {
                if (jobs == null) jobs = new android.app.job.JobScheduler();
                return jobs;
            }
            if (TELECOM_SERVICE.equals(service)) {
                if (telecom == null) telecom = new android.telecom.TelecomManager();
                return telecom;
            }
            if (ABSENT_SERVICES.contains(service)) return null;
            if (!ACTIVITY_SERVICE.equals(service)) throw new UnsupportedOperationException("System service is not implemented: " + service);
            if (activities == null) {
                activities = ActivityManager.shirazForProcess(Application.getProcessName()==null?name:Application.getProcessName());
                activities.shirazSetForeground(foreground);
            }
            return activities;
        }
        synchronized void foreground(boolean value) {
            foreground = value;capture.setForeground(value);
            if(locations!=null)locations.shirazSetForeground(value);
            if (activities != null) activities.shirazSetForeground(value);
        }
        @Override public synchronized android.content.res.Resources getResources() {
            if (resources == null) {
                resources = android.content.res.Resources.shirazOpen(getPackageCodePath(), width, height, densityDpi);
                // The desktop endpoint supplies keyboard and text events through SDL.
                android.content.res.Configuration input=new android.content.res.Configuration();
                input.keyboard=android.content.res.Configuration.KEYBOARD_QWERTY;
                input.keyboardHidden=android.content.res.Configuration.KEYBOARDHIDDEN_NO;
                input.hardKeyboardHidden=android.content.res.Configuration.HARDKEYBOARDHIDDEN_NO;
                resources.updateConfiguration(input,null);
            }
            return resources;
        }
        synchronized void close() {
            if(downloads!=null)downloads.shirazClose();
            launches.close();
            if(locations!=null)locations.shirazClose();
            try { try { services.close(); } finally { try { if (broadcasts != null) broadcasts.close(); } finally { if (notifications != null) notifications.shirazClose(); } } }
            finally { try { if(accessibility!=null)accessibility.shirazClose();preferences.close(); } finally { if (resources != null) resources.shirazClose(); } }
        }
        @Override public synchronized void setTheme(int id) { getTheme().applyStyle(id, true); }
        @Override public synchronized android.content.res.Resources.Theme getTheme() {
            if (theme == null) theme = getResources().newTheme();
            return theme;
        }
        @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) { return preferences.get(name, mode); }
        @Override public PackageManager getPackageManager() { return packages; }
        @Override public ApplicationInfo getApplicationInfo() {
            try { return packages.getApplicationInfo(name, 0); }
            catch (PackageManager.NameNotFoundException error) { throw new IllegalStateException(error); }
        }
        @Override public String getPackageName() { return name; }
        @Override public Context getApplicationContext() { return application; }
        @Override public File getDatabasePath(String name) { return storage.databasePath(name); }
        @Override public String[] databaseList() { return storage.databases(); }
        @Override public File getFileStreamPath(String name){return storage.filePath(name);}
        @Override public File getFilesDir() { return storage.files(); }
        @Override public File getCacheDir() { return storage.cache(); }
        @Override public File getDataDir() { return storage.data(); }
        @Override public File getCodeCacheDir() { return storage.codeCache(); }
        @Override public File getNoBackupFilesDir() { return storage.noBackup(); }
        @Override public File getDir(String name, int mode) { return storage.named(name, mode); }
        @Override public File getExternalFilesDir(String type) { return storage.externalFiles(type); }
        @Override public File getExternalCacheDir() { return storage.externalCache(); }
        @Override public File getObbDir() { return storage.obb(); }
        @Override public File[] getExternalMediaDirs() { return new File[]{storage.media()}; }
    }
    static void attach(ContextWrapper object, Context context) throws Exception {
        Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
        attach.setAccessible(true); attach.invoke(object, context);
    }
    private static void snapshot(File path, Bitmap bitmap) throws Exception {
        java.nio.file.Path target=path.toPath().toAbsolutePath();
        java.nio.file.Path temporary=java.nio.file.Files.createTempFile(target.getParent(),".shiraz-frame-",".ppm");
        try {
            writeSnapshot(temporary.toFile(),bitmap);
            java.nio.file.Files.move(temporary,target,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {java.nio.file.Files.deleteIfExists(temporary);}
    }
    private static void writeSnapshot(File path, Bitmap bitmap) throws Exception {
        int width=bitmap.getWidth(),height=bitmap.getHeight();
        int[] pixels=new int[width];
        try (FileOutputStream stream = new FileOutputStream(path)) {
            stream.write(("P6\n" + width + " " + height + "\n255\n").getBytes(StandardCharsets.US_ASCII));
            byte[] row = new byte[width * 3];
            for (int y = 0; y < height; ++y) {
                bitmap.getPixels(pixels,0,width,0,y,width,1);
                for (int x = 0; x < width; ++x) {
                    int pixel = pixels[x];
                    row[x * 3] = (byte) (pixel >>> 16); row[x * 3 + 1] = (byte) (pixel >>> 8); row[x * 3 + 2] = (byte) pixel;
                }
                stream.write(row);
            }
        }
    }
    public static void main(final String[] args) {
        DedicatedProcess.run(new DedicatedProcess.Task(){public void run() throws Throwable {
            HostTlsProvider.install();
            android.os.Looper.prepareMainLooper();
            ActivityRunner.run(args);
        }},new DedicatedProcess.Task(){public void run() throws Throwable {
            android.app.ActivityThread.shirazClear();
            try { android.os.AsyncTask.shirazShutdown(); }
            finally {
                try { try { android.database.sqlite.SQLiteDatabase.shirazShutdown(); } finally { try { android.media.SoundPool.shirazShutdown(); } finally { android.speech.tts.TextToSpeech.shirazShutdown(); } } }
                finally {
                    try { try { android.widget.PopupWindow.shirazClear();DialogOverlay.clear(); } finally { ToastOverlay.clear();TooltipOverlay.clear(); } }
                    finally { try { android.os.Environment.shirazShutdown(); } finally { android.os.Looper.shirazShutdownMain(); } }
                }
            }
        }});
    }
    private static void run(String[] args) throws Exception {
        if (args.length != 9) throw new IllegalArgumentException("package application activity width height frames data snapshot metadata");
        final boolean traceFrames="1".equals(System.getenv("SHIRAZ_TRACE_FRAMES"));
        final boolean liveSnapshot="1".equals(System.getenv("SHIRAZ_LIVE_SNAPSHOT"));
        if(liveSnapshot&&"-".equals(args[7]))throw new IllegalArgumentException("Live capture requires a snapshot path");
        String name = args[0];
        Application.shirazSetProcessName(name);ServiceProcess.configure(args);
        int width = Integer.parseInt(args[3]), height = Integer.parseInt(args[4]), frames = Integer.parseInt(args[5]);
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || (long) width * height > 16 * 1024 * 1024 || (frames != -1 && (frames < 1 || frames > 600)))
            throw new IllegalArgumentException("Invalid activity geometry/frame limit");
        try(final NativeSurface.HostDisplay display=NativeSurface.prepareDisplay(width,height)) {
        NativeSurface.prepareNativeLooper();
        width=display.width;height=display.height;
        final int startupDensity=display.densityDpi;
        HostInput.updateTouchscreen(NativeSurface.hasTouchscreen());
        android.content.res.Resources.shirazSetSystemDisplay(width,height,startupDensity);
        Properties metadata = new Properties();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(args[8]), StandardCharsets.UTF_8)) { metadata.load(reader); }
        if (!name.equals(metadata.getProperty("package"))) throw new IllegalArgumentException("Package metadata identity mismatch");
        HostStorage storage = new HostStorage(new File(args[6]), name);
        android.os.Environment.shirazInitialize(storage);
        HostKeyStoreProvider.install(storage.files());
        android.os.Process.shirazSetDumpable("true".equals(metadata.getProperty("debuggable")));
        Application app = Class.forName(args[1]).asSubclass(Application.class).newInstance();
        HostContext context = new HostContext(name, new File(args[6]), storage, app, metadata, width, height, startupDensity);
        android.os.StrictMode.ThreadPolicy oldPolicy = android.os.StrictMode.getThreadPolicy();
        if (context.getApplicationInfo().targetSdkVersion >= 11)
            android.os.StrictMode.setThreadPolicy(new android.os.StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeathOnNetwork().build());
        try {
            android.app.ActivityThread.shirazBind(app, name);
            attach(app, context);if(context.getApplicationInfo().theme!=0)context.setTheme(context.getApplicationInfo().theme);context.capture.setAppName(context.packages.getApplicationLabel(context.getApplicationInfo())); ProviderHost.attach(context, metadata); app.onCreate();
            final android.view.ViewTreeObserver[] windowObserver={android.view.ViewTreeObserver.shirazCreateWindowObserver()};
            final View[] attachedRoot={null};
            final java.util.IdentityHashMap<Activity,OrientationRequest> orientations=new java.util.IdentityHashMap<Activity,OrientationRequest>();
            final java.util.IdentityHashMap<Activity,android.content.res.Configuration> configurations=new java.util.IdentityHashMap<Activity,android.content.res.Configuration>();
            ActivityStack stack=new ActivityStack(new ActivityStack.Factory(){public ActivityLifecycle create(final android.content.Intent launch){
                final String className=launch.getComponent().getClassName();
                ActivityConfigurationPolicy policy=new ActivityConfigurationPolicy(metadata,className,context.getApplicationInfo().targetSdkVersion);
                policy.setSizeConfigurations(context.getResources().getSizeConfigurations());
                android.util.DisplayMetrics metrics=context.getResources().getDisplayMetrics();
                final OrientationRequest orientation=new OrientationRequest(policy.requestedOrientation(),metrics.widthPixels,metrics.heightPixels);
                return new ActivityLifecycle(new ActivityLifecycle.Factory(){public Activity create(){
                    try{Activity instance=Class.forName(className).asSubclass(Activity.class).newInstance();instance.shirazBindComponent(launch.getComponent());instance.setIntent(launch.shirazBroadcastCopy());instance.shirazBindOrientation(orientation);attach(instance,context);android.content.pm.ActivityInfo info=context.getPackageManager().getActivityInfo(instance.getComponentName(),0);if(info.getThemeResource()!=0)instance.setTheme(info.getThemeResource());
                        CharSequence title=info.nonLocalizedLabel!=null?info.nonLocalizedLabel:info.labelRes!=0?instance.getString(info.labelRes):context.getPackageManager().getApplicationLabel(info.applicationInfo);instance.shirazBindTitle(title);instance.getLayoutInflater().setPrivateFactory(instance);orientations.put(instance,orientation);configurations.put(instance,new android.content.res.Configuration(instance.getResources().getConfiguration()));return instance;}
                    catch(Exception error){throw new IllegalStateException("Cannot construct activity",error);}
                }},new ActivityLifecycle.Cleanup(){public void detached(Activity old){orientations.remove(old);configurations.remove(old);if(attachedRoot[0]!=null&&attachedRoot[0]==old.shirazContentView()){try{android.widget.PopupWindow.shirazClear();DialogOverlay.clear();}finally{attachedRoot[0].shirazDetachTree();attachedRoot[0]=null;windowObserver[0].shirazKill();windowObserver[0]=android.view.ViewTreeObserver.shirazCreateWindowObserver();}}}},policy,context.getApplicationInfo().targetSdkVersion);
            }},new ActivityStack.Activation(){public void prepare(ActivityLifecycle lifecycle){
                Activity previous=lifecycle.current();android.content.res.Configuration before=configurations.get(previous),next=new android.content.res.Configuration(previous.getResources().getConfiguration());
                int changed=new android.content.res.Configuration(before).updateFrom(next);
                if(changed!=0)lifecycle.reconfigure(before,next,changed,new Runnable(){public void run(){}});
                configurations.put(lifecycle.current(),new android.content.res.Configuration(lifecycle.current().getResources().getConfiguration()));
            }});
            Throwable activityFailure=null;
            try {
                stack.launch(new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER).setComponent(new android.content.ComponentName(name,args[2])));
                final Activity activity=stack.current();
                if (stack.current()!=null) {
                    activity.shirazTakeTransition();
                    final Bitmap[] bitmap = {Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)};
                    bitmap[0].setDensity(startupDensity);
                    final ActivityTransition navigation=new ActivityTransition();
                    try (final NativeSurface surface = new NativeSurface(width, height)) {
                        surface.presentDispatched(frames, new NativeSurface.ResizeCallback() {
                            private int width=surface.getWidth(),height=surface.getHeight();
                            private int densityDpi=startupDensity;
                            private int keyboardFullHeight;
                            private Activity activity=stack.current();
                            @Override public void onResize(final int newWidth,final int newHeight){onDisplayChanged(newWidth,newHeight,densityDpi);}
                            @Override public void onDisplayChanged(final int newWidth,final int newHeight,final int newDensity){
                                if(width==newWidth&&height==newHeight&&densityDpi==newDensity)return;
                                navigation.close();
                                final long resizeStarted=android.os.SystemClock.uptimeMillis();
                                cancelApplicationPointer(resizeStarted);
                                // Wayland keyboards reduce the available viewport, not Android's
                                // screen configuration. Retain the editor/activity across that resize.
                                boolean requested=context.inputMethod!=null&&context.inputMethod.shirazKeyboardRequested();
                                if(NativeSurface.usesWaylandTextInput()&&newDensity==densityDpi&&newWidth==width&&
                                        (keyboardFullHeight>0&&newHeight<=keyboardFullHeight||requested&&newHeight<height)){
                                    if(keyboardFullHeight==0)keyboardFullHeight=height;
                                    Bitmap replacement=Bitmap.createBitmap(newWidth,newHeight,Bitmap.Config.ARGB_8888);replacement.setDensity(newDensity);
                                    surface.resize(newWidth,newHeight);Bitmap previous=bitmap[0];bitmap[0]=replacement;previous.recycle();
                                    width=newWidth;height=newHeight;if(newHeight==keyboardFullHeight)keyboardFullHeight=0;
                                    rendered=null;overlayRevision=dialogRevision=-1;
                                    View root=activity.shirazContentView();if(root!=null){root.requestLayout();root.invalidate();}
                                    System.err.println("SHIRAZ_IME_VIEWPORT "+newWidth+"x"+newHeight);return;
                                }
                                keyboardFullHeight=0;
                                final android.content.res.Resources resources=context.getResources();
                                final android.content.res.Configuration next=resources.shirazPreviewDisplay(newWidth,newHeight,newDensity);
                                final int appChanged=resources.getConfiguration().updateFrom(next);
                                final android.content.res.Configuration activityNext=activity.getResources().shirazPreviewInheritedConfiguration(next);
                                final android.content.res.Configuration before=activity.getResources().getConfiguration();
                                int changed=new android.content.res.Configuration(before).updateFrom(activityNext);
                                final Bitmap replacement=Bitmap.createBitmap(newWidth,newHeight,Bitmap.Config.ARGB_8888);replacement.setDensity(newDensity);
                                try{
                                    boolean recreated=stack.currentLifecycle().reconfigure(before,activityNext,changed,new Runnable(){public void run(){
                                        surface.resize(newWidth,newHeight);resources.shirazResizeDisplay(newWidth,newHeight,newDensity);
                                        android.content.res.Resources.shirazSetSystemDisplay(newWidth,newHeight,newDensity);
                                        Bitmap previous=bitmap[0];bitmap[0]=replacement;previous.recycle();
                                        width=newWidth;height=newHeight;densityDpi=newDensity;
                                        if(appChanged!=0)app.onConfigurationChanged(new android.content.res.Configuration(next));
                                    }});
                                    System.err.println("SHIRAZ_RESIZE applied="+newWidth+"x"+newHeight+" changes=0x"+Integer.toHexString(changed)+" recreated="+recreated+" elapsed_ms="+(android.os.SystemClock.uptimeMillis()-resizeStarted));
                                    activity=stack.current();configurations.put(activity,new android.content.res.Configuration(activity.getResources().getConfiguration()));rendered=null;overlayRevision=dialogRevision=-1;if(recreated){priorFocus=null;modalFocus=false;}
                                    View root=activity.shirazContentView();if(root!=null){if(!recreated)root.dispatchConfigurationChanged(new android.content.res.Configuration(activity.getResources().getConfiguration()));root.requestLayout();root.invalidate();}
                                    if(!recreated){DialogOverlay.configurationChanged(new android.content.res.Configuration(next));android.widget.PopupWindow.shirazConfigurationChanged(new android.content.res.Configuration(next));}
                                }finally{if(bitmap[0]!=replacement)replacement.recycle();}
                            }
                            @Override public void onTestLocation(double latitude,double longitude,float accuracy){
                                android.location.LocationManager manager=(android.location.LocationManager)context.getSystemService(android.content.Context.LOCATION_SERVICE);
                                manager.shirazSetTestLocation(TestLocationConfig.parse(Double.toString(latitude)+","+Double.toString(longitude)+","+Float.toString(accuracy)));
                            }
                            private boolean hostFocused,touchMode,hostVisible=true;
                            @Override public void onWindowVisibility(boolean visible){hostVisible=visible;if(!visible)navigation.close();context.capture.setForeground(visible&&hostFocused);android.widget.PopupWindow.shirazWindowState(hostFocused,hostVisible,touchMode);DialogOverlay.windowVisibility(activity.shirazContentView(),visible);}
                            private void touchMode(boolean value){touchMode=value;android.widget.PopupWindow.shirazWindowState(hostFocused,hostVisible,touchMode);DialogOverlay.touchMode(activity.shirazContentView(),value);}
                            @Override public void onWindowFocus(boolean focused){hostFocused=focused;context.capture.setForeground(focused&&hostVisible);android.widget.PopupWindow.shirazWindowState(hostFocused,hostVisible,touchMode);DialogOverlay.windowFocus(activity.shirazContentView(),focused);}
                            private String lastTitle;
                            private View rendered;
                            private void attach(View view){if(attachedRoot[0]!=view){if(attachedRoot[0]!=null)attachedRoot[0].shirazDetachTree();if(view!=null){view.shirazAttachTree(windowObserver[0]);view.shirazSetWindowVisibility(hostVisible?View.VISIBLE:View.GONE);}attachedRoot[0]=view;((HostWindowManager)context.getSystemService(android.content.Context.WINDOW_SERVICE)).shirazSetRoot(view);}}
                            private void finishNavigation(){if(navigation.isRunning()){navigation.close();View root=activity.shirazContentView();if(root!=null)root.invalidate();}}
                            private long downTime;
                            private long overlayRevision = -1, dialogRevision = -1;
                            private boolean applicationPointer, modalFocus, dumpAfterLayout;private View priorFocus;private android.view.MotionEvent lastApplicationEvent;
                            private void cancelApplicationPointer(long time){if(applicationPointer){applicationPointer=false;android.view.MotionEvent event=lastApplicationEvent==null?android.view.MotionEvent.shirazObtain(downTime,time,android.view.MotionEvent.ACTION_CANCEL,0,0,0x1002):android.view.MotionEvent.obtain(lastApplicationEvent);event.setAction(android.view.MotionEvent.ACTION_CANCEL);try{activity.getWindow().shirazDispatchTouchEvent(event);}finally{event.recycle();}}}
                            private void modalInput(long time){if(DialogOverlay.hasVisible()||android.widget.PopupWindow.shirazCapturesApplicationPointer()){cancelApplicationPointer(time);if(!modalFocus){View root=activity.shirazContentView();if(root!=null){priorFocus=root.findFocus();root.clearFocus();}modalFocus=true;}}else if(modalFocus){modalFocus=false;if(priorFocus!=null&&priorFocus.isAttachedToWindow())priorFocus.requestFocus();priorFocus=null;}}
                            @Override public void onKey(int action, int key, int scan, int repeat, int meta, int flags, long down, long time) {
                                if (activity.isFinishing()) return;
                                android.view.KeyEvent event = new android.view.KeyEvent(down,time,action,key,repeat,meta,0,scan,flags,0x101);
                                if(action==0&&(key>=19&&key<=23||key==61||key==66||key==92||key==93||key==122||key==123))touchMode(false);
                                // KeyEvents may be retained for asynchronous dispatch by applications.
                                if(event.getKeyCode()==android.view.KeyEvent.KEYCODE_P&&event.isCtrlPressed()&&event.isShiftPressed()){if(event.getAction()==0&&event.getRepeatCount()==0)context.capture.showPrivacy(activity);return;}modalInput(time); if(!android.widget.PopupWindow.shirazKey(event)&&!DialogOverlay.key(event))activity.getWindow().shirazDispatchKeyEvent(event);
                            }
                            @Override public void onText(String text, long time) {
                                if (activity.isFinishing()) return;
                                modalInput(time);
                                if(context.inputMethod!=null&&context.inputMethod.shirazCommitText(text))return;
                                touchMode(false);
                                android.view.KeyEvent event = new android.view.KeyEvent(time,text,0,android.view.KeyEvent.FLAG_FROM_SYSTEM);
                                // KeyEvents may be retained for asynchronous dispatch by applications.
                                if(event.getKeyCode()==android.view.KeyEvent.KEYCODE_P&&event.isCtrlPressed()&&event.isShiftPressed()){if(event.getAction()==0&&event.getRepeatCount()==0)context.capture.showPrivacy(activity);return;}modalInput(time); if(!android.widget.PopupWindow.shirazKey(event)&&!DialogOverlay.key(event))activity.getWindow().shirazDispatchKeyEvent(event);
                            }
                            @Override public void onScroll(float x,float y,float horizontal,float vertical,long time){
                                if(activity.isFinishing())return;finishNavigation();
                                View root=activity.getWindow().getDecorView();if(root==null)return;
                                android.view.MotionEvent event=android.view.MotionEvent.shirazScroll(time,x,y,horizontal,vertical);
                                try{if(!DialogOverlay.genericMotion(event)&&!android.widget.PopupWindow.shirazGenericMotion(event))root.dispatchGenericMotionEvent(event);}finally{event.recycle();}
                            }
                            @Override public void onPointer(int action, float x, float y, int source, long eventTime) {
                                if((action&255)==android.view.MotionEvent.ACTION_DOWN)downTime=eventTime;
                                android.view.MotionEvent event=android.view.MotionEvent.shirazObtain(downTime,eventTime,action,x,y,source);
                                try{routePointer(event);}finally{event.recycle();}
                            }
                            @Override public void onTouch(int action,int[] ids,float[] xs,float[] ys,float[] pressures,long eventTime){
                                if((action&255)==android.view.MotionEvent.ACTION_DOWN)downTime=eventTime;
                                android.view.MotionEvent event=android.view.MotionEvent.shirazTouch(downTime,eventTime,action,ids,xs,ys,pressures);
                                try{routePointer(event);}finally{event.recycle();}
                            }
                            private void routePointer(android.view.MotionEvent event) {
                                int action=event.getActionMasked(),source=event.getSource();float x=event.getX(),y=event.getY();long eventTime=event.getEventTime();
                                if (activity.isFinishing()) return;
                                if(action==android.view.MotionEvent.ACTION_HOVER_MOVE){View root=activity.shirazContentView();android.view.PointerIcon icon=DialogOverlay.hasVisible()?DialogOverlay.pointerIcon(x,y):root==null?null:root.shirazPointerIcon(x,y);surface.requestPointerIcon(icon==null?1000:icon.getType());return;}
                                if(action==android.view.MotionEvent.ACTION_DOWN)finishNavigation();
                                if(context.capture.pointer(action,x,y,width,activity)){cancelApplicationPointer(eventTime);return;}
                                if(action==android.view.MotionEvent.ACTION_DOWN)touchMode((source&0x1002)==0x1002);
                                boolean routed=false;
                                if(DialogOverlay.hasVisible()){
                                 if("1".equals(System.getenv("SHIRAZ_TRACE_INPUT"))&&action==android.view.MotionEvent.ACTION_DOWN){System.err.println("SHIRAZ_POINTER_ROUTE dialog x="+x+" y="+y);if("1".equals(System.getenv("SHIRAZ_DUMP_VIEWS")))DialogOverlay.dumpVisible(System.err);}
                                 routed=DialogOverlay.pointer(event,width,height);
                                }else if(android.widget.PopupWindow.shirazPointer(event,width,height)){
                                 if("1".equals(System.getenv("SHIRAZ_TRACE_INPUT"))&&action==android.view.MotionEvent.ACTION_DOWN)System.err.println("SHIRAZ_POINTER_ROUTE popup x="+x+" y="+y);
                                 routed=true;
                                }else if(DialogOverlay.pointer(event,width,height)){
                                 if("1".equals(System.getenv("SHIRAZ_TRACE_INPUT"))&&action==android.view.MotionEvent.ACTION_DOWN)System.err.println("SHIRAZ_POINTER_ROUTE dialog2 x="+x+" y="+y);
                                 routed=true;
                                }
                                modalInput(eventTime);
                                if(routed)return;
                                if("1".equals(System.getenv("SHIRAZ_TRACE_INPUT"))&&action==android.view.MotionEvent.ACTION_DOWN)System.err.println("SHIRAZ_POINTER_ROUTE activity x="+x+" y="+y);
                                View view = activity.shirazContentView();
                                attach(view);DialogOverlay.touchMode(view,touchMode);DialogOverlay.windowVisibility(view,hostVisible);DialogOverlay.windowFocus(view,hostFocused);if(view!=null){view.shirazUpdateSystemUiVisibility();view.shirazApplyWindowInsets(width,height);}
                                if (view != null && (view.isLayoutRequested() || view.getWidth() != width || view.getHeight() != height)) {
                                    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
                                    view.layout(0,0,width,height);
                                    view.getViewTreeObserver().dispatchOnGlobalLayout();
                                }
                                long now = eventTime;
                                if (action == android.view.MotionEvent.ACTION_DOWN) {ViewDiagnostics.inputTree(view);downTime = now;applicationPointer=true;}
                                if(action==android.view.MotionEvent.ACTION_UP||action==android.view.MotionEvent.ACTION_CANCEL)applicationPointer=false;
                                if(lastApplicationEvent!=null)lastApplicationEvent.recycle();lastApplicationEvent=android.view.MotionEvent.obtain(event);
                                activity.getWindow().shirazDispatchTouchEvent(event);
                                modalInput(now);
                            }
                            private boolean keepFrame;
                            private final Runnable traversal=new Runnable(){public void run(){keepFrame=renderFrame();}};
                            @Override public boolean onFrame() {
                                HostInput.updateTouchscreen(NativeSurface.hasTouchscreen());
                                if(context.inputMethod!=null)context.inputMethod.shirazCheckEditor();
                                long frameStart=traceFrames?System.nanoTime():0;
                                android.os.Looper.myLooper().shirazDispatchDue(256);keepFrame=true;
                                NativeSurface.pollNativeLooper();
                                ActivityLaunchResolver.Request launch=context.launches.poll();
                                if(launch!=null||activity.isFinishing()){
                                    Activity previousActivity=activity;boolean back=launch==null;
                                    cancelApplicationPointer(android.os.SystemClock.uptimeMillis());
                                    configurations.put(activity,new android.content.res.Configuration(activity.getResources().getConfiguration()));
                                    android.widget.PopupWindow.shirazClear();DialogOverlay.clear();attach(null);windowObserver[0].shirazKill();windowObserver[0]=android.view.ViewTreeObserver.shirazCreateWindowObserver();
                                    if(launch!=null)stack.launch(launch.intent(),launch.launchMode);
                                    while(stack.finishTop()){}
                                    activity=stack.current();if(activity==null){navigation.close();return false;}
                                    int[] oldTransition=previousActivity.shirazTakeTransition(),newTransition=activity.shirazTakeTransition();
                                    if(activity!=previousActivity)navigation.start(bitmap[0],activity,newTransition!=null?newTransition:oldTransition,back,launch!=null&&(launch.intent().getFlags()&android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)!=0);
                                    rendered=null;overlayRevision=dialogRevision=-1;priorFocus=null;modalFocus=false;lastTitle=null;
                                    if("1".equals(System.getenv("SHIRAZ_DUMP_VIEWS")))dumpAfterLayout=true;
                                }
                                if(activity.shirazTakeRecreationRequest()){
                                    navigation.close();cancelApplicationPointer(android.os.SystemClock.uptimeMillis());
                                    stack.currentLifecycle().recreate();activity=stack.current();
                                    configurations.put(activity,new android.content.res.Configuration(activity.getResources().getConfiguration()));
                                    rendered=null;overlayRevision=dialogRevision=-1;priorFocus=null;modalFocus=false;lastTitle=null;
                                    System.err.println("SHIRAZ_ACTIVITY_RECREATED requested=true");
                                }
                                long dispatched=traceFrames?System.nanoTime():0;
                                android.view.Choreographer.getInstance().shirazDoFrame(System.nanoTime(),traversal);
                                if(traceFrames)System.err.println("SHIRAZ_FRAME_TIMING dispatch_ms="+(dispatched-frameStart)/1000000+" traversal_ms="+(System.nanoTime()-dispatched)/1000000);
                                return keepFrame;
                            }
                            private boolean renderFrame(){
                                boolean measureFrames=traceFrames||activity.getWindow().shirazObservesFrames();
                                long renderStart=measureFrames?System.nanoTime():0;
                                modalInput(android.os.SystemClock.uptimeMillis());
                                if (activity.isFinishing()) return false;
                                String title=activity.getWindow().shirazGetTitle()+" — Privacy: Ctrl+Shift+P";String captureSummary=context.capture.activeSummary();if(!captureSummary.isEmpty())title+=" — "+captureSummary;String progress=context.notifications==null?"":context.notifications.shirazForegroundSummary();if(!progress.isEmpty())title+=" — "+progress;if(!title.equals(lastTitle)){surface.requestWindowTitle(title);lastTitle=title;}
                                int[] requested=orientations.get(activity).take(width,height);if(requested!=null){surface.requestWindowSize(requested[0],requested[1]);return true;}
                                View view = activity.shirazContentView();
                                attach(view);DialogOverlay.touchMode(view,touchMode);DialogOverlay.windowVisibility(view,hostVisible);DialogOverlay.windowFocus(view,hostFocused);if(view!=null){view.shirazUpdateSystemUiVisibility();view.shirazApplyWindowInsets(width,height);}
                                if (view == null) throw new UnsupportedOperationException("Activity has no supported programmatic content View");
                                surface.requestKeepScreenOn(view.shirazRequestsKeepScreenOn());
                                // Animate the two captured surfaces independently of incoming app
                                // invalidations. Keep those pending for a fresh traversal at the end;
                                // input/resize cancel immediately, and modal/overlay changes bypass it.
                                if(navigation.hasCachedContent()&&context.capture.revision()+ToastOverlay.revision()+TooltipOverlay.revision()+android.widget.PopupWindow.shirazRevision(width,height)==overlayRevision&&DialogOverlay.revision()==dialogRevision){
                                    long composeStarted=measureFrames?System.nanoTime():0;
                                    if(navigation.composeCached(bitmap[0],android.os.SystemClock.uptimeMillis())){
                                        bitmap[0].shirazPostToSurface(surface);
                                        activity.getWindow().shirazReportFrame(0,0);
                                        if(liveSnapshot){try{snapshot(new File(args[7]),bitmap[0]);}catch(Exception failure){throw new IllegalStateException("Cannot capture live app frame",failure);}System.err.println("SHIRAZ_FRAME_CAPTURE "+width+"x"+height+" uptime="+android.os.SystemClock.uptimeMillis());}
                                        if(traceFrames)System.err.println("SHIRAZ_NAVIGATION_FRAME compose_present_ms="+(System.nanoTime()-composeStarted)/1000000);
                                        return true;
                                    }
                                    view.invalidate();
                                }
                                long prepared=measureFrames?System.nanoTime():0;
                                boolean changed = rendered != view;
                                if (changed || view.isLayoutRequested()) {
                                    ViewDiagnostics.layoutPass(true);try{
                                    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
                                    view.layout(0,0,width,height); rendered=view;
                                    view.getViewTreeObserver().dispatchOnGlobalLayout();
                                    }finally{ViewDiagnostics.layoutPass(false);}
                                    if(dumpAfterLayout){dumpAfterLayout=false;ViewDiagnostics.dump(view,System.err);DialogOverlay.dumpVisible(System.err);}
                                }
                                long laidOut=measureFrames?System.nanoTime():0;
                                android.widget.PopupWindow.shirazWindowState(hostFocused,hostVisible,touchMode);
                                long overlays = context.capture.revision()+ToastOverlay.revision()+TooltipOverlay.revision()+android.widget.PopupWindow.shirazRevision(width,height), dialogs=DialogOverlay.revision();
                                boolean redraw=changed || view.shirazIsInvalidated() || overlays != overlayRevision || dialogs != dialogRevision;
                                if (redraw || navigation.isRunning()) {
                                    android.view.ViewTreeObserver observer=view.getViewTreeObserver();
                                    boolean ready=false;
                                    for(int attempt=0;attempt<16;attempt++){
                                     if(observer.dispatchOnPreDraw()){view.invalidate();continue;}
                                     if(!DialogOverlay.prepare(width,height)){view.invalidate();continue;}
                                     if(!android.widget.PopupWindow.shirazPrepare()){view.invalidate();continue;}
                                     ready=true;break;
                                    }
                                    if(!ready){DialogOverlay.prepare(width,height);android.widget.PopupWindow.shirazPrepare();}
                                    long drawStart=measureFrames?System.nanoTime():0;
                                    if(redraw || view.shirazIsInvalidated()){
                                    observer.dispatchOnDraw();
                                    Bitmap content=navigation.drawingSurface(bitmap[0]);content.eraseColor(0);
                                    Canvas canvas = Canvas.shirazScreen(content);
                                    view.shirazDrawWithAlpha(canvas);
                                    DialogOverlay.draw(canvas,width,height);
                                    android.widget.PopupWindow.shirazDraw(canvas);
                                    TooltipOverlay.draw(canvas, width, height);
                                    ToastOverlay.draw(canvas, width, height);context.capture.draw(canvas,width);
                                    overlayRevision = overlays; dialogRevision=dialogs;
                                    }
                                    // Use presentation time: first layout may outlast the app's animation.
                                    navigation.composeCached(bitmap[0],android.os.SystemClock.uptimeMillis());
                                    long drawn=measureFrames?System.nanoTime():0;
                                    bitmap[0].shirazPostToSurface(surface);
                                    long posted=measureFrames?System.nanoTime():0;
                                    activity.getWindow().shirazReportFrame(laidOut-prepared,drawn-drawStart);
                                    if(liveSnapshot){
                                        try{snapshot(new File(args[7]),bitmap[0]);}
                                        catch(Exception failure){throw new IllegalStateException("Cannot capture live app frame",failure);}
                                        System.err.println("SHIRAZ_FRAME_CAPTURE "+width+"x"+height+" uptime="+android.os.SystemClock.uptimeMillis());
                                    }
                                    if(traceFrames)System.err.println("SHIRAZ_DRAW_TIMING draw_ms="+(drawn-drawStart)/1000000+" setup_ms="+(prepared-renderStart)/1000000+" layout_ms="+(laidOut-prepared)/1000000+" predraw_ms="+(drawStart-laidOut)/1000000+" post_ms="+(posted-drawn)/1000000+" snapshot_ms="+(System.nanoTime()-posted)/1000000);
                                }
                                return true;
                            }
                        });
                        if ("1".equals(System.getenv("SHIRAZ_DUMP_VIEWS"))&&stack.current()!=null) ViewDiagnostics.dump(stack.current().shirazContentView(),System.out);
                        if (!"-".equals(args[7])) snapshot(new File(args[7]),bitmap[0]);
                    } finally { navigation.close();bitmap[0].recycle(); }
                }
            } catch(Throwable error){activityFailure=error;android.util.Log.e("ActivityRunner","Activity failed before teardown",error);throw error;} finally {
                context.foreground(false);
                try{stack.close();app.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);}
                catch(Throwable cleanup){if(activityFailure==null)throw cleanup;if(cleanup!=activityFailure)activityFailure.addSuppressed(cleanup);}
                finally{windowObserver[0].shirazKill();}
            }
        } finally {
            context.foreground(false);
            try { context.close(); } finally { android.os.StrictMode.setThreadPolicy(oldPolicy); }
        }
        System.out.println("SHIRAZ_ACTIVITY_EXITED " + name + " " + args[2]);
        }
    }
}
