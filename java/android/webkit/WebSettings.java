// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;
/** Settings for the placeholder WebView; values are retained but no engine is present. */
public class WebSettings {
 public enum PluginState { ON, ON_DEMAND, OFF }
 public enum RenderPriority { NORMAL, HIGH, LOW }
 private PluginState pluginState=PluginState.OFF;
 private boolean savePassword=true,saveFormData=true,lightTouch,smoothTransition;
 private String databasePath="";
 /** Deprecated engine hints; the placeholder has no browser database or plugins. */
 public void setGeolocationDatabasePath(String path){}
 public void setDatabasePath(String path){databasePath=path;}
 public String getDatabasePath(){return databasePath;}
 public void setSavePassword(boolean value){savePassword=value;}
 public boolean getSavePassword(){return savePassword;}
 public void setSaveFormData(boolean value){saveFormData=value;}
 public boolean getSaveFormData(){return saveFormData;}
 public void setLightTouchEnabled(boolean value){lightTouch=value;}
 public boolean getLightTouchEnabled(){return lightTouch;}
 public void setEnableSmoothTransition(boolean value){smoothTransition=value;}
 public boolean enableSmoothTransition(){return smoothTransition;}
 public void setNeedInitialFocus(boolean value){}
 public void setPluginState(PluginState value){pluginState=value;}
 public PluginState getPluginState(){return pluginState;}
 public void setRenderPriority(RenderPriority value){}
 public static String getDefaultUserAgent(android.content.Context context){return WebViewFactory.getProvider().getStatics().getDefaultUserAgent(context);}
 public static final int LOAD_DEFAULT=-1,LOAD_NORMAL=0,LOAD_CACHE_ELSE_NETWORK=1,LOAD_NO_CACHE=2,LOAD_CACHE_ONLY=3;
 public static final int MIXED_CONTENT_ALWAYS_ALLOW=0,MIXED_CONTENT_NEVER_ALLOW=1,MIXED_CONTENT_COMPATIBILITY_MODE=2;
 private boolean javaScript=true,domStorage,zoom=true,builtInZoom,displayZoom=true,overview,wideViewport=true,fileAccess=true,contentAccess=true,mediaGesture=true;
 private int cacheMode=LOAD_DEFAULT,mixedContent=MIXED_CONTENT_NEVER_ALLOW;
 private String userAgent="";
 public void setJavaScriptEnabled(boolean value){javaScript=value;}
 public boolean getJavaScriptEnabled(){return javaScript;}
 public void setDomStorageEnabled(boolean value){domStorage=value;}
 public boolean getDomStorageEnabled(){return domStorage;}
 public void setSupportZoom(boolean value){zoom=value;}
 public boolean supportZoom(){return zoom;}
 public void setBuiltInZoomControls(boolean value){builtInZoom=value;}
 public boolean getBuiltInZoomControls(){return builtInZoom;}
 public void setDisplayZoomControls(boolean value){displayZoom=value;}
 public boolean getDisplayZoomControls(){return displayZoom;}
 public void setLoadWithOverviewMode(boolean value){overview=value;}
 public boolean getLoadWithOverviewMode(){return overview;}
 public void setUseWideViewPort(boolean value){wideViewport=value;}
 public boolean getUseWideViewPort(){return wideViewport;}
 public void setAllowFileAccess(boolean value){fileAccess=value;}
 public boolean getAllowFileAccess(){return fileAccess;}
 public void setAllowContentAccess(boolean value){contentAccess=value;}
 public boolean getAllowContentAccess(){return contentAccess;}
 public void setCacheMode(int mode){cacheMode=mode;}
 public int getCacheMode(){return cacheMode;}
 public void setUserAgentString(String value){userAgent=value==null?"":value;}
 public String getUserAgentString(){return userAgent;}
 public void setMediaPlaybackRequiresUserGesture(boolean value){mediaGesture=value;}
 public boolean getMediaPlaybackRequiresUserGesture(){return mediaGesture;}
 public void setMixedContentMode(int mode){mixedContent=mode;}
 public int getMixedContentMode(){return mixedContent;}
 public void setAllowFileAccessFromFileURLs(boolean value){}
 public void setAllowUniversalAccessFromFileURLs(boolean value){}
 public void setJavaScriptCanOpenWindowsAutomatically(boolean value){}
 public void setSupportMultipleWindows(boolean value){}
 public void setDatabaseEnabled(boolean value){}
 public void setGeolocationEnabled(boolean value){}
 public void setLoadsImagesAutomatically(boolean value){}
 public void setBlockNetworkImage(boolean value){}
 public void setBlockNetworkLoads(boolean value){}
 public void setDefaultTextEncodingName(String value){}
 public void setDefaultFontSize(int size){}
 public void setMinimumFontSize(int size){}
 public void setTextZoom(int textZoom){}
 public int getTextZoom(){return 100;}
 public void setSafeBrowsingEnabled(boolean enabled){}
 public boolean getSafeBrowsingEnabled(){return false;}
 public void setOffscreenPreRaster(boolean enabled){}
 public boolean getOffscreenPreRaster(){return false;}
 public void setDisabledActionModeMenuItems(int menuItems){}
 public int getDisabledActionModeMenuItems(){return 0;}
 public void setForceDark(int forceDark){}
 public int getForceDark(){return 0;}
 public void setAlgorithmicDarkeningAllowed(boolean allow){}
 public boolean isAlgorithmicDarkeningAllowed(){return false;}
}
