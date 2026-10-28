// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;
/** Chrome-style callbacks for the placeholder WebView. */
public class WebChromeClient {
 public interface CustomViewCallback { void onCustomViewHidden(); }
 public void onShowCustomView(android.view.View view,CustomViewCallback callback){}
 public void onHideCustomView(){}
 public void onProgressChanged(WebView view,int newProgress){}
 public void onReceivedTitle(WebView view,String title){}
 public void onReceivedIcon(WebView view,android.graphics.Bitmap icon){}
 public boolean onConsoleMessage(ConsoleMessage consoleMessage){return false;}
 public boolean onJsAlert(WebView view,String url,String message,JsResult result){if(result!=null)result.cancel();return false;}
 public boolean onJsConfirm(WebView view,String url,String message,JsResult result){if(result!=null)result.cancel();return false;}
 public void onPermissionRequest(PermissionRequest request){if(request!=null)request.deny();}
 public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback){if(callback!=null)callback.invoke(origin,false,false);}
}
