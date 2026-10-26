// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;
/** Minimal provider surface for AndroidX WebKit reflection against Shiraz's placeholder WebView. */
public class WebViewFactoryProvider {
 public interface Statics {
  String getDefaultUserAgent(android.content.Context context);
  void setSafeBrowsingWhitelist(java.util.List<String> hosts,ValueCallback<Boolean> callback);
  void initSafeBrowsing(android.content.Context context,ValueCallback<Boolean> callback);
 }
 private final Statics statics=new Statics(){
  public String getDefaultUserAgent(android.content.Context context){return "Shiraz-WebView";}
  public void setSafeBrowsingWhitelist(java.util.List<String> hosts,ValueCallback<Boolean> callback){if(callback!=null)callback.onReceiveValue(Boolean.TRUE);}
  public void initSafeBrowsing(android.content.Context context,ValueCallback<Boolean> callback){if(callback!=null)callback.onReceiveValue(Boolean.TRUE);}
 };
 public Statics getStatics(){return statics;}
 public Object getFactory(){return this;}
 public CookieManager getCookieManager(){return CookieManager.getInstance();}
 public WebViewProvider createWebView(WebView webView,WebView.PrivateAccess privateAccess){return new WebViewProvider();}
}
