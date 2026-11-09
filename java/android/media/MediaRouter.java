// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Display;
import java.util.*;
/** Logical default host audio route; remote playback/casting is not advertised. */
public class MediaRouter {
 public static final int ROUTE_TYPE_LIVE_AUDIO=1,ROUTE_TYPE_LIVE_VIDEO=2,ROUTE_TYPE_USER=0x800000;
 public static final int CALLBACK_FLAG_PERFORM_ACTIVE_SCAN=1,CALLBACK_FLAG_UNFILTERED_EVENTS=2,CALLBACK_FLAG_REQUEST_DISCOVERY=4,CALLBACK_FLAG_PASSIVE_DISCOVERY=8;
 private final RouteCategory systemCategory=new RouteCategory("System",ROUTE_TYPE_LIVE_AUDIO,false);
 private final RouteInfo defaultRoute=new RouteInfo(systemCategory,"System audio");
 private final IdentityHashMap<Callback,Integer> callbacks=new IdentityHashMap<Callback,Integer>();
 public MediaRouter(Context context){if(context==null)throw new IllegalArgumentException("context");}
 public static MediaRouter shirazForHost(Context context){return new MediaRouter(context);}
 public int getRouteCount(){return 1;}public RouteInfo getRouteAt(int index){if(index!=0)throw new IndexOutOfBoundsException("route");return defaultRoute;}
 public RouteInfo getDefaultRoute(){return defaultRoute;}public RouteInfo getSelectedRoute(int types){return defaultRoute;}
 public int getCategoryCount(){return 1;}public RouteCategory getCategoryAt(int index){if(index!=0)throw new IndexOutOfBoundsException("category");return systemCategory;}
 public void selectRoute(int types,RouteInfo route){if(route==null||route!=defaultRoute||(types&ROUTE_TYPE_LIVE_AUDIO)==0)throw new IllegalArgumentException("Only the default host audio route is available");}
 public void addCallback(int types,Callback callback){addCallback(types,callback,0);}
 public synchronized void addCallback(int types,Callback callback,int flags){if(callback==null)throw new IllegalArgumentException("callback");if(!callbacks.containsKey(callback)&&callbacks.size()>=128)throw new IllegalStateException("Media route callback limit");Integer previous=callbacks.get(callback);callbacks.put(callback,(previous==null?0:previous)|types);}
 public synchronized void removeCallback(Callback callback){if(callback==null)throw new IllegalArgumentException("callback");callbacks.remove(callback);}
 public RouteCategory createRouteCategory(CharSequence name,boolean groupable){if(name==null)throw new IllegalArgumentException("name");return new RouteCategory(name,ROUTE_TYPE_USER,groupable);}
 public RouteCategory createRouteCategory(int name,boolean groupable){throw new UnsupportedOperationException("Resource-named user media routes are unavailable");}
 public UserRouteInfo createUserRoute(RouteCategory category){throw new UnsupportedOperationException("App-provided remote media routes are unavailable");}
 public void addUserRoute(UserRouteInfo route){throw new UnsupportedOperationException("Remote media routes are unavailable");}
 public void removeUserRoute(UserRouteInfo route){throw new UnsupportedOperationException("Remote media routes are unavailable");}public void clearUserRoutes(){}
 public class RouteInfo {
  public static final int PLAYBACK_TYPE_LOCAL=0,PLAYBACK_TYPE_REMOTE=1,PLAYBACK_VOLUME_FIXED=0,PLAYBACK_VOLUME_VARIABLE=1;
  public static final int DEVICE_TYPE_UNKNOWN=0,DEVICE_TYPE_TV=1,DEVICE_TYPE_SPEAKER=2,DEVICE_TYPE_BLUETOOTH=3;
  public static final int STATUS_NONE=0,STATUS_SCANNING=1,STATUS_CONNECTING=2,STATUS_AVAILABLE=3,STATUS_NOT_AVAILABLE=4,STATUS_IN_USE=5;
  private final RouteCategory category;private final CharSequence name;private Object tag;
  private RouteInfo(RouteCategory category,CharSequence name){this.category=category;this.name=name;}
  public CharSequence getName(){return name;}public CharSequence getName(Context context){return name;}public CharSequence getDescription(){return "Default host audio output";}public CharSequence getStatus(){return null;}public int getStatusCode(){return STATUS_NONE;}
  public int getSupportedTypes(){return ROUTE_TYPE_LIVE_AUDIO;}public boolean matchesTypes(int types){return (getSupportedTypes()&types)!=0;}public RouteCategory getCategory(){return category;}public RouteGroup getGroup(){return null;}
  public int getPlaybackType(){return PLAYBACK_TYPE_LOCAL;}public int getPlaybackStream(){return AudioManager.STREAM_MUSIC;}public int getVolumeHandling(){return PLAYBACK_VOLUME_FIXED;}public int getVolume(){return 1;}public int getVolumeMax(){return 1;}
  public boolean isEnabled(){return true;}public boolean isConnecting(){return false;}public boolean isDefault(){return this==defaultRoute;}public int getDeviceType(){return DEVICE_TYPE_UNKNOWN;}public Display getPresentationDisplay(){return null;}public Drawable getIconDrawable(){return null;}
  public Object getTag(){return tag;}public void setTag(Object value){tag=value;}
  public void select(){MediaRouter.this.selectRoute(getSupportedTypes(),this);}
  public void requestSetVolume(int value){if(value<0||value>1)throw new IllegalArgumentException("volume");if(value!=1)throw new UnsupportedOperationException("Default host route has fixed volume");}
  public void requestUpdateVolume(int direction){if(direction!=0)throw new UnsupportedOperationException("Default host route has fixed volume");}
 }
 public class RouteCategory {
  private final CharSequence name;private final int types;private final boolean groupable;
  private RouteCategory(CharSequence name,int types,boolean groupable){this.name=name;this.types=types;this.groupable=groupable;}
  public CharSequence getName(){return name;}public CharSequence getName(Context context){return name;}public int getSupportedTypes(){return types;}public boolean isGroupable(){return groupable;}
  public List<RouteInfo> getRoutes(List<RouteInfo> output){if(output==null)output=new ArrayList<RouteInfo>();else output.clear();if(this==systemCategory)output.add(defaultRoute);return output;}
 }
 public class UserRouteInfo extends RouteInfo {
  private UserRouteInfo(RouteCategory category){super(category,"");}
  private void unavailable(){throw new UnsupportedOperationException("Remote media routes are unavailable");}
  public void setName(CharSequence name){unavailable();}public void setDescription(CharSequence description){unavailable();}
  public void setPlaybackStream(int stream){unavailable();}public void setPlaybackType(int type){unavailable();}
  public void setVolume(int volume){unavailable();}public void setVolumeMax(int volume){unavailable();}public void setVolumeHandling(int handling){unavailable();}public void setVolumeCallback(VolumeCallback callback){unavailable();}
 }
 public class RouteGroup extends RouteInfo {private RouteGroup(RouteCategory category){super(category,"");}public int getRouteCount(){return 0;}public RouteInfo getRouteAt(int index){throw new IndexOutOfBoundsException("route");}}
 public abstract static class VolumeCallback {public void onVolumeUpdateRequest(RouteInfo route,int direction){}public void onVolumeSetRequest(RouteInfo route,int volume){}}
 public abstract static class Callback {
  public void onRouteSelected(MediaRouter router,int type,RouteInfo info){}public void onRouteUnselected(MediaRouter router,int type,RouteInfo info){}
  public void onRouteAdded(MediaRouter router,RouteInfo info){}public void onRouteRemoved(MediaRouter router,RouteInfo info){}public void onRouteChanged(MediaRouter router,RouteInfo info){}
  public void onRouteGrouped(MediaRouter router,RouteInfo info,RouteGroup group,int index){}public void onRouteUngrouped(MediaRouter router,RouteInfo info,RouteGroup group){}
  public void onRouteVolumeChanged(MediaRouter router,RouteInfo info){}public void onRoutePresentationDisplayChanged(MediaRouter router,RouteInfo info){}
 }
 public static class SimpleCallback extends Callback {}
}
