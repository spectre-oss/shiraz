// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.*;
import android.content.res.*;
import android.util.TypedValue;
import java.util.Locale;
public final class StyledAttributesFixture {
 public static final class IdOverrideView extends android.view.View {
  private final java.util.ArrayList<Integer> assigned=new java.util.ArrayList<Integer>();
  public IdOverrideView(Context context,android.util.AttributeSet attributes){super(context,attributes);}
  @Override public void setId(int value){if(assigned==null)throw new AssertionError("setId dispatched before subclass initialization");assigned.add(value);super.setId(value);}
  public int assignments(){return assigned.size();}
 }
 private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void check(Context base)throws Exception {
  IdOverrideView idView=(IdOverrideView)android.view.LayoutInflater.from(base).inflate(R.layout.id_constructor_fixture,null,false);
  check(idView.getId()==R.id.constructor_identity&&idView.assignments()==0,"constructor initializes ID without virtual dispatch");idView.setId(123);check(idView.getId()==123&&idView.assignments()==1,"later setId remains virtual");
  Resources arrays=base.getResources();
  CharSequence[] choices=arrays.getTextArray(R.array.typed_choices_alias);
  check(choices.length==4&&"First".contentEquals(choices[0])&&"true".contentEquals(choices[1])&&choices[2]==null&&"حافظ".contentEquals(choices[3]),"Array values, reference, null and Unicode");
  choices[0]="changed";check("First".equals(arrays.getStringArray(R.array.typed_choices)[0]),"Array results independent");
  check(arrays.getTextArray(R.array.typed_empty_choices).length==0,"Empty array");
  Configuration saved=new Configuration(arrays.getConfiguration());
  try {
   check("1 track".equals(arrays.getQuantityString(R.plurals.typed_tracks,1,1))&&"0 tracks".equals(arrays.getQuantityString(R.plurals.typed_tracks,0,0)),"English one and other");
   Configuration french=new Configuration(saved);french.setLocale(Locale.FRENCH);arrays.updateConfiguration(french,null);
   check("0 track".equals(arrays.getQuantityString(R.plurals.typed_tracks,0,0)),"French zero uses one");
   Configuration russian=new Configuration(saved);russian.setLocale(new Locale("ru"));arrays.updateConfiguration(russian,null);
   check("2 few".equals(arrays.getQuantityString(R.plurals.typed_tracks,2,2))&&"5 many".equals(arrays.getQuantityString(R.plurals.typed_tracks,5,5))&&"21 track".equals(arrays.getQuantityString(R.plurals.typed_tracks,21,21)),"Russian few, many and one");
   Configuration arabic=new Configuration(saved);arabic.setLocale(new Locale("ar"));arrays.updateConfiguration(arabic,null);
   check("%d zero".contentEquals(arrays.getQuantityText(R.plurals.typed_tracks,0))&&"%d two".contentEquals(arrays.getQuantityText(R.plurals.typed_tracks,2))&&"%d tracks".contentEquals(arrays.getQuantityText(R.plurals.typed_tracks,100)),"Arabic zero, two and other");
   try{arrays.getQuantityString(0,1);throw new AssertionError("Missing plural accepted");}catch(Resources.NotFoundException expected){}
  } finally {arrays.updateConfiguration(saved,null);}
  TypedArray entries=base.obtainStyledAttributes(R.style.TypedArrayChoices,new int[]{android.R.attr.entries,android.R.attr.entryValues});
  check(entries.getTextArray(0).length==4&&entries.getTextArray(1)==null,"Styled text-array and absent attribute");entries.recycle();
  try{entries.getTextArray(0);throw new AssertionError("Recycled text array access");}catch(RuntimeException expected){}
  try{arrays.getTextArray(0);throw new AssertionError("Missing array accepted");}catch(Resources.NotFoundException expected){}
  Resources resources=base.getResources();final Resources.Theme theme=resources.newTheme();theme.applyStyle(R.style.TypedBase,true);
  Context context=new ContextWrapper(base){public Resources.Theme getTheme(){return theme;}};
  int[] ids={R.attr.typedCount,R.attr.typedFallback,R.attr.themeFlag,R.attr.typedEmpty,R.attr.typedText,R.attr.typedSize,R.attr.typedColor};
  TypedArray themeOnly=context.obtainStyledAttributes(ids);check(themeOnly.getInt(0,-1)==1&&themeOnly.getBoolean(2,false)&&themeOnly.getIndexCount()==3,"theme only values");themeOnly.recycle();
  for(int fallbackStyle:new int[]{R.attr.typedDefault,0x7fffffff}) {
   TypedArray fallback=context.obtainStyledAttributes(fallbackStyle,ids);
   check(fallback.getInt(0,-1)==1&&fallback.getBoolean(2,false),"non-style bag falls back to theme");fallback.recycle();
  }
  TypedArray style=context.obtainStyledAttributes(R.style.TypedOther,ids);check(style.getInt(0,0)==3&&style.getInt(1,0)==31&&style.getBoolean(2,false),"style over theme");style.recycle();
  XmlResourceParser xml=resources.getXml(R.xml.styled_attributes);while(xml.next()!=XmlResourceParser.START_TAG){}
  TypedArray a=context.obtainStyledAttributes(xml,ids,R.attr.typedDefault,R.style.TypedOther);
  check(a.getInt(0,0)==13&&a.getInteger(1,0)==21&&!a.getBoolean(2,true),"XML/default attribute precedence");
  check(!a.hasValue(3)&&a.hasValueOrEmpty(3)&&a.getType(3)==TypedValue.TYPE_NULL,"explicit empty masks theme");
  check("true".equals(a.getText(4))&&a.getResourceId(4,0)==R.string.service_meta_text,"text resource identity");
  check(a.getDimensionPixelSize(5,0)==Math.round(3*resources.getDisplayMetrics().density)&&a.getColor(6,0)==0xff112233,"dimensions and theme reference");
  check(a.getIndexCount()==7&&a.getIndex(6)==6&&a.length()==7,"defined indices");
  TypedValue copy=a.peekValue(0);copy.data=999;check(a.getInt(0,0)==13,"snapshot accessor isolation");
  try{a.getDimension(0,0);throw new AssertionError("type mismatch hidden");}catch(UnsupportedOperationException expected){}
  xml.close();check(a.getInt(0,0)==13,"snapshot survives XML close");a.recycle();
  try{a.hasValue(0);throw new AssertionError("recycled access");}catch(RuntimeException expected){}
  try{a.recycle();throw new AssertionError("double recycle");}catch(RuntimeException expected){}
  xml=resources.getXml(R.xml.styled_attributes);while(xml.next()!=XmlResourceParser.START_TAG){}
  TypedArray raw=resources.obtainAttributes(xml,ids);check(raw.getInt(0,0)==13&&!raw.hasValue(1)&&!raw.hasValue(2)&&raw.peekValue(6).type==TypedValue.TYPE_ATTRIBUTE&&raw.peekValue(6).data==R.attr.themeAccent,"unthemed attributes exclude styles");raw.recycle();xml.close();
  TypedArray unchanged=context.obtainStyledAttributes(ids);check(unchanged.getInt(0,0)==1&&unchanged.getBoolean(2,false),"query does not mutate base theme");unchanged.recycle();
  TypedArray bags=context.obtainStyledAttributes(new int[]{R.attr.typedDefault});
  check(bags.getResourceId(0,0)==R.style.TypedDefault&&bags.getType(0)==TypedValue.TYPE_REFERENCE&&bags.peekValue(0).data==R.style.TypedDefault,"theme style reference is preserved");bags.recycle();
  bags=context.obtainStyledAttributes(R.style.TypedBags,new int[]{R.attr.typedBagAlias,R.attr.typedBagArray,R.attr.typedParentBag});
  check(bags.getResourceId(0,0)==R.style.TypedDefault&&bags.getResourceId(1,0)==R.array.typed_numbers&&bags.getResourceId(2,0)==R.style.TypedPlatformParent,"aliased style and array references");
  try{bags.getInteger(0,0);throw new AssertionError("style treated as integer");}catch(UnsupportedOperationException expected){}bags.recycle();
  try{resources.getValue(R.style.TypedDefault,new TypedValue(),true);throw new AssertionError("scalar lookup accepted style");}catch(Resources.NotFoundException expected){}
  try{context.obtainStyledAttributes(R.style.TypedCycle,new int[]{R.attr.typedCycle});throw new AssertionError("cyclic attribute accepted");}catch(Resources.NotFoundException expected){}
  checkViews(base);
  System.out.println("SHIRAZ_FIXTURE_STYLED_ATTRIBUTES_OK");
 }
 private static void checkViews(Context base)throws Exception {
  final Resources.Theme theme=base.getResources().newTheme();theme.applyStyle(R.style.ConstructorTheme,true);
  Context context=new ContextWrapper(base){public Resources.Theme getTheme(){return theme;}};
  XmlResourceParser xml=context.getResources().getLayout(R.layout.constructor_style);while(xml.next()!=XmlResourceParser.START_TAG){}
  android.view.View view=new android.view.View(context,xml,R.attr.constructorDefault,R.style.ConstructorFallback);
  check(view.getPaddingLeft()==9&&view.getPaddingTop()==3&&view.getPaddingRight()==7&&view.getPaddingBottom()==2,"constructor attribute precedence");
  check(view.getImportantForAccessibility()==android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES,"styled accessibility importance");
  check(view.getId()==R.id.constructor_view&&!view.isEnabled()&&view.getBackground()==null,"ID, state and explicit null");
  check(view.getContentDescription().toString().equals("Inherited description"),"inherited XML style");
  android.widget.FrameLayout.LayoutParams params=new android.widget.FrameLayout.LayoutParams(context,xml);check(params.width==20&&params.height==30,"style layout dimensions");xml.close();
  android.view.View defaults=new android.view.View(context,null,R.attr.constructorDefault,R.style.ConstructorFallback);
  check(defaults.getPaddingLeft()==4&&defaults.getPaddingTop()==3&&defaults.getPaddingBottom()==2&&!defaults.isEnabled(),"default style without XML");
  defaults.layout(0,0,8,8);android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888);defaults.draw(new android.graphics.Canvas(bitmap));check(bitmap.getPixel(0,0)==0xff0000ff,"styled background pixels");bitmap.recycle();
  android.view.View fallback=new android.view.View(context,null,R.attr.constructorAbsent,R.style.ConstructorFallback);check(fallback.getPaddingTop()==99&&fallback.isEnabled(),"fallback default resource");
  android.widget.FrameLayout frame=new android.widget.FrameLayout(context,null,0,R.style.ConstructorFrame);check(frame.getMeasureAllChildren()&&frame.getForeground()!=null,"FrameLayout default attributes");
  android.widget.LinearLayout linear=new android.widget.LinearLayout(context,null,0,R.style.ConstructorLinear);check(linear.getOrientation()==1&&linear.getWeightSum()==2&&!linear.isBaselineAligned(),"LinearLayout default attributes");
  android.widget.ImageView image=new android.widget.ImageView(context,null,0,R.style.ConstructorImage);check(image.getDrawable()!=null&&image.getScaleType()==android.widget.ImageView.ScaleType.CENTER_INSIDE&&image.getMaxHeight()==42,"ImageView default attributes");
  check(image.getImageTintList().getDefaultColor()==0xff112233&&image.getImageTintMode()==android.graphics.PorterDuff.Mode.SRC_IN,"XML image tint");
  image.setPadding(0,0,0,0);image.layout(0,0,2,2);android.graphics.Bitmap tinted=android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888);image.draw(new android.graphics.Canvas(tinted));check(tinted.getPixel(0,0)==0xff112233,"XML tinted image pixels");tinted.recycle();
  android.widget.TextView text=new android.widget.TextView(context,null,0,R.style.ConstructorText);check(text.getText().toString().equals("Styled text")&&text.getTextSize()==18&&text.getCurrentTextColor()==0xff778899,"TextView default attributes");
  try{new android.view.View(context,null,0,R.style.ConstructorUnsupported);throw new AssertionError("Unsupported styled rotation ignored");}catch(UnsupportedOperationException expected){}
  TypedArray unchanged=context.obtainStyledAttributes(new int[]{android.R.attr.paddingBottom,android.R.attr.paddingTop});check(unchanged.getDimensionPixelSize(0,0)==2&&!unchanged.hasValue(1),"constructors mutated base theme");unchanged.recycle();
  System.out.println("VIEW_CONSTRUCTOR_STYLE_CASCADE_OK");
 }

}
