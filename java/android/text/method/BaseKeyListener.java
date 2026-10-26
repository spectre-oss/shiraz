// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;
import android.text.*;
import android.view.*;
/** Host committed-text editing. Character generation belongs to SDL text input. */
public abstract class BaseKeyListener extends MetaKeyKeyListener implements KeyListener {
 private final shiraz.runtime.TextKeyListener editor=new shiraz.runtime.TextKeyListener(InputType.TYPE_CLASS_TEXT);
 public boolean backspace(View view,Editable text,int code,KeyEvent event){return editor.onKeyDown(view,text,KeyEvent.KEYCODE_DEL,event);}
 public boolean forwardDelete(View view,Editable text,int code,KeyEvent event){return editor.onKeyDown(view,text,KeyEvent.KEYCODE_FORWARD_DEL,event);}
 @Override public boolean onKeyDown(View view,Editable text,int code,KeyEvent event){
  if(code==KeyEvent.KEYCODE_DEL||code==KeyEvent.KEYCODE_FORWARD_DEL){boolean handled=code==KeyEvent.KEYCODE_DEL?backspace(view,text,code,event):forwardDelete(view,text,code,event);if(handled){adjustMetaAfterKeypress(text);return true;}}
  return super.onKeyDown(view,text,code,event);
 }
 public boolean onKeyOther(View view,Editable text,KeyEvent event){return editor.onKeyOther(view,text,event);}
}
