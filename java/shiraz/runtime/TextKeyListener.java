// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.text.*;
import android.view.*;
/** Stateless committed-text editor; modifier state comes from each host KeyEvent. */
public final class TextKeyListener implements android.text.method.KeyListener {
 private final int type;
 public TextKeyListener(int type){this.type=type;}
 public int getInputType(){return type;}
 private static void replace(Editable text,String value){int a=Selection.getSelectionStart(text),b=Selection.getSelectionEnd(text);if(a<0||b<0)a=b=text.length();int start=Math.min(a,b),end=Math.max(a,b);Selection.setSelection(text,end);text.replace(start,end,value);}
 public boolean onKeyOther(View view,Editable text,KeyEvent event){if(event.getAction()!=KeyEvent.ACTION_MULTIPLE||event.getKeyCode()!=KeyEvent.KEYCODE_UNKNOWN||event.getCharacters()==null)return false;replace(text,event.getCharacters());return true;}
 public boolean onKeyDown(View view,Editable text,int code,KeyEvent event){
  int a=Selection.getSelectionStart(text),b=Selection.getSelectionEnd(text);if(a<0||b<0){Selection.setSelection(text,text.length());a=b=text.length();}int start=Math.min(a,b),end=Math.max(a,b);
  if(event.isCtrlPressed()){
   if(!event.isAltPressed()&&!event.isMetaPressed()){
    if(code==KeyEvent.KEYCODE_A){Selection.selectAll(text);return true;}
    if(code==KeyEvent.KEYCODE_C&&start<end&&view!=null&&view.getContext()!=null){
     android.content.ClipboardManager cm=(android.content.ClipboardManager)view.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
     if(cm!=null)cm.setPrimaryClip(android.content.ClipData.newPlainText(null,text.subSequence(start,end)));
     return true;
    }
    if(code==KeyEvent.KEYCODE_X&&start<end&&view!=null&&view.getContext()!=null){
     android.content.ClipboardManager cm=(android.content.ClipboardManager)view.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
     if(cm!=null)cm.setPrimaryClip(android.content.ClipData.newPlainText(null,text.subSequence(start,end)));
     text.delete(start,end);Selection.setSelection(text,start);
     return true;
    }
    if(code==KeyEvent.KEYCODE_V&&view!=null&&view.getContext()!=null){
     android.content.ClipboardManager cm=(android.content.ClipboardManager)view.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
     CharSequence clip=cm==null?null:cm.getText();
     if(clip!=null&&clip.length()>0)replace(text,clip.toString());
     return true;
    }
   }
   return false;
  }
  if(event.isAltPressed()||event.isMetaPressed())return false;
  if(code==KeyEvent.KEYCODE_COPY&&start<end&&view!=null&&view.getContext()!=null){
   android.content.ClipboardManager cm=(android.content.ClipboardManager)view.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
   if(cm!=null)cm.setPrimaryClip(android.content.ClipData.newPlainText(null,text.subSequence(start,end)));
   return true;
  }
  if(code==KeyEvent.KEYCODE_PASTE&&view!=null&&view.getContext()!=null){
   android.content.ClipboardManager cm=(android.content.ClipboardManager)view.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
   CharSequence clip=cm==null?null:cm.getText();
   if(clip!=null&&clip.length()>0)replace(text,clip.toString());
   return true;
  }
  switch(code){
   case KeyEvent.KEYCODE_DEL:if(start==end&&start>0)start=Character.offsetByCodePoints(text,start,-1);text.delete(start,end);Selection.setSelection(text,start);return true;
   case KeyEvent.KEYCODE_FORWARD_DEL:if(start==end&&end<text.length())end=Character.offsetByCodePoints(text,end,1);text.delete(start,end);Selection.setSelection(text,start);return true;
   case KeyEvent.KEYCODE_DPAD_LEFT:case KeyEvent.KEYCODE_DPAD_RIGHT:{boolean right=code==KeyEvent.KEYCODE_DPAD_RIGHT;int next;if(!event.isShiftPressed()&&start!=end)next=right?end:start;else next=right?(b<text.length()?Character.offsetByCodePoints(text,b,1):b):(b>0?Character.offsetByCodePoints(text,b,-1):b);if(event.isShiftPressed())Selection.extendSelection(text,next);else Selection.setSelection(text,next);return true;}
   case KeyEvent.KEYCODE_MOVE_HOME:case KeyEvent.KEYCODE_MOVE_END:{int next=code==KeyEvent.KEYCODE_MOVE_HOME?0:text.length();if(event.isShiftPressed())Selection.extendSelection(text,next);else Selection.setSelection(text,next);return true;}
   case KeyEvent.KEYCODE_ENTER:if(view instanceof android.widget.TextView&&((android.widget.TextView)view).getMaxLines()==1)return false;replace(text,"\n");return true;
   default:return false;
  }
 }
 public boolean onKeyUp(View view,Editable text,int code,KeyEvent event){return false;}
 public void clearMetaKeyState(View view,Editable text,int states){/* No latched modifier spans are created by this listener. */}
}
