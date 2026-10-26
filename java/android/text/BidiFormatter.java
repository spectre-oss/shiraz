// SPDX-License-Identifier: GPL-3.0-only
package android.text;
/** Wraps text whose first strong character runs against the context direction in Unicode embeddings. */
public final class BidiFormatter {
 private static final BidiFormatter INSTANCE=new BidiFormatter(false),RTL_INSTANCE=new BidiFormatter(true);
 private final boolean contextRtl;
 private BidiFormatter(boolean contextRtl){this.contextRtl=contextRtl;}
 public static BidiFormatter getInstance(){return INSTANCE;}
 public static BidiFormatter getInstance(boolean rtlContext){return rtlContext?RTL_INSTANCE:INSTANCE;}
 public static BidiFormatter getInstance(java.util.Locale locale){if(locale==null)throw new NullPointerException("locale");return getInstance(TextUtils.getLayoutDirectionFromLocale(locale)==1);}
 public boolean isRtlContext(){return contextRtl;}
 public String unicodeWrap(String text){
  if(text==null)return null;
  int direction=0;
  for(int i=0;i<text.length()&&direction==0;){
   int cp=text.codePointAt(i);i+=Character.charCount(cp);
   byte d=Character.getDirectionality(cp);
   if(d==Character.DIRECTIONALITY_LEFT_TO_RIGHT)direction=1;
   else if(d==Character.DIRECTIONALITY_RIGHT_TO_LEFT||d==Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC)direction=-1;
  }
  if(direction==0||(direction==-1)==contextRtl)return text;
  return (direction==-1?"\u202B":"\u202A")+text+"\u202C"+(contextRtl?"\u200F":"\u200E");
 }
}
