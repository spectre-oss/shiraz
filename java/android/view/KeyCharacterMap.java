// SPDX-License-Identifier: GPL-3.0-only
package android.view;

/** Logical desktop/virtual key map. Locale text is delivered separately by SDL text input. */
public final class KeyCharacterMap {
 public static final int BUILT_IN_KEYBOARD=0,VIRTUAL_KEYBOARD=-1,NUMERIC=1,PREDICTIVE=2,ALPHA=3,FULL=4,SPECIAL_FUNCTION=5;
 public static final int MODIFIER_BEHAVIOR_CHORDED=0,MODIFIER_BEHAVIOR_CHORDED_OR_TOGGLED=1;
 public static final int COMBINING_ACCENT=0x80000000,COMBINING_ACCENT_MASK=0x7fffffff;
 private static final KeyCharacterMap DESKTOP=new KeyCharacterMap();
 private KeyCharacterMap(){}
 /** Missing physical devices fall back to the virtual keyboard, as on Android. */
 public static KeyCharacterMap load(int deviceId){return DESKTOP;}
 public int getKeyboardType(){return FULL;}
 public int getModifierBehavior(){return MODIFIER_BEHAVIOR_CHORDED;}
 private static char base(int key){
  if(key>=KeyEvent.KEYCODE_A&&key<=KeyEvent.KEYCODE_Z)return (char)('a'+key-KeyEvent.KEYCODE_A);
  if(key>=KeyEvent.KEYCODE_0&&key<=KeyEvent.KEYCODE_9)return (char)('0'+key-KeyEvent.KEYCODE_0);
  if(key>=KeyEvent.KEYCODE_NUMPAD_0&&key<=KeyEvent.KEYCODE_NUMPAD_9)return (char)('0'+key-KeyEvent.KEYCODE_NUMPAD_0);
  switch(key){case KeyEvent.KEYCODE_SPACE:return ' ';case KeyEvent.KEYCODE_TAB:return '\t';case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_NUMPAD_ENTER:return '\n';case KeyEvent.KEYCODE_COMMA:return ',';case KeyEvent.KEYCODE_PERIOD:case KeyEvent.KEYCODE_NUMPAD_DOT:return '.';case KeyEvent.KEYCODE_GRAVE:return '`';case KeyEvent.KEYCODE_MINUS:case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:return '-';case KeyEvent.KEYCODE_EQUALS:case KeyEvent.KEYCODE_NUMPAD_EQUALS:return '=';case KeyEvent.KEYCODE_LEFT_BRACKET:return '[';case KeyEvent.KEYCODE_RIGHT_BRACKET:return ']';case KeyEvent.KEYCODE_BACKSLASH:return '\\';case KeyEvent.KEYCODE_SEMICOLON:return ';';case KeyEvent.KEYCODE_APOSTROPHE:return '\'';case KeyEvent.KEYCODE_SLASH:case KeyEvent.KEYCODE_NUMPAD_DIVIDE:return '/';case KeyEvent.KEYCODE_AT:return '@';case KeyEvent.KEYCODE_PLUS:case KeyEvent.KEYCODE_NUMPAD_ADD:return '+';case KeyEvent.KEYCODE_STAR:case KeyEvent.KEYCODE_NUMPAD_MULTIPLY:return '*';case KeyEvent.KEYCODE_POUND:return '#';case KeyEvent.KEYCODE_NUMPAD_COMMA:return ',';case KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN:return '(';case KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN:return ')';default:return 0;}
 }
 public int get(int key,int metaState){
  int meta=KeyEvent.normalizeMetaState(metaState);char value=base(key);if(value==0)return 0;
  // Alt/Meta/Control are shortcuts, not committed characters in this map.
  if((meta&(KeyEvent.META_ALT_ON|KeyEvent.META_CTRL_ON|KeyEvent.META_META_ON|KeyEvent.META_SYM_ON))!=0)return 0;
  boolean shift=(meta&KeyEvent.META_SHIFT_ON)!=0;
  if(value>='a'&&value<='z')return shift||((meta&KeyEvent.META_CAPS_LOCK_ON)!=0)?Character.toUpperCase(value):value;
  if((key>=KeyEvent.KEYCODE_NUMPAD_0&&key<=KeyEvent.KEYCODE_NUMPAD_9||key==KeyEvent.KEYCODE_NUMPAD_DOT)&&(meta&KeyEvent.META_NUM_LOCK_ON)==0)return 0;
  if(!shift||key>=KeyEvent.KEYCODE_NUMPAD_0&&key<=KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN)return value;
  String normal="0123456789`-=[]\\;',./",shifted=")!@#$%^&*(~_+{}|:\"<>?";int at=normal.indexOf(value);return at<0?value:shifted.charAt(at);
 }
 /** Synthesize only characters representable by this logical map; IMEs handle others. */
 public KeyEvent[] getEvents(char[] chars){
  if(chars==null)throw new NullPointerException("chars");
  java.util.ArrayList<KeyEvent> events=new java.util.ArrayList<KeyEvent>();
  long time=android.os.SystemClock.uptimeMillis();
  for(char ch:chars){
   int key=-1,meta=0;
   for(int shift=0;shift<2&&key<0;shift++)for(int candidate=KeyEvent.KEYCODE_0;candidate<=KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN;candidate++){
    int modifiers=shift==0?0:KeyEvent.META_SHIFT_ON;
    if(ch!=0&&get(candidate,modifiers)==ch){key=candidate;meta=modifiers;break;}
   }
   if(key<0)return null;
   if(meta!=0)events.add(new KeyEvent(time,time,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_SHIFT_LEFT,0,meta,VIRTUAL_KEYBOARD,0));
   events.add(new KeyEvent(time,time,KeyEvent.ACTION_DOWN,key,0,meta,VIRTUAL_KEYBOARD,0));
   events.add(new KeyEvent(time,time,KeyEvent.ACTION_UP,key,0,meta,VIRTUAL_KEYBOARD,0));
   if(meta!=0)events.add(new KeyEvent(time,time,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_SHIFT_LEFT,0,0,VIRTUAL_KEYBOARD,0));
  }
  return events.toArray(new KeyEvent[events.size()]);
 }
 public char getDisplayLabel(int key){char c=base(key);return c>='a'&&c<='z'?Character.toUpperCase(c):c;}
 public char getNumber(int key){char c=base(key);return c>='0'&&c<='9'||c=='*'||c=='#'||c=='+'?c:0;}
 public boolean isPrintingKey(int key){char c=base(key);return c!=0&&!Character.isISOControl(c)&&!Character.isWhitespace(c);}
 public char getMatch(int key,char[] chars){return getMatch(key,chars,0);}
 public char getMatch(int key,char[] chars,int meta){if(chars==null)throw new NullPointerException("chars");if(chars.length>65536)throw new IllegalArgumentException("Character candidates limit");int preferred=get(key,meta);for(char c:chars)if(c!=0&&c==preferred)return c;int lower=get(key,0),upper=get(key,KeyEvent.META_SHIFT_ON);for(char c:chars)if(c!=0&&(c==lower||c==upper))return c;return 0;}
 @Deprecated public static class KeyData {public static final int META_LENGTH=4;public char displayLabel,number;public char[] meta=new char[META_LENGTH];}
 @Deprecated public boolean getKeyData(int key,KeyData out){if(out==null)throw new NullPointerException("results");if(out.meta==null||out.meta.length<4)throw new IndexOutOfBoundsException("KeyData meta capacity");if(base(key)==0)return false;out.displayLabel=getDisplayLabel(key);out.number=getNumber(key);out.meta[0]=(char)get(key,0);out.meta[1]=(char)get(key,KeyEvent.META_SHIFT_ON);out.meta[2]=(char)get(key,KeyEvent.META_ALT_ON);out.meta[3]=(char)get(key,KeyEvent.META_ALT_ON|KeyEvent.META_SHIFT_ON);return true;}
}
