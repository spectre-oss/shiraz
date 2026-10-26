// SPDX-License-Identifier: GPL-3.0-only
package android.content.res;
import android.util.TypedValue;
/** Immutable resolved attribute snapshot with Android's explicit recycle lifetime. */
public class TypedArray {
 private final Resources resources; private final Resources.Theme theme;
 private TypedValue[] values; private final String position;
 TypedArray(Resources resources,Resources.Theme theme,TypedValue[] values,String position){this.resources=resources;this.theme=theme;this.values=values;this.position=position;}
 private void live(){if(values==null)throw new RuntimeException("TypedArray has been recycled");}
 private TypedValue value(int index){live();if(index<0||index>=values.length)throw new IndexOutOfBoundsException("Attribute index");return values[index];}
 private boolean present(TypedValue v){return v!=null&&v.type!=TypedValue.TYPE_NULL;}
 private RuntimeException mismatch(int index){return new UnsupportedOperationException("Attribute "+index+" has incompatible type at "+position);}
 public int length(){live();return values.length;}
 public Resources getResources(){live();return resources;}
 public String getPositionDescription(){live();return position;}
 public int getIndexCount(){live();int count=0;for(TypedValue v:values)if(present(v)||v!=null&&v.data==TypedValue.DATA_NULL_EMPTY)count++;return count;}
 public int getIndex(int at){live();if(at<0)throw new IndexOutOfBoundsException();for(int i=0;i<values.length;i++)if(present(values[i])||values[i]!=null&&values[i].data==TypedValue.DATA_NULL_EMPTY)if(at--==0)return i;throw new IndexOutOfBoundsException();}
 public boolean hasValue(int index){return present(value(index));}
 public boolean hasValueOrEmpty(int index){TypedValue v=value(index);return present(v)||v!=null&&v.data==TypedValue.DATA_NULL_EMPTY;}
 public int getType(int index){TypedValue v=value(index);return v==null?0:v.type;}
 public boolean getValue(int index,TypedValue out){if(out==null)throw new NullPointerException();TypedValue v=value(index);if(!present(v))return false;out.setTo(v);return true;}
 public TypedValue shirazPeekValue(int index){TypedValue value=value(index);if(value==null)return null;TypedValue copy=new TypedValue();copy.setTo(value);return copy;}
 public TypedValue peekValue(int index){TypedValue out=new TypedValue();return getValue(index,out)?out:null;}
 public CharSequence getText(int index){TypedValue v=value(index);return present(v)?v.coerceToString():null;}
 public CharSequence[] getTextArray(int index){TypedValue v=value(index);return present(v)?resources.getTextArray(v.resourceId):null;}
 public int[] getIntArray(int index){TypedValue v=value(index);return present(v)&&v.resourceId!=0?resources.getIntArray(v.resourceId):null;}
 public String getString(int index){CharSequence text=getText(index);return text==null?null:text.toString();}
 public String getNonResourceString(int index){TypedValue v=value(index);return v!=null&&v.type==3&&v.assetCookie<0?v.string.toString():null;}
 public boolean getBoolean(int index,boolean fallback){TypedValue v=value(index);if(!present(v))return fallback;if(v.type>=16&&v.type<=31)return v.data!=0;if(v.type==3){String s=v.string.toString();if(s.equals("true")||s.equals("TRUE")||s.equals("1"))return true;if(s.equals("false")||s.equals("FALSE")||s.equals("0"))return false;return fallback;}throw mismatch(index);}
 public int getInt(int index,int fallback){TypedValue v=value(index);if(!present(v))return fallback;if(v.type>=16&&v.type<=31)return v.data;if(v.type==3)return Integer.decode(v.string.toString());throw mismatch(index);}
 public int getInteger(int index,int fallback){TypedValue v=value(index);if(!present(v))return fallback;if(v.type>=16&&v.type<=31)return v.data;throw mismatch(index);}
 public float getFloat(int index,float fallback){TypedValue v=value(index);if(!present(v))return fallback;if(v.type==4)return v.getFloat();if(v.type>=16&&v.type<=31)return v.data;if(v.type==3)return Float.parseFloat(v.string.toString());throw mismatch(index);}
 public int getResourceId(int index,int fallback){TypedValue v=value(index);return present(v)&&v.resourceId!=0?v.resourceId:fallback;}
 public int getThemeAttributeId(int index,int fallback){TypedValue v=value(index);return v!=null&&v.type==2?v.data:fallback;}
 public float getDimension(int index,float fallback){TypedValue v=value(index);if(!present(v))return fallback;if(v.type!=5)throw mismatch(index);return v.getDimension(resources.getDisplayMetrics());}
 public int getDimensionPixelOffset(int index,int fallback){TypedValue v=value(index);if(!present(v))return fallback;if(v.type!=5)throw mismatch(index);return TypedValue.complexToDimensionPixelOffset(v.data,resources.getDisplayMetrics());}
 public int getDimensionPixelSize(int index,int fallback){TypedValue v=value(index);if(!present(v))return fallback;if(v.type!=5)throw mismatch(index);return TypedValue.complexToDimensionPixelSize(v.data,resources.getDisplayMetrics());}
 public int getLayoutDimension(int index,int fallback){TypedValue v=value(index);if(!present(v))return fallback;if(v.type>=16&&v.type<=31)return v.data;if(v.type==5)return getDimensionPixelSize(index,fallback);throw mismatch(index);}
 public int getLayoutDimension(int index,String name){if(!hasValue(index))throw new RuntimeException("Missing layout dimension "+name+" at "+position);return getLayoutDimension(index,0);}
 public float getFraction(int index,int base,int parentBase,float fallback){TypedValue v=value(index);if(!present(v))return fallback;if(v.type!=6)throw mismatch(index);return v.getFraction(base,parentBase);}
 public ColorStateList getColorStateList(int index){TypedValue v=value(index);if(!present(v))return null;if(v.isColorType())return ColorStateList.valueOf(v.data);if(v.resourceId!=0)return resources.getColorStateList(v.resourceId,theme);throw mismatch(index);}
 public int getColor(int index,int fallback){TypedValue v=value(index);if(v!=null&&v.type>=16&&v.type<=31)return v.data;ColorStateList color=getColorStateList(index);return color==null?fallback:color.getDefaultColor();}
 public android.graphics.drawable.Drawable getDrawable(int index){TypedValue v=value(index);if(!present(v))return null;if(v.isColorType())return new android.graphics.drawable.ColorDrawable(v.data);if(v.resourceId!=0)return resources.getDrawable(v.resourceId,theme);throw mismatch(index);}
 public int getChangingConfigurations(){live();int flags=0;for(TypedValue v:values)if(v!=null)flags|=v.changingConfigurations;return flags;}
 public void recycle(){live();values=null;}
}
