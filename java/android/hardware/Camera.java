// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.util.*;
import shiraz.runtime.*;
/** Legacy camera preview subset for Linux V4L2 webcams and QR scanners. */
public class Camera {
 public static final int CAMERA_ERROR_UNKNOWN=1,CAMERA_ERROR_SERVER_DIED=100;
 public static class CameraInfo {public static final int CAMERA_FACING_BACK=0,CAMERA_FACING_FRONT=1;public int facing=0,orientation=0;public boolean canDisableShutterSound=true;}
 public class Size {public int width,height;public Size(int w,int h){width=w;height=h;}public boolean equals(Object value){return value instanceof Camera.Size&&((Size)value).width==width&&((Size)value).height==height;}public int hashCode(){return width*32713+height;}}
 public interface PreviewCallback {void onPreviewFrame(byte[] data,Camera camera);}public interface AutoFocusCallback {void onAutoFocus(boolean success,Camera camera);}public interface ErrorCallback {void onError(int error,Camera camera);}
 public interface ShutterCallback {void onShutter();}public interface PictureCallback {void onPictureTaken(byte[] data,Camera camera);}
 public static class Area {public Rect rect;public int weight;public Area(Rect rect,int weight){this.rect=rect;this.weight=weight;}}
 private boolean unlocked,takingPicture;private int pictureGeneration;
 private final Handler handler;private final CapturePermissions permissions;private long device,lease;private volatile boolean previewing,released,callbackQueued;private volatile int generation;private volatile PreviewCallback callback;private boolean oneShot,buffered;private ErrorCallback error;private SurfaceHolder holder;private SurfaceTexture previewTexture;private int orientation;private final ArrayDeque<byte[]> buffers=new ArrayDeque<byte[]>();private Parameters parameters;
 private Camera(){permissions=CapturePermissions.current();if(permissions==null)throw new SecurityException("Camera consent is unavailable");permissions.require(CapturePermissions.CAMERA);handler=new Handler(Looper.getMainLooper());long opened=0;try{opened=NativeCapture.cameraOpen();device=opened;int[] size=NativeCapture.cameraSize(device);parameters=new Parameters(size[0],size[1]);lease=permissions.begin(CapturePermissions.CAMERA,new Runnable(){public void run(){release();}});}catch(RuntimeException e){if(opened!=0)NativeCapture.close(opened);permissions.failure(CapturePermissions.CAMERA);throw e;}}
 public static int getNumberOfCameras(){return NativeCapture.cameraCount();}
 public static void getCameraInfo(int id,CameraInfo info){if(id!=0||getNumberOfCameras()==0||info==null)throw new IllegalArgumentException("Camera index");info.facing=0;info.orientation=0;info.canDisableShutterSound=true;}
 public static Camera open(){return open(0);}public static Camera open(int id){if(id!=0)throw new IllegalArgumentException("Only the default host camera is supported");return new Camera();}
 public synchronized Parameters getParameters(){live();return new Parameters(parameters);}public synchronized void setParameters(Parameters p){live();if(p==null||p.width!=parameters.width||p.height!=parameters.height)throw new IllegalArgumentException("Unsupported camera dimensions");parameters=new Parameters(p);}
 public synchronized void unlock(){live();unlocked=true;}
 public synchronized void reconnect()throws java.io.IOException{if(released)throw new java.io.IOException("Camera released");permissions.require(CapturePermissions.CAMERA);unlocked=false;}
 public synchronized void lock(){if(released)throw new IllegalStateException("Camera released");unlocked=false;}
 public synchronized void setPreviewDisplay(SurfaceHolder value)throws java.io.IOException{live();holder=value;previewTexture=null;}
 public synchronized void setPreviewTexture(SurfaceTexture texture)throws java.io.IOException{live();if(texture!=null){if(texture.isReleased())throw new java.io.IOException("Preview texture released");texture.setDefaultBufferSize(parameters.width,parameters.height);}previewTexture=texture;holder=null;}
 public synchronized void setDisplayOrientation(int degrees){if(degrees!=0&&degrees!=90&&degrees!=180&&degrees!=270)throw new IllegalArgumentException("orientation");orientation=degrees;}
 public synchronized void setErrorCallback(ErrorCallback value){error=value;}
 public synchronized void setOneShotPreviewCallback(PreviewCallback value){callback=value;oneShot=true;buffered=false;}
 public synchronized void setPreviewCallback(PreviewCallback value){callback=value;oneShot=false;buffered=false;}
 public synchronized void setPreviewCallbackWithBuffer(PreviewCallback value){callback=value;oneShot=false;buffered=true;}
 public synchronized void addCallbackBuffer(byte[] bytes){live();if(bytes==null||bytes.length<parameters.width*parameters.height*3/2)throw new IllegalArgumentException("NV21 preview buffer");if(buffers.size()>=8)throw new IllegalStateException("Preview buffer limit");buffers.addLast(bytes);}
 public synchronized void autoFocus(final AutoFocusCallback value){live();if(value!=null)handler.post(new Runnable(){public void run(){if(!released)value.onAutoFocus(true,Camera.this);}});}public void cancelAutoFocus(){}
 private void live(){if(released)throw new IllegalStateException("Camera released");if(unlocked)throw new IllegalStateException("Camera is unlocked for recording");}
 public void takePicture(ShutterCallback shutter,PictureCallback raw,PictureCallback jpeg){takePicture(shutter,raw,null,jpeg);}
 public void takePicture(final ShutterCallback shutter,final PictureCallback raw,final PictureCallback postview,final PictureCallback jpeg){
  final long source;final int token,quality,rotation;synchronized(this){live();permissions.require(CapturePermissions.CAMERA);if(takingPicture)throw new IllegalStateException("Picture capture already active");takingPicture=true;previewing=false;generation++;source=device;token=++pictureGeneration;quality=parameters.jpegQuality;rotation=parameters.rotation;}
  new Thread(new Runnable(){public void run(){try{int[] frame=NativeCapture.cameraSnapshot(source);if(frame==null)throw new IllegalStateException("Camera snapshot unavailable");final byte[] bytes=encodePicture(frame,quality,rotation);handler.post(new Runnable(){public void run(){try{if(released||pictureGeneration!=token||!permissions.granted(CapturePermissions.CAMERA))return;if(shutter!=null)shutter.onShutter();if(raw!=null)raw.onPictureTaken(null,Camera.this);if(postview!=null)postview.onPictureTaken(null,Camera.this);if(jpeg!=null)jpeg.onPictureTaken(bytes,Camera.this);}finally{synchronized(Camera.this){if(pictureGeneration==token)takingPicture=false;}}}});}catch(RuntimeException failure){synchronized(Camera.this){if(pictureGeneration==token)takingPicture=false;}final ErrorCallback target=error;if(target!=null)handler.post(new Runnable(){public void run(){if(!released&&pictureGeneration==token)target.onError(CAMERA_ERROR_UNKNOWN,Camera.this);}});else android.util.Log.w("Camera","Host JPEG capture failed",failure);}}},"Shiraz camera picture").start();
 }
 private static byte[] encodePicture(int[] frame,int quality,int rotation){
  if(frame.length<2||frame[0]<1||frame[1]<1||(long)frame[0]*frame[1]!=frame.length-2)throw new IllegalArgumentException("Camera frame");
  Bitmap original=Bitmap.createBitmap(frame[0],frame[1],Bitmap.Config.ARGB_8888),image=original;
  try{original.setPixels(frame,2,frame[0],0,0,frame[0],frame[1]);if(rotation!=0){Matrix transform=new Matrix();transform.setRotate(rotation);image=Bitmap.createBitmap(original,0,0,frame[0],frame[1],transform,false);}java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();if(!image.compress(Bitmap.CompressFormat.JPEG,quality,output))throw new IllegalStateException("JPEG encoding failed");return output.toByteArray();}
  finally{if(image!=original)image.recycle();original.recycle();}
 }
 public synchronized void startPreview(){live();if(previewing)return;previewing=true;final int token=++generation;new Thread(new Runnable(){public void run(){frames(token);}},"Shiraz camera preview").start();}
 public synchronized void stopPreview(){previewing=false;generation++;}
 public void release(){long old,id;synchronized(this){if(released)return;released=true;previewing=false;generation++;pictureGeneration++;takingPicture=false;old=device;device=0;id=lease;lease=0;callback=null;holder=null;previewTexture=null;buffers.clear();}try{NativeCapture.close(old);}finally{permissions.end(id);}}
 private void frames(final int token){try{while(previewing&&!released&&generation==token){long id;SurfaceHolder surface;SurfaceTexture texture;int rotation;synchronized(this){id=device;surface=holder;texture=previewTexture;rotation=orientation;}final int[] frame=NativeCapture.cameraFrame(id);if(frame==null)continue;if(!previewing||released||generation!=token)break;
  Surface output=surface!=null?surface.getSurface():texture!=null&&!texture.isReleased()?texture.shirazSurface():null;
  if(output!=null&&output.isValid()){Bitmap bitmap=Bitmap.createBitmap(frame[0],frame[1],Bitmap.Config.ARGB_8888);bitmap.setPixels(frame,2,frame[0],0,0,frame[0],frame[1]);Canvas canvas=null;try{canvas=surface!=null?surface.lockCanvas():output.lockCanvas(null);if(canvas!=null){canvas.drawColor(0xff000000);if(rotation!=0)canvas.rotate(rotation,canvas.getWidth()/2f,canvas.getHeight()/2f);canvas.drawBitmap(bitmap,null,new Rect(0,0,canvas.getWidth(),canvas.getHeight()),null);}}finally{if(canvas!=null){if(surface!=null)surface.unlockCanvasAndPost(canvas);else output.unlockCanvasAndPost(canvas);}bitmap.recycle();}}
  final PreviewCallback target;final byte[] bytes;synchronized(this){target=callback;if(target==null||callbackQueued)continue;bytes=buffered?buffers.pollFirst():new byte[frame[0]*frame[1]*3/2];if(bytes==null)continue;if(oneShot)callback=null;callbackQueued=true;}
  nv21(frame,bytes);handler.post(new Runnable(){public void run(){try{if(!released&&previewing&&generation==token)target.onPreviewFrame(bytes,Camera.this);}finally{callbackQueued=false;}}});
 }}catch(RuntimeException failure){if(!released){final ErrorCallback target=error;release();if(target!=null)handler.post(new Runnable(){public void run(){target.onError(CAMERA_ERROR_UNKNOWN,Camera.this);}});}}}
 private static int clamp(int value){return Math.min(255,Math.max(0,value));}
 private static void nv21(int[] frame,byte[] out){int w=frame[0],h=frame[1],uv=w*h;if(out.length<uv*3/2)throw new IllegalArgumentException("Preview size changed");for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=frame[2+y*w+x],r=c>>16&255,g=c>>8&255,b=c&255;out[y*w+x]=(byte)clamp(((66*r+129*g+25*b+128)>>8)+16);if((x&1)==0&&(y&1)==0&&uv+1<out.length){out[uv++]=(byte)clamp(((112*r-94*g-18*b+128)>>8)+128);out[uv++]=(byte)clamp(((-38*r-74*g+112*b+128)>>8)+128);}}}
 public final class Parameters {
  public static final String FOCUS_MODE_FIXED="fixed",FOCUS_MODE_AUTO="auto",FOCUS_MODE_CONTINUOUS_PICTURE="continuous-picture",FOCUS_MODE_CONTINUOUS_VIDEO="continuous-video",FLASH_MODE_OFF="off",FLASH_MODE_TORCH="torch",FLASH_MODE_AUTO="auto";
  private final int width,height;private int jpegQuality=90,thumbnailQuality=90,rotation;private boolean recordingHint;
  private Parameters(int width,int height){this.width=width;this.height=height;}private Parameters(Parameters other){width=other.width;height=other.height;jpegQuality=other.jpegQuality;thumbnailQuality=other.thumbnailQuality;rotation=other.rotation;recordingHint=other.recordingHint;}
  public int getMaxNumMeteringAreas(){return 0;}public int getMaxNumFocusAreas(){return 0;}
  public void setMeteringAreas(List<Area> areas){if(areas!=null&&!areas.isEmpty())throw new IllegalArgumentException("Host metering areas unavailable");}
  public void setFocusAreas(List<Area> areas){if(areas!=null&&!areas.isEmpty())throw new IllegalArgumentException("Host focus areas unavailable");}
  public int getMaxZoom(){return 0;}public int getZoom(){return 0;}public boolean isZoomSupported(){return false;}public void setZoom(int zoom){if(zoom!=0)throw new IllegalArgumentException("Host camera zoom unavailable");}
  public List<String> getSupportedSceneModes(){return null;}public String getSceneMode(){return "auto";}public void setSceneMode(String mode){if(mode!=null&&!"auto".equals(mode))throw new IllegalArgumentException("Host camera scene control unavailable");}
  public void setRecordingHint(boolean hint){recordingHint=hint;}
  public void setPictureFormat(int format){if(format!=256)throw new IllegalArgumentException("Only JPEG pictures are supported");}public int getPictureFormat(){return 256;}
  public void setJpegQuality(int quality){if(quality<1||quality>100)throw new IllegalArgumentException("JPEG quality");jpegQuality=quality;}public int getJpegQuality(){return jpegQuality;}
  public void setJpegThumbnailQuality(int quality){if(quality<1||quality>100)throw new IllegalArgumentException("JPEG thumbnail quality");thumbnailQuality=quality;}public int getJpegThumbnailQuality(){return thumbnailQuality;}
  public Size getJpegThumbnailSize(){return new Size(0,0);}public List<Size> getSupportedJpegThumbnailSizes(){return Collections.singletonList(getJpegThumbnailSize());}public void setJpegThumbnailSize(int w,int h){if(w!=0||h!=0)throw new IllegalArgumentException("JPEG thumbnails unavailable");}
  public void setRotation(int value){if(value<0||value>=360||value%90!=0)throw new IllegalArgumentException("Picture rotation");rotation=value;}
  public Size getPreviewSize(){return new Size(width,height);}public Size getPictureSize(){return getPreviewSize();}public List<Size> getSupportedPreviewSizes(){return Collections.singletonList(getPreviewSize());}public List<Size> getSupportedPictureSizes(){return getSupportedPreviewSizes();}
  public int getPreviewFormat(){return 17;}public void setPreviewFormat(int value){if(value!=17)throw new IllegalArgumentException("Only NV21 preview");}
  public void setPreviewSize(int w,int h){if(w!=width||h!=height)throw new IllegalArgumentException("Unsupported preview size");}public void setPictureSize(int w,int h){setPreviewSize(w,h);}
  public List<String> getSupportedFocusModes(){return Collections.singletonList("fixed");}public String getFocusMode(){return "fixed";}public void setFocusMode(String value){if(!"fixed".equals(value))throw new IllegalArgumentException("Only fixed webcam focus");}
  public List<String> getSupportedFlashModes(){return Collections.singletonList("off");}public String getFlashMode(){return "off";}public void setFlashMode(String value){if(!"off".equals(value))throw new IllegalArgumentException("Webcam flash unavailable");}
  public boolean isVideoStabilizationSupported(){return false;}public void setVideoStabilization(boolean value){if(value)throw new UnsupportedOperationException("Video stabilization unavailable");}
  public List<int[]> getSupportedPreviewFpsRange(){return Collections.singletonList(new int[]{15000,30000});}public void getPreviewFpsRange(int[] out){if(out==null||out.length<2)throw new IllegalArgumentException("FPS range");out[0]=15000;out[1]=30000;}public void setPreviewFpsRange(int min,int max){if(min!=15000||max!=30000)throw new IllegalArgumentException("FPS range");}
  public String flatten(){return "preview-size="+width+"x"+height+";picture-size="+width+"x"+height+";preview-format=yuv420sp;focus-mode=fixed;flash-mode=off";}
  public void unflatten(String value){if(value==null||!value.equals(flatten()))throw new IllegalArgumentException("Unsupported camera parameter snapshot");}
 }
}
