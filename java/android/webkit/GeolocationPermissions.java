// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;
/** Geolocation permission bridge for the placeholder WebView. */
public class GeolocationPermissions {
 public interface Callback {void invoke(String origin,boolean allow,boolean retain);}
 private static final GeolocationPermissions INSTANCE=new GeolocationPermissions();
 public static GeolocationPermissions getInstance(){return INSTANCE;}
 public void clear(String origin){}
 public void clearAll(){}
 public void allow(String origin){}
 public void getAllowed(String origin,ValueCallback<Boolean> callback){if(callback!=null)callback.onReceiveValue(Boolean.FALSE);}
 public void getOrigins(ValueCallback<java.util.Set<String>> callback){if(callback!=null)callback.onReceiveValue(java.util.Collections.<String>emptySet());}
}
