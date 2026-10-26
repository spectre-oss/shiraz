// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.Context;
import android.content.res.*;
import android.util.*;
import android.graphics.drawable.*;
/** Constructor attributes resolved through the shared theme/style cascade. */
public final class XmlAttributes {
    private final Context context;private final BinaryXml.Node node;private final AttributeSet attrs;
    private final int defAttr,defRes;private final boolean active;
    private final java.util.HashMap<String,TypedValue> cache=new java.util.HashMap<String,TypedValue>();
    public XmlAttributes(Context context,AttributeSet attrs){this(context,attrs,0,0);}
    public XmlAttributes(Context context,AttributeSet attrs,int defAttr,int defRes){
        this.context=context;this.attrs=attrs;this.defAttr=defAttr;this.defRes=defRes;active=attrs!=null||defAttr!=0||defRes!=0;
        if(attrs==null)node=null;else if(attrs instanceof CompiledXmlParser)node=((CompiledXmlParser)attrs).shirazNode();else throw new UnsupportedOperationException("Only compiled resource attributes are implemented");
    }
    /** Resolve constructor styleables together, as Android View/TextView do. */
    public void prefetch(String[] names){
        if(!active)return;if(names.length>256)throw new IllegalArgumentException("Constructor attribute limit");
        int[] ids=new int[names.length];for(int i=0;i<names.length;i++){ids[i]=FrameworkAttributes.id(names[i]);if(ids[i]==0)ids[i]=context.getResources().getIdentifier(names[i],"attr","android");}
        TypedArray array=context.obtainStyledAttributes(attrs,ids,defAttr,defRes);
        try{for(int i=0;i<names.length;i++)if(ids[i]!=0)cache.put(names[i],array.shirazPeekValue(i));}finally{array.recycle();}
    }
    private TypedValue selected(String name){
        if(!active)return null;if(cache.containsKey(name))return cache.get(name);if(cache.size()>=256)throw new IllegalArgumentException("Constructor attribute limit");
        int id=FrameworkAttributes.id(name);if(id==0)id=context.getResources().getIdentifier(name,"attr","android");
        if(id==0&&node!=null)for(BinaryXml.Attribute attr:node.attributes)if(BinaryXml.ANDROID.equals(attr.namespace)&&name.equals(attr.name)){id=attr.resourceId;break;}
        TypedValue value=null;
        if(id!=0){TypedArray array=context.obtainStyledAttributes(attrs,new int[]{id},defAttr,defRes);try{value=array.shirazPeekValue(0);}finally{array.recycle();}}
        else if(node!=null){BinaryXml.Value raw=node.android(name);if(raw!=null){value=new TypedValue();value.type=raw.type;value.data=raw.data;value.string=raw.text;}}
        cache.put(name,value);return value;
    }
    public BinaryXml.Value raw(String name){TypedValue value=selected(name);return value==null?null:new BinaryXml.Value(value.type,value.data,value.string==null?null:value.string.toString());}
    public boolean has(String name){return selected(name)!=null;}
    private BinaryXml.Value resolve(BinaryXml.Value value){for(int i=0;i<32&&value!=null&&(value.type==1||value.type==2);i++){if(value.type==1&&value.data==0)return new BinaryXml.Value(0,0,null);TypedValue out=new TypedValue();if(value.type==2){Resources.Theme theme=context.getTheme();if(theme==null||!theme.resolveAttribute(value.data,out,true))throw new Resources.NotFoundException("XML theme attribute");}else context.getResources().getValue(value.data,out,true);value=new BinaryXml.Value(out.type,out.data,out.string==null?null:out.string.toString());}if(value!=null&&(value.type==1||value.type==2))throw new IllegalArgumentException("XML attribute reference depth");return value;}
    public int integer(String name,int fallback){BinaryXml.Value value=resolve(raw(name));if(value==null||value.type==0)return fallback;if(value.type<16||value.type>31)throw new IllegalArgumentException("XML integer "+name);return value.data;}
    public boolean bool(String name,boolean fallback){return integer(name,fallback?1:0)!=0;}
    public float number(String name,float fallback){BinaryXml.Value v=resolve(raw(name));if(v==null||v.type==0)return fallback;float result=v.type==4?Float.intBitsToFloat(v.data):v.type>=16&&v.type<=31?v.data:Float.NaN;if(!Float.isFinite(result))throw new IllegalArgumentException("XML number "+name);return result;}
    public float dimensionFloat(String name,float fallback){BinaryXml.Value v=resolve(raw(name));if(v==null||v.type==0)return fallback;if(v.type==5){float pixels=TypedValue.complexToDimension(v.data,context.getResources().getDisplayMetrics());if(!Float.isFinite(pixels)||Math.abs(pixels)>8192)throw new IllegalArgumentException("XML dimension bound");return pixels;}if(v.type>=16&&v.type<=31&&v.data==0)return 0;throw new IllegalArgumentException("XML dimension "+name);}
    public int dimension(String name,int fallback,boolean layout){BinaryXml.Value v=resolve(raw(name));if(v==null||v.type==0)return fallback;if(v.type==5){float pixels=TypedValue.complexToDimension(v.data,context.getResources().getDisplayMetrics());if(!Float.isFinite(pixels)||Math.abs(pixels)>8192)throw new IllegalArgumentException("XML dimension bound");return TypedValue.complexToDimensionPixelSize(v.data,context.getResources().getDisplayMetrics());}if(v.type>=16&&v.type<=31&&(v.data==0||layout&&(v.data==-1||v.data==-2)))return v.data;throw new IllegalArgumentException("XML dimension "+name);}
    public int reference(String name,int fallback){TypedValue value=selected(name);if(value==null||value.type==0)return fallback;if(value.resourceId!=0)return value.resourceId;if(value.type==1)return value.data;throw new IllegalArgumentException("XML resource reference "+name);}
    public String text(String name){BinaryXml.Value value=resolve(raw(name));return value==null||value.type==0?null:value.type==3?value.text:TypedValue.coerceToString(value.type,value.data);}
    public Drawable drawable(String name){TypedValue value=selected(name);if(value==null||value.type==0||value.type==1&&value.data==0)return null;if(value.isColorType())return new ColorDrawable(value.data);if(value.resourceId!=0)return context.getResources().getDrawable(value.resourceId,context.getTheme());BinaryXml.Value v=resolve(raw(name));if(v==null||v.type==0)return null;if(v.type>=28&&v.type<=31)return new ColorDrawable(v.data);throw new UnsupportedOperationException("XML drawable attribute "+name);}
    public void reject(String names){for(String name:names.split(" ")){TypedValue value=selected(name);if(value!=null&&value.type!=TypedValue.TYPE_NULL)throw new UnsupportedOperationException("XML attribute is not implemented: "+name);}}
}
