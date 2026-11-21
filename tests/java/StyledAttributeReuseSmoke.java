// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.*;
import android.util.TypedValue;
public final class StyledAttributeReuseSmoke {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static int id(Resources r,String name,String type){int value=r.getIdentifier(name,type,"org.shiraz.fixture");check(value!=0,name);return value;}
 public static void main(String[] args){
  Resources r=Resources.shirazOpen(new java.io.File("build/apk-fixture/fixture.apk").getAbsolutePath(),96,160);
  try{
   Resources.Theme theme=r.newTheme();int accent=id(r,"themeAccent","attr"),label=id(r,"themeLabel","attr"),flag=id(r,"themeFlag","attr"),count=id(r,"themeCount","attr");
   theme.applyStyle(id(r,"FixtureBase","style"),true);
   int[] attrs=new int[48];attrs[1]=accent;attrs[10]=label;attrs[20]=flag;attrs[30]=count;attrs[40]=accent;
   TypedArray retained=null;
   for(int repeat=0;repeat<4;repeat++){
    TypedArray array=theme.obtainStyledAttributes(attrs);
    check(array.getIndexCount()==4,"missing attributes became present");
    for(int i=0;i<attrs.length;i++)if(i!=1&&i!=10&&i!=20&&i!=40)check(array.peekValue(i)==null,"scratch leaked into missing slot "+i);
    check(array.getColor(1,0)==0xff112233&&array.getColor(40,0)==0xff112233&&array.getBoolean(20,false),"present values");
    TypedValue copied=array.peekValue(1);copied.data=0;copied.string="caller mutation";
    check(array.getColor(1,0)==0xff112233&&array.getString(10)!=null,"snapshot ownership");
    if(retained!=null)retained.recycle();retained=array;
   }
   TypedValue out=new TypedValue();check(theme.resolveAttribute(accent,out,true),"resolve accent");int changing=out.changingConfigurations,cookie=out.assetCookie;
   out.data=0;out.string="mutation";out.changingConfigurations=-1;out.assetCookie=-1;out.density=999;
   check(theme.resolveAttribute(accent,out,true)&&out.data==0xff112233&&out.string==null&&out.changingConfigurations==changing&&out.assetCookie==cookie&&out.density!=999,"resolved cache isolation");
   check(theme.resolveAttribute(label,out,false)&&out.type==TypedValue.TYPE_REFERENCE,"unresolved reference");int labelId=out.data;
   check(theme.resolveAttribute(label,out,true)&&out.type==TypedValue.TYPE_STRING&&out.string!=null,"resolved reference");
   check(theme.resolveAttribute(label,out,false)&&out.type==TypedValue.TYPE_REFERENCE&&out.data==labelId,"resolution modes contaminated cache");
   for(int i=0;i<3;i++){out.data=123;check(!theme.resolveAttribute(count,out,true)&&out.data==123,"missing lookup changed output");}
   theme.applyStyle(id(r,"FixtureOverlay","style"),true);
   TypedArray overlay=theme.obtainStyledAttributes(attrs);check(overlay.getColor(1,0)==0xff445566&&overlay.getInt(30,0)==7,"style invalidation");
   check(retained.getColor(1,0)==0xff112233&&!retained.hasValue(30),"old array changed after overlay");overlay.recycle();retained.recycle();
   Resources.Theme base=r.newTheme();base.applyStyle(id(r,"FixtureBase","style"),true);theme.setTo(base);
   Configuration config=new Configuration(r.getConfiguration());config.setLocale(java.util.Locale.FRENCH);config.uiMode=Configuration.UI_MODE_TYPE_NORMAL|Configuration.UI_MODE_NIGHT_YES;r.updateConfiguration(config,null);
   TypedArray night=theme.obtainStyledAttributes(attrs);check(night.getColor(1,0)==0xffabcdef&&"Langue française".equals(night.getString(10))&&!night.hasValue(30),"configuration/setTo invalidation");night.recycle();
   theme.setTo(r.newTheme());check(!theme.resolveAttribute(accent,out,true),"empty theme retained resolved value");
   System.out.println("STYLED_ATTRIBUTE_REUSE_OK");
  }finally{r.shirazClose();}
 }
}
