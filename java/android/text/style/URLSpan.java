// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
public class URLSpan extends ClickableSpan {
 private final String url;
 public URLSpan(String url){this.url=url;}
 public String getURL(){return url;}
 public void onClick(View widget){
  try{widget.getContext().startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
  catch(RuntimeException ignored){}
 }
}
