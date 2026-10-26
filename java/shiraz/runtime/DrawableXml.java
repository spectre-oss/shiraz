// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.res.*;
import android.graphics.drawable.*;
import android.util.TypedValue;
import java.util.Arrays;

/** Bounded layer-list/bitmap XML inflation. Other drawable tags reject explicitly. */
public final class DrawableXml {
    private final Resources resources; private final Resources.Theme theme; private int flags;
    private DrawableXml(Resources r,Resources.Theme t){resources=r;theme=t;}
    public static Drawable inflate(Resources r,Resources.Theme theme,byte[] xml){
        BinaryXml.Node root=BinaryXml.parse(xml);
        if(root.name.equals("vector"))return VectorDrawable.shirazInflate(r,theme,xml);
        DrawableXml loader=new DrawableXml(r,theme);Drawable d=loader.node(root,0);d.setChangingConfigurations(d.getChangingConfigurations()|loader.flags);return d;
    }
    private void attrs(BinaryXml.Node node,String allowed){for(BinaryXml.Attribute a:node.attributes)if(!BinaryXml.ANDROID.equals(a.namespace)||!Arrays.asList(allowed.split(" ")).contains(a.name))throw new UnsupportedOperationException("Drawable XML attribute "+a.name);}
    private BinaryXml.Value resolve(BinaryXml.Value v){
        if(v==null)return null;
        for(int n=0;n<32&&(v.type==1||v.type==2);n++){
            if(v.type==1&&v.data==0)return new BinaryXml.Value(0,0,null);
            TypedValue out=new TypedValue();if(v.type==2){if(theme==null||!theme.resolveAttribute(v.data,out,true))throw new Resources.NotFoundException("Drawable theme attribute");}else resources.getValue(v.data,out,true);
            flags|=out.changingConfigurations;v=new BinaryXml.Value(out.type,out.data,out.string==null?null:out.string.toString());
        }
        if(v.type==1||v.type==2)throw new IllegalArgumentException("Drawable reference depth");return v;
    }
    private int integer(BinaryXml.Node n,String key,int fallback){BinaryXml.Value v=resolve(n.android(key));if(v==null)return fallback;if(v.type<16||v.type>31)throw new IllegalArgumentException("Drawable integer "+key);return v.data;}
    private float percent(BinaryXml.Node node,String name){BinaryXml.Value v=resolve(node.android(name));if(v==null||v.type==0)return -1;if(v.type==6)return TypedValue.complexToFloat(v.data);if(v.type==3&&v.text!=null&&v.text.endsWith("%"))return Float.parseFloat(v.text.substring(0,v.text.length()-1))/100f;throw new IllegalArgumentException("Scale percentage "+name);}
    private int pixels(BinaryXml.Node n,String key,int fallback){BinaryXml.Value v=resolve(n.android(key));if(v==null)return fallback;if(v.type!=5)throw new IllegalArgumentException("Drawable dimension "+key);float value=TypedValue.complexToDimension(v.data,resources.getDisplayMetrics());if(!Float.isFinite(value)||Math.abs(value)>8192)throw new IllegalArgumentException("Drawable dimension bound");return Math.round(value);}
    private Drawable reference(BinaryXml.Value v){
        if(v==null)throw new IllegalArgumentException("Drawable reference required");
        if(v.type==1&&v.data!=0)return resources.getDrawable(v.data,theme);
        if(v.type==2){TypedValue out=new TypedValue();if(theme==null||!theme.resolveAttribute(v.data,out,true))throw new Resources.NotFoundException("Drawable theme reference");flags|=out.changingConfigurations;if(out.type==3&&out.resourceId!=0)return resources.getDrawable(out.resourceId,theme);v=new BinaryXml.Value(out.type,out.data,null);}
        if(v.type>=28&&v.type<=31)return new ColorDrawable(v.data);
        throw new UnsupportedOperationException("Drawable reference type");
    }
    private ColorStateList colors(BinaryXml.Value value){
        ColorStateList result=optionalColors(value);if(result==null)throw new IllegalArgumentException("Drawable requires color");return result;
    }
    private ColorStateList optionalColors(BinaryXml.Value value){
        if(value==null||value.type==0||(value.type==1&&value.data==0))return null;
        if(value.type==1&&value.data!=0)return resources.getColorStateList(value.data,theme);
        if(value.type==2){TypedValue out=new TypedValue();if(theme==null||!theme.resolveAttribute(value.data,out,true))throw new Resources.NotFoundException("Drawable theme color");flags|=out.changingConfigurations;if(out.type==3&&out.resourceId!=0)return resources.getColorStateList(out.resourceId,theme);value=new BinaryXml.Value(out.type,out.data,null);}
        if(value.type==0||(value.type==1&&value.data==0))return null;
        if(value.type>=28&&value.type<=31)return ColorStateList.valueOf(value.data);
        throw new IllegalArgumentException("Drawable color type");
    }
    private float decimal(BinaryXml.Node n,String key,float fallback){BinaryXml.Value v=resolve(n.android(key));if(v==null)return fallback;float value=v.type==4?Float.intBitsToFloat(v.data):v.type==6?TypedValue.complexToFloat(v.data):v.type>=16&&v.type<=31?v.data:Float.NaN;if(!Float.isFinite(value))throw new IllegalArgumentException("Drawable number "+key);return value;}
    private float dimension(BinaryXml.Node n,String key,float fallback){BinaryXml.Value v=resolve(n.android(key));if(v==null)return fallback;if(v.type!=5)throw new IllegalArgumentException("Drawable dimension "+key);float value=TypedValue.complexToDimension(v.data,resources.getDisplayMetrics());if(!Float.isFinite(value)||value<0||value>8192)throw new IllegalArgumentException("Shape dimension bound");return value;}
    private android.graphics.PorterDuff.Mode tintMode(BinaryXml.Node node){switch(integer(node,"tintMode",5)){case 3:return android.graphics.PorterDuff.Mode.SRC_OVER;case 5:return android.graphics.PorterDuff.Mode.SRC_IN;case 9:return android.graphics.PorterDuff.Mode.SRC_ATOP;case 14:return android.graphics.PorterDuff.Mode.MULTIPLY;case 15:return android.graphics.PorterDuff.Mode.SCREEN;case 16:return android.graphics.PorterDuff.Mode.ADD;default:throw new IllegalArgumentException("Drawable tint mode");}}
    private Drawable shape(BinaryXml.Node n){
        // Android reads angle/type only from <gradient>; misplaced root attributes have no effect.
        attrs(n,"shape dither visible tint tintMode angle type innerRadius innerRadiusRatio thickness thicknessRatio useLevel opticalInsetLeft opticalInsetTop opticalInsetRight opticalInsetBottom");GradientDrawable result=new GradientDrawable();if(n.android("tint")!=null)result.setTintList(optionalColors(n.android("tint")));result.setTintMode(tintMode(n));result.setShape(integer(n,"shape",0));if(result.getShape()==GradientDrawable.RING){if(n.android("innerRadius")!=null)result.setInnerRadius(pixels(n,"innerRadius",0));else result.setInnerRadiusRatio(decimal(n,"innerRadiusRatio",3));if(n.android("thickness")!=null)result.setThickness(pixels(n,"thickness",0));else result.setThicknessRatio(decimal(n,"thicknessRatio",9));result.shirazSetUseLevelForShape(integer(n,"useLevel",1)!=0);}result.setDither(integer(n,"dither",0)!=0);result.setVisible(integer(n,"visible",1)!=0,true);java.util.HashSet<String> seen=new java.util.HashSet<String>();
        for(BinaryXml.Node child:n.children){if(!child.namespace.isEmpty()||!child.children.isEmpty()||!seen.add(child.name))throw new IllegalArgumentException("Duplicate or nested shape property");
            if(child.name.equals("solid")){attrs(child,"color");ColorStateList solid=optionalColors(child.android("color"));if(solid!=null)result.setColor(solid);}
            // Android GradientDrawableStroke reads width/color/dash only; radius here is ignored.
            else if(child.name.equals("stroke")){attrs(child,"width color dashWidth dashGap radius");result.setStroke(pixels(child,"width",0),optionalColors(child.android("color")),dimension(child,"dashWidth",0),dimension(child,"dashGap",0));}
            else if(child.name.equals("corners")){attrs(child,"radius topLeftRadius topRightRadius bottomLeftRadius bottomRightRadius");float radius=dimension(child,"radius",0);boolean separate=child.android("topLeftRadius")!=null||child.android("topRightRadius")!=null||child.android("bottomLeftRadius")!=null||child.android("bottomRightRadius")!=null;if(separate){float tl=dimension(child,"topLeftRadius",radius),tr=dimension(child,"topRightRadius",radius),bl=dimension(child,"bottomLeftRadius",radius),br=dimension(child,"bottomRightRadius",radius);result.setCornerRadii(new float[]{tl,tl,tr,tr,br,br,bl,bl});}else result.setCornerRadius(radius);}
            else if(child.name.equals("size")){attrs(child,"width height");result.setSize(pixels(child,"width",-1),pixels(child,"height",-1));}
            else if(child.name.equals("padding")){attrs(child,"left top right bottom");result.shirazSetPadding(pixels(child,"left",0),pixels(child,"top",0),pixels(child,"right",0),pixels(child,"bottom",0));}
            else if(child.name.equals("gradient")){attrs(child,"startColor centerColor endColor type angle centerX centerY gradientRadius useLevel dither");int type=integer(child,"type",0);result.setGradientType(type);float cx=decimal(child,"centerX",.5f),cy=decimal(child,"centerY",.5f);result.setGradientCenter(cx,cy);result.setUseLevel(integer(child,"useLevel",0)!=0);int start=colors(child.android("startColor")).getDefaultColor(),end=colors(child.android("endColor")).getDefaultColor();if(child.android("centerColor")!=null)result.setColors(new int[]{start,colors(child.android("centerColor")).getDefaultColor(),end},new float[]{0,cx!=.5f?cx:cy,1});else result.setColors(new int[]{start,end});
                if(type==0){float angle=decimal(child,"angle",0);if(angle%45!=0)throw new IllegalArgumentException("Linear gradient angle must be a multiple of 45");int a=(int)(((angle%360)+360)%360);GradientDrawable.Orientation[] directions={GradientDrawable.Orientation.LEFT_RIGHT,GradientDrawable.Orientation.BL_TR,GradientDrawable.Orientation.BOTTOM_TOP,GradientDrawable.Orientation.BR_TL,GradientDrawable.Orientation.RIGHT_LEFT,GradientDrawable.Orientation.TR_BL,GradientDrawable.Orientation.TOP_BOTTOM,GradientDrawable.Orientation.TL_BR};result.setOrientation(directions[a/45]);}
                BinaryXml.Value radius=resolve(child.android("gradientRadius"));if(radius!=null){if(radius.type==6)result.shirazSetGradientRadiusFraction(TypedValue.complexToFloat(radius.data),(radius.data&15)==1);else result.setGradientRadius(radius.type==5?dimension(child,"gradientRadius",0):decimal(child,"gradientRadius",0));}else if(type==1)throw new IllegalArgumentException("Radial gradient radius required");
            }else throw new UnsupportedOperationException("Shape property "+child.name);
        }return result;
    }
    // Android's pull parser discovers targets at every depth and ignores wrapper tags.
    private void vectorTargets(BinaryXml.Node node,int depth,java.util.List<BinaryXml.Node> targets){
        if(depth>32)throw new IllegalArgumentException("Animated vector XML depth");
        for(BinaryXml.Node child:node.children){
            resources.shirazClaimDrawableNode();
            if(child.name.equals("target")&&child.namespace.isEmpty()){
                if(targets.size()>=256)throw new IllegalArgumentException("Animated vector target limit");
                targets.add(child);
            }
            vectorTargets(child,depth+1,targets);
        }
    }
    private Drawable node(BinaryXml.Node n,int depth){
        resources.shirazClaimDrawableNode();
        if(depth>32||!n.namespace.isEmpty())throw new IllegalArgumentException("Drawable XML depth/namespace");
        if(n.name.equals("animated-vector")){
            attrs(n,"drawable");Drawable source=reference(n.android("drawable"));if(!(source instanceof VectorDrawable))throw new IllegalArgumentException("Animated vector source must be vector");AnimatedVectorDrawable result=new AnimatedVectorDrawable((VectorDrawable)source);
            final Resources.Theme animatorTheme=theme==null?resources.newTheme():theme;android.content.Context context=new android.content.ContextWrapper(null){public Resources getResources(){return resources;}public Resources.Theme getTheme(){return animatorTheme;}};
            java.util.List<BinaryXml.Node> targets=new java.util.ArrayList<BinaryXml.Node>();vectorTargets(n,depth,targets);
            for(BinaryXml.Node target:targets){attrs(target,"name animation");BinaryXml.Value name=resolve(target.android("name")),animation=target.android("animation");if(name==null||name.type!=3||name.text==null||animation==null)throw new IllegalArgumentException("Animated vector target attributes");int id;if(animation.type==1)id=animation.data;else if(animation.type==2){TypedValue out=new TypedValue();if(theme==null||!theme.resolveAttribute(animation.data,out,true))throw new Resources.NotFoundException("Animator theme reference");id=out.resourceId;flags|=out.changingConfigurations;}else throw new IllegalArgumentException("Animator resource reference");try{result.shirazAddTarget(name.text,android.animation.AnimatorInflater.loadAnimator(context,id));}catch(Resources.NotFoundException failure){System.err.println("W/AnimatedVector: animation for target "+name.text+" is not supported, drawing it unanimated: "+failure.getCause());}}return result;
        }
        if(n.name.equals("shape"))return shape(n);
        if(n.name.equals("color")){attrs(n,"color");if(!n.children.isEmpty())throw new IllegalArgumentException("Color drawable children");return new ColorDrawable(n.android("color")==null?0:colors(n.android("color")).getDefaultColor());}
        if(n.name.equals("nine-patch")){
            attrs(n,"src dither autoMirrored alpha tint tintMode visible");if(!n.children.isEmpty())throw new IllegalArgumentException("Nine-patch drawable children");
            Drawable source=reference(n.android("src"));if(!(source instanceof NinePatchDrawable))throw new IllegalArgumentException("Nine-patch src must reference a compiled nine-patch");
            NinePatchDrawable result=((NinePatchDrawable)source).shirazCopySource(resources);result.setDither(integer(n,"dither",1)!=0);result.setAutoMirrored(integer(n,"autoMirrored",0)!=0);result.shirazSetBaseAlpha(decimal(n,"alpha",1));result.setVisible(integer(n,"visible",1)!=0,true);
            if(n.android("tint")!=null)result.setTintList(colors(n.android("tint")));
            result.setTintMode(tintMode(n));return result;
        }
        if(n.name.equals("bitmap")){
            attrs(n,"src gravity filter antialias tint tintMode");if(!n.children.isEmpty())throw new IllegalArgumentException("Bitmap drawable children");
            Drawable source=reference(n.android("src"));if(!(source instanceof BitmapDrawable))throw new IllegalArgumentException("Bitmap src must reference a bitmap");
            BitmapDrawable d=new BitmapDrawable(resources,((BitmapDrawable)source).getBitmap());d.setChangingConfigurations(source.getChangingConfigurations());d.setGravity(integer(n,"gravity",119));d.setFilterBitmap(integer(n,"filter",1)!=0);d.setAntiAlias(integer(n,"antialias",0)!=0);if(n.android("tint")!=null)d.setTintList(colors(n.android("tint")));d.setTintMode(tintMode(n));return d;
        }
        if(n.name.equals("com.android.internal.graphics.drawable.AnimationScaleListDrawable")){
            attrs(n,"");if(n.children.size()>256)throw new IllegalArgumentException("Scale drawable child limit");com.android.internal.graphics.drawable.AnimationScaleListDrawable result=new com.android.internal.graphics.drawable.AnimationScaleListDrawable();
            for(BinaryXml.Node item:n.children){if(!item.name.equals("item")||!item.namespace.isEmpty())throw new IllegalArgumentException("Scale drawable item");attrs(item,"drawable");BinaryXml.Value ref=item.android("drawable");Drawable child;if(ref!=null){if(!item.children.isEmpty())throw new IllegalArgumentException("Duplicate scale drawable");child=reference(ref);}else{if(item.children.size()!=1)throw new IllegalArgumentException("Scale item requires one drawable");child=node(item.children.get(0),depth+1);}result.shirazAddDrawable(child);}return result;
        }
        if(n.name.equals("animation-list")){
            attrs(n,"visible oneshot variablePadding constantSize dither");AnimationDrawable result=new AnimationDrawable();
            result.setOneShot(integer(n,"oneshot",0)!=0);result.shirazSetSizing(integer(n,"variablePadding",0)!=0,integer(n,"constantSize",0)!=0);
            for(BinaryXml.Node item:n.children){
                if(!item.name.equals("item")||!item.namespace.isEmpty())throw new IllegalArgumentException("Animation frame item");
                attrs(item,"duration drawable");int duration=integer(item,"duration",-1);if(duration<0)throw new IllegalArgumentException("Animation duration required");
                BinaryXml.Value ref=item.android("drawable");Drawable child;
                if(ref!=null){if(!item.children.isEmpty())throw new IllegalArgumentException("Duplicate animation drawable");child=reference(ref);}
                else{if(item.children.size()!=1)throw new IllegalArgumentException("Animation frame requires one drawable");child=node(item.children.get(0),depth+1);}
                result.addFrame(child,duration);
            }
            result.setVisible(integer(n,"visible",1)!=0,true);return result;
        }
        if(n.name.equals("selector")||n.name.equals("animated-selector")){
            boolean animated=n.name.equals("animated-selector");
            // Width/height are not StateListDrawable styleable fields in Android; children determine size.
            attrs(n,"visible variablePadding constantSize dither enterFadeDuration exitFadeDuration autoMirrored width height");
            if(n.children.size()>256)throw new IllegalArgumentException("Selector child limit");
            StateListDrawable result=animated?new AnimatedStateListDrawable():new StateListDrawable();result.shirazSetSizing(integer(n,"variablePadding",0)!=0,integer(n,"constantSize",0)!=0);result.setEnterFadeDuration(integer(n,"enterFadeDuration",0));result.setExitFadeDuration(integer(n,"exitFadeDuration",0));
            for(BinaryXml.Node item:n.children){
                if(animated&&item.name.equals("transition")&&item.namespace.isEmpty()){
                    attrs(item,"fromId toId drawable reversible");BinaryXml.Value from=item.android("fromId"),to=item.android("toId"),ref=item.android("drawable");
                    if(from==null||to==null||from.type!=1||to.type!=1||from.data==0||to.data==0)throw new IllegalArgumentException("Transition state IDs");
                    Drawable child;if(ref!=null){if(!item.children.isEmpty())throw new IllegalArgumentException("Duplicate transition drawable");child=reference(ref);}else{if(item.children.size()!=1)throw new IllegalArgumentException("Transition drawable");child=node(item.children.get(0),depth+1);}
                    if(!(child instanceof Animatable))throw new IllegalArgumentException("Transition must animate");
                    if(child instanceof AnimationDrawable)((AnimatedStateListDrawable)result).addTransition(from.data,to.data,(AnimationDrawable)child,integer(item,"reversible",0)!=0);
                    else if(child instanceof AnimatedVectorDrawable)((AnimatedStateListDrawable)result).addTransition(from.data,to.data,(AnimatedVectorDrawable)child,integer(item,"reversible",0)!=0);
                    else throw new UnsupportedOperationException("State transition animation type");continue;
                }
                if(!item.name.equals("item")||!item.namespace.isEmpty())throw new IllegalArgumentException("Selector item");
                int[] states=new int[item.attributes.size()];int count=0;
                for(BinaryXml.Attribute a:item.attributes){
                    if(BinaryXml.ANDROID.equals(a.namespace)&&(a.name.equals("drawable")||a.name.equals("id")))continue;
                    if(a.resourceId==0)throw new IllegalArgumentException("Selector state resource ID");
                    BinaryXml.Value value=resolve(a.value);if(value.type!=18)throw new IllegalArgumentException("Selector state boolean");states[count++]=value.data!=0?a.resourceId:-a.resourceId;
                }
                BinaryXml.Value ref=item.android("drawable");Drawable child;
                if(ref!=null){if(!item.children.isEmpty())throw new IllegalArgumentException("Duplicate selector drawable");child=reference(ref);}else{if(item.children.size()!=1)throw new IllegalArgumentException("Selector requires one drawable");child=node(item.children.get(0),depth+1);}
                if(animated){BinaryXml.Value id=item.android("id");if(id!=null&&id.type!=1)throw new IllegalArgumentException("State ID");((AnimatedStateListDrawable)result).addState(Arrays.copyOf(states,count),child,id==null?0:id.data);}else result.addState(Arrays.copyOf(states,count),child);
            }
            result.setVisible(integer(n,"visible",1)!=0,true);result.setAutoMirrored(integer(n,"autoMirrored",0)!=0);return result;
        }
        if(n.name.equals("inset")){
            attrs(n,"drawable visible inset insetLeft insetTop insetRight insetBottom");BinaryXml.Value ref=n.android("drawable");Drawable child;
            if(ref!=null){if(!n.children.isEmpty())throw new IllegalArgumentException("Duplicate inset drawable");child=reference(ref);}else{if(n.children.size()!=1)throw new IllegalArgumentException("Inset requires one drawable");child=node(n.children.get(0),depth+1);}
            int[] dimensions=new int[4];float[] fractions=new float[4];String[] names={"insetLeft","insetTop","insetRight","insetBottom"};
            for(int i=0;i<4;i++){BinaryXml.Value value=resolve(n.android(names[i])==null?n.android("inset"):n.android(names[i]));if(value!=null&&n.android(names[i])!=null&&value.type==5&&TypedValue.complexToDimensionPixelOffset(value.data,resources.getDisplayMetrics())==0)value=resolve(n.android("inset"));if(value==null)continue;if(value.type==6)fractions[i]=TypedValue.complexToFloat(value.data);else if(value.type==5){float dimension=TypedValue.complexToDimension(value.data,resources.getDisplayMetrics());if(!Float.isFinite(dimension)||Math.abs(dimension)>8192)throw new IllegalArgumentException("Inset dimension bound");dimensions[i]=(int)dimension;}else if(value.type>=16&&value.type<=31&&value.data==0)dimensions[i]=0;else throw new IllegalArgumentException("Inset requires dimension or fraction");}
            InsetDrawable result=InsetDrawable.shirazCreate(child,dimensions,fractions);result.setVisible(integer(n,"visible",1)!=0,true);return result;
        }
        if(n.name.equals("scale")){
            attrs(n,"drawable scaleWidth scaleHeight scaleGravity useIntrinsicSizeAsMinimum level visible");BinaryXml.Value ref=n.android("drawable");Drawable child;
            if(ref!=null){if(!n.children.isEmpty())throw new IllegalArgumentException("Duplicate scale drawable");child=reference(ref);}else{if(n.children.size()!=1)throw new IllegalArgumentException("Scale requires one drawable");child=node(n.children.get(0),depth+1);}
            ScaleDrawable result=new ScaleDrawable(child,integer(n,"scaleGravity",3),percent(n,"scaleWidth"),percent(n,"scaleHeight"));result.shirazSetIntrinsicMinimum(integer(n,"useIntrinsicSizeAsMinimum",0)!=0);result.setLevel(integer(n,"level",0));result.setVisible(integer(n,"visible",1)!=0,true);return result;
        }
        if(n.name.equals("clip")){
            attrs(n,"drawable clipOrientation gravity");BinaryXml.Value ref=n.android("drawable");Drawable child;
            if(ref!=null){if(!n.children.isEmpty())throw new IllegalArgumentException("Duplicate clip drawable");child=reference(ref);}else{if(n.children.size()!=1)throw new IllegalArgumentException("Clip requires one drawable");child=node(n.children.get(0),depth+1);}
            return new ClipDrawable(child,integer(n,"gravity",3),integer(n,"clipOrientation",1));
        }
        boolean ripple=n.name.equals("ripple"),transition=n.name.equals("transition");
        if(!ripple&&!transition&&!n.name.equals("layer-list"))throw new UnsupportedOperationException("Drawable XML element "+n.name);
        attrs(n,ripple?"paddingMode color radius":"paddingMode");if(n.children.size()>256)throw new IllegalArgumentException("Layer count");Drawable[] children=new Drawable[n.children.size()];
        for(int i=0;i<children.length;i++){
            BinaryXml.Node item=n.children.get(i);if(!item.name.equals("item")||!item.namespace.isEmpty())throw new IllegalArgumentException("Layer item");attrs(item,"id drawable left top right bottom start end width height gravity scaleType");
            BinaryXml.Value ref=item.android("drawable");if(ref!=null){if(!item.children.isEmpty())throw new IllegalArgumentException("Duplicate layer drawable");children[i]=reference(ref);}else{if(item.children.size()!=1)throw new IllegalArgumentException("Layer requires one drawable");children[i]=node(item.children.get(0),depth+1);}
        }
        LayerDrawable result=ripple?RippleDrawable.shirazCreate(colors(n.android("color")),children):transition?new TransitionDrawable(children):new LayerDrawable(children);result.setPaddingMode(integer(n,"paddingMode",ripple?1:0));if(ripple)((RippleDrawable)result).setRadius(pixels(n,"radius",-1));
        for(int i=0;i<children.length;i++){
            BinaryXml.Node item=n.children.get(i);BinaryXml.Value id=item.android("id");if(id!=null){if(id.type!=1)throw new IllegalArgumentException("Layer ID");result.setId(i,id.data);}
            result.setLayerInset(i,pixels(item,"left",0),pixels(item,"top",0),pixels(item,"right",0),pixels(item,"bottom",0));
            if(item.android("start")!=null||item.android("end")!=null)result.setLayerInsetRelative(i,pixels(item,"start",Integer.MIN_VALUE),pixels(item,"top",0),pixels(item,"end",Integer.MIN_VALUE),pixels(item,"bottom",0));
            result.setLayerSize(i,pixels(item,"width",-1),pixels(item,"height",-1));result.setLayerGravity(i,integer(item,"gravity",0));
        }
        return result;
    }
}
