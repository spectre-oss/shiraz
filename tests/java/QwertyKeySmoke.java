// SPDX-License-Identifier: GPL-3.0-only
import android.text.*;
import android.text.method.*;
import android.view.*;
public final class QwertyKeySmoke {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  QwertyKeyListener key=QwertyKeyListener.getInstanceForFullKeyboard();
  check(key==QwertyKeyListener.getInstanceForFullKeyboard()&&key.getInputType()==InputType.TYPE_CLASS_TEXT,"full keyboard policy");
  Editable text=new SpannableStringBuilder("A😀B");Selection.setSelection(text,3);
  check(key.onKeyDown(null,text,KeyEvent.KEYCODE_DEL,new KeyEvent(0,KeyEvent.KEYCODE_DEL))&&text.toString().equals("AB")&&Selection.getSelectionStart(text)==1,"delete whole supplementary character");
  check(!key.onKeyDown(null,text,KeyEvent.KEYCODE_C,new KeyEvent(0,KeyEvent.KEYCODE_C))&&text.toString().equals("AB"),"physical key does not duplicate committed text");
  KeyEvent committed=new KeyEvent(0,"é",0,0);
  check(key.onKeyOther(null,text,committed)&&text.toString().equals("AéB"),"committed text inserted once");
  key.onKeyDown(null,text,KeyEvent.KEYCODE_MOVE_HOME,new KeyEvent(0,KeyEvent.KEYCODE_MOVE_HOME));
  check(Selection.getSelectionStart(text)==0,"navigation");
  QwertyKeyListener caps=QwertyKeyListener.getInstance(true,TextKeyListener.Capitalize.WORDS);
  check(caps==QwertyKeyListener.getInstance(true,TextKeyListener.Capitalize.WORDS)&&(caps.getInputType()&InputType.TYPE_TEXT_FLAG_CAP_WORDS)!=0,"input method hints");
  System.out.println("QWERTY_COMMITTED_TEXT_EDITING_OK");
 }
}
