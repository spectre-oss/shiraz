// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Copies the most recent software/native surface or window frame. */
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
  validateDestination(destination);
  if(source==null||!source.isValid())throw new IllegalArgumentException("Source surface is invalid");
  if(crop!=null&&crop.isEmpty())throw new IllegalArgumentException("Source rectangle is empty");
  if(listener==null||handler==null)throw new NullPointerException("PixelCopy callback/handler");
  final int result=source.shirazCopyPixels(crop,destination);
  if(!handler.post(new Runnable(){public void run(){listener.onPixelCopyFinished(result);}}))throw new IllegalStateException("PixelCopy callback Looper has quit");
 }
 public static void request(Window source,Bitmap destination,OnPixelCopyFinishedListener listener,Handler listenerThread){
  request(source,null,destination,listener,listenerThread);
 }
 public static void request(Window source,Rect srcRect,Bitmap destination,final OnPixelCopyFinishedListener listener,Handler listenerThread){
  validateDestination(destination);
  if(source==null)throw new IllegalArgumentException("source is null");
  if(srcRect!=null&&srcRect.isEmpty())throw new IllegalArgumentException("sourceRect is empty");
  if(listener==null||listenerThread==null)throw new NullPointerException("PixelCopy callback/handler");
  final int result=copyViewTree(source.peekDecorView(),srcRect,destination);
  if(!listenerThread.post(new Runnable(){public void run(){listener.onPixelCopyFinished(result);}}))throw new IllegalStateException("PixelCopy callback Looper has quit");
 }

 private static void validateDestination(Bitmap destination){
  if(destination==null||destination.isRecycled()||!destination.isMutable())throw new IllegalArgumentException("Destination must be a live mutable bitmap");
 }

 private static int copyViewTree(View root,Rect srcRect,Bitmap dest){
  if(root==null||root.getWidth()<=0||root.getHeight()<=0)return ERROR_SOURCE_NO_DATA;
  Rect src=srcRect!=null?srcRect:new Rect(0,0,root.getWidth(),root.getHeight());
  if(src.width()<=0||src.height()<=0)return ERROR_SOURCE_INVALID;
  Bitmap full=(src.left==0&&src.top==0&&src.width()==dest.getWidth()&&src.height()==dest.getHeight())
   ?dest:Bitmap.createBitmap(Math.max(1,src.width()),Math.max(1,src.height()),Bitmap.Config.ARGB_8888);
  Canvas canvas=new Canvas(full);
  canvas.translate(-src.left,-src.top);
  root.draw(canvas);
  if(full!=dest){
   Canvas scaled=new Canvas(dest);
   scaled.drawBitmap(full,new Rect(0,0,full.getWidth(),full.getHeight()),new Rect(0,0,dest.getWidth(),dest.getHeight()),null);
  }
  return SUCCESS;
 }

 public static void request(Request request,Executor callbackExecutor,final Consumer<Result> listener){
  Objects.requireNonNull(request,"request must not be null");
  Objects.requireNonNull(callbackExecutor,"callbackExecutor must not be null");
  Objects.requireNonNull(listener,"listener must not be null");
  Bitmap dest=request.mDest;
  Rect srcRect=request.mSrcRect;
  int status;
  if(request.mSurface!=null){
   if(!request.mSurface.isValid()){
    status=ERROR_SOURCE_INVALID;
   }else{
    if(dest==null){
     int w=srcRect!=null&&!srcRect.isEmpty()?srcRect.width():1;
     int h=srcRect!=null&&!srcRect.isEmpty()?srcRect.height():1;
     dest=Bitmap.createBitmap(Math.max(1,w),Math.max(1,h),Bitmap.Config.ARGB_8888);
    }
    status=request.mSurface.shirazCopyPixels(srcRect,dest);
   }
  }else if(request.mRootView!=null){
   if(dest==null){
    int w=srcRect!=null&&!srcRect.isEmpty()?srcRect.width():Math.max(1,request.mRootView.getWidth());
    int h=srcRect!=null&&!srcRect.isEmpty()?srcRect.height():Math.max(1,request.mRootView.getHeight());
    dest=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
   }
   status=copyViewTree(request.mRootView,srcRect,dest);
  }else{
   status=ERROR_SOURCE_INVALID;
  }
  final Result copyResult=new Result(status,status==SUCCESS?dest:null);
  callbackExecutor.execute(new Runnable(){
   @Override public void run(){
    listener.accept(copyResult);
   }
  });
 }

 public static final class Result {
  private final int mStatus;
  private final Bitmap mBitmap;

  private Result(int status,Bitmap bitmap){
   mStatus=status;
   mBitmap=bitmap;
  }

  public int getStatus(){
   return mStatus;
  }

  public Bitmap getBitmap(){
   if(mStatus!=SUCCESS){
    throw new IllegalStateException("Copy request didn't succeed, status = "+mStatus);
   }
   return mBitmap;
  }
 }

 public static final class Request {
  final Surface mSurface;
  final View mRootView;
  Rect mSrcRect;
  Bitmap mDest;

  private Request(Surface surface,View rootView){
   mSurface=surface;
   mRootView=rootView;
  }

  public Bitmap getDestinationBitmap(){
   return mDest;
  }

  public Rect getSourceRect(){
   return mSrcRect;
  }

  public static final class Builder {
   private Request mRequest;

   private Builder(Request request){
    mRequest=request;
   }

   private void requireNotBuilt(){
    if(mRequest==null){
     throw new IllegalStateException("build() has already been called on this Builder");
    }
   }

   public static Builder ofWindow(Window source){
    if(source==null||source.peekDecorView()==null){
     throw new IllegalArgumentException("Window must not be null and must have a DecorView");
    }
    return new Builder(new Request(null,source.peekDecorView()));
   }

   public static Builder ofWindow(View source){
    if(source==null||!source.isAttachedToWindow()){
     throw new IllegalArgumentException("View must not be null and must be attached to window");
    }
    return new Builder(new Request(null,source.getRootView()));
   }

   public static Builder ofSurface(Surface source){
    if(source==null||!source.isValid()){
     throw new IllegalArgumentException("Source must not be null and must be valid");
    }
    return new Builder(new Request(source,null));
   }

   public static Builder ofSurface(SurfaceView source){
    if(source==null){
     throw new IllegalArgumentException("Source must not be null");
    }
    return ofSurface(source.getHolder().getSurface());
   }

   public Builder setSourceRect(Rect srcRect){
    requireNotBuilt();
    if(srcRect!=null&&srcRect.isEmpty()){
     throw new IllegalArgumentException("sourceRect is empty");
    }
    mRequest.mSrcRect=srcRect!=null?new Rect(srcRect):null;
    return this;
   }

   public Builder setDestinationBitmap(Bitmap destination){
    requireNotBuilt();
    if(destination!=null){
     validateDestination(destination);
    }
    mRequest.mDest=destination;
    return this;
   }

   public Request build(){
    requireNotBuilt();
    Request ret=mRequest;
    mRequest=null;
    return ret;
   }
  }
 }
}
