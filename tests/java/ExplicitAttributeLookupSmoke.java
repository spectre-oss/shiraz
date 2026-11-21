// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.*;
import android.util.TypedValue;
import shiraz.runtime.*;
public final class ExplicitAttributeLookupSmoke {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  Resources r=Resources.shirazOpen(new java.io.File("build/apk-fixture/fixture.apk").getAbsolutePath(),96,160);
  try{
   int layout=r.getIdentifier("inflater_live","layout","org.shiraz.fixture");
   CompiledXmlParser parser=(CompiledXmlParser)r.getLayout(layout);parser.next();
   BinaryXml.Node node=parser.shirazNode();node.attributes.clear();
   java.lang.reflect.Constructor<BinaryXml.Attribute> ctor=BinaryXml.Attribute.class.getDeclaredConstructor(String.class,String.class,BinaryXml.Value.class,int.class);ctor.setAccessible(true);
   int accent=r.getIdentifier("themeAccent","attr","org.shiraz.fixture"),flag=r.getIdentifier("themeFlag","attr","org.shiraz.fixture");
   int[] keys={0,accent,0x7f0ffff0,accent,0x010100d4};int[] values={99,17,23,88,42};
   for(int i=0;i<keys.length;i++)node.attributes.add(ctor.newInstance("","test"+i,new BinaryXml.Value(16,values[i],null),keys[i]));
   for(int size:new int[]{15,16,129}){
    int[] query=new int[size];query[0]=accent;query[1]=0x7f0ffff0;query[2]=0x010100d4;query[3]=accent;query[4]=flag;
    Resources.Theme theme=r.newTheme();theme.applyStyle(r.getIdentifier("FixtureBase","style","org.shiraz.fixture"),true);
    TypedArray a=theme.obtainStyledAttributes(parser,query,0,0);
    check(a.getInt(0,0)==17&&a.getInt(3,0)==17,"first duplicate wins "+size);
    check(a.getInt(1,0)==23&&a.getInt(2,0)==42,"unsorted explicit lookup "+size);
    check(a.getBoolean(4,false),"theme fallback "+size);
    for(int i=5;i<size;i++)check(a.peekValue(i)==null,"zero ID must remain absent");a.recycle();
   }
   int[] query=new int[16];query[0]=accent;
   TypedArray before=r.obtainAttributes(parser,query);node.attributes.remove(1);
   TypedArray after=r.obtainAttributes(parser,query);
   check(before.getInt(0,0)==17&&after.getInt(0,0)==88,"query local index and returned snapshot");before.recycle();after.recycle();
   parser.close();System.out.println("EXPLICIT_ATTRIBUTE_LOOKUP_OK");
  }finally{r.shirazClose();}
 }
}
