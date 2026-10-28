// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
/** Copies the most recent software/native surface frame; callbacks use the supplied Handler. */
public final class PixelCopy {
 public static final int SUCCESS=0,ERROR_UNKNOWN=1,ERROR_TIMEOUT=2,ERROR_SOURCE_NO_DATA=3,ERROR_SOURCE_INVALID=4,ERROR_DESTINATION_INVALID=5;
 public interface OnPixelCopyFinishedListener {void onPixelCopyFinished(int copyResult);}
 private PixelCopy(){}
 public static void request(SurfaceView source,Bitmap destination,OnPixelCopyFinishedListener listener,Handler handler){request(source,null,destination,listener,handler);}
 public static void request(SurfaceView source,Rect crop,Bitmap destination,OnPixelCopyFinishedListener listener,Handler handler){
  if(source==null)throw new IllegalArgumentException("null SurfaceView");
  request(source.getHolder().getSurface(),crop,destination,listener,handler);
 }
 public static void request(Surface source,Bitmap destination,OnPixelCopyFinishedListener listener,Handler handler){request(source,null,destination,listener,handler);}
 public static void request(Surface source,Rect crop,Bitmap destination,final OnPixelCopyFinishedListener listener,Handler handler){
  if(destination==null||destination.isRecycled()||!destination.isMutable())throw new IllegalArgumentException("Destination must be a live mutable bitmap");
  if(source==null||!source.isValid())throw new IllegalArgumentException("Source surface is invalid");
  if(crop!=null&&crop.isEmpty())throw new IllegalArgumentException("Source rectangle is empty");
  if(listener==null||handler==null)throw new NullPointerException("PixelCopy callback/handler");
  final int result=source.shirazCopyPixels(crop,destination);
  if(!handler.post(new Runnable(){public void run(){listener.onPixelCopyFinished(result);}}))throw new IllegalStateException("PixelCopy callback Looper has quit");
 }
}
