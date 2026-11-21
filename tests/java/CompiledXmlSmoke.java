// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.*;
public final class CompiledXmlSmoke {
 private static final class Facade extends Resources {int xmlCalls;Facade(){super(null);}public XmlResourceParser getXml(int id){xmlCalls++;return super.getXml(id);}}
 private static int styledColor(Resources r,int layout,Resources.Theme theme,int attr)throws Exception{
  int[] attrs=new int[16];java.util.Arrays.fill(attrs,attr);
  try(XmlResourceParser parser=r.getLayout(layout)){while(parser.next()!=org.xmlpull.v1.XmlPullParser.START_TAG||parser.getDepth()!=2){}TypedArray values=theme.obtainStyledAttributes(parser,attrs,0,0);try{return values.getColor(0,0);}finally{values.recycle();}}
 }
 private static byte[] strippedAttributeNames(byte[] original,boolean removeIds){
  byte[] data=original.clone();java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
  int pool=0,map=0,flags=0,strings=0,header=0;
  for(int at=8;at<data.length;at+=b.getInt(at+4)){int kind=b.getShort(at)&65535;if(kind==1){pool=at;header=b.getShort(at+2)&65535;flags=b.getInt(at+16);strings=b.getInt(at+20);}else if(kind==0x180)map=at;}
  if(pool==0||map==0)throw new AssertionError("Missing XML pool/map");
  for(int i=0;i<(b.getInt(map+4)-8)/4;i++)if(b.getInt(map+8+i*4)!=0){int at=pool+strings+b.getInt(pool+header+i*4);if((flags&256)!=0){data[at]=data[at+1]=data[at+2]=0;}else{data[at]=data[at+1]=data[at+2]=data[at+3]=0;}if(removeIds)b.putInt(map+8+i*4,0);}
  for(int at=8;at<data.length;at+=b.getInt(at+4))if((b.getShort(at)&65535)==0x102){int first=b.getShort(at+24)&65535,stride=b.getShort(at+26)&65535,count=b.getShort(at+28)&65535;for(int i=0;i<count;i++){int pos=at+16+first+i*stride,index=b.getInt(pos+4);if(index>=0&&index<(b.getInt(map+4)-8)/4)b.putInt(pos,-1);}}
  return data;
 }
 private static void extensionlessDrawables(String original,String renamed)throws Exception{
  Resources expected=Resources.shirazOpen(original,96,160),actual=Resources.shirazOpen(renamed,96,160);
  try{
   for(String name:new String[]{"png_grid","shape_solid"}){
    int id=actual.getIdentifier(name,"drawable","org.shiraz.fixture");
    android.util.TypedValue value=new android.util.TypedValue();actual.getValue(id,value,true);
    if(value.string.toString().endsWith(".png")||value.string.toString().endsWith(".xml"))throw new AssertionError("Fixture retains suffix");
    android.graphics.drawable.Drawable a=expected.getDrawable(id),b=actual.getDrawable(id);
    if(a.getClass()!=b.getClass()||a.getIntrinsicWidth()!=b.getIntrinsicWidth()||a.getIntrinsicHeight()!=b.getIntrinsicHeight())throw new AssertionError("Extensionless drawable metadata "+name);
    android.graphics.Bitmap x=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.ARGB_8888),y=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.ARGB_8888);
    try{a.setBounds(0,0,32,32);b.setBounds(0,0,32,32);a.draw(new android.graphics.Canvas(x));b.draw(new android.graphics.Canvas(y));boolean visible=false;for(int row=0;row<32;row++)for(int col=0;col<32;col++){int pixel=x.getPixel(col,row);visible|=pixel!=0;if(pixel!=y.getPixel(col,row))throw new AssertionError("Extensionless drawable pixels "+name);}if(!visible)throw new AssertionError("Empty drawable comparison");}finally{x.recycle();y.recycle();}
   }
  }finally{expected.shirazClose();actual.shirazClose();}
 }
 public static void main(String[] args)throws Exception{
  extensionlessDrawables(args[0],args[1]);
  Facade unattached=new Facade();if(unattached.getImpl()!=null)throw new AssertionError("New facade unexpectedly attached");
  try{unattached.getAssets();throw new AssertionError("Unattached facade exposed resources");}catch(IllegalStateException expected){}
  Resources r=Resources.shirazOpen(args[0],96,160);XmlResourceParser owned=r.getLayout(r.getIdentifier("xml_attributes","layout","org.shiraz.fixture"));org.shiraz.fixture.CompiledXmlFixture.check(r);
  int layout=r.getIdentifier("xml_attributes","layout","org.shiraz.fixture");
  XmlResourceParser positions=r.getLayout(layout);
  while(true){
   String expectedPosition="Binary XML resource #"+Integer.toHexString(layout)+" line "+positions.getLineNumber();
   String description=positions.getPositionDescription();
   if(!expectedPosition.equals(description)||!description.equals(positions.getPositionDescription()))throw new AssertionError("XML cursor position description");
   if(positions.getEventType()==org.xmlpull.v1.XmlPullParser.END_DOCUMENT)break;
   positions.next();
  }
  positions.close();try{positions.getPositionDescription();throw new AssertionError("Closed XML description accepted");}catch(IllegalStateException expected){}

  java.lang.reflect.Field implField=Resources.class.getDeclaredField("mResourcesImpl");implField.setAccessible(true);
  Object impl=implField.get(r);if(impl==null||impl!=r.getImpl())throw new AssertionError("Resource implementation identity");
  if(r.getDisplayAdjustments()!=r.getImpl().getDisplayAdjustments())throw new AssertionError("Display adjustments not shared with implementation");
  ResourcesImpl secondImpl=new ResourcesImpl(r.getAssets(),r.getDisplayMetrics(),r.getConfiguration(),r.getDisplayAdjustments());if(secondImpl.getAssets()!=r.getAssets()||secondImpl.getDisplayAdjustments()!=r.getDisplayAdjustments())throw new AssertionError("Matching implementation did not share owner");
  unattached.setImpl(secondImpl);if(unattached.getImpl()!=secondImpl||unattached.getAssets()!=r.getAssets())throw new AssertionError("Facade attachment");
  int welcome=r.getIdentifier("welcome","string","org.shiraz.fixture");if(welcome==0||unattached.getIdentifier("welcome","string","org.shiraz.fixture")!=welcome||!unattached.getString(welcome).equals(r.getString(welcome)))throw new AssertionError("Facade string or identifier delegation");
  android.util.TypedValue ownerValue=new android.util.TypedValue(),facadeValue=new android.util.TypedValue();r.getValue(layout,ownerValue,true);unattached.getValue(layout,facadeValue,true);if(ownerValue.assetCookie!=facadeValue.assetCookie||!ownerValue.string.toString().equals(facadeValue.string.toString()))throw new AssertionError("Facade resource value delegation");
  try(XmlResourceParser wrapped=unattached.getLayout(layout)){while(wrapped.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}if(!"FrameLayout".equals(wrapped.getName())||unattached.xmlCalls!=1)throw new AssertionError("Facade XML override or parser");}
  Resources.Theme facadeTheme=unattached.newTheme();
  if(facadeTheme.getResources()!=unattached)throw new AssertionError("Facade theme identity");
  try{unattached.shirazClose();throw new AssertionError("Facade closed resource owner");}catch(UnsupportedOperationException expected){}
  android.util.DisplayMetrics mismatched=r.getDisplayMetrics();mismatched.widthPixels++;try{new ResourcesImpl(r.getAssets(),mismatched,r.getConfiguration(),r.getDisplayAdjustments());throw new AssertionError("Independent resource configuration accepted");}catch(UnsupportedOperationException expected){}
  android.view.DisplayAdjustments copied=new android.view.DisplayAdjustments(r.getDisplayAdjustments());android.content.res.Configuration override=new android.content.res.Configuration();override.orientation=android.content.res.Configuration.ORIENTATION_LANDSCAPE;copied.setConfiguration(override);override.orientation=android.content.res.Configuration.ORIENTATION_PORTRAIT;if(copied.getConfiguration().orientation!=android.content.res.Configuration.ORIENTATION_LANDSCAPE)throw new AssertionError("Display adjustment configuration not copied");
  try{android.view.DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS.setConfiguration(null);throw new AssertionError("Default adjustments were mutable");}catch(IllegalArgumentException expected){}
  java.lang.reflect.Field cached=impl.getClass().getDeclaredField("mCachedXmlBlocks");cached.setAccessible(true);
  java.lang.reflect.Field lastCache=impl.getClass().getDeclaredField("mLastCachedXmlBlockIndex");lastCache.setAccessible(true);
  Object[] cachedBlocks=(Object[])cached.get(impl);boolean foundBlock=false;for(Object block:cachedBlocks)foundBlock|=block!=null;
  int cacheIndex=lastCache.getInt(impl);if(!foundBlock||cachedBlocks.length!=4||cacheIndex<0||cacheIndex>=4||cachedBlocks[cacheIndex]==null)throw new AssertionError("Implementation cache does not own parsed XML");
  try(XmlResourceParser first=r.getLayout(layout);XmlResourceParser second=r.getLayout(layout)){
   while(first.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}first.close();
   if(second.getEventType()!=org.xmlpull.v1.XmlPullParser.START_DOCUMENT)throw new AssertionError("Cached XML cursor position shared");
   while(second.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}if(!"FrameLayout".equals(second.getName()))throw new AssertionError("Closing peer invalidated cached XML");
  }
  // Retain a cursor across more documents than the resource cache can hold.
  try(XmlResourceParser retained=r.getLayout(layout);java.util.zip.ZipFile apk=new java.util.zip.ZipFile(args[0])){
   int opened=0;java.util.Enumeration<? extends java.util.zip.ZipEntry> entries=apk.entries();
   while(entries.hasMoreElements()&&opened<35){String path=entries.nextElement().getName();if(!path.startsWith("res/layout/")||!path.endsWith(".xml"))continue;
    String name=path.substring("res/layout/".length(),path.length()-4);int id=r.getIdentifier(name,"layout","org.shiraz.fixture");if(id==0||id==layout)continue;
    try(XmlResourceParser cursor=r.getLayout(id)){while(cursor.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}opened++;}
    catch(Resources.NotFoundException unsupportedFixture){/* Other fixtures may exercise unsupported XML. */}
   }
   if(opened<33)throw new AssertionError("Not enough parseable layouts to exercise XML cache eviction");
   while(retained.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}if(!"FrameLayout".equals(retained.getName()))throw new AssertionError("Cache eviction invalidated an open cursor");
  }
  Resources.Theme theme=r.newTheme();int accent=r.getIdentifier("themeAccent","attr","org.shiraz.fixture");
  theme.applyStyle(r.getIdentifier("FixtureBase","style","org.shiraz.fixture"),true);
  facadeTheme.setTo(theme);
  android.util.TypedValue facadeAccent=new android.util.TypedValue();
  if(!facadeTheme.resolveAttribute(accent,facadeAccent,true)||facadeAccent.data!=0xff112233)throw new AssertionError("Facade theme resolution");
  if(styledColor(unattached,layout,facadeTheme,accent)!=0xff112233)throw new AssertionError("Facade themed XML");
  TypedArray facadeAttributes=facadeTheme.obtainStyledAttributes(new int[]{accent});
  try{if(facadeAttributes.getResources()!=unattached||facadeAttributes.getColor(0,0)!=0xff112233)throw new AssertionError("Facade typed array identity/value");}finally{facadeAttributes.recycle();}
  facadeTheme.applyStyle(r.getIdentifier("FixtureOverlay","style","org.shiraz.fixture"),true);
  facadeTheme.rebase();
  if(!facadeTheme.resolveAttribute(accent,facadeAccent,true)||facadeAccent.data!=0xff445566)throw new AssertionError("Facade theme overlay/rebase");
  if(styledColor(r,layout,theme,accent)!=0xff112233)throw new AssertionError("Facade theme mutated source theme");
  Resources.Theme copiedBack=r.newTheme();copiedBack.setTo(facadeTheme);
  if(!copiedBack.resolveAttribute(accent,facadeAccent,true)||facadeAccent.data!=0xff445566)throw new AssertionError("Copy facade theme to owner");
  if(styledColor(r,layout,theme,accent)!=0xff112233||styledColor(r,layout,theme,accent)!=0xff112233)throw new AssertionError("Repeated styled XML queries");
  java.lang.reflect.Field resolutions=theme.getClass().getDeclaredField("resolved");resolutions.setAccessible(true);
  java.util.Map<?,?> resolutionCache=(java.util.Map<?,?>)resolutions.get(theme);
  for(int i=0;i<700;i++){
   android.util.TypedValue hot=new android.util.TypedValue();
   if(!theme.resolveAttribute(accent,hot,true)||hot.data!=0xff112233)throw new AssertionError("Hot theme attribute changed under query pressure");
   if(theme.resolveAttribute(0x7e010000+i,new android.util.TypedValue(),false))throw new AssertionError("Unknown theme attribute resolved");
   if(resolutionCache.size()>256||!resolutionCache.containsKey(((long)accent<<1)|1))throw new AssertionError("Bounded resolution cache discarded the recently used attribute");
  }
  theme.applyStyle(r.getIdentifier("FixtureOverlay","style","org.shiraz.fixture"),true);
  if(styledColor(r,layout,theme,accent)!=0xff445566)throw new AssertionError("Styled XML cache retained old theme");
  Resources.Theme changingTheme=r.newTheme();changingTheme.applyStyle(r.getIdentifier("FixtureBase","style","org.shiraz.fixture"),true);
  android.util.TypedValue changingValue=new android.util.TypedValue();
  if(!changingTheme.resolveAttribute(accent,changingValue,true)||changingValue.data!=0xff112233)throw new AssertionError("Initial theme configuration");
  Configuration originalConfig=new Configuration(r.getConfiguration());
  Configuration nightConfig=new Configuration(originalConfig);nightConfig.uiMode=(nightConfig.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|Configuration.UI_MODE_NIGHT_YES;
  r.updateConfiguration(nightConfig,null);
  if(!changingTheme.resolveAttribute(accent,changingValue,true)||changingValue.data!=0xffabcdef)throw new AssertionError("Resolved theme cache ignored night configuration");
  r.updateConfiguration(originalConfig,null);
  if(!changingTheme.resolveAttribute(accent,changingValue,true)||changingValue.data!=0xff112233)throw new AssertionError("Resolved theme cache ignored restored configuration");
  byte[] xml=r.shirazReadResourceFile(r.getIdentifier("xml_attributes","layout","org.shiraz.fixture"),new android.util.TypedValue());
  byte[] stripped=strippedAttributeNames(xml,false);
  shiraz.runtime.BinaryXml.Node strippedRoot=shiraz.runtime.BinaryXml.parse(stripped);
  if(!"FrameLayout".equals(strippedRoot.name)||strippedRoot.android("layout_width")==null)throw new AssertionError("ID-mapped attribute names/namespace not restored");
  try(shiraz.runtime.CompiledXmlParser strippedParser=new shiraz.runtime.CompiledXmlParser(stripped,layout)){while(strippedParser.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}boolean width=false;for(int i=0;i<strippedParser.getAttributeCount();i++)if(strippedParser.getAttributeNameResource(i)==0x010100f4){width=true;if(!"layout_width".equals(strippedParser.getAttributeName(i)))throw new AssertionError("Mapped parser attribute identity");}if(!width)throw new AssertionError("Mapped width ID lost");}
  try{shiraz.runtime.BinaryXml.parse(strippedAttributeNames(xml,true));throw new AssertionError("Unmapped empty attribute accepted");}catch(IllegalArgumentException expected){}
  android.util.TypedValue xmlValue=new android.util.TypedValue();r.getValue(layout,xmlValue,true);
  try(java.io.InputStream stream=r.getAssets().openNonAsset(xmlValue.assetCookie,xmlValue.string.toString());java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()){byte[] buffer=new byte[1024];for(int n;(n=stream.read(buffer))!=-1;)bytes.write(buffer,0,n);if(!java.util.Arrays.equals(xml,bytes.toByteArray()))throw new AssertionError("Cookie-specific non-asset stream differs from resource");}
  java.nio.ByteBuffer words=java.nio.ByteBuffer.wrap(xml).order(java.nio.ByteOrder.LITTLE_ENDIAN);for(int offset=8;offset<xml.length;offset+=words.getInt(offset+4))if((words.getShort(offset)&65535)==0x102){byte[] bad=xml.clone();bad[offset+30]=(byte)255;bad[offset+31]=(byte)255;try{new shiraz.runtime.CompiledXmlParser(bad,0);throw new AssertionError("invalid compiled special index accepted");}catch(IllegalArgumentException expected){}break;}
  byte[] changed=xml.clone();java.nio.ByteBuffer editable=java.nio.ByteBuffer.wrap(changed).order(java.nio.ByteOrder.LITTLE_ENDIAN);int last=0;for(int offset=8;offset<changed.length;offset+=editable.getInt(offset+4))if((editable.getShort(offset)&65535)==0x103)last=offset;editable.putInt(last+8,12345);
  try(shiraz.runtime.CompiledXmlParser cursor=new shiraz.runtime.CompiledXmlParser(changed,0)){while(cursor.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT)if(cursor.getEventType()==org.xmlpull.v1.XmlPullParser.END_TAG&&cursor.getDepth()==1&&cursor.getLineNumber()!=12345)throw new AssertionError("end-tag line metadata lost");}
  try{r.getXml(r.getIdentifier("png_grid","drawable","org.shiraz.fixture"));throw new AssertionError("PNG parsed as XML");}catch(Resources.NotFoundException expected){}
  Object[] facadeBlocks=(Object[])cached.get(secondImpl);Object previousBlock=null;for(Object block:facadeBlocks)if(block!=null){previousBlock=block;break;}if(previousBlock==null)throw new AssertionError("Facade XML cache empty");
  try(XmlResourceParser retained=unattached.getLayout(layout)){android.content.res.Configuration delta=new android.content.res.Configuration();delta.fontScale=1.25f;r.updateConfiguration(delta,null);if(unattached.getConfiguration().fontScale!=1.25f)throw new AssertionError("Facade configuration did not follow owner");try(XmlResourceParser refreshed=unattached.getLayout(layout)){while(refreshed.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}}for(Object block:facadeBlocks)if(block==previousBlock)throw new AssertionError("Facade cache survived owner configuration update");while(retained.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}if(!"FrameLayout".equals(retained.getName()))throw new AssertionError("Configuration update invalidated retained cursor");}
  r.shirazClose();
  try{facadeTheme.resolveAttribute(accent,facadeAccent,true);throw new AssertionError("Facade theme accessed closed owner");}catch(IllegalStateException expected){}
  try{unattached.newTheme();throw new AssertionError("Facade created theme after owner close");}catch(IllegalStateException expected){}
  try{unattached.getString(welcome);throw new AssertionError("Facade accessed closed owner");}catch(IllegalStateException expected){}for(Object block:cachedBlocks)if(block!=null)throw new AssertionError("Closed resource cache retained a block");if(lastCache.getInt(impl)!=-1)throw new AssertionError("Closed resource cache index retained");while(owned.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}if(!owned.getName().equals("FrameLayout"))throw new AssertionError("parser tied to resource manager lifetime");owned.close();owned.close();try{owned.next();throw new AssertionError("closed parser advanced");}catch(IllegalStateException expected){}
  System.out.println("COMPILED_XML_OWNERSHIP_OK");
 }
}
