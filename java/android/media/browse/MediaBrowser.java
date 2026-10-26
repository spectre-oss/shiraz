// SPDX-License-Identifier: GPL-3.0-only
package android.media.browse;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Bundle;
import android.media.session.MediaSession;
import android.service.media.MediaBrowserService;
import java.util.List;

/** Client API for browsing an in-package media service. */
public final class MediaBrowser {
 private final Context context;
 private final ComponentName service;
 private final ConnectionCallback callback;
 private final ServiceConnection connection=new ServiceConnection(){
  public void onServiceConnected(ComponentName name,IBinder binder){
   if(!(binder instanceof MediaBrowserService.BrowserBinder)){callback.onConnectionFailed();return;}
   platformService=((MediaBrowserService.BrowserBinder)binder).service();
   root=platformService.onGetRoot(context.getPackageName(),android.os.Process.myUid(),hints);
   if(root==null||platformService.getSessionToken()==null){callback.onConnectionFailed();return;}
   connected=true;callback.onConnected();
  }
  public void onServiceDisconnected(ComponentName name){connected=false;callback.onConnectionSuspended();}
  public void onBindingDied(ComponentName name){connected=false;callback.onConnectionFailed();}
 };
 private boolean bound,connected;
 private final Bundle hints;
 private MediaBrowserService platformService;
 private MediaBrowserService.BrowserRoot root;
 public MediaBrowser(Context context,ComponentName service,ConnectionCallback callback,Bundle hints){
  if(context==null||service==null||callback==null)throw new IllegalArgumentException("context, service and callback required");
  this.context=context;this.service=service;this.callback=callback;this.hints=hints==null?null:new Bundle(hints);
 }
 public void connect(){if(bound)return;Intent intent=new Intent("android.media.browse.MediaBrowserService").setComponent(service);bound=context.bindService(intent,connection,Context.BIND_AUTO_CREATE);if(!bound)callback.onConnectionFailed();}
 public void disconnect(){if(bound){context.unbindService(connection);bound=false;}connected=false;root=null;platformService=null;}
 public boolean isConnected(){return connected;}
 public ComponentName getServiceComponent(){return service;}
 public String getRoot(){if(!connected)throw new IllegalStateException("Media browser is not connected");return root.getRootId();}
 public Bundle getExtras(){if(!connected)throw new IllegalStateException("Media browser is not connected");return root.getExtras();}
 public MediaSession.Token getSessionToken(){if(!connected)throw new IllegalStateException("Media browser is not connected");return platformService.getSessionToken();}
 public void subscribe(String parentId,SubscriptionCallback callback){throw new IllegalStateException("Media browser is not connected");}
 public void unsubscribe(String parentId){throw new IllegalStateException("Media browser is not connected");}
 public static class ConnectionCallback {
  public void onConnected(){}
  public void onConnectionSuspended(){}
  public void onConnectionFailed(){}
 }
 public static abstract class SubscriptionCallback {
  public void onChildrenLoaded(String parentId,List<MediaItem> children){}
  public void onChildrenLoaded(String parentId,List<MediaItem> children,Bundle options){}
  public void onError(String parentId){}
  public void onError(String parentId,Bundle options){}
 }
 public static final class MediaItem {
  private MediaItem(){throw new UnsupportedOperationException("No media browser service");}
 }
}
