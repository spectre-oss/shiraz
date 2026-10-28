// SPDX-License-Identifier: GPL-3.0-only
public final class SurfaceSmoke {
 private static native int glBegin();private static native int glSample(int texture);private static native void glEnd();
 private static void externalTexture(){
  android.graphics.SurfaceTexture texture=new android.graphics.SurfaceTexture(0);texture.detachFromGLContext();
  try{texture.updateTexImage();throw new AssertionError("Detached update accepted");}catch(IllegalStateException expected){}
  int name=glBegin();if(name==0)throw new AssertionError("EGL test context");
  try{texture.attachToGLContext(name);texture.setDefaultBufferSize(2,2);android.view.Surface producer=new android.view.Surface(texture);
   for(int color:new int[]{0xffff0000,0xff00ff00,0xff0000ff}){android.graphics.Canvas c=producer.lockCanvas(null);c.drawColor(color);producer.unlockCanvasAndPost(c);texture.updateTexImage();int actual=glSample(name);if(actual!=color)throw new AssertionError("External texture pixels "+Integer.toHexString(actual)+" expected "+Integer.toHexString(color));}
   long timestamp=texture.getTimestamp();texture.updateTexImage();if(texture.getTimestamp()!=timestamp)throw new AssertionError("Unchanged frame timestamp");
   texture.detachFromGLContext();texture.attachToGLContext(name);texture.updateTexImage();if(glSample(name)!=0xff0000ff)throw new AssertionError("Reattached image");
   producer.release();texture.release();
  }finally{texture.release();glEnd();}
  System.out.println("SURFACE_EXTERNAL_TEXTURE_PIXELS_OK");
 }
 private static native android.view.Surface wrap(android.view.Surface source);
 private static void parcelInterop(){
  android.view.Surface source=new android.view.Surface();source.shirazConfigure(2,2,1);
  android.graphics.Canvas c=source.lockCanvas(null);c.drawColor(0xff234567);source.unlockCanvasAndPost(c);
  android.os.Parcel p=android.os.Parcel.obtain();source.writeToParcel(p,0);
  if(!p.hasFileDescriptors())throw new AssertionError("Surface descriptor missing");
  p.setDataPosition(0);android.view.Surface copy=android.view.Surface.CREATOR.createFromParcel(p);p.recycle();
  android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888);
  copy.shirazDraw(new android.graphics.Canvas(b),2,2);
  if(b.getPixel(1,1)!=0xff234567)throw new AssertionError("Pre-export pixels lost");
  copy.shirazConfigure(3,2,1);c=copy.lockCanvas(null);c.drawColor(0xffabcdef);copy.unlockCanvasAndPost(c);
  source.shirazDraw(new android.graphics.Canvas(b),2,2);
  if(source.shirazWidth()!=3||b.getPixel(0,0)!=0xffabcdef)throw new AssertionError("Parcel producer resize/pixels");
  p=android.os.Parcel.obtain();copy.writeToParcel(p,android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
  if(copy.isValid())throw new AssertionError("Returned Surface ownership");
  p.setDataPosition(0);android.view.Surface survivor=android.view.Surface.CREATOR.createFromParcel(p);p.recycle();source.release();
  survivor.shirazDraw(new android.graphics.Canvas(b),2,2);
  if(b.getPixel(0,0)!=0xffabcdef)throw new AssertionError("Parcel ownership lifetime");survivor.release();
  p=android.os.Parcel.obtain();new android.view.Surface().writeToParcel(p,0);p.setDataPosition(0);
  if(android.view.Surface.CREATOR.createFromParcel(p).isValid())throw new AssertionError("Invalid Surface became valid");p.recycle();b.recycle();
  System.out.println("SURFACE_PARCEL_PIXELS_RESIZE_LIFETIME_OK");
 }
 private static void nativeInterop(){
  System.loadLibrary("test-surface-wrapper");
  android.view.Surface original=new android.view.Surface();original.shirazConfigure(2,2,android.graphics.PixelFormat.RGBA_8888);
  android.view.Surface copy=wrap(original);if(copy==null||!copy.isValid())throw new AssertionError("Native surface wrapper");
  android.graphics.Canvas canvas=original.lockCanvas(null);canvas.drawColor(0xff123456);original.unlockCanvasAndPost(canvas);
  android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888);copy.shirazDraw(new android.graphics.Canvas(bitmap),2,2);
  if(bitmap.getPixel(1,1)!=0xff123456)throw new AssertionError("Java-to-native-to-Java pixels");
  original.shirazConfigure(3,2,android.graphics.PixelFormat.RGBA_8888);canvas=original.lockCanvas(null);canvas.drawColor(0xff987654);original.unlockCanvasAndPost(canvas);copy.shirazDraw(new android.graphics.Canvas(bitmap),2,2);
  if(copy.shirazWidth()!=3||bitmap.getPixel(0,0)!=0xff987654)throw new AssertionError("Native wrapper resize");
  original.release();canvas=copy.lockCanvas(null);canvas.drawColor(0xffabcdef);copy.unlockCanvasAndPost(canvas);copy.shirazDraw(new android.graphics.Canvas(bitmap),2,2);
  if(bitmap.getPixel(0,0)!=0xffabcdef)throw new AssertionError("Native wrapper lifetime");copy.release();bitmap.recycle();
 }

 private static Object field(Object target,String name)throws Exception{java.lang.reflect.Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 private static void buffers()throws Exception{
  android.view.Surface surface=new android.view.Surface();java.lang.reflect.Method configure=android.view.Surface.class.getDeclaredMethod("shirazConfigure",int.class,int.class,int.class);configure.setAccessible(true);
  configure.invoke(surface,8,6,android.graphics.PixelFormat.RGBA_8888);
  android.graphics.Bitmap first=(android.graphics.Bitmap)field(surface,"front");
  android.graphics.Canvas c=surface.lockCanvas(null);c.drawColor(0xffff0000);surface.unlockCanvasAndPost(c);
  android.graphics.Bitmap second=(android.graphics.Bitmap)field(surface,"front");
  for(int i=0;i<6;i++){c=surface.lockCanvas(null);android.graphics.Bitmap back=(android.graphics.Bitmap)field(surface,"back");if(back!=(i%2==0?first:second)||back.getPixel(7,5)!=0xffff0000)throw new AssertionError("Reusable buffer identity/contents");c.drawRect(0,0,1,1,new android.graphics.Paint());surface.unlockCanvasAndPost(c);}
  c=surface.lockCanvas(null);android.graphics.Bitmap stale=(android.graphics.Bitmap)field(surface,"back");configure.invoke(surface,4,3,android.graphics.PixelFormat.RGBA_8888);surface.unlockCanvasAndPost(c);if(!stale.isRecycled())throw new AssertionError("Stale resized producer retained");
  android.graphics.Bitmap resized=(android.graphics.Bitmap)field(surface,"front");if(resized.getWidth()!=4||resized.getPixel(0,0)!=0)throw new AssertionError("Stale producer replaced resized front");
  c=surface.lockCanvas(null);android.graphics.Bitmap released=(android.graphics.Bitmap)field(surface,"back");surface.release();if(released.isRecycled())throw new AssertionError("Released active producer prematurely");surface.unlockCanvasAndPost(c);if(!released.isRecycled()||!resized.isRecycled()||field(surface,"spare")!=null)throw new AssertionError("Release retained buffers");
  try{first.shirazCopyPixelsInto(resized);throw new AssertionError("Recycled copy accepted");}catch(IllegalStateException expected){}
 }
 private static void textures()throws Exception{
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
  android.view.TextureView view=new android.view.TextureView(context);final int[] calls=new int[3];final android.graphics.SurfaceTexture[] retained={null};
  view.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener(){public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture s,int w,int h){calls[0]++;if(w!=8||h!=6)throw new AssertionError("Texture available dimensions");}public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture s,int w,int h){calls[1]++;}public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture s){retained[0]=s;calls[2]++;return false;}public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture s){}});
  view.layout(0,0,8,6);view.shirazAttachTree(android.view.ViewTreeObserver.shirazCreateWindowObserver());
  if(!view.isAvailable()||calls[0]!=1)throw new AssertionError("Texture availability");
  android.view.Surface producer=new android.view.Surface(view.getSurfaceTexture());android.graphics.Canvas c=producer.lockCanvas(null);c.drawColor(0xff12ab34);producer.unlockCanvasAndPost(c);
  android.graphics.Bitmap image=view.getBitmap();if(image.getPixel(7,5)!=0xff12ab34)throw new AssertionError("Texture producer pixels");image.recycle();producer.release();
  if(!view.isAvailable()||view.getSurfaceTexture().isReleased())throw new AssertionError("Producer release destroyed consumer");
  android.graphics.Matrix transform=new android.graphics.Matrix();transform.setTranslate(2,0);view.setTransform(transform);image=view.getBitmap();if(image.getPixel(0,2)!=0||image.getPixel(3,2)!=0xff12ab34)throw new AssertionError("Texture transform");image.recycle();
  view.layout(0,0,4,3);if(calls[1]!=1)throw new AssertionError("Texture resize callback");view.shirazDetachTree();if(calls[2]!=1||view.isAvailable()||retained[0].isReleased())throw new AssertionError("Retained texture ownership");retained[0].release();
  android.view.TextureView auto=new android.view.TextureView(context);auto.layout(0,0,4,3);auto.shirazAttachTree(android.view.ViewTreeObserver.shirazCreateWindowObserver());android.graphics.SurfaceTexture texture=auto.getSurfaceTexture();auto.shirazDetachTree();if(!texture.isReleased())throw new AssertionError("Default detach release");
 }
 private static void delayedInvalidation()throws Exception{
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
  final int[] calls={0};final Thread owner=Thread.currentThread();
  android.view.View view=new android.view.View(context){public void invalidate(){if(Thread.currentThread()!=owner)throw new AssertionError("Invalidation off UI thread");calls[0]++;super.invalidate();}};
  view.postInvalidateDelayed(0,0,0,4,4);
  view.shirazAttachTree(android.view.ViewTreeObserver.shirazCreateWindowObserver());
  android.os.Looper.myLooper().shirazDispatchDue(100);int baseline=calls[0];
  view.postInvalidateDelayed(100,0,0,4,4);android.os.Looper.myLooper().shirazDispatchDue(100);
  if(calls[0]!=baseline)throw new AssertionError("Delayed redraw ran early");
  Thread.sleep(130);android.os.Looper.myLooper().shirazDispatchDue(100);
  if(calls[0]!=baseline+1)throw new AssertionError("Delayed rectangle redraw missing");
  Thread worker=new Thread(new Runnable(){public void run(){view.postInvalidate(0,0,4,4);}});worker.start();worker.join();
  if(calls[0]!=baseline+1)throw new AssertionError("Worker invalidated synchronously");
  android.os.Looper.myLooper().shirazDispatchDue(100);if(calls[0]!=baseline+2)throw new AssertionError("Worker redraw missing");
  view.postInvalidateDelayed(50);view.shirazDetachTree();view.shirazAttachTree(android.view.ViewTreeObserver.shirazCreateWindowObserver());baseline=calls[0];
  Thread.sleep(80);android.os.Looper.myLooper().shirazDispatchDue(100);if(calls[0]!=baseline)throw new AssertionError("Detached redraw leaked into new attachment");view.shirazDetachTree();
 }
 private static void windowVisibility(){
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
  android.widget.FrameLayout root=new android.widget.FrameLayout(context);android.view.SurfaceView view=new android.view.SurfaceView(context);root.addView(view);root.layout(0,0,8,8);view.layout(0,0,8,8);
  final StringBuilder events=new StringBuilder();view.getHolder().addCallback(new android.view.SurfaceHolder.Callback(){public void surfaceCreated(android.view.SurfaceHolder h){events.append("C");}public void surfaceChanged(android.view.SurfaceHolder h,int f,int w,int height){events.append("R");}public void surfaceDestroyed(android.view.SurfaceHolder h){events.append("D");}});
  root.shirazAttachTree(android.view.ViewTreeObserver.shirazCreateWindowObserver());
  root.shirazSetWindowVisibility(android.view.View.GONE);root.shirazSetWindowVisibility(android.view.View.GONE);
  if(view.getHolder().getSurface().isValid()||!events.toString().equals("CRD"))throw new AssertionError("Hidden window kept surface alive: "+events);
  view.layout(0,0,10,8);if(view.getHolder().getSurface().isValid())throw new AssertionError("Hidden resize recreated surface");
  root.shirazSetWindowVisibility(android.view.View.VISIBLE);if(!view.getHolder().getSurface().isValid()||!events.toString().equals("CRDCR"))throw new AssertionError("Visible window did not recreate surface: "+events);
  root.shirazDetachTree();if(!events.toString().equals("CRDCRD"))throw new AssertionError("Duplicate surface teardown: "+events);
  System.out.println("SURFACE_WINDOW_VISIBILITY_OK");
 }
 private static void activitySurface(){
  final android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}public Object getSystemService(String name){return null;}};
  final android.view.SurfaceView view=new android.view.SurfaceView(context);
  class WindowActivity extends android.app.Activity {
   WindowActivity(){attachBaseContext(context);}
   public android.content.Context getApplicationContext(){return null;}
   public android.content.res.Resources.Theme getTheme(){return getResources().newTheme();}
   protected void onCreate(android.os.Bundle state){super.onCreate(state);setContentView(view);}
   protected void onDestroy(){if(view.getHolder().getSurface().isValid())throw new AssertionError("Engine destroyed before its surface");super.onDestroy();}
  }
  final WindowActivity activity=new WindowActivity();java.util.Properties facts=new java.util.Properties();facts.setProperty("activity.count","1");facts.setProperty("activity.0.name","sample.Main");
  shiraz.runtime.ActivityLifecycle life=new shiraz.runtime.ActivityLifecycle(new shiraz.runtime.ActivityLifecycle.Factory(){public android.app.Activity create(){return activity;}},new shiraz.runtime.ActivityLifecycle.Cleanup(){public void detached(android.app.Activity old){old.shirazContentView().shirazDetachTree();}},new shiraz.runtime.ActivityConfigurationPolicy(facts,"sample.Main",27),27);
  life.start();android.view.View root=activity.shirazContentView();root.layout(0,0,8,8);view.layout(0,0,8,8);root.shirazAttachTree(android.view.ViewTreeObserver.shirazCreateWindowObserver());
  if(!view.getHolder().getSurface().isValid())throw new AssertionError("Activity surface not created");
  life.pauseForLaunch();life.stopCovered();if(view.getHolder().getSurface().isValid())throw new AssertionError("Stopped activity surface still valid");
  life.resumeFromBackStack();if(!view.getHolder().getSurface().isValid())throw new AssertionError("Resumed activity surface not recreated");
  life.close();life.close();System.out.println("ACTIVITY_SURFACE_STOP_RESUME_DESTROY_OK");
 }
 public static void main(String[] args)throws Exception {
  nativeInterop();externalTexture();
  android.os.MemoryFile memory=new android.os.MemoryFile("media-test",4);memory.writeBytes(new byte[]{1,2,3,4},0,0,4);
  android.os.ParcelFileDescriptor duplicate=android.os.ParcelFileDescriptor.dup(memory.getFileDescriptor());
  byte[] bytes=shiraz.runtime.NativeFd.readRegion(duplicate.getFileDescriptor(),0,4);if(bytes[2]!=3)throw new AssertionError("MemoryFile descriptor data");
  memory.close();bytes=shiraz.runtime.NativeFd.readRegion(duplicate.getFileDescriptor(),0,4);if(bytes[3]!=4)throw new AssertionError("MemoryFile duplicate lifetime");duplicate.close();
  System.out.println("MEMORY_FILE_SHARED_DESCRIPTOR_OK");
  android.view.Display.HdrCapabilities hdr=new android.view.Display.HdrCapabilities();
  if(hdr.getSupportedHdrTypes().length!=0||hdr.getDesiredMaxLuminance()!=-1||hdr.getDesiredMinLuminance()!=-1||hdr.getDesiredMaxAverageLuminance()!=-1)throw new AssertionError("Non-HDR display capabilities");
  android.graphics.PixelFormat pixel=new android.graphics.PixelFormat();
  android.graphics.PixelFormat.getPixelFormatInfo(android.graphics.PixelFormat.RGBA_8888,pixel);
  if(pixel.bitsPerPixel!=32||pixel.bytesPerPixel!=4||!android.graphics.PixelFormat.formatHasAlpha(1)||android.graphics.PixelFormat.formatHasAlpha(4))throw new AssertionError("RGBA format information");
  android.graphics.PixelFormat.getPixelFormatInfo(android.graphics.PixelFormat.YCbCr_420_SP,pixel);
  if(pixel.bitsPerPixel!=12||pixel.bytesPerPixel!=1)throw new AssertionError("Planar format information");
  try{android.graphics.PixelFormat.getPixelFormatInfo(999,pixel);throw new AssertionError("Unknown format accepted");}catch(IllegalArgumentException expected){}

  android.os.Looper.prepareMainLooper();
  parcelInterop();
  buffers();
  textures();
  delayedInvalidation();
  windowVisibility();
  activitySurface();
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
  android.widget.FrameLayout root=org.shiraz.fixture.SurfaceFixture.prepare(context);root.shirazAttachTree(android.view.ViewTreeObserver.shirazCreateWindowObserver());
  try{org.shiraz.fixture.SurfaceFixture.checkAttached(root);}catch(Throwable failure){root.shirazDetachTree();throw failure;}
  final android.view.SurfaceHolder holder=((android.view.SurfaceView)root.getChildAt(0)).getHolder();android.graphics.Canvas old=holder.lockCanvas();final boolean[] released={false};final Throwable[] error={null};
  final Thread waiter=new Thread(new Runnable(){public void run(){try{released[0]=holder.lockCanvas()==null;}catch(Throwable failure){error[0]=failure;}}});holder.addCallback(new android.view.SurfaceHolder.Callback(){public void surfaceCreated(android.view.SurfaceHolder h){}public void surfaceChanged(android.view.SurfaceHolder h,int f,int w,int height){}public void surfaceDestroyed(android.view.SurfaceHolder h){try{waiter.join(3000);}catch(InterruptedException failure){throw new AssertionError(failure);}if(waiter.isAlive())throw new AssertionError("Destruction callback could not stop waiting producer");}});waiter.setDaemon(true);waiter.start();Thread.sleep(20);root.shirazDetachTree();waiter.join(3000);holder.unlockCanvasAndPost(old);
  if(waiter.isAlive()||error[0]!=null||!released[0])throw new AssertionError("Waiting producer did not release during teardown");
  org.shiraz.fixture.SurfaceFixture.checkDetached(root);
  android.os.Looper.shirazShutdownMain();
 }
}
