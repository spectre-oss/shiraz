// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.res.Resources;
import android.os.Looper;
import android.webkit.*;
public final class WebViewProgressSmoke {
 public static void main(String[] args){
  Looper.prepareMainLooper();
  Context context=new ContextWrapper(null){
   private final Resources.Theme theme=Resources.getSystem().newTheme();
   public Resources getResources(){return Resources.getSystem();}
   public Resources.Theme getTheme(){return theme;}
  };
  WebView.setWebContentsDebuggingEnabled(false);
  try{WebView.setWebContentsDebuggingEnabled(true);throw new AssertionError("unsupported debugging accepted");}
  catch(UnsupportedOperationException expected){}
  WebView view=new WebView(context);
  if(view.getProgress()!=0)throw new AssertionError("initial progress");
  final int[] starts={0},finishes={0},notifications={0};
  view.setWebViewClient(new WebViewClient(){
   public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){
    if(v.getProgress()!=0||starts[0]!=finishes[0])throw new AssertionError("start progress/order");
    starts[0]++;
   }
   public void onPageFinished(WebView v,String url){
    if(v.getProgress()!=100||starts[0]!=finishes[0]+1)throw new AssertionError("finish progress/order");
    finishes[0]++;
   }
  });
  view.setWebChromeClient(new WebChromeClient(){public void onProgressChanged(WebView v,int value){
   if(value!=100||v.getProgress()!=value)throw new AssertionError("progress notification");
   notifications[0]++;
  }});
  view.loadDataWithBaseURL("about:blank","<p>Local content</p>","text/html","UTF-8",null);
  view.loadUrl("data:text/html,<p>Second load</p>");
  if(starts[0]!=2||finishes[0]!=2||notifications[0]!=2)throw new AssertionError("callback counts");
  if(view.getTitle()!=null)throw new AssertionError("absent title");
  view.loadData("<!-- <title>Wrong</title> --><TITLE> A &amp; B </TITLE><p>Body</p>","text/html","UTF-8");
  if(!"A & B".equals(view.getTitle()))throw new AssertionError("HTML title");
  view.loadUrl("data:text/html,<title>Second</title>");
  if(!"Second".equals(view.getTitle()))throw new AssertionError("URL title");
  view.loadData("<script>var x='<title>Fake</title>';</script><p>No title</p>","text/html","UTF-8");
  if(view.getTitle()!=null)throw new AssertionError("stale/script title");
  view.destroy();
  System.out.println("WEBVIEW_PLACEHOLDER_PROGRESS_OK");
 }
}
