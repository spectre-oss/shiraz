// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.*;
import android.util.TypedValue;
public final class CrossManagerColorSmoke {
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 static int id(Resources r,String name,String kind){int v=r.getIdentifier(name,kind,"org.shiraz.fixture");check(v!=0,name);return v;}
 public static void main(String[] args)throws Exception{
  String apk=new java.io.File("build/apk-fixture/fixture.apk").getAbsolutePath();
  final Resources day=Resources.shirazOpen(apk,96,160),night=Resources.shirazOpen(apk,96,160);
  try{
   Configuration config=night.getConfiguration();config.uiMode=Configuration.UI_MODE_TYPE_NORMAL|Configuration.UI_MODE_NIGHT_YES;night.updateConfiguration(config,null);
   final Resources.Theme a=day.newTheme(),b=night.newTheme();int base=id(day,"FixtureBase","style");a.applyStyle(base,true);b.applyStyle(base,true);
   final int color=id(day,"theme_color","color");
   int plain=id(day,"accent","color"),vector=id(day,"vector_theme","drawable");
   check(((android.graphics.drawable.ColorDrawable)night.getDrawable(plain,a)).getColor()==night.getColor(plain),"cross-manager color drawable");
   check(night.getDrawable(vector,a)!=null&&day.getDrawable(vector,b)!=null,"cross-manager themed vector");
   Resources other=Resources.shirazOpen(new java.io.File("build/macos-apk/service-only.apk").getAbsolutePath(),96,160);
   try{
    Resources.Theme foreign=other.newTheme();
    try{day.getColor(color,foreign);throw new AssertionError("foreign color theme accepted");}catch(UnsupportedOperationException expected){}
    try{day.getDrawable(vector,foreign);throw new AssertionError("foreign drawable theme accepted");}catch(UnsupportedOperationException expected){}
   }finally{other.shirazClose();}

   check(night.getColor(color,a)==0xffabcdef,"target night configuration ignored");
   check(day.getColorStateList(color,b).getDefaultColor()==0xff112233,"target day configuration ignored");
   TypedValue value=new TypedValue();a.resolveAttribute(id(day,"themeAccent","attr"),value,true);check(value.data==0xff112233,"source theme mutated");
   a.applyStyle(id(day,"FixtureOverlay","style"),true);check(night.getColor(color,a)==0xff445566,"source style changes not copied");
   a.setTo(day.newTheme());a.applyStyle(base,true);
   final Throwable[] failures=new Throwable[2];
   Thread first=new Thread(new Runnable(){public void run(){try{for(int i=0;i<100;i++)check(day.getColor(color,b)==0xff112233,"day concurrent");}catch(Throwable e){failures[0]=e;}}});
   Thread second=new Thread(new Runnable(){public void run(){try{for(int i=0;i<100;i++)check(night.getColor(color,a)==0xffabcdef,"night concurrent");}catch(Throwable e){failures[1]=e;}}});
   first.setDaemon(true);second.setDaemon(true);first.start();second.start();first.join(5000);second.join(5000);
   check(!first.isAlive()&&!second.isAlive(),"cross-manager lock inversion");check(failures[0]==null&&failures[1]==null,"concurrent lookup failed");
   night.shirazClose();try{day.getColor(color,b);throw new AssertionError("closed source theme accepted");}catch(IllegalStateException expected){}
  }finally{day.shirazClose();night.shirazClose();}
  System.out.println("CROSS_MANAGER_COLOR_OK");
 }
}
