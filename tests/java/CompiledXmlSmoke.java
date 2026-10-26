// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.*;
public final class CompiledXmlSmoke {
 private static int styledColor(Resources r,int layout,Resources.Theme theme,int attr)throws Exception{
  int[] attrs=new int[16];java.util.Arrays.fill(attrs,attr);
  try(XmlResourceParser parser=r.getLayout(layout)){while(parser.next()!=org.xmlpull.v1.XmlPullParser.START_TAG||parser.getDepth()!=2){}TypedArray values=theme.obtainStyledAttributes(parser,attrs,0,0);try{return values.getColor(0,0);}finally{values.recycle();}}
 }
 public static void main(String[] args)throws Exception{
  Resources r=Resources.shirazOpen(args[0],96,160);XmlResourceParser owned=r.getLayout(r.getIdentifier("xml_attributes","layout","org.shiraz.fixture"));org.shiraz.fixture.CompiledXmlFixture.check(r);
  int layout=r.getIdentifier("xml_attributes","layout","org.shiraz.fixture");
  try(XmlResourceParser first=r.getLayout(layout);XmlResourceParser second=r.getLayout(layout)){
   while(first.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}first.close();
   if(second.getEventType()!=org.xmlpull.v1.XmlPullParser.START_DOCUMENT)throw new AssertionError("Cached XML cursor position shared");
   while(second.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}if(!"FrameLayout".equals(second.getName()))throw new AssertionError("Closing peer invalidated cached XML");
  }
  Resources.Theme theme=r.newTheme();int accent=r.getIdentifier("themeAccent","attr","org.shiraz.fixture");
  theme.applyStyle(r.getIdentifier("FixtureBase","style","org.shiraz.fixture"),true);
  if(styledColor(r,layout,theme,accent)!=0xff112233||styledColor(r,layout,theme,accent)!=0xff112233)throw new AssertionError("Repeated styled XML queries");
  theme.applyStyle(r.getIdentifier("FixtureOverlay","style","org.shiraz.fixture"),true);
  if(styledColor(r,layout,theme,accent)!=0xff445566)throw new AssertionError("Styled XML cache retained old theme");
  byte[] xml=r.shirazReadResourceFile(r.getIdentifier("xml_attributes","layout","org.shiraz.fixture"),new android.util.TypedValue());
  java.nio.ByteBuffer words=java.nio.ByteBuffer.wrap(xml).order(java.nio.ByteOrder.LITTLE_ENDIAN);for(int offset=8;offset<xml.length;offset+=words.getInt(offset+4))if((words.getShort(offset)&65535)==0x102){byte[] bad=xml.clone();bad[offset+30]=(byte)255;bad[offset+31]=(byte)255;try{new shiraz.runtime.CompiledXmlParser(bad,0);throw new AssertionError("invalid compiled special index accepted");}catch(IllegalArgumentException expected){}break;}
  byte[] changed=xml.clone();java.nio.ByteBuffer editable=java.nio.ByteBuffer.wrap(changed).order(java.nio.ByteOrder.LITTLE_ENDIAN);int last=0;for(int offset=8;offset<changed.length;offset+=editable.getInt(offset+4))if((editable.getShort(offset)&65535)==0x103)last=offset;editable.putInt(last+8,12345);
  try(shiraz.runtime.CompiledXmlParser cursor=new shiraz.runtime.CompiledXmlParser(changed,0)){while(cursor.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT)if(cursor.getEventType()==org.xmlpull.v1.XmlPullParser.END_TAG&&cursor.getDepth()==1&&cursor.getLineNumber()!=12345)throw new AssertionError("end-tag line metadata lost");}
  try{r.getXml(r.getIdentifier("png_grid","drawable","org.shiraz.fixture"));throw new AssertionError("PNG parsed as XML");}catch(Resources.NotFoundException expected){}
  r.shirazClose();while(owned.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}if(!owned.getName().equals("FrameLayout"))throw new AssertionError("parser tied to resource manager lifetime");owned.close();owned.close();try{owned.next();throw new AssertionError("closed parser advanced");}catch(IllegalStateException expected){}
  System.out.println("COMPILED_XML_OWNERSHIP_OK");
 }
}
