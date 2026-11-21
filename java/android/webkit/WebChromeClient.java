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
 public boolean onJsPrompt(WebView view,String url,String message,String defaultValue,JsPromptResult result){if(result!=null)result.cancel();return false;}
 public boolean onJsBeforeUnload(WebView view,String url,String message,JsResult result){if(result!=null)result.cancel();return false;}
 public void onPermissionRequest(PermissionRequest request){if(request!=null)request.deny();}
 public void onPermissionRequestCanceled(PermissionRequest request){}
 public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback){if(callback!=null)callback.invoke(origin,false,false);}
 public void onGeolocationPermissionsHidePrompt(){}
 public boolean onShowFileChooser(WebView webView,ValueCallback<android.net.Uri[]> filePathCallback,FileChooserParams fileChooserParams){return false;}
 public android.graphics.Bitmap getDefaultVideoPoster(){return null;}
 public android.view.View getVideoLoadingProgressView(){return null;}
 public void getVisitedHistory(ValueCallback<String[]> callback){}
 public abstract static class FileChooserParams {
  public static final int MODE_OPEN = 0;
  public static final int MODE_OPEN_MULTIPLE = 1;
  public static final int MODE_SAVE = 3;
  public FileChooserParams() {}
  public static android.net.Uri[] parseResult(int resultCode, android.content.Intent data) {
   if (resultCode != android.app.Activity.RESULT_OK || data == null) return null;
   android.net.Uri uri = data.getData();
   return uri != null ? new android.net.Uri[] { uri } : null;
  }
  public abstract int getMode();
  public abstract String[] getAcceptTypes();
  public abstract boolean isCaptureEnabled();
  public abstract CharSequence getTitle();
  public abstract String getFilenameHint();
  public abstract android.content.Intent createIntent();
 }
}
