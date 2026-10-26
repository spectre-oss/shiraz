// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.res.Resources;
import android.net.Uri;
import android.provider.Settings;
import java.io.*;
import java.util.Arrays;
public final class ContentResolverSmoke {
 private static final class ProbeProvider extends ContentProvider {
  private final String type;ProbeProvider(String type){this.type=type;}
  public boolean onCreate(){return true;}
  public String getType(Uri uri){return type;}
  public android.database.Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
  public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
  public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
  public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static byte[] read(InputStream input)throws Exception{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);return out.toByteArray();}}
 public static void main(String[] args)throws Exception{
  final Resources resources=Resources.shirazOpen(args[0],100,100);
  Context context=new ContextWrapper(null){private ContentResolver resolver;public String getPackageName(){return "org.shiraz.fixture";}public Resources getResources(){return resources;}public synchronized ContentResolver getContentResolver(){if(resolver==null)resolver=new ContentResolver(this);return resolver;}};
  try{ContentResolver resolver=context.getContentResolver();check(new ContextWrapper(context).getContentResolver()==resolver,"wrapper resolver identity");
   check(Settings.Global.getString(resolver,Settings.Global.AIRPLANE_MODE_ON)==null,"airplane absent, not false");check(Settings.Global.getInt(resolver,Settings.Global.AIRPLANE_MODE_ON,1)==1&&Settings.Global.getInt(resolver,"missing",-7)==-7,"caller defaults retained");check(Settings.System.getInt(resolver,Settings.System.AIRPLANE_MODE_ON,9)==9,"legacy airplane alias absent");try{Settings.Global.getInt(resolver,"missing");throw new AssertionError("missing value invented");}catch(Settings.SettingNotFoundException expected){}
   ContentResolver values=new ContentResolver(context){public String shirazReadSetting(String table,String name){return name;}};check(Settings.Global.getInt(values,"42",0)==42&&Settings.Global.getInt(values,"2147483648",8)==8&&Settings.Global.getInt(values," 1",7)==7,"integer parse/default semantics");try{Settings.Global.getInt(values,"invalid");throw new AssertionError("malformed accepted");}catch(Settings.SettingNotFoundException expected){}
   try{Settings.Global.putInt(resolver,Settings.Global.AIRPLANE_MODE_ON,1);throw new AssertionError("write claimed");}catch(UnsupportedOperationException expected){}
   File file=new File(args[1],"حافظ 🌍.bin");byte[] expected={0,1,2,(byte)255};try(FileOutputStream out=new FileOutputStream(file)){out.write(expected);}check(Arrays.equals(read(resolver.openInputStream(Uri.fromFile(file))),expected),"real unicode file stream");
   int id=resources.getIdentifier("vector_overlap","drawable","org.shiraz.fixture");byte[] raw=read(resources.openRawResource(id));check(raw.length>0&&Arrays.equals(raw,read(resolver.openInputStream(Uri.parse("android.resource://org.shiraz.fixture/"+id))))&&Arrays.equals(raw,read(resolver.openInputStream(Uri.parse("android.resource://org.shiraz.fixture/drawable/vector_overlap")))),"resource id and type/name bytes");
   Resources system=Resources.getSystem();int systemId=system.getIdentifier("btn_default","drawable","android");check(Arrays.equals(read(system.openRawResource(systemId)),read(resolver.openInputStream(Uri.parse("android.resource://android/drawable/btn_default")))),"framework resource authority");
   try{resolver.openInputStream(null);throw new AssertionError("null URI accepted");}catch(NullPointerException expectedNull){}
   for(String uri:new String[]{"content://unknown/items","https://example.invalid/a","file:///missing-shiraz-resolver-file","file://remote/tmp/x","file:///tmp/a%00b","android.resource://unknown/1","android.resource://org.shiraz.fixture/nope","android.resource://org.shiraz.fixture/drawable/missing"}){try{resolver.openInputStream(Uri.parse(uri));throw new AssertionError("unavailable URI accepted: "+uri);}catch(FileNotFoundException failure){}}
   android.content.pm.ProviderInfo providerInfo=new android.content.pm.ProviderInfo();providerInfo.authority="fixture.duplicate";
   new ProbeProvider("first").attachInfo(context,providerInfo);new ProbeProvider("second").attachInfo(context,providerInfo);
   check("first".equals(resolver.getType(Uri.parse("content://fixture.duplicate/item"))),"Duplicate authority retains first publication");
   check(file.delete(),"fixture cleanup");System.out.println("CONTENT_RESOLVER_SETTINGS_STREAMS_OK");
  }finally{resources.shirazClose();}
 }
}
