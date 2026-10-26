// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Android image-capture intent implemented inside the app's native window. */
public final class HostCameraCapture {
 public interface Result {void complete(int code,Intent data);}
 public static boolean handles(Intent intent){Intent target=HostContentPicker.unwrap(intent);return target!=null&&target.getComponent()==null&&target.getPackage()==null&&"android.media.action.IMAGE_CAPTURE".equals(target.getAction());}
 public static android.content.pm.ResolveInfo resolve(Context context,Intent intent){
  if(!handles(intent))return null;android.content.pm.ResolveInfo r=new android.content.pm.ResolveInfo();r.activityInfo=new android.content.pm.ActivityInfo();r.activityInfo.packageName=context.getPackageName();r.activityInfo.name=HostCameraCapture.class.getName();r.activityInfo.enabled=true;r.activityInfo.exported=false;return r;
 }
 public static void launch(final Activity activity,final Intent intent,final Result result){
  DialogOverlay.requireMain();final CapturePermissions policy=CapturePermissions.current();
  if(policy==null||!policy.canRequest(CapturePermissions.CAMERA)){result.complete(Activity.RESULT_CANCELED,null);return;}
  final Runnable open=new Runnable(){public void run(){if(activity.isDestroyed()||!policy.granted(CapturePermissions.CAMERA)){result.complete(Activity.RESULT_CANCELED,null);return;}new CaptureDialog(activity,HostContentPicker.unwrap(intent),policy,result).show();}};
  if(policy.granted(CapturePermissions.CAMERA))open.run();else policy.prompt(activity,CapturePermissions.CAMERA,new Runnable(){public void run(){new Handler(Looper.getMainLooper()).post(open);}});
 }
 private static final class CaptureDialog extends AlertDialog {
  private final Activity owner;private final Intent request;private final CapturePermissions permissions;private final Result result;private final Handler main=new Handler(Looper.getMainLooper());
  private volatile boolean closing,frameQueued;private volatile long handle;private long lease;private Bitmap image;private ImageView preview;private boolean completed;
  CaptureDialog(Activity owner,Intent request,CapturePermissions policy,Result result){super(owner);this.owner=owner;this.request=request;permissions=policy;this.result=result;setTitle("Camera — Shiraz");setMessage("Starting camera…");setButton(-1,"Take photo",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){savePhoto();}});setButton(-2,"Cancel",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){complete(Activity.RESULT_CANCELED,null);}});setOnDismissListener(new DialogInterface.OnDismissListener(){public void onDismiss(DialogInterface d){complete(Activity.RESULT_CANCELED,null);}});setCanceledOnTouchOutside(false);}
  protected void onCreate(Bundle state){super.onCreate(state);CapturePermissions.styleDialog(this);}
  protected View createBody(){preview=new ImageView(owner);preview.setMinimumHeight(dp(240));preview.setScaleType(ImageView.ScaleType.FIT_CENTER);return preview;}
  protected void onStart(){super.onStart();getButton(-1).setEnabled(false);new Thread(new Runnable(){public void run(){capture();}},"Shiraz camera").start();}
  private void capture(){try{
   final long opened=NativeCapture.cameraOpen();synchronized(this){if(closing){NativeCapture.close(opened);return;}handle=opened;}
   long started=permissions.begin(CapturePermissions.CAMERA,new Runnable(){public void run(){closeDevice();main.post(new Runnable(){public void run(){dismiss();}});}});synchronized(this){if(closing){permissions.end(started);return;}lease=started;}
   while(!closing){final int[] frame=NativeCapture.cameraFrame(opened);if(frame==null||frameQueued)continue;frameQueued=true;main.post(new Runnable(){public void run(){try{if(closing)return;Bitmap next=Bitmap.createBitmap(frame[0],frame[1],Bitmap.Config.ARGB_8888);next.setPixels(frame,2,frame[0],0,0,frame[0],frame[1]);Bitmap old=image;image=next;preview.setImageBitmap(next);if(old!=null)old.recycle();getButton(-1).setEnabled(true);setMessage("Camera in use. Take a photo or cancel.");}finally{frameQueued=false;}}});}
  }catch(final RuntimeException failure){if(!closing){permissions.failure(CapturePermissions.CAMERA);closeDevice();main.post(new Runnable(){public void run(){setMessage("No camera is available. Connect a webcam, then try again.");preview.setVisibility(View.GONE);getButton(-1).setVisibility(View.GONE);getButton(-2).setText("Close");}});}}}
  private void savePhoto(){if(image==null||closing){complete(Activity.RESULT_CANCELED,null);return;}try{
   Uri output=request.getParcelableExtra("output");if(output==null)output=Uri.fromFile(File.createTempFile("shiraz-photo-",".jpg",owner.getCacheDir()));
   try(OutputStream stream="file".equals(output.getScheme())?new FileOutputStream(output.getPath()):owner.getContentResolver().openOutputStream(output)){if(!image.compress(Bitmap.CompressFormat.JPEG,90,stream))throw new IOException("JPEG encoding failed");}
   complete(Activity.RESULT_OK,new Intent().setData(output));
  }catch(IOException|RuntimeException failure){android.util.Log.w("ShirazCamera","Unable to save captured photo",failure);complete(Activity.RESULT_CANCELED,null);}}
  private void complete(int code,Intent data){if(completed)return;completed=true;dismiss();if(!owner.isDestroyed())result.complete(code,data);}
  private void closeDevice(){long old,id;synchronized(this){closing=true;old=handle;handle=0;id=lease;lease=0;}try{if(old!=0)NativeCapture.close(old);}finally{if(id!=0)permissions.end(id);}}
  protected void onStop(){closeDevice();if(image!=null){preview.setImageBitmap(null);image.recycle();image=null;}super.onStop();}
 }
}
