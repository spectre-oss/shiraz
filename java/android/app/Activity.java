// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.View;

/** First lifecycle/content subset. Window management and the broader styled-widget framework remain incomplete. */
public class Activity extends ContextThemeWrapper implements android.view.Window.Callback,android.view.View.OnCreateContextMenuListener,android.content.ComponentCallbacks2,android.view.KeyEvent.Callback,android.view.LayoutInflater.Factory2 {
    private final java.util.IdentityHashMap<android.content.ServiceConnection,Boolean> serviceBindings=new java.util.IdentityHashMap<android.content.ServiceConnection,Boolean>();
    private final java.util.IdentityHashMap<android.content.ServiceConnection,Boolean> releasedServiceBindings=new java.util.IdentityHashMap<android.content.ServiceConnection,Boolean>();
    @Override public boolean bindService(android.content.Intent intent,android.content.ServiceConnection connection,int flags){
        boolean bound=super.bindService(intent,connection,flags);
        releasedServiceBindings.remove(connection);
        serviceBindings.put(connection,Boolean.TRUE);
        return bound;
    }
    @Override public void unbindService(android.content.ServiceConnection connection){
        if(releasedServiceBindings.remove(connection)!=null)return;
        super.unbindService(connection);
        serviceBindings.remove(connection);
    }
    /** Android releases connections registered through an Activity when that Activity is destroyed. */
    public final void shirazReleaseServiceBindings(){
        android.content.ServiceConnection[] connections=serviceBindings.keySet().toArray(new android.content.ServiceConnection[serviceBindings.size()]);
        serviceBindings.clear();
        for(android.content.ServiceConnection connection:connections){
            releasedServiceBindings.put(connection,Boolean.TRUE);
            try{super.unbindService(connection);}catch(IllegalArgumentException alreadyUnbound){}
        }
    }
    private android.media.session.MediaController mediaController;
    public void setMediaController(android.media.session.MediaController controller){mediaController=controller;}
    public android.media.session.MediaController getMediaController(){return mediaController;}
    private ActivityManager.TaskDescription taskDescription;
    public void setTaskDescription(ActivityManager.TaskDescription description){taskDescription=description;}
    public ActivityManager.TaskDescription getTaskDescription(){return taskDescription;}
    public final void runOnUiThread(Runnable action){
        if(action==null)throw new NullPointerException("action");
        android.os.Looper main=android.os.Looper.getMainLooper();
        if(main==null)throw new IllegalStateException("UI Looper is not initialized");
        if(Thread.currentThread()==main.getThread())action.run();
        else if(!new android.os.Handler(main).post(action))throw new IllegalStateException("UI Looper has stopped");
    }
    private shiraz.runtime.PermissionRequests permissionRequests;
    public final void requestPermissions(String[] permissions,int requestCode){
        if(permissionRequests==null)permissionRequests=new shiraz.runtime.PermissionRequests(this);
        permissionRequests.request(permissions,requestCode);
    }
    public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){}
    public boolean shouldShowRequestPermissionRationale(String permission){if(permission==null)throw new IllegalArgumentException("permission");shiraz.runtime.CapturePermissions policy=shiraz.runtime.CapturePermissions.current();return policy!=null&&policy.canRequest(permission)&&policy.wasDenied(permission);}
    public final shiraz.runtime.PermissionRequests shirazTakePermissionRequests(){
        shiraz.runtime.PermissionRequests value=permissionRequests;permissionRequests=null;if(value!=null)value.bind(null);return value;
    }
    public final void shirazRestorePermissionRequests(shiraz.runtime.PermissionRequests value){
        if(permissionRequests!=null)throw new IllegalStateException("Permission owner already initialized");permissionRequests=value;if(value!=null)value.bind(this);
    }
    private android.content.Intent intent;
    public android.content.Intent getIntent(){return intent;}
    public void setIntent(android.content.Intent value){intent=value;}
    protected void onNewIntent(android.content.Intent value){}
    private FragmentManager fragments;private int fragmentPhase;
    public FragmentManager getFragmentManager(){if(fragments==null)fragments=new FragmentManager(this,fragmentPhase);return fragments;}
    public void onAttachFragment(Fragment fragment){}
    public final void shirazPrepareFragments(Bundle state){fragmentPhase=1;if(state!=null&&state.getBundle("android:fragments")!=null)getFragmentManager().restore(state.getBundle("android:fragments"));}
    public final void shirazMoveFragments(int phase){fragmentPhase=phase;if(fragments!=null)fragments.moveTo(phase);}
    public final void shirazSaveFragments(Bundle out){if(fragments!=null)out.putBundle("android:fragments",fragments.save());}
    public final void shirazDestroyFragments(){fragmentPhase=0;if(fragments!=null)fragments.destroy();}
    private CharSequence title="";private int titleColor;private boolean titleBound;
    public final void shirazBindTitle(CharSequence value){if(titleBound)throw new IllegalStateException("Activity title already initialized");android.view.Window.shirazTitle(value);titleBound=true;title=value;getWindow().setTitle(value);}
    public void setTitle(CharSequence value){android.view.Window.shirazTitle(value);title=value;onTitleChanged(value,titleColor);}
    public void setTitle(int id){setTitle(getString(id));}
    public final CharSequence getTitle(){return title;}
    public void setTitleColor(int color){titleColor=color;onTitleChanged(title,color);}
    public final int getTitleColor(){return titleColor;}
    protected void onTitleChanged(CharSequence value,int color){getWindow().setTitle(value);if(color!=0)getWindow().setTitleColor(color);}
    protected void onChildTitleChanged(Activity child,CharSequence value){}
    private android.content.ComponentName component;
    public final void shirazBindComponent(android.content.ComponentName value){
        if(value==null)throw new NullPointerException("component");
        if(component!=null)throw new IllegalStateException("Activity component already bound");
        if(!getClass().getName().equals(value.getClassName()))throw new IllegalArgumentException("Activity class identity mismatch");
        component=value;
    }
    public android.content.ComponentName getComponentName(){return component;}
    public final void setVolumeControlStream(int streamType){getWindow().setVolumeControlStream(streamType);}
    public final int getVolumeControlStream(){return getWindow().getVolumeControlStream();}
    public android.content.SharedPreferences getPreferences(int mode){return getSharedPreferences(getLocalClassName(),mode);}
    public String getLocalClassName(){
        if(component==null)throw new IllegalStateException("Activity component is not bound");
        String name=component.getClassName(),prefix=component.getPackageName()+".";
        return name.startsWith(prefix)?name.substring(prefix.length()):name;
    }
    private final android.view.KeyEvent.DispatcherState keyState = new android.view.KeyEvent.DispatcherState();
    private View content;
    private android.view.Window window;
    public android.view.Window getWindow(){if(window==null){window=new android.view.Window(this);window.setCallback(this);window.shirazSetRootListener(new Runnable(){public void run(){content=window.peekDecorView();}});}return window;}
    public void onContentChanged(){ }
    public void onWindowFocusChanged(boolean focused){ }
    private android.view.MenuInflater menuInflater;
    public android.view.MenuInflater getMenuInflater(){if(menuInflater==null)menuInflater=new android.view.MenuInflater(this);return menuInflater;}
    public void invalidateOptionsMenu(){getWindow().invalidatePanelMenu(0);}
    public boolean onCreateOptionsMenu(android.view.Menu menu){return true;}
    public boolean onPrepareOptionsMenu(android.view.Menu menu){return true;}
    public boolean onOptionsItemSelected(android.view.MenuItem item){return false;}
    public void onOptionsMenuClosed(android.view.Menu menu){ }
    public View onCreatePanelView(int feature){return null;}
    public boolean onCreatePanelMenu(int feature,android.view.Menu menu){return feature==0&&onCreateOptionsMenu(menu);}
    public boolean onPreparePanel(int feature,View view,android.view.Menu menu){return feature==0&&onPrepareOptionsMenu(menu);}
    public boolean onMenuItemSelected(int feature,android.view.MenuItem item){return feature==0?onOptionsItemSelected(item):feature==6&&onContextItemSelected(item);}
    public boolean onMenuOpened(int feature,android.view.Menu menu){return true;}
    public void onPanelClosed(int feature,android.view.Menu menu){if(feature==0)onOptionsMenuClosed(menu);}
    public void openOptionsMenu(){getWindow().openPanel(0,null);}
    public void closeOptionsMenu(){getWindow().closePanel(0);}
    public final void shirazReleaseWindow(){if(window!=null)window.shirazDestroy();}
    public boolean requestWindowFeature(int feature){return getWindow().requestFeature(feature);}
    public View getCurrentFocus(){return getWindow().getCurrentFocus();}
    public void addContentView(View view,android.view.ViewGroup.LayoutParams params){getWindow().addContentView(view,params);}
    public void setContentView(View view,android.view.ViewGroup.LayoutParams params){keyState.reset();getWindow().setContentView(view,params);}

    private shiraz.runtime.OrientationRequest orientation;
    public final void shirazBindOrientation(shiraz.runtime.OrientationRequest value){if(orientation!=null||value==null)throw new IllegalStateException("Orientation owner already bound or missing");orientation=value;}
    public int getRequestedOrientation(){return orientation==null?-1:orientation.get();}
    public void setRequestedOrientation(int value){if(orientation==null)throw new IllegalStateException("Activity has no host orientation owner");android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();orientation.request(value,metrics.widthPixels,metrics.heightPixels);}

    private boolean finishing,changingConfigurations,destroyed,recreationRequested;
    private final Thread activityThread=Thread.currentThread();
    /** Schedule replacement after the current input/lifecycle callback returns. */
    public void recreate(){
        if(Thread.currentThread()!=activityThread)throw new IllegalStateException("Activity recreation requires the activity thread");
        if(!finishing&&!destroyed)recreationRequested=true;
    }
    public final boolean shirazTakeRecreationRequest(){boolean requested=recreationRequested;recreationRequested=false;return requested&&!finishing&&!destroyed;}
    private Object lastNonConfiguration;
    public boolean isChangingConfigurations(){return changingConfigurations;}
    public boolean isDestroyed(){return destroyed;}
    /** Desktop window resizing does not place this activity in an Android split-screen task. */
    public boolean isInMultiWindowMode(){return false;}
    public Object onRetainNonConfigurationInstance(){return null;}
    public Object getLastNonConfigurationInstance(){return lastNonConfiguration;}
    public final void shirazSetLifecycleState(boolean changing,boolean destroyed,Object retained){changingConfigurations=changing;this.destroyed=destroyed;lastNonConfiguration=retained;}
    public final void shirazSetDestroyed(){destroyed=true;if(permissionRequests!=null){permissionRequests.close();permissionRequests=null;}}
    public Activity() { super(); }
    public void registerForContextMenu(View view){view.setOnCreateContextMenuListener(this);}
    public void unregisterForContextMenu(View view){view.setOnCreateContextMenuListener(null);}
    public void onCreateContextMenu(android.view.ContextMenu menu,View view,android.view.ContextMenu.ContextMenuInfo info){ }
    public boolean onContextItemSelected(android.view.MenuItem item){return false;}
    public void openContextMenu(View view){view.showContextMenu();}
    public final shiraz.runtime.HostContextMenu shirazCreateContextMenu(View view){
        if(view==null)throw new NullPointerException("view");
        shiraz.runtime.HostContextMenu menu=new shiraz.runtime.HostContextMenu(this);
        menu.shirazSetSelectionListener(new android.view.MenuItem.OnMenuItemClickListener(){public boolean onMenuItemClick(android.view.MenuItem item){return onContextItemSelected(item);}});
        view.createContextMenu(menu);return menu;
    }
    public void closeContextMenu(){throw new UnsupportedOperationException("Context menu popup presentation is not implemented");}
    public void onConfigurationChanged(android.content.res.Configuration configuration) {if(fragments!=null)fragments.configuration(configuration); }
    public void onLowMemory() {if(fragments!=null)fragments.lowMemory(); }
    public void onTrimMemory(int level) {if(fragments!=null)fragments.trimMemory(level); }
    protected void onCreate(Bundle savedInstanceState) { }
    protected void onSaveInstanceState(Bundle state) {
        if(state==null)throw new NullPointerException("state");
        if(content!=null){Bundle hierarchy=new Bundle();android.util.SparseArray<android.os.Parcelable> views=new android.util.SparseArray<android.os.Parcelable>();content.saveHierarchyState(views);hierarchy.putSparseParcelableArray("android:views",views);View focused=content.findFocus();if(focused!=null&&focused.getId()!=View.NO_ID)hierarchy.putInt("android:focusedViewId",focused.getId());state.putBundle("android:viewHierarchyState",hierarchy);}
    }
    protected void onRestoreInstanceState(Bundle state) {
        if(state==null)throw new NullPointerException("state");Bundle hierarchy=state.getBundle("android:viewHierarchyState");
        if(content!=null&&hierarchy!=null){android.util.SparseArray<android.os.Parcelable> views=hierarchy.getSparseParcelableArray("android:views");if(views!=null)content.restoreHierarchyState(views);int focused=hierarchy.getInt("android:focusedViewId",View.NO_ID);View view=focused==View.NO_ID?null:content.findViewById(focused);if(view!=null)view.requestFocus();}
    }
    protected void onStart() { }
    protected void onRestart() { }
    protected void onResume() { }
    protected void onPostCreate(Bundle state) { }
    protected void onPostResume() { }
    protected void onPause() { }
    protected void onStop() { }
    protected void onDestroy() { }
    @Override public Object getSystemService(String name){Object value=super.getSystemService(name);if(LAYOUT_INFLATER_SERVICE.equals(name))((android.view.LayoutInflater)value).setPrivateFactory(this);return value;}
    public View onCreateView(String name,android.content.Context context,android.util.AttributeSet attrs){if("fragment".equals(name))throw new UnsupportedOperationException("Platform fragment inflation is not implemented");return null;}
    public View onCreateView(View parent,String name,android.content.Context context,android.util.AttributeSet attrs){return onCreateView(name,context,attrs);}
    public android.view.LayoutInflater getLayoutInflater(){return android.view.LayoutInflater.from(this);}
    public void setContentView(int resource){keyState.reset();getWindow().setContentView(resource);}
    public void setContentView(View view){keyState.reset();getWindow().setContentView(view);}
    public <T extends View> T findViewById(int id) { return content == null ? null : content.<T>findViewById(id); }
    public void onUserInteraction() { }
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event == null) throw new NullPointerException("event");
        onUserInteraction();
        if (content != null && content.getVisibility() == View.VISIBLE && content.hasFocus() && content.dispatchKeyEvent(event)) return true;
        return event.dispatch(this,keyState,this);
    }
    public boolean onKeyDown(int code, android.view.KeyEvent event) { if (code == android.view.KeyEvent.KEYCODE_BACK) { event.startTracking(); return true; } return false; }
    public boolean onKeyLongPress(int code, android.view.KeyEvent event) { return false; }
    public boolean onKeyUp(int code, android.view.KeyEvent event) { if (code == android.view.KeyEvent.KEYCODE_BACK && event.isTracking() && !event.isCanceled()) { onBackPressed(); return true; } return false; }
    public boolean onKeyMultiple(int code, int count, android.view.KeyEvent event) { return false; }
    public void onBackPressed() { finish(); }
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (event == null) throw new NullPointerException("event");
        if(event.getAction()==android.view.MotionEvent.ACTION_DOWN)onUserInteraction();
        if (content != null && content.getVisibility() == View.VISIBLE && content.dispatchTouchEvent(event)) return true;
        return onTouchEvent(event);
    }
    public boolean onTouchEvent(android.view.MotionEvent event) { return false; }
    public android.view.WindowManager getWindowManager() { return (android.view.WindowManager)getSystemService(WINDOW_SERVICE); }
    public Application getApplication() { return (Application) getApplicationContext(); }
    @Override public void startActivity(android.content.Intent intent){startActivity(intent,null);}
    @Override public void startActivity(android.content.Intent intent,Bundle options){if(options!=null&&!options.isEmpty())throw new UnsupportedOperationException("Activity launch options");shiraz.runtime.CapturePermissions policy=shiraz.runtime.CapturePermissions.current();if(policy!=null&&intent!=null&&intent.getComponent()==null&&"android.settings.APPLICATION_DETAILS_SETTINGS".equals(intent.getAction())&&intent.getData()!=null&&("package:"+getPackageName()).equals(intent.getData().toString())){policy.showPrivacy(this);return;}if(shiraz.runtime.HostAppInstaller.handlesRemoval(intent)){shiraz.runtime.HostAppInstaller.confirmRemoval(this,intent,new shiraz.runtime.HostAppInstaller.RemovalResult(){public void complete(boolean removed){shirazDeliverResult(removed?RESULT_OK:RESULT_CANCELED,new android.content.Intent().putExtra("android.intent.extra.INSTALL_RESULT",removed?1:-1));}});return;}shirazActivityLaunches().enqueue(intent);}
    public static final int RESULT_CANCELED = 0, RESULT_OK = -1, RESULT_FIRST_USER = 1;
    private int resultCode = RESULT_CANCELED, pendingRequestCode = -1;
    private android.content.Intent resultData;
    public final void setResult(int code) { resultCode = code; resultData = null; }
    public final void setResult(int code, android.content.Intent data) { resultCode = code; resultData = data; }
    public void startActivityForResult(android.content.Intent intent, int requestCode) { startActivityForResult(intent, requestCode, null); }
    public void startActivityForResult(android.content.Intent intent, int requestCode, Bundle options) {
        if (options != null && !options.isEmpty()) throw new UnsupportedOperationException("Activity launch options");
        if(requestCode>=0&&shiraz.runtime.HostDocumentPicker.handles(intent)
                &&(!shiraz.runtime.HostContentPicker.handles(intent)||!shiraz.runtime.HostContentPicker.hasConfiguredFile())){
            pendingRequestCode=requestCode;android.widget.PopupWindow.shirazClear();
            shiraz.runtime.HostDocumentPicker.launch(this,intent,new shiraz.runtime.HostDocumentPicker.Result(){public void complete(int code,android.content.Intent data){shirazDeliverResult(code,data);}});return;
        }
        if(requestCode>=0&&shiraz.runtime.HostCameraCapture.handles(intent)){
            pendingRequestCode=requestCode;android.widget.PopupWindow.shirazClear();
            shiraz.runtime.HostCameraCapture.launch(this,intent,new shiraz.runtime.HostCameraCapture.Result(){public void complete(int code,android.content.Intent data){shirazDeliverResult(code,data);}});return;
        }
        if (requestCode >= 0 && shiraz.runtime.HostContentPicker.handles(intent)) {
            pendingRequestCode = requestCode;
            android.widget.PopupWindow.shirazClear();
            android.net.Uri uri = shiraz.runtime.HostContentPicker.pick(intent);
            android.content.Intent data = null;
            int code = RESULT_CANCELED;
            if (uri != null) {
                data = new android.content.Intent();
                data.setData(uri);
                data.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                code = RESULT_OK;
            }
            shirazDeliverResult(code, data);
            return;
        }
        if (requestCode >= 0) pendingRequestCode = requestCode;
        startActivity(intent, options);
    }
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {}
    public final int shirazResultCode() { return resultCode; }
    public final android.content.Intent shirazResultData() { return resultData; }
    public final void shirazDeliverResult(int code, android.content.Intent data) {
        int request = pendingRequestCode;
        if (request < 0) return;
        pendingRequestCode = -1;
        onActivityResult(request, code, data);
    }
    public android.content.ComponentName getCallingActivity() { return null; }
    public String getCallingPackage() { android.content.ComponentName caller=getCallingActivity();return caller==null?null:caller.getPackageName(); }
    private int[] pendingTransition;
    public void overridePendingTransition(int enterAnim, int exitAnim) {pendingTransition=new int[]{enterAnim,exitAnim};}
    public final int[] shirazTakeTransition(){int[] value=pendingTransition;pendingTransition=null;return value;}
    protected void onUserLeaveHint() {}
    public void reportFullyDrawn() {}
    public void setShowWhenLocked(boolean value) {}
    public void setTurnScreenOn(boolean value) {}
    public boolean isInPictureInPictureMode() { return false; }
    public boolean isChild() { return false; }
    public android.content.Intent getParentActivityIntent() { return null; }
    public boolean shouldUpRecreateTask(android.content.Intent target) { return false; }
    public void finish() { finishing = true; }
    // The host keeps no persistent Android recents entry. Finishing the root
    // activity closes its host task through the normal runner lifecycle.
    public void finishAndRemoveTask() { finish(); }
    public void finishAffinity() { finish(); }
    public void finishAfterTransition() { finish(); }
    public boolean isFinishing() { return finishing; }
    // Host adapter access. Applications compiled against the Android SDK cannot call this.
    public final View shirazContentView() { return content; }
}
