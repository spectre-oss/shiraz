// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;
/** Cookie store for the placeholder WebView. */
public class CookieManager {
 private static final CookieManager INSTANCE=new CookieManager();
 private boolean accept=true,acceptThird=false;
 public static CookieManager getInstance(){return INSTANCE;}
 public void setAcceptCookie(boolean accept){this.accept=accept;}
 public boolean acceptCookie(){return accept;}
 public void setAcceptThirdPartyCookies(WebView webView,boolean accept){acceptThird=accept;}
 public boolean acceptThirdPartyCookies(WebView webView){return acceptThird;}
 public void setCookie(String url,String value){}
 public void setCookie(String url,String value,ValueCallback<Boolean> callback){if(callback!=null)callback.onReceiveValue(Boolean.TRUE);}
 public String getCookie(String url){return null;}
 public void removeSessionCookie(){}
 public void removeSessionCookies(ValueCallback<Boolean> callback){if(callback!=null)callback.onReceiveValue(Boolean.TRUE);}
 public void removeAllCookie(){}
 public void removeAllCookies(ValueCallback<Boolean> callback){if(callback!=null)callback.onReceiveValue(Boolean.TRUE);}
 public void flush(){}
 public boolean hasCookies(){return false;}
}
