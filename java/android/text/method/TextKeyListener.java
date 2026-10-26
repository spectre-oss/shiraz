// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;
import android.text.*;
import android.view.*;
/** Desktop text is committed by SDL/IME; physical key events must not insert it twice. */
public class TextKeyListener extends BaseKeyListener implements SpanWatcher {
 public enum Capitalize { NONE, SENTENCES, WORDS, CHARACTERS }
 private static final TextKeyListener[] INSTANCES=new TextKeyListener[8];
 private final Capitalize capitalization;
 private final boolean autoText;
 public TextKeyListener(Capitalize cap,boolean auto){capitalization=java.util.Objects.requireNonNull(cap);autoText=auto;}
 public static synchronized TextKeyListener getInstance(boolean auto,Capitalize cap){int index=cap.ordinal()*2+(auto?1:0);if(INSTANCES[index]==null)INSTANCES[index]=new TextKeyListener(cap,auto);return INSTANCES[index];}
 public static TextKeyListener getInstance(){return getInstance(false,Capitalize.NONE);}
 public static boolean shouldCap(Capitalize cap,CharSequence text,int offset){return (TextUtils.getCapsMode(text,offset,mode(cap))&mode(cap))!=0;}
 private static int mode(Capitalize cap){switch(cap){case CHARACTERS:return TextUtils.CAP_MODE_CHARACTERS;case WORDS:return TextUtils.CAP_MODE_WORDS;case SENTENCES:return TextUtils.CAP_MODE_SENTENCES;default:return 0;}}
 public int getInputType(){return InputType.TYPE_CLASS_TEXT|mode(capitalization)|(autoText?InputType.TYPE_TEXT_FLAG_AUTO_CORRECT:0);}
 public static void clear(Editable text){text.clear();resetMetaState(text);}
 public void onSpanAdded(Spannable text,Object what,int start,int end){}
 public void onSpanRemoved(Spannable text,Object what,int start,int end){}
 public void onSpanChanged(Spannable text,Object what,int oldStart,int oldEnd,int newStart,int newEnd){}
 public void release(){}
}
