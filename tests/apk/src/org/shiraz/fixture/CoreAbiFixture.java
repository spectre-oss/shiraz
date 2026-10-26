// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.os.*;
import android.view.*;
/** Compiled against Android SDK, so erased signatures must match on Shiraz. */
public final class CoreAbiFixture {
 private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static final class Value implements Parcelable {
  final int value;Value(int value){this.value=value;}
  public int describeContents(){return 0;}
  public void writeToParcel(Parcel p,int flags){p.writeInt(value);}
  public static final Parcelable.Creator<Value> CREATOR=new Parcelable.Creator<Value>(){
   public Value createFromParcel(Parcel p){return new Value(p.readInt());}
   public Value[] newArray(int count){return new Value[count];}
  };
 }
 public static void main(String[] args)throws Exception{
  check(!android.content.res.Configuration.needNewResources(0,-1),"No resource changes");
  check(!android.content.res.Configuration.needNewResources(128,1024),"Unrelated resource qualifier");
  check(android.content.res.Configuration.needNewResources(128,128),"Interested orientation change");
  check(android.content.res.Configuration.needNewResources(0x40000000,0)&&android.content.res.Configuration.needNewResources(0x80000000,0),"Global font/asset invalidation");
  android.net.Uri userUri=android.net.Uri.parse("https://user%40label:pass%3Aword@example.com/path");
  check("user%40label:pass%3Aword".equals(userUri.getEncodedUserInfo())&&"user@label:pass:word".equals(userUri.getUserInfo()),"Encoded and decoded user info");
  check(android.net.Uri.parse("https://example.com").getEncodedUserInfo()==null&&android.net.Uri.parse("about:blank").getUserInfo()==null,"Absent and opaque user info");
  check("".equals(android.net.Uri.parse("https://@example.com").getUserInfo())&&"a@b".equals(android.net.Uri.parse("https://a@b@example.com").getEncodedUserInfo()),"Empty user info and final authority separator");
  check(!android.webkit.URLUtil.isValidUrl(null)&&!android.webkit.URLUtil.isValidUrl(""),"Null/empty URL classification");
  check(android.webkit.URLUtil.isNetworkUrl("HtTpS://example.com")&&!android.webkit.URLUtil.isNetworkUrl("https:"),"Network prefix case and length");
  check(android.webkit.URLUtil.isFileUrl("file:///tmp/a")&&!android.webkit.URLUtil.isFileUrl("file:///android_asset/a")&&!android.webkit.URLUtil.isFileUrl("file:///cookieless_proxy/a"),"File namespace exclusions");
  check(android.webkit.URLUtil.isValidUrl("file:///android_asset/a")&&android.webkit.URLUtil.isValidUrl("file:///android_res/a"),"Asset/resource classification");
  check(android.webkit.URLUtil.isContentUrl("content:x")&&!android.webkit.URLUtil.isContentUrl("Content:x"),"Content prefix case");
  check(android.webkit.URLUtil.isJavaScriptUrl("javascript:1")&&!android.webkit.URLUtil.isJavaScriptUrl("JavaScript:1"),"Javascript prefix case");
  check(android.webkit.URLUtil.isDataUrl("data:text/plain,a")&&!android.webkit.URLUtil.isValidUrl("data:text/plain,a")&&!android.webkit.URLUtil.isValidUrl("ftp://example.com"),"Android valid URL predicate is scheme classification");
  check(android.webkit.URLUtil.stripAnchor("https://example.com/#a#b").equals("https://example.com/"),"First fragment separator");
  check(Build.UNKNOWN.equals(Build.class.getField("SOC_MANUFACTURER").get(null))&&Build.UNKNOWN.equals(Build.class.getField("SOC_MODEL").get(null)),"Unavailable Android SoC properties");
  java.util.Locale previous=java.util.Locale.getDefault();try{
   java.util.Locale.setDefault(new java.util.Locale("tr","TR"));
   android.net.Uri mixed=android.net.Uri.parse("FILE://Host/A%2fb?Q=Upper#Frag");
   check(mixed.normalizeScheme().toString().equals("file://Host/A%2fb?Q=Upper#Frag"),"Scheme-only locale independent normalization");
   android.net.Uri relative=android.net.Uri.parse("/Relative/Path");check(relative.normalizeScheme()==relative,"Relative URI identity");
   android.net.Uri normal=android.net.Uri.parse("mailto:User@Example.com");check(normal.normalizeScheme()==normal,"Normalized URI identity");
  }finally{java.util.Locale.setDefault(previous);}
  View view=new View(new android.content.ContextWrapper(null));check(!view.willNotCacheDrawing(),"Default cache hint");view.setWillNotCacheDrawing(true);check(view.willNotCacheDrawing(),"Disable cache hint");view.setWillNotCacheDrawing(false);check(!view.willNotCacheDrawing(),"Restore cache hint");
  android.net.Uri query=android.net.Uri.parse("https://example.com/?a=one+two&a=three%20four&flag&no=FALSE&space+key=value");
  check(query.getQueryParameters("a").equals(java.util.Arrays.asList("one+two","three four")),"Repeated query values");
  check(query.getQueryParameters("space key").equals(java.util.Collections.singletonList("value")),"Form-encoded multi-query key");
  check(query.getQueryParameter("a").equals("one two")&&query.getBooleanQueryParameter("flag",false)&&!query.getBooleanQueryParameter("no",true),"Single and boolean query values");
  check(query.getQueryParameterNames().equals(new java.util.LinkedHashSet<String>(java.util.Arrays.asList("a","flag","no","space+key"))),"Ordered distinct query names");
  try{query.getQueryParameters("a").add("bad");throw new AssertionError("Mutable query values");}catch(UnsupportedOperationException expected){}
  try{android.net.Uri.parse("mailto:test").getQueryParameters("a");throw new AssertionError("Opaque query accepted");}catch(UnsupportedOperationException expected){}
  check(android.util.Patterns.IP_ADDRESS.matcher("192.168.1.255").matches()&&!android.util.Patterns.IP_ADDRESS.matcher("256.1.1.1").matches()&&!android.util.Patterns.IP_ADDRESS.matcher("example.com").matches(),"Android IPv4 heuristic");
  java.io.StringWriter json=new java.io.StringWriter();android.util.JsonWriter writer=new android.util.JsonWriter(json);
  writer.beginObject().name("tabs").beginArray().beginObject().name("url").value("https://example.com/?q=\"line\n").name("selected").value(true).name("count").value(2L).name("optional").nullValue().endObject().endArray().endObject();writer.close();
  android.util.JsonReader reader=new android.util.JsonReader(new java.io.StringReader(json.toString()));reader.beginObject();check(reader.nextName().equals("tabs"),"JSON session key");reader.beginArray();reader.beginObject();check(reader.nextName().equals("url")&&reader.nextString().equals("https://example.com/?q=\"line\n"),"JSON escaping roundtrip");check(reader.nextName().equals("selected")&&reader.nextBoolean(),"JSON boolean");check(reader.nextName().equals("count")&&reader.nextLong()==2,"JSON integer");check(reader.nextName().equals("optional"),"JSON null key");reader.nextNull();reader.endObject();reader.endArray();reader.endObject();check(reader.peek()==android.util.JsonToken.END_DOCUMENT,"Complete session JSON");reader.close();
  try{new android.util.JsonWriter(new java.io.StringWriter()).beginArray().value(Double.NaN);throw new AssertionError("Nonfinite JSON accepted");}catch(IllegalArgumentException expected){}
  try{new android.util.JsonWriter(new java.io.StringWriter()).beginObject().close();throw new AssertionError("Incomplete JSON accepted");}catch(java.io.IOException expected){}
  view.announceForAccessibility("Loading");
  ActionMode.Callback2 callback=new ActionMode.Callback2(){
   public boolean onCreateActionMode(ActionMode mode,Menu menu){return true;}
   public boolean onPrepareActionMode(ActionMode mode,Menu menu){return false;}
   public boolean onActionItemClicked(ActionMode mode,MenuItem item){return false;}
   public void onDestroyActionMode(ActionMode mode){}
  };
  android.graphics.Rect content=new android.graphics.Rect(1,2,3,4);callback.onGetContentRect(null,null,content);check(content.isEmpty(),"Null action-mode origin");
  view.layout(0,0,37,53);callback.onGetContentRect(null,view,content);check(content.equals(new android.graphics.Rect(0,0,37,53)),"Default action-mode content rectangle");
  android.content.Intent plain=android.content.Intent.parseUri("https://example.com/A%2fb#section",android.content.Intent.URI_INTENT_SCHEME);
  check(plain.getAction().equals(android.content.Intent.ACTION_VIEW)&&plain.getDataString().equals("https://example.com/A%2fb#section"),"Plain browser URI preserved");
  String intentText="intent://example.com/path#Intent;scheme=https;package=test.pkg;launchFlags=0x100000c3;S.label=Hello%20world;i.count=42;B.ready=true;sourceBounds=1%202%203%204;end";
  android.content.Intent parsed=android.content.Intent.parseUri(intentText,android.content.Intent.URI_INTENT_SCHEME);
  check(parsed.getDataString().equals("https://example.com/path")&&parsed.getPackage().equals("test.pkg")&&parsed.getFlags()==android.content.Intent.FLAG_ACTIVITY_NEW_TASK,"Intent scheme and stripped URI grants");
  check(parsed.getIntExtra("count",0)==42&&parsed.getBooleanExtra("ready",false)&&parsed.getStringExtra("label").equals("Hello world")&&parsed.getSourceBounds().equals(new android.graphics.Rect(1,2,3,4)),"Typed intent URI fields");
  check((android.content.Intent.parseUri(intentText,android.content.Intent.URI_ALLOW_UNSAFE).getFlags()&0xc3)==0xc3,"Explicit unsafe parser flag");
  android.content.Intent appLink=android.content.Intent.parseUri("android-app://test.pkg/https/example.com/path?q=1",android.content.Intent.URI_ANDROID_APP_SCHEME);
  check(appLink.getPackage().equals("test.pkg")&&appLink.getDataString().equals("https://example.com/path?q=1"),"Android app URI");
  android.content.Intent selected=android.content.Intent.parseUri("intent:#Intent;SEL;component=test.pkg/.Main;action=test.SELECT;end",android.content.Intent.URI_INTENT_SCHEME);
  check(selected.getSelector()!=null&&selected.getSelector().getComponent().getClassName().equals("test.pkg.Main"),"Intent selector");
  android.content.Intent copied=new android.content.Intent(selected);selected.getSelector().setAction("changed");check(copied.getSelector().getAction().equals("test.SELECT"),"Selector copy isolation");
  Parcel intentParcel=Parcel.obtain();try{copied.writeToParcel(intentParcel,0);parsed.writeToParcel(intentParcel,0);intentParcel.setDataPosition(0);check(android.content.Intent.CREATOR.createFromParcel(intentParcel).getSelector().getAction().equals("test.SELECT"),"Selector parcel roundtrip");check(android.content.Intent.CREATOR.createFromParcel(intentParcel).getSourceBounds().equals(parsed.getSourceBounds()),"Bounds parcel roundtrip");}finally{intentParcel.recycle();}
  try{selected.getSelector().setSelector(selected);throw new AssertionError("Selector cycle accepted");}catch(IllegalArgumentException expected){}
  for(String invalid:new String[]{"intent:#Intent;S.a=missing-end;","intent:#Intent;i.a=no;end","intent:#Intent;X.a=x;end"})try{android.content.Intent.parseUri(invalid,1);throw new AssertionError("Malformed intent accepted");}catch(java.net.URISyntaxException expected){}
  InputEvent host=new KeyEvent(1,2,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,0,0,0,0);
  check(host.getDevice()==InputDevice.getDevice(0),"InputEvent device lookup");
  InputEvent missing=new KeyEvent(1,2,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,0,0,9876,0);
  check(missing.getDevice()==null,"Unknown device");
  MotionEvent pointer=MotionEvent.obtain(1,2,MotionEvent.ACTION_DOWN,1,1,0);
  check(pointer.getDevice()==InputDevice.getDevice(pointer.getDeviceId()),"Pointer lookup");pointer.recycle();
  Parcel p=Parcel.obtain();try{
   Value[] source={new Value(41),null,new Value(73)};
   p.writeTypedArray(source,0);p.writeTypedArray(source,0);p.writeTypedObject(source[0],0);p.writeTypedObject(null,0);p.writeTypedArray(null,0);
   p.setDataPosition(0);Value[] decoded=p.createTypedArray(Value.CREATOR);
   check(decoded.length==3&&decoded[0].value==41&&decoded[1]==null&&decoded[2].value==73,"Typed array erased return type");
   p.readTypedArray(decoded,Value.CREATOR);check(decoded[2].value==73,"Typed array erased argument type");
   Value object=p.readTypedObject(Value.CREATOR);check(object.value==41&&p.readTypedObject(Value.CREATOR)==null&&p.createTypedArray(Value.CREATOR)==null,"Typed object erased return and null values");
  }finally{p.recycle();}
  android.content.Context inputContext=new android.content.ContextWrapper(null){public Object getSystemService(String name){return null;}};
  final java.util.ArrayList<KeyEvent> sent=new java.util.ArrayList<KeyEvent>();
  View inputView=new View(inputContext){public boolean dispatchKeyEvent(KeyEvent event){sent.add(event);return true;}};
  android.view.inputmethod.BaseInputConnection input=new android.view.inputmethod.BaseInputConnection(inputView,true);
  check(input.commitText("A\ud83d\ude00B",1)&&input.getEditable().toString().equals("A\ud83d\ude00B"),"Base input Unicode commit");
  input.setSelection(3,3);input.deleteSurroundingTextInCodePoints(1,0);check(input.getEditable().toString().equals("AB"),"Delete one codepoint preserves UTF16");
  input.setSelection(1,1);input.setComposingText("xy",1);check(input.getEditable().toString().equals("AxyB")&&android.view.inputmethod.BaseInputConnection.getComposingSpanStart(input.getEditable())==1,"Composition range");
  input.setComposingText("z",1);check(input.getEditable().toString().equals("AzB"),"Composition replaces prior range");
  input.finishComposingText();check(android.view.inputmethod.BaseInputConnection.getComposingSpanStart(input.getEditable())==-1,"Finish composing removes span");
  input.setSelection(2,1);check(input.getSelectedText(0).toString().equals("z"),"Reversed selection");
  check(input.getTextBeforeCursor(10,0).toString().equals("A")&&input.getTextAfterCursor(10,0).toString().equals("B"),"Text surrounding selection");
  input.commitText("Q",1);check(input.getEditable().toString().equals("AQB"),"Replace selection");
  android.view.inputmethod.BaseInputConnection dummy=new android.view.inputmethod.BaseInputConnection(inputView,false);dummy.commitText("ab",1);check(sent.size()==1&&"ab".equals(sent.get(0).getCharacters())&&dummy.getEditable().length()==0,"Dummy connection sends text event");
  check((android.text.TextUtils.getCapsMode("Hello. ",7,android.text.TextUtils.CAP_MODE_SENTENCES)&android.text.TextUtils.CAP_MODE_SENTENCES)!=0,"Sentence caps mode");
  android.util.LongSparseArray<Object> longKeys=new android.util.LongSparseArray<Object>(0);Object texture=new Object();longKeys.put(Long.MAX_VALUE,texture);longKeys.put(1L<<40,null);longKeys.append(Long.MIN_VALUE,"first");check(longKeys.size()==3&&longKeys.keyAt(0)==Long.MIN_VALUE&&longKeys.keyAt(2)==Long.MAX_VALUE,"Long sparse key ordering/no truncation");check(longKeys.get(Long.MAX_VALUE)==texture&&longKeys.get(1L<<40,texture)==null&&longKeys.get(2,texture)==texture,"Long sparse identity/null/default");android.util.LongSparseArray<Object> copyKeys=longKeys.clone();longKeys.delete(Long.MAX_VALUE);check(copyKeys.get(Long.MAX_VALUE)==texture&&longKeys.indexOfKey(Long.MAX_VALUE)<0,"Long sparse clone storage isolation");copyKeys.removeAt(0);check(copyKeys.size()==2&&copyKeys.keyAt(0)==1L<<40,"Long sparse remove index");copyKeys.setValueAt(0,texture);check(copyKeys.indexOfValue(texture)==0,"Long sparse value identity");copyKeys.clear();check(copyKeys.size()==0,"Long sparse clear");
  android.net.ConnectivityManager connectivity=(android.net.ConnectivityManager)android.net.ConnectivityManager.class.getMethod("shirazForHost").invoke(null);check(connectivity.getRestrictBackgroundStatus()==android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED,"No Android Data Saver restriction on desktop");
  System.out.println("SDK_CORE_ABI_OK");
 }
}
