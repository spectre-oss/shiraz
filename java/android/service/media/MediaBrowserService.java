// SPDX-License-Identifier: GPL-3.0-only
package android.service.media;

import android.app.Service;
import android.content.Intent;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import java.util.List;

/** In-process framework side of a manifest-declared media browser service. */
public abstract class MediaBrowserService extends Service {
 public static final String SERVICE_INTERFACE="android.media.browse.MediaBrowserService";
 private MediaSession.Token token;
 private final BrowserBinder binder=new BrowserBinder(this);
 public abstract BrowserRoot onGetRoot(String clientPackageName,int clientUid,Bundle rootHints);
 public abstract void onLoadChildren(String parentId,Result<List<MediaBrowser.MediaItem>> result);
 public void onLoadItem(String itemId,Result<MediaBrowser.MediaItem> result){result.sendResult(null);}
 @Override public IBinder onBind(Intent intent){return intent!=null&&SERVICE_INTERFACE.equals(intent.getAction())?binder:null;}
 public void setSessionToken(MediaSession.Token value){if(value==null)throw new IllegalArgumentException("session token");token=value;}
 public MediaSession.Token getSessionToken(){return token;}
 public void notifyChildrenChanged(String parentId){}
 public void notifyChildrenChanged(String parentId,Bundle options){notifyChildrenChanged(parentId);}
 public Bundle getBrowserRootHints(){return null;}
 public static final class BrowserBinder extends Binder {
  private final MediaBrowserService service;
  BrowserBinder(MediaBrowserService service){this.service=service;}
  public MediaBrowserService service(){return service;}
 }
 public static final class BrowserRoot {
  private final String rootId;private final Bundle extras;
  public BrowserRoot(String rootId,Bundle extras){if(rootId==null)throw new IllegalArgumentException("root ID");this.rootId=rootId;this.extras=extras;}
  public String getRootId(){return rootId;}
  public Bundle getExtras(){return extras;}
 }
 public static class Result<T> {
  private boolean complete;
  public void sendResult(T value){if(complete)throw new IllegalStateException("Result already sent");complete=true;}
  public void detach(){if(complete)throw new IllegalStateException("Result already sent");complete=true;}
  public void sendError(Bundle extras){throw new UnsupportedOperationException("Media browser result errors are not implemented");}
  public void setFlags(int flags){}
 }
}
