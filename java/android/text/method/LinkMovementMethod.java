// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;
import android.text.*;import android.text.style.ClickableSpan;import android.widget.TextView;import android.view.*;
/** Pointer link activation. Keyboard traversal and scrolling remain separate. */
public class LinkMovementMethod implements MovementMethod {
 private static final LinkMovementMethod INSTANCE=new LinkMovementMethod();
 private static final class Press implements NoCopySpan {final ClickableSpan link;Press(ClickableSpan link){this.link=link;}}
 public static MovementMethod getInstance(){return INSTANCE;}
 private static void clear(Spannable text){for(Press press:text.getSpans(0,text.length(),Press.class))text.removeSpan(press);Selection.removeSelection(text);}
 public void initialize(TextView widget,Spannable text){clear(text);}public void onTakeFocus(TextView widget,Spannable text,int direction){clear(text);}
 public boolean canSelectArbitrarily(){return false;}
 public boolean onKeyDown(TextView widget,Spannable text,int code,KeyEvent event){return false;}public boolean onKeyUp(TextView widget,Spannable text,int code,KeyEvent event){return false;}public boolean onKeyOther(TextView widget,Spannable text,KeyEvent event){return false;}
 public boolean onTrackballEvent(TextView widget,Spannable text,MotionEvent event){return false;}public boolean onGenericMotionEvent(TextView widget,Spannable text,MotionEvent event){return false;}
 public boolean onTouchEvent(TextView widget,Spannable text,MotionEvent event){
  int action=event.getActionMasked();Press[] held=text.getSpans(0,text.length(),Press.class);boolean captured=held.length>0;
  if(action==MotionEvent.ACTION_CANCEL){clear(text);return captured;}
  ClickableSpan hit=widget.shirazLinkAt(event.getX()+widget.getScrollX(),event.getY()+widget.getScrollY());
  if(action==MotionEvent.ACTION_DOWN){clear(text);if(hit==null)return false;text.setSpan(new Press(hit),0,text.length(),Spanned.SPAN_INCLUSIVE_INCLUSIVE);Selection.setSelection(text,text.getSpanStart(hit),text.getSpanEnd(hit));return true;}
  if(!captured)return false;
  if(action==MotionEvent.ACTION_MOVE&&hit!=held[0].link){clear(text);return true;}
  if(action==MotionEvent.ACTION_UP){ClickableSpan target=held[0].link;clear(text);if(hit==target&&text.getSpanStart(target)>=0)target.onClick(widget);}
  return true;
 }
}
