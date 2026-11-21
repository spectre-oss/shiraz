// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;
/** Client callbacks for the placeholder WebView. */
public class WebViewClient {
 public static final int ERROR_UNKNOWN=-1,ERROR_HOST_LOOKUP=-2,ERROR_UNSUPPORTED_AUTH_SCHEME=-3,ERROR_AUTHENTICATION=-4,ERROR_PROXY_AUTHENTICATION=-5,ERROR_CONNECT=-6,ERROR_IO=-7,ERROR_TIMEOUT=-8,ERROR_REDIRECT_LOOP=-9,ERROR_UNSUPPORTED_SCHEME=-10,ERROR_FAILED_SSL_HANDSHAKE=-11,ERROR_BAD_URL=-12,ERROR_FILE=-13,ERROR_FILE_NOT_FOUND=-14,ERROR_TOO_MANY_REQUESTS=-15;
 public static final int SAFE_BROWSING_THREAT_UNKNOWN = 0, SAFE_BROWSING_THREAT_MALWARE = 1, SAFE_BROWSING_THREAT_PHISHING = 2, SAFE_BROWSING_THREAT_UNWANTED_SOFTWARE = 3, SAFE_BROWSING_THREAT_BILLING = 4;
 public boolean shouldOverrideUrlLoading(WebView view,String url){return false;}
 public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return request!=null&&request.getUrl()!=null&&shouldOverrideUrlLoading(view,request.getUrl().toString());}
 public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){}
 public void onPageFinished(WebView view,String url){}
 public void onPageCommitVisible(WebView view,String url){}
 public void onLoadResource(WebView view,String url){}
 public WebResourceResponse shouldInterceptRequest(WebView view,String url){return null;}
 public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return request!=null&&request.getUrl()!=null?shouldInterceptRequest(view,request.getUrl().toString()):null;}
 public void onReceivedError(WebView view,int errorCode,String description,String failingUrl){}
 public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request!=null&&request.isForMainFrame()&&error!=null&&request.getUrl()!=null)onReceivedError(view,error.getErrorCode(),error.getDescription()!=null?error.getDescription().toString():"",request.getUrl().toString());}
 public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse errorResponse){}
 public void onReceivedSslError(WebView view,SslErrorHandler handler,Object error){if(handler!=null)handler.cancel();}
 public void onReceivedClientCertRequest(WebView view,ClientCertRequest request){if(request!=null)request.cancel();}
 public void onReceivedHttpAuthRequest(WebView view,HttpAuthHandler handler,String host,String realm){if(handler!=null)handler.cancel();}
 public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){return false;}
 public void onSafeBrowsingHit(WebView view,WebResourceRequest request,int threatType,SafeBrowsingResponse callback){if(callback!=null)callback.showInterstitial(true);}
}
