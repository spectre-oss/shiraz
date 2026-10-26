// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;
/** Client callbacks for the placeholder WebView. */
public class WebViewClient {
 public static final int ERROR_UNKNOWN=-1,ERROR_HOST_LOOKUP=-2,ERROR_UNSUPPORTED_AUTH_SCHEME=-3,ERROR_AUTHENTICATION=-4,ERROR_PROXY_AUTHENTICATION=-5,ERROR_CONNECT=-6,ERROR_IO=-7,ERROR_TIMEOUT=-8,ERROR_REDIRECT_LOOP=-9,ERROR_UNSUPPORTED_SCHEME=-10,ERROR_FAILED_SSL_HANDSHAKE=-11,ERROR_BAD_URL=-12,ERROR_FILE=-13,ERROR_FILE_NOT_FOUND=-14,ERROR_TOO_MANY_REQUESTS=-15;
 public boolean shouldOverrideUrlLoading(WebView view,String url){return false;}
 public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){}
 public void onPageFinished(WebView view,String url){}
 public void onReceivedError(WebView view,int errorCode,String description,String failingUrl){}
 public void onReceivedSslError(WebView view,SslErrorHandler handler,Object error){if(handler!=null)handler.cancel();}
}
