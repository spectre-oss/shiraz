// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;
/** Reflective entry used by AndroidX WebKit against the platform WebView. */
public final class WebViewFactory {
 private static final Object LOCK=new Object();
 private static WebViewFactoryProvider provider;
 private WebViewFactory(){}
 public static WebViewFactoryProvider getFactory(){return getProvider();}
 public static WebViewFactoryProvider getProvider(){
  synchronized(LOCK){
   if(provider==null)provider=new WebViewFactoryProvider();
   return provider;
  }
 }
}
