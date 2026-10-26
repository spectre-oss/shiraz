// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.*;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import java.util.ArrayList;

/** Software text widget. Visual glyph clusters are shared with native shaping. */
public class TextView extends View {
    private boolean showSoftInputOnFocus=true;
    public void setShowSoftInputOnFocus(boolean value){showSoftInputOnFocus=value;}
    public boolean getShowSoftInputOnFocus(){return showSoftInputOnFocus;}

    private boolean freezesText;
    public void setFreezesText(boolean value){freezesText=value;}
    public boolean getFreezesText(){return freezesText;}
    /** In-process text snapshot; transient buffer observers are not copied. */
    public static class SavedState extends View.BaseSavedState {
        private final CharSequence text;private final int start,end;
        private SavedState(android.os.Parcelable parent,CharSequence text,int start,int end){super(parent);this.text=text;this.start=start;this.end=end;}
    }
    @Override public android.os.Parcelable onSaveInstanceState(){
        android.os.Parcelable parent=super.onSaveInstanceState();int start=getSelectionStart(),end=getSelectionEnd();
        if(!getFreezesText()&&start<0&&end<0)return parent;
        CharSequence snapshot=null;if(getFreezesText())snapshot=text instanceof Spanned?new SpannedString(new SpannableStringBuilder(text)):text.toString();
        return new SavedState(parent,snapshot,start,end);
    }
    @Override public void onRestoreInstanceState(android.os.Parcelable state){
        if(!(state instanceof SavedState)){super.onRestoreInstanceState(state);return;}
        SavedState saved=(SavedState)state;super.onRestoreInstanceState(saved.getSuperState());if(saved.text!=null)setText(saved.text);
        if(text instanceof Spannable&&saved.start>=0&&saved.end>=0&&saved.start<=text.length()&&saved.end<=text.length())Selection.setSelection((Spannable)text,saved.start,saved.end);
    }
    public enum BufferType { NORMAL, SPANNABLE, EDITABLE }
    private final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private CharSequence text="";
    private BufferType bufferType=BufferType.NORMAL;
    private Editable.Factory editableFactory=Editable.Factory.getInstance();
    private Spannable.Factory spannableFactory=Spannable.Factory.getInstance();
    public final void setEditableFactory(Editable.Factory factory){if(factory==null)throw new NullPointerException("editable factory");editableFactory=factory;setText(text);}
    public final void setSpannableFactory(Spannable.Factory factory){if(factory==null)throw new NullPointerException("spannable factory");spannableFactory=factory;setText(text);}

    private String hint;
    private final ArrayList<TextWatcher> watchers=new ArrayList<TextWatcher>();
    private InputFilter[] filters=new InputFilter[0];
    private BufferObserver observer;
    private android.text.method.TransformationMethod transformation;
    private Layout measuredLayout;
    public final Layout getLayout(){return measuredLayout;}
    private static final class MeasuredLayout extends Layout {
        ArrayList<byte[]> levels;ArrayList<Integer> bases;
        public int getParagraphDirection(int line){return bases.get(line)==0?DIR_LEFT_TO_RIGHT:DIR_RIGHT_TO_LEFT;}
        public Directions getLineDirections(int line){return new Directions(shiraz.runtime.TextGeometry.directions(levels.get(line)));}
        final int[] starts;final float[] widths;final int baseline,step,height;
        MeasuredLayout(CharSequence text,TextPaint paint,int width,Alignment alignment,float mult,float add,int[] starts,float[] widths,int baseline,int step,int height){super(text,paint,width,alignment,mult,add);this.starts=starts;this.widths=widths;this.baseline=baseline;this.step=step;this.height=height;}
        private void index(int line,boolean end){if(line<0||line>(end?widths.length:widths.length-1))throw new IndexOutOfBoundsException("Layout line");}
        public int getLineCount(){return widths.length;}
        public int getLineTop(int line){index(line,true);return line==widths.length?height:line*step;}
        public int getLineDescent(int line){index(line,false);return getLineTop(line+1)-(baseline+line*step);}
        public int getLineStart(int line){index(line,true);return starts[line];}
        public float getLineWidth(int line){index(line,false);return widths[line];}
        public float getLineLeft(int line){index(line,false);return getAlignment()==Alignment.ALIGN_CENTER?(getWidth()-widths[line])/2:getAlignment()==Alignment.ALIGN_OPPOSITE?getWidth()-widths[line]:0;}
        public float getLineRight(int line){return getLineLeft(line)+widths[line];}
    }
    private String layoutText;
    private TextUtils.TruncateAt ellipsize;
    private int breakStrategy;
    private java.util.Locale layoutLocale;
    private ColorStateList colors=ColorStateList.valueOf(0xff000000),hintColors=ColorStateList.valueOf(0xff808080);
    private boolean minHeightPixels,maxHeightPixels;private int textMinHeight,textMaxHeight=Integer.MAX_VALUE;
    private int gravity=Gravity.TOP|Gravity.START,minLines=0,maxLines=Integer.MAX_VALUE;
    private boolean singleLine,includePadding=true;
    private int textMinWidth=0,textMaxWidth=Integer.MAX_VALUE;
    private boolean minWidthEms,maxWidthEms;
    private float spacingAdd,spacingMult=1;
    private final ArrayList<String> lines=new ArrayList<String>();
    private final ArrayList<Integer> lineStarts=new ArrayList<Integer>();
    private int renderOffset;
    private boolean cursorVisible=true;
    private int inputType=this instanceof EditText?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE:InputType.TYPE_NULL;
    private android.text.method.KeyListener keyListener=this instanceof EditText?new shiraz.runtime.TextKeyListener(inputType):null;
    public final android.text.method.KeyListener getKeyListener(){return keyListener;}
    public void setKeyListener(android.text.method.KeyListener value){
        int type=value==null?InputType.TYPE_NULL:value.getInputType();
        if((type&InputType.TYPE_MASK_CLASS)==InputType.TYPE_CLASS_TEXT)type=singleLine?type&~InputType.TYPE_TEXT_FLAG_MULTI_LINE:type|InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        keyListener=value;inputType=type;if(value!=null&&!(text instanceof Editable))setText(text,bufferType);if(text instanceof Editable)configureFilters((Editable)text);
        boolean active=value!=null||movement!=null;setFocusable(active);setClickable(active);setLongClickable(active);
    }
    @Override public boolean onCheckIsTextEditor(){return inputType!=InputType.TYPE_NULL;}
    private boolean editorEnterDown;
    @Override public boolean onKeyMultiple(int code,int count,android.view.KeyEvent event){if(isEnabled()&&isFocused()&&keyListener!=null&&text instanceof Editable&&keyListener.onKeyOther(this,(Editable)text,event))return true;return super.onKeyMultiple(code,count,event);}
    @Override public boolean onKeyDown(int code,android.view.KeyEvent event){
        if(!isEnabled()||!isFocused()||keyListener==null||!(text instanceof Editable))return super.onKeyDown(code,event);
        if(code==android.view.KeyEvent.KEYCODE_ENTER&&event.hasNoModifiers()){
            if(shirazEditorActionKey(event)){editorEnterDown=true;return true;}
            if((event.getFlags()&android.view.KeyEvent.FLAG_EDITOR_ACTION)!=0)return true;
        }
        return keyListener!=null&&text instanceof Editable&&keyListener.onKeyDown(this,(Editable)text,code,event)||super.onKeyDown(code,event);
    }
    @Override public boolean onKeyUp(int code,android.view.KeyEvent event){
        if(code==android.view.KeyEvent.KEYCODE_ENTER&&editorEnterDown){editorEnterDown=false;if(isEnabled()&&isFocused()&&!event.isCanceled()&&event.hasNoModifiers()&&shirazEditorActionKey(event))return true;}
        if(isEnabled()&&isFocused()&&keyListener!=null&&text instanceof Editable&&keyListener.onKeyUp(this,(Editable)text,code,event))return true;return super.onKeyUp(code,event);
    }

    private int imeOptions,imeActionId;private CharSequence imeActionLabel;private OnEditorActionListener editorActionListener;
    public interface OnEditorActionListener {boolean onEditorAction(TextView view,int actionId,android.view.KeyEvent event);}
    public int getInputType(){return inputType;}
    public void setRawInputType(int value){inputType=value;}
    public void setInputType(int value){
        int kind=value&InputType.TYPE_MASK_CLASS,variation=value&InputType.TYPE_MASK_VARIATION;
        if(value!=0&&(kind!=InputType.TYPE_CLASS_TEXT||(variation&15)!=0||variation>0xe0||(value&~0xff0ff)!=0))throw new UnsupportedOperationException("Input type requires an unsupported key listener");
        boolean masksText=variation==InputType.TYPE_TEXT_VARIATION_PASSWORD||variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        inputType=value;keyListener=value==0?null:new shiraz.runtime.TextKeyListener(value);if(text instanceof Editable)configureFilters((Editable)text);
        if(value!=0){if(!(text instanceof Editable))setText(text,BufferType.EDITABLE);setSingleLine((value&InputType.TYPE_TEXT_FLAG_MULTI_LINE)==0);}
        if(masksText)setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());else if(transformation instanceof android.text.method.PasswordTransformationMethod)setTransformationMethod(null);
    }
    protected final boolean shirazAcceptsTextInput(){return keyListener!=null;}
    public int getImeOptions(){return imeOptions;}
    public void setImeOptions(int value){if((value&255)>7)throw new IllegalArgumentException("IME action");imeOptions=value;}
    public void setImeActionLabel(CharSequence label,int id){imeActionLabel=label;imeActionId=id;}
    public CharSequence getImeActionLabel(){return imeActionLabel;}public int getImeActionId(){return imeActionId;}
    public void setOnEditorActionListener(OnEditorActionListener listener){editorActionListener=listener;}
    protected final boolean shirazEditorActionKey(android.view.KeyEvent event){return editorActionListener!=null&&editorActionListener.onEditorAction(this,android.view.inputmethod.EditorInfo.IME_NULL,event);}
    public void onEditorAction(int action){
        if(editorActionListener!=null&&editorActionListener.onEditorAction(this,action,null))return;
        if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE)return;
        if(action==android.view.inputmethod.EditorInfo.IME_ACTION_NEXT||action==android.view.inputmethod.EditorInfo.IME_ACTION_PREVIOUS)throw new UnsupportedOperationException("Editor next/previous focus actions are not implemented");
        if(!isAttachedToWindow())return;
        long now=android.os.SystemClock.uptimeMillis();for(int phase:new int[]{android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.ACTION_UP}){android.view.KeyEvent event=new android.view.KeyEvent(now,now,phase,android.view.KeyEvent.KEYCODE_ENTER,0,0,-1,0,android.view.KeyEvent.FLAG_SOFT_KEYBOARD|android.view.KeyEvent.FLAG_EDITOR_ACTION);try{dispatchKeyEvent(event);}finally{event.recycle();}}
    }

    private final ArrayList<Float> advances=new ArrayList<Float>();
    private int layoutWidth=-1,contentHeight,firstBaseline,lineStep;
    private float layoutSize=-1;private Typeface layoutFace;
    private static final String[] CONSTRUCTOR_ATTRIBUTES={"linksClickable","autoLink","breakStrategy","drawableBottom","drawableEnd","drawableLeft","drawablePadding","drawableRight","drawableStart","drawableTop","editable","ellipsize","ems","fontFeatureSettings","freezesText","gravity","height","hint","hyphenationFrequency","imeActionId","imeActionLabel","imeOptions","includeFontPadding","inputType","lineSpacingExtra","lineSpacingMultiplier","lines","maxEms","maxHeight","maxLength","maxLines","maxWidth","minEms","minHeight","minLines","minWidth","password","singleLine","text","textAppearance","textIsSelectable","width"};
    public TextView(Context context){this(context,null);}
    public TextView(Context context,AttributeSet attrs){this(context,attrs,0);}
    public TextView(Context context,AttributeSet attrs,int style){this(context,attrs,style,0);}
    public TextView(Context context,AttributeSet attrs,int style,int resource){
        super(context,attrs,style,resource);
        paint.density=getResources().getDisplayMetrics().density;setTextSize(15);
        if(attrs!=null||style!=0||resource!=0){
            shiraz.runtime.XmlAttributes a=new shiraz.runtime.XmlAttributes(context,attrs,style,resource);a.prefetch(CONSTRUCTOR_ATTRIBUTES);
            if(a.has("freezesText"))setFreezesText(a.bool("freezesText",false));
            a.reject("editable password textIsSelectable fontFeatureSettings");setAutoLinkMask(a.integer("autoLink",0));if(a.has("linksClickable"))setLinksClickable(a.bool("linksClickable",true));
            if(a.has("maxLength")){int max=a.integer("maxLength",-1);if(max>=0)setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});}
            if(a.has("imeOptions"))setImeOptions(a.integer("imeOptions",0));if(a.has("imeActionLabel")||a.has("imeActionId"))setImeActionLabel(a.text("imeActionLabel"),a.integer("imeActionId",0));
            if(a.has("breakStrategy"))setBreakStrategy(a.integer("breakStrategy",0));if(a.has("hyphenationFrequency"))setHyphenationFrequency(a.integer("hyphenationFrequency",0));
            if(a.has("ellipsize")){int mode=a.integer("ellipsize",0);switch(mode){case 0:setEllipsize(null);break;case 1:setEllipsize(TextUtils.TruncateAt.START);break;case 2:setEllipsize(TextUtils.TruncateAt.MIDDLE);break;case 3:setEllipsize(TextUtils.TruncateAt.END);break;case 4:setEllipsize(TextUtils.TruncateAt.MARQUEE);break;default:throw new IllegalArgumentException("ellipsis mode");}}
            if(a.has("text"))setText(a.text("text"));if(a.has("hint"))setHint(a.text("hint"));
            int appearance=a.reference("textAppearance",0);if(appearance!=0)setTextAppearance(context,appearance);
            android.content.res.TypedArray typography=context.obtainStyledAttributes(attrs,TEXT_APPEARANCE,style,resource);
            try{applyTextAppearance(typography,false);}finally{typography.recycle();}
            if(a.has("inputType"))setInputType(a.integer("inputType",0));
            setGravity(a.integer("gravity",gravity));setIncludeFontPadding(a.bool("includeFontPadding",true));
            if(a.has("singleLine"))setSingleLine(a.bool("singleLine",false));
            if(a.has("maxLines"))setMaxLines(a.integer("maxLines",0));
            if(a.has("maxHeight"))setMaxHeight(a.dimension("maxHeight",0,false));
            if(a.has("lines"))setLines(a.integer("lines",0));
            if(a.has("height"))setHeight(a.dimension("height",0,false));
            if(a.has("minLines"))setMinLines(a.integer("minLines",0));
            if(a.has("minHeight"))setMinHeight(a.dimension("minHeight",0,false));
            // TypedArray iteration follows public attribute IDs, including mode overrides.
            if(a.has("maxWidth"))setMaxWidth(a.dimension("maxWidth",Integer.MAX_VALUE,false));
            if(a.has("minWidth"))setMinWidth(a.dimension("minWidth",0,false));
            if(a.has("maxEms"))setMaxEms(a.integer("maxEms",0));
            if(a.has("ems"))setEms(a.integer("ems",0));
            if(a.has("width"))setWidth(a.dimension("width",0,false));
            if(a.has("minEms"))setMinEms(a.integer("minEms",0));
            if(a.has("drawableLeft")||a.has("drawableTop")||a.has("drawableRight")||a.has("drawableBottom"))setCompoundDrawablesWithIntrinsicBounds(a.drawable("drawableLeft"),a.drawable("drawableTop"),a.drawable("drawableRight"),a.drawable("drawableBottom"));
            if(a.has("drawableStart")||a.has("drawableEnd"))setCompoundDrawablesRelativeWithIntrinsicBounds(a.drawable("drawableStart"),compoundTop,a.drawable("drawableEnd"),compoundBottom);
            setCompoundDrawablePadding(a.dimension("drawablePadding",0,false));
            setLineSpacing(a.dimension("lineSpacingExtra",0,false),a.number("lineSpacingMultiplier",1));
        }
    }
    // Public API27 attribute IDs; theme/style precedence is resolved by Resources.Theme.
    private static final int[] TEXT_APPEARANCE={0x01010095,0x01010098,0x0101009a,0x01010097,0x01010096,0x010103ac,0x0101009b,0x0101038c,0x010104b6,0x01010161,0x01010162,0x01010163,0x01010164,0x010104b7};
    private ColorStateList linkColors=ColorStateList.valueOf(0xff0000ff);
    public void setTextAppearance(int resource){setTextAppearance(getContext(),resource);}
    public void setTextAppearance(Context context,int resource){android.content.res.TypedArray values=context.obtainStyledAttributes(resource,TEXT_APPEARANCE);try{applyTextAppearance(values,true);}finally{values.recycle();}}
    public final ColorStateList getLinkTextColors(){return linkColors;}
    public final void setLinkTextColor(int color){setLinkTextColor(ColorStateList.valueOf(color));}
    public final void setLinkTextColor(ColorStateList colors){if(colors==null)throw new NullPointerException("link colors");linkColors=colors;invalidate();}
    private void applyTextAppearance(android.content.res.TypedArray values,boolean resetTypeface){
        if(values.hasValue(7))setAllCaps(values.getBoolean(7,false));
        if(values.hasValue(8))setLetterSpacing(values.getFloat(8,0));
        if(values.hasValue(9)||values.hasValue(10)||values.hasValue(11)||values.hasValue(12))setShadowLayer(values.getFloat(12,0),values.getFloat(10,0),values.getFloat(11,0),values.getColor(9,0));
        String features=values.getString(13);if(features!=null&&!features.isEmpty())throw new UnsupportedOperationException("Text font feature settings are not implemented");
        if(values.hasValue(0))setTextSize(TypedValue.COMPLEX_UNIT_PX,values.getDimensionPixelSize(0,(int)getTextSize()));
        if(values.hasValue(1))setTextColor(values.getColorStateList(1));if(values.hasValue(2))setHintTextColor(values.getColorStateList(2));if(values.hasValue(6))setLinkTextColor(values.getColorStateList(6));
        if(resetTypeface&&!values.hasValue(3)&&!values.hasValue(4)&&!values.hasValue(5))setTypeface((Typeface)null);
        if(values.hasValue(3)||values.hasValue(4)||values.hasValue(5)){
            Typeface face=resetTypeface?null:getTypeface();int style=values.getInt(3,face==null?Typeface.NORMAL:face.getStyle());if(style<0||style>3)throw new IllegalArgumentException("Text style");
            if(values.hasValue(4)){int index=values.getInt(4,0);switch(index){case 0:face=null;break;case 1:face=Typeface.SANS_SERIF;break;case 2:face=Typeface.SERIF;break;case 3:face=Typeface.MONOSPACE;break;default:throw new IllegalArgumentException("Text typeface");}}
            if(values.hasValue(5)){String family=values.getString(5);if(family!=null&&family.startsWith("res/"))throw new UnsupportedOperationException("Resource fonts are not implemented");face=Typeface.create(family,style);}
            if(face==null&&style==0)setTypeface((Typeface)null);else setTypeface(face,style);
        }
    }
    @Override protected boolean shirazHasAccessibilityContent(){return super.shirazHasAccessibilityContent()||text.length()!=0||hint!=null&&!hint.isEmpty();}
    @Override protected void shirazInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.shirazInitializeAccessibilityNodeInfo(info);info.setText(transformation instanceof android.text.method.PasswordTransformationMethod?transformedText():text);info.setPassword(transformation instanceof android.text.method.PasswordTransformationMethod);info.setHintText(hint);info.setShowingHintText(text.length()==0&&hint!=null);info.setMultiLine(!singleLine);info.setEditable(keyListener!=null&&text instanceof Editable);if(text instanceof Editable)info.setTextSelection(getSelectionStart(),getSelectionEnd());}
    @Override protected void shirazInitializeAccessibilityEvent(android.view.accessibility.AccessibilityEvent event){super.shirazInitializeAccessibilityEvent(event);event.setPassword(transformation instanceof android.text.method.PasswordTransformationMethod);}
    @Override protected void shirazPopulateAccessibilityEvent(android.view.accessibility.AccessibilityEvent event){super.shirazPopulateAccessibilityEvent(event);String value=displayed();if(!value.isEmpty())event.getText().add(value);}
    private void changed(){measuredLayout=null;layoutWidth=-1;requestLayout();invalidate();}
    private static String plain(CharSequence value){
        if(value==null)return "";
        if(value instanceof Spanned){for(Object span:((Spanned)value).getSpans(0,value.length(),Object.class))if(!(span instanceof NoCopySpan||span instanceof TextWatcher||span instanceof SpanWatcher||span instanceof android.text.style.CharacterStyle))throw new UnsupportedOperationException("Styled text rendering is not implemented");}
        else if(!(value instanceof String||value instanceof StringBuilder||value instanceof StringBuffer))throw new UnsupportedOperationException("Styled text is not implemented");
        if(value.length()>32768)throw new IllegalArgumentException("TextView text limit");
        String result=value.toString();

        return result;
    }
    public void setText(CharSequence value){setText(value,bufferType);}
    private final class BufferObserver implements TextWatcher,SpanWatcher,NoCopySpan {
        public void beforeTextChanged(CharSequence value,int start,int before,int count){if(value==text)for(TextWatcher w:snapshot())w.beforeTextChanged(value,start,before,count);}
        public void onTextChanged(CharSequence value,int start,int before,int count){if(value==text){changed();TextView.this.onTextChanged(value,start,before,count);for(TextWatcher w:snapshot())w.onTextChanged(value,start,before,count);}}
        public void afterTextChanged(Editable value){if(value==text)for(TextWatcher w:snapshot())w.afterTextChanged(value);}
        public void onSpanAdded(Spannable value,Object what,int start,int end){spanChanged(value,what);}
        public void onSpanRemoved(Spannable value,Object what,int start,int end){spanChanged(value,what);}
        public void onSpanChanged(Spannable value,Object what,int a,int b,int c,int d){spanChanged(value,what);}
        private void spanChanged(Spannable value,Object what){if(value==text){if(what instanceof android.text.style.MetricAffectingSpan)changed();else invalidate();if(what==Selection.SELECTION_END)onSelectionChanged(getSelectionStart(),getSelectionEnd());}}
    }
    private TextWatcher[] snapshot(){return watchers.toArray(new TextWatcher[0]);}
    public void addTextChangedListener(TextWatcher watcher){if(watcher==null)throw new NullPointerException("watcher");if(watchers.size()>=1024)throw new IllegalStateException("Text watcher limit");watchers.add(watcher);}
    public void removeTextChangedListener(TextWatcher watcher){watchers.remove(watcher);}
    private void configureFilters(Editable buffer){boolean numeric=keyListener instanceof InputFilter;InputFilter[] all=java.util.Arrays.copyOf(filters,filters.length+(numeric?2:1));if(numeric)all[filters.length+1]=(InputFilter)keyListener;all[filters.length]=new InputFilter(){public CharSequence filter(CharSequence source,int start,int end,Spanned dest,int a,int b){plain(source.subSequence(start,end));return null;}};buffer.setFilters(all);}
    public void setFilters(InputFilter[] value){if(value==null)throw new NullPointerException("filters");if(value.length>1024)throw new IllegalArgumentException("Filter limit");for(InputFilter f:value)if(f==null)throw new NullPointerException("filter");filters=value.clone();if(text instanceof Editable)configureFilters((Editable)text);}
    public InputFilter[] getFilters(){return filters.clone();}
    public void setText(CharSequence value,BufferType type){
        if(type==null)throw new NullPointerException("buffer type");
        CharSequence source=value==null?"":value;plain(source);
        if(filters.length!=0||keyListener instanceof InputFilter){SpannableStringBuilder filtered=new SpannableStringBuilder();configureFilters(filtered);filtered.append(source);source=filtered;}
        CharSequence replacement;
        if(type==BufferType.EDITABLE||keyListener!=null||!watchers.isEmpty()){Editable buffer=editableFactory.newEditable(source);if(buffer==null)throw new IllegalStateException("Editable factory returned null");configureFilters(buffer);replacement=buffer;}
        else if(type==BufferType.SPANNABLE||movement!=null||autoLinkMask!=0){replacement=spannableFactory.newSpannable(source);if(replacement==null)throw new IllegalStateException("Spannable factory returned null");}
        else replacement=source instanceof Spanned?new SpannedString(source):source.toString();
        if(autoLinkMask!=0&&replacement instanceof Spannable&&android.text.util.Linkify.addLinks((Spannable)replacement,autoLinkMask)&&linksClickable){movement=android.text.method.LinkMovementMethod.getInstance();setFocusable(true);}
        plain(replacement);
        CharSequence old=text;for(TextWatcher w:snapshot())w.beforeTextChanged(old,0,old.length(),replacement.length());
        if(old instanceof Spannable&&observer!=null)((Spannable)old).removeSpan(observer);
        text=replacement;bufferType=type;
        if(movement!=null&&text instanceof Spannable)movement.initialize(this,(Spannable)text);
        if(transformation instanceof android.text.method.TransformationMethod2)((android.text.method.TransformationMethod2)transformation).setLengthChangesAllowed(!(text instanceof Editable));
        if(text instanceof Spannable){observer=new BufferObserver();((Spannable)text).setSpan(observer,0,text.length(),Spanned.SPAN_INCLUSIVE_INCLUSIVE);}
        changed();onTextChanged(text,0,old.length(),text.length());for(TextWatcher w:snapshot())w.onTextChanged(text,0,old.length(),text.length());
        if(text instanceof Editable)for(TextWatcher w:snapshot())w.afterTextChanged((Editable)text);
    }
    private int autoLinkMask;private boolean linksClickable=true;
    public final void setAutoLinkMask(int mask){autoLinkMask=mask;}
    public final int getAutoLinkMask(){return autoLinkMask;}
    public final void setLinksClickable(boolean value){linksClickable=value;}
    public final boolean getLinksClickable(){return linksClickable;}
    public android.text.style.URLSpan[] getUrls(){return text instanceof Spanned?((Spanned)text).getSpans(0,text.length(),android.text.style.URLSpan.class):new android.text.style.URLSpan[0];}
    private android.text.method.MovementMethod movement;
    public final android.text.method.MovementMethod getMovementMethod(){return movement;}
    public final void setMovementMethod(android.text.method.MovementMethod value){if(movement==value)return;movement=value;if(value!=null){if(!(text instanceof Spannable))setText(text,BufferType.SPANNABLE);else value.initialize(this,(Spannable)text);setFocusable(true);}}
    @Override public boolean onTouchEvent(android.view.MotionEvent event){if(isEnabled()&&movement!=null&&text instanceof Spannable&&movement.onTouchEvent(this,(Spannable)text,event))return true;return super.onTouchEvent(event);}
    public android.text.style.ClickableSpan shirazLinkAt(float x,float y){
        if(!(text instanceof Spanned)||!displayed().equals(text.toString()))return null;ensure(available(getWidth()));float localY=y-getCompoundPaddingTop()-verticalOffset(getHeight());int line=(int)(localY/Math.max(1,lineStep));if(localY<0||line<0||line>=Math.min(lines.size(),maxLines)||x<lineX(line)||x>=lineX(line)+advances.get(line))return null;
        int offset=getOffsetForPosition(x,y);Spanned spans=(Spanned)text;for(android.text.style.ClickableSpan link:spans.getSpans(offset,offset,android.text.style.ClickableSpan.class))if(offset>=spans.getSpanStart(link)&&offset<spans.getSpanEnd(link))return link;return null;
    }
    public Editable getEditableText(){return text instanceof Editable?(Editable)text:null;}
    public final void append(CharSequence value){append(value,0,value.length());}
    public void append(CharSequence value,int start,int end){if(!(text instanceof Editable))setText(text,BufferType.EDITABLE);((Editable)text).append(value,start,end);}
    public int getSelectionStart(){return Selection.getSelectionStart(text);}
    public int getSelectionEnd(){return Selection.getSelectionEnd(text);}
    public boolean hasSelection(){return getSelectionStart()>=0&&getSelectionStart()!=getSelectionEnd();}
    protected void onSelectionChanged(int start,int end){}
    public final void setText(int id){setText(getContext().getString(id));}
    public final void setText(int id,BufferType type){setText(getContext().getString(id),type);}
    public final void setText(char[] value,int start,int length){setText(new String(value,start,length));}
    public CharSequence getText(){return text;}
    public int length(){return text.length();}
    protected void onTextChanged(CharSequence value,int start,int before,int count){}
    public final void setHint(CharSequence value){String next=value==null?null:plain(value);if(java.util.Objects.equals(hint,next))return;hint=next;changed();}
    public final void setHint(int id){setHint(getContext().getString(id));}
    public CharSequence getHint(){return hint;}
    public void setTextColor(int value){setTextColor(ColorStateList.valueOf(value));}
    public void setTextColor(ColorStateList value){if(value==null)throw new NullPointerException("colors");colors=value;invalidate();}
    public final ColorStateList getTextColors(){return colors;}
    public final int getCurrentTextColor(){return colors.getColorForState(getDrawableState(),colors.getDefaultColor());}
    public void setHintTextColor(int value){setHintTextColor(ColorStateList.valueOf(value));}
    public void setHintTextColor(ColorStateList value){if(value==null)throw new NullPointerException("hint colors");hintColors=value;invalidate();}
    public final ColorStateList getHintTextColors(){return hintColors;}
    public final int getCurrentHintTextColor(){return hintColors.getColorForState(getDrawableState(),hintColors.getDefaultColor());}
    public TextPaint getPaint(){return paint;}
    public float getTextSize(){return paint.getTextSize();}
    public void setShadowLayer(float radius,float dx,float dy,int color){paint.setShadowLayer(radius,dx,dy,color);invalidate();}
    public float getShadowRadius(){return paint.getShadowLayerRadius();}public float getShadowDx(){return paint.getShadowLayerDx();}public float getShadowDy(){return paint.getShadowLayerDy();}public int getShadowColor(){return paint.getShadowLayerColor();}
    public float getLetterSpacing(){return paint.getLetterSpacing();}
    public void setLetterSpacing(float value){if(value!=paint.getLetterSpacing()){paint.setLetterSpacing(value);changed();}}
    public void setTextSize(float value){setTextSize(TypedValue.COMPLEX_UNIT_SP,value);}
    public void setTextSize(int unit,float value){float size=TypedValue.applyDimension(unit,value,getResources().getDisplayMetrics());if(size!=paint.getTextSize()){paint.setTextSize(size);changed();}}
    public void setTypeface(Typeface value){paint.setTypeface(value);changed();}
    public void setTypeface(Typeface value,int style){setTypeface(Typeface.create(value,style));}
    public Typeface getTypeface(){return paint.getTypeface();}
    @Override public void onRtlPropertiesChanged(int direction){super.onRtlPropertiesChanged(direction);changed();}
    public void setGravity(int value){if((value&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)==0)value|=Gravity.START;if((value&Gravity.VERTICAL_GRAVITY_MASK)==0)value|=Gravity.TOP;gravity=value;changed();}
    public int getGravity(){return gravity;}
    public void setIncludeFontPadding(boolean value){includePadding=value;changed();}
    public boolean getIncludeFontPadding(){return includePadding;}
    public void setLineSpacing(float add,float mult){if(!Float.isFinite(add)||!Float.isFinite(mult)||mult<0||Math.abs(add)>8192||mult>128)throw new IllegalArgumentException("Line spacing bounds");spacingAdd=add;spacingMult=mult;changed();}
    public float getLineSpacingExtra(){return spacingAdd;}
    public float getLineSpacingMultiplier(){return spacingMult;}
    public void setMinWidth(int value){if(value<0)throw new IllegalArgumentException("min width");textMinWidth=value;minWidthEms=false;changed();}
    public int getMinWidth(){return minWidthEms?-1:textMinWidth;}
    public void setMaxWidth(int value){if(value<0)throw new IllegalArgumentException("max width");textMaxWidth=value;maxWidthEms=false;changed();}
    public int getMaxWidth(){return maxWidthEms?-1:textMaxWidth;}
    public void setWidth(int value){setMinWidth(value);setMaxWidth(value);}
    public void setMinEms(int value){if(value<0)throw new IllegalArgumentException("min ems");textMinWidth=value;minWidthEms=true;changed();}
    public int getMinEms(){return minWidthEms?textMinWidth:-1;}
    public void setMaxEms(int value){if(value<0)throw new IllegalArgumentException("max ems");textMaxWidth=value;maxWidthEms=true;changed();}
    public int getMaxEms(){return maxWidthEms?textMaxWidth:-1;}
    public void setEms(int value){setMinEms(value);setMaxEms(value);}
    private int widthLimit(int value,boolean ems){return (int)Math.min(Integer.MAX_VALUE,ems?(long)value*getLineHeight():value);}
    public void setMinHeight(int value){if(value<0)throw new IllegalArgumentException("min height");textMinHeight=value;minHeightPixels=true;minLines=0;changed();}
    public int getMinHeight(){return minHeightPixels?textMinHeight:-1;}
    public void setMaxHeight(int value){if(value<0)throw new IllegalArgumentException("max height");textMaxHeight=value;maxHeightPixels=true;maxLines=Integer.MAX_VALUE;changed();}
    public int getMaxHeight(){return maxHeightPixels?textMaxHeight:-1;}
    public void setHeight(int value){setMinHeight(value);setMaxHeight(value);}
    public void setMinLines(int value){if(value<0)throw new IllegalArgumentException("min lines");minLines=value;minHeightPixels=false;changed();}
    public int getMinLines(){return minHeightPixels?-1:minLines;}
    public void setMaxLines(int value){if(value<0)throw new IllegalArgumentException("max lines");if(maxLines!=value||maxHeightPixels){maxLines=value;maxHeightPixels=false;changed();}}
    public int getMaxLines(){return maxHeightPixels?-1:maxLines;}
    public void setLines(int value){setMinLines(value);setMaxLines(value);}
    public int getBreakStrategy(){return breakStrategy;}
    public void setBreakStrategy(int strategy){if(strategy<0||strategy>2)throw new IllegalArgumentException("break strategy");if(breakStrategy!=strategy){breakStrategy=strategy;changed();}}
    private int hyphenationFrequency;
    public int getHyphenationFrequency(){return hyphenationFrequency;}
    public void setHyphenationFrequency(int frequency){if(frequency<0||frequency>2)throw new IllegalArgumentException("hyphenation frequency");hyphenationFrequency=frequency;}
    @Override public void setSelected(boolean selected){boolean old=isSelected();super.setSelected(selected);if(old!=selected&&ellipsize==TextUtils.TruncateAt.MARQUEE)changed();}
    public TextUtils.TruncateAt getEllipsize(){return ellipsize;}
    public void setEllipsize(TextUtils.TruncateAt where){ellipsize=where;changed();}
    public void setSingleLine(){setSingleLine(true);}
    public void setSingleLine(boolean value){if((inputType&InputType.TYPE_MASK_CLASS)==InputType.TYPE_CLASS_TEXT)inputType=value?inputType&~InputType.TYPE_TEXT_FLAG_MULTI_LINE:inputType|InputType.TYPE_TEXT_FLAG_MULTI_LINE;singleLine=value;if(value)setLines(1);else setMaxLines(Integer.MAX_VALUE);changed();}
    public android.text.method.TransformationMethod getTransformationMethod(){return transformation;}
    public final void setTransformationMethod(android.text.method.TransformationMethod value){transformation=value;if(value instanceof android.text.method.TransformationMethod2)((android.text.method.TransformationMethod2)value).setLengthChangesAllowed(!(text instanceof Editable));changed();}
    public void setAllCaps(boolean enabled){setTransformationMethod(enabled?new android.text.method.AllCapsTransformationMethod(getContext()):null);}
    public boolean isAllCaps(){return transformation instanceof android.text.method.AllCapsTransformationMethod;}
    @Override protected void onFocusChanged(boolean focused,int direction,android.graphics.Rect previous){super.onFocusChanged(focused,direction,previous);if(ellipsize==TextUtils.TruncateAt.MARQUEE)changed();if(!focused)editorEnterDown=false;if(transformation!=null){transformation.onFocusChanged(this,text,focused,direction,previous);changed();}}
    private String transformedText(){return transformation==null?plain(text):plain(transformation.getTransformation(text,this));}
    private String displayed(){boolean showingHint=text.length()==0&&hint!=null;String value=showingHint?hint:transformedText();return singleLine?value.replace('\n',' ').replace('\r',' '):value;}

    private static int dimension(long value){if(value>MEASURED_SIZE_MASK)throw new IllegalArgumentException("TextView layout extent");return (int)Math.max(0,value);}
    private Drawable compoundLeft,compoundRight,compoundTop,compoundBottom,compoundStart,compoundEnd;
    private boolean compoundRelative;
    private int compoundPadding;
    private Drawable leftDrawable(){return compoundRelative?(getLayoutDirection()==LAYOUT_DIRECTION_RTL?compoundEnd:compoundStart):compoundLeft;}
    private Drawable rightDrawable(){return compoundRelative?(getLayoutDirection()==LAYOUT_DIRECTION_RTL?compoundStart:compoundEnd):compoundRight;}
    private static int dw(Drawable d){return d==null?0:Math.max(0,d.getBounds().width());}
    private static int dh(Drawable d){return d==null?0:Math.max(0,d.getBounds().height());}
    public void setCompoundDrawablePadding(int value){if(value<0||value>8192)throw new IllegalArgumentException("Drawable padding");compoundPadding=value;changed();}
    public int getCompoundDrawablePadding(){return compoundPadding;}
    public Drawable[] getCompoundDrawables(){return new Drawable[]{leftDrawable(),compoundTop,rightDrawable(),compoundBottom};}
    public Drawable[] getCompoundDrawablesRelative(){return new Drawable[]{compoundStart,compoundTop,compoundEnd,compoundBottom};}
    private android.content.res.ColorStateList compoundTint;
    private android.graphics.PorterDuff.Mode compoundTintMode=android.graphics.PorterDuff.Mode.SRC_IN;
    public void setCompoundDrawableTintList(android.content.res.ColorStateList tint){compoundTint=tint;applyCompoundTint();invalidate();}
    public android.content.res.ColorStateList getCompoundDrawableTintList(){return compoundTint;}
    public void setCompoundDrawableTintMode(android.graphics.PorterDuff.Mode mode){compoundTintMode=mode==null?android.graphics.PorterDuff.Mode.SRC_IN:mode;applyCompoundTint();invalidate();}
    public android.graphics.PorterDuff.Mode getCompoundDrawableTintMode(){return compoundTintMode;}
    private void applyCompoundTint(){if(compoundTint==null)return;int color=compoundTint.getColorForState(getDrawableState(),compoundTint.getDefaultColor());for(Drawable d:getCompoundDrawables())if(d!=null)d.setColorFilter(color,compoundTintMode);}
    private void compound(Drawable l,Drawable t,Drawable r,Drawable b,boolean relative){
        Drawable[] old=getCompoundDrawables();compoundRelative=relative;
        if(relative){compoundStart=l;compoundEnd=r;compoundLeft=compoundRight=null;}else{compoundLeft=l;compoundRight=r;compoundStart=compoundEnd=null;}
        compoundTop=t;compoundBottom=b;
        for(Drawable d:old)if(d!=null&&!verifyDrawable(d)){d.setCallback(null);unscheduleDrawable(d);}
        for(Drawable d:getCompoundDrawables())if(d!=null){d.setCallback(this);d.setState(getDrawableState());d.setVisible(getVisibility()==VISIBLE,false);}
        applyCompoundTint();changed();
    }
    public void setCompoundDrawables(Drawable l,Drawable t,Drawable r,Drawable b){compound(l,t,r,b,false);}
    public void setCompoundDrawablesRelative(Drawable l,Drawable t,Drawable r,Drawable b){compound(l,t,r,b,true);}
    private static Drawable intrinsic(Drawable d){if(d!=null)d.setBounds(0,0,Math.max(0,d.getIntrinsicWidth()),Math.max(0,d.getIntrinsicHeight()));return d;}
    public void setCompoundDrawablesWithIntrinsicBounds(Drawable l,Drawable t,Drawable r,Drawable b){setCompoundDrawables(intrinsic(l),intrinsic(t),intrinsic(r),intrinsic(b));}
    public void setCompoundDrawablesRelativeWithIntrinsicBounds(Drawable l,Drawable t,Drawable r,Drawable b){setCompoundDrawablesRelative(intrinsic(l),intrinsic(t),intrinsic(r),intrinsic(b));}
    private Drawable compoundResource(int id){return id==0?null:getResources().getDrawable(id,getContext().getTheme());}
    public void setCompoundDrawablesWithIntrinsicBounds(int l,int t,int r,int b){setCompoundDrawablesWithIntrinsicBounds(compoundResource(l),compoundResource(t),compoundResource(r),compoundResource(b));}
    public void setCompoundDrawablesRelativeWithIntrinsicBounds(int l,int t,int r,int b){setCompoundDrawablesRelativeWithIntrinsicBounds(compoundResource(l),compoundResource(t),compoundResource(r),compoundResource(b));}
    public int getCompoundPaddingLeft(){Drawable d=leftDrawable();return getPaddingLeft()+(d==null?0:dw(d)+compoundPadding);}
    public int getCompoundPaddingRight(){Drawable d=rightDrawable();return getPaddingRight()+(d==null?0:dw(d)+compoundPadding);}
    public int getCompoundPaddingTop(){return getPaddingTop()+(compoundTop==null?0:dh(compoundTop)+compoundPadding);}
    public int getCompoundPaddingBottom(){return getPaddingBottom()+(compoundBottom==null?0:dh(compoundBottom)+compoundPadding);}
    public int getCompoundPaddingStart(){return getLayoutDirection()==LAYOUT_DIRECTION_RTL?getCompoundPaddingRight():getCompoundPaddingLeft();}
    public int getCompoundPaddingEnd(){return getLayoutDirection()==LAYOUT_DIRECTION_RTL?getCompoundPaddingLeft():getCompoundPaddingRight();}
    public int getTotalPaddingLeft(){return getCompoundPaddingLeft();}
    public int getTotalPaddingRight(){return getCompoundPaddingRight();}
    public int getTotalPaddingTop(){return getCompoundPaddingTop();}
    public int getTotalPaddingBottom(){return getCompoundPaddingBottom();}
    public int getTotalPaddingStart(){return getCompoundPaddingStart();}
    public int getTotalPaddingEnd(){return getCompoundPaddingEnd();}
    @Override protected boolean verifyDrawable(Drawable d){return super.verifyDrawable(d)||d!=null&&(d==compoundLeft||d==compoundRight||d==compoundTop||d==compoundBottom||d==compoundStart||d==compoundEnd);}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);for(Drawable d:getCompoundDrawables())if(d!=null)d.setVisible(isShown(),false);}
    @Override public void jumpDrawablesToCurrentState(){super.jumpDrawablesToCurrentState();for(Drawable d:getCompoundDrawables())if(d!=null)d.jumpToCurrentState();}
    private void drawCompound(Canvas canvas,Drawable d,int x,int y){if(d==null)return;int save=canvas.save();try{d.setLayoutDirection(getLayoutDirection());canvas.translate(x+getScrollX(),y+getScrollY());d.draw(canvas);}finally{canvas.restoreToCount(save);}}
    private void drawCompounds(Canvas canvas){int l=getCompoundPaddingLeft(),r=getWidth()-getCompoundPaddingRight(),t=getCompoundPaddingTop(),b=getHeight()-getCompoundPaddingBottom();Drawable left=leftDrawable(),right=rightDrawable();drawCompound(canvas,left,getPaddingLeft(),t+(b-t-dh(left))/2);drawCompound(canvas,right,getWidth()-getPaddingRight()-dw(right),t+(b-t-dh(right))/2);drawCompound(canvas,compoundTop,l+(r-l-dw(compoundTop))/2,getPaddingTop());drawCompound(canvas,compoundBottom,l+(r-l-dw(compoundBottom))/2,getHeight()-getPaddingBottom()-dh(compoundBottom));}
    private int available(int width){return dimension((long)width-getCompoundPaddingLeft()-getCompoundPaddingRight());}
    private static int next(String value,int index){
        int end=index+Character.charCount(value.codePointAt(index));
        while(end<value.length()){int cp=value.codePointAt(end),type=Character.getType(cp);if(type!=Character.NON_SPACING_MARK&&type!=Character.COMBINING_SPACING_MARK&&type!=Character.ENCLOSING_MARK&&cp!=0xfe0f)break;end+=Character.charCount(cp);}return end;
    }
    private final ArrayList<Integer> lineBaseLevels=new ArrayList<Integer>();
    private final ArrayList<byte[]> lineLevels=new ArrayList<byte[]>();
    private java.text.Bidi paragraphBidi;private int paragraphOrigin;
    private TextPaint linePaint(int line){TextPaint result=uniformMetrics()?metricPaint(0,text.length()):new TextPaint(paint);result.shirazSetBidiLevels(lineLevels.get(line));return result;}
    private final ArrayList<CharSequence> styledLines=new ArrayList<CharSequence>();
    private boolean hasMetrics(){return text instanceof Spanned&&((Spanned)text).getSpans(0,text.length(),android.text.style.MetricAffectingSpan.class).length!=0;}
    // A full-text metric span changes the font, not the shaping boundaries. Keep
    // the whole bidi paragraph together so Persian joining and visual order survive.
    private boolean uniformMetrics(){
        if(!(text instanceof Spanned))return false;Spanned spans=(Spanned)text;
        android.text.style.MetricAffectingSpan[] metrics=spans.getSpans(0,text.length(),android.text.style.MetricAffectingSpan.class);
        if(metrics.length==0)return false;
        for(android.text.style.MetricAffectingSpan span:metrics)if(span instanceof android.text.style.ReplacementSpan||spans.getSpanStart(span)!=0||spans.getSpanEnd(span)!=text.length())return false;
        return true;
    }
    private TextPaint metricPaint(int start,int end){TextPaint result=new TextPaint();result.set(paint);if(text instanceof Spanned)for(android.text.style.MetricAffectingSpan span:((Spanned)text).getSpans(start,end,android.text.style.MetricAffectingSpan.class))span.updateMeasureState(result);return result;}
    private float advance(String value,int start,int end,int origin){
        if(!hasMetrics())return paint.measureText(value,start,end);if(uniformMetrics()){if(!displayed().equals(text.toString()))throw new UnsupportedOperationException("Transformed metric text");TextPaint measured=metricPaint(0,text.length());measured.shirazSetBidiLevels(null);return measured.measureText(value,start,end);}if(java.text.Bidi.requiresBidi(value.toCharArray(),0,value.length()))throw new UnsupportedOperationException("Bidirectional metric spans require styled shaping context");if(!displayed().equals(text.toString()))throw new UnsupportedOperationException("Transformed metric text");
        Spanned spans=(Spanned)text;float width=0;for(int at=start;at<end;){int next=spans.nextSpanTransition(origin+at,origin+end,android.text.style.MetricAffectingSpan.class)-origin;TextPaint run=metricPaint(origin+at,origin+next);width+=run.measureText(value,at,next);at=next;}return width;
    }
    private void append(String value){if(lines.size()>=4096)throw new IllegalArgumentException("TextView line limit");lineStarts.add(renderOffset);lineBaseLevels.add(paragraphBidi.getBaseLevel());lineLevels.add(shiraz.runtime.TextGeometry.levels(paragraphBidi,renderOffset-paragraphOrigin,renderOffset-paragraphOrigin+value.length()));advances.add(advance(value,0,value.length(),renderOffset));renderOffset+=value.length();lines.add(value);styledLines.add(null);}
    private void paragraph(String value,int width){
        paragraphOrigin=renderOffset;paragraphBidi=new java.text.Bidi(value,java.text.Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        int origin=renderOffset;
        if(singleLine||ellipsize!=null&&maxLines==1||value.isEmpty()){append(value);return;}
        if(breakStrategy!=0){optimalParagraph(value,width);return;}
        // Measure exponentially growing prefixes, then binary search cluster boundaries.
        ArrayList<Integer> ends=new shiraz.runtime.TextGeometry(value,paint).boundaries();
        int start=0;
        while(start<ends.size()-1){int low=start,high=Math.min(start+1,ends.size()-1),span=1;
            while(advance(value,ends.get(start),ends.get(high),origin)<=width){low=high;if(high==ends.size()-1)break;span*=2;high=Math.min(start+span,ends.size()-1);}
            while(high-low>1){int mid=(low+high)/2;if(advance(value,ends.get(start),ends.get(mid),origin)<=width)low=mid;else high=mid;}
            int end=Math.max(start+1,low);
            if(end<ends.size()-1){for(int i=end;i>start;i--){if(Character.isWhitespace(value.codePointAt(ends.get(i-1)))){end=i;break;}}}
            append(value.substring(ends.get(start),ends.get(end)));start=end;
        }
    }
    private int trimLineEnd(String value,int start,int end){while(end>start){int cp=value.codePointBefore(end);if(cp!=32&&cp!=9)break;end-=Character.charCount(cp);}return end;}
    private void optimalParagraph(String value,int width){
        int origin=renderOffset;
        if(advance(value,0,value.length(),origin)<=width){append(value);return;}
        java.util.Locale locale=getResources().getConfiguration().locale;
        java.text.BreakIterator iterator=java.text.BreakIterator.getLineInstance(locale);iterator.setText(value);
        boolean[] normal=new boolean[value.length()+1],cluster=new boolean[value.length()+1];cluster[0]=true;for(int at=0;at<value.length();){at=next(value,at);cluster[at]=true;}
        java.util.TreeSet<Integer> candidates=new java.util.TreeSet<Integer>();candidates.add(0);normal[0]=true;int previous=0;
        for(int end=iterator.next();end!=java.text.BreakIterator.DONE;end=iterator.next()){
            if(!cluster[end])continue;normal[end]=true;candidates.add(end);
            if(advance(value,previous,trimLineEnd(value,previous,end),origin)>width)for(int at=previous;at<end;){at=next(value,at);candidates.add(at);}
            previous=end;
        }
        candidates.add(value.length());normal[value.length()]=true;Integer[] ends=candidates.toArray(new Integer[0]);int count=ends.length;
        double[] cost=new double[count];java.util.Arrays.fill(cost,Double.POSITIVE_INFINITY);cost[0]=0;int[] parent=new int[count];float[] widths=new float[count];long measured=0;
        for(int end=1;end<count;end++){
            parent[end]=-1;
            for(int start=end-1;start>=0;start--){int left=ends[start],right=trimLineEnd(value,left,ends[end]);measured+=right-left;if(measured>8000000)throw new IllegalArgumentException("Optimal text measurement budget");float advance=advance(value,left,right,origin);
                if(advance>width&&start<end-1)break;
                double slack=Math.max(0,width-advance),penalty=end==count-1&&breakStrategy==1?0:slack*slack;
                if(!normal[ends[end]])penalty+=1e12;if(advance>width)penalty+=1e15;
                double score=cost[start]+penalty+1;
                if(score<cost[end]){cost[end]=score;parent[end]=start;widths[end]=advance;}
            }
            if(parent[end]<0)throw new IllegalStateException("No text break candidate");
        }
        ArrayList<Integer> breaks=new ArrayList<Integer>();for(int at=count-1;at>0;at=parent[at])breaks.add(at);
        for(int i=breaks.size()-1;i>=0;i--){int end=breaks.get(i);append(value.substring(ends[parent[end]],ends[end]));advances.set(advances.size()-1,widths[end]);}
    }
    private void ensure(int width){
        String rendered=displayed();
        if(text instanceof Editable&&text.length()!=0&&rendered.length()!=text.length())throw new UnsupportedOperationException("Length-changing editable transformations are not implemented");
        if(layoutWidth==width&&layoutSize==paint.getTextSize()&&layoutFace==paint.getTypeface()&&rendered.equals(layoutText)&&getResources().getConfiguration().locale.equals(layoutLocale))return;
        measuredLayout=null;layoutWidth=-1;lines.clear();lineLevels.clear();lineBaseLevels.clear();styledLines.clear();advances.clear();lineStarts.clear();renderOffset=0;for(String part:rendered.split("\n",-1)){paragraph(part,width);renderOffset++;}
        if(ellipsize!=null&&!(text instanceof Editable)){
            if(!singleLine&&maxLines!=1&&(ellipsize==TextUtils.TruncateAt.START||ellipsize==TextUtils.TruncateAt.MIDDLE))throw new UnsupportedOperationException("Multiline start/middle ellipsis is not implemented");
            int visible=Math.min(lines.size(),maxLines);
            for(int i=0;i<visible;i++){boolean hidden=i==visible-1&&lines.size()>visible;if(hidden&&(ellipsize==TextUtils.TruncateAt.START||ellipsize==TextUtils.TruncateAt.MIDDLE))throw new UnsupportedOperationException("Multiline start/middle ellipsis is not implemented");String value=lines.get(i);if(hidden||advances.get(i)>width){if(ellipsize==TextUtils.TruncateAt.MARQUEE&&(isSelected()||isFocused()))throw new UnsupportedOperationException("Active overflowing marquee requires scrolling support");final int[] cut=new int[2];String result=TextUtils.shirazEllipsize(value,paint,width,ellipsize==TextUtils.TruncateAt.MARQUEE?TextUtils.TruncateAt.END_SMALL:ellipsize,hidden,new TextUtils.EllipsizeCallback(){public void ellipsized(int start,int end){cut[0]=start;cut[1]=end;}});lines.set(i,result);java.text.Bidi ellipsisBidi=new java.text.Bidi(result,(lineBaseLevels.get(i)&1)!=0?java.text.Bidi.DIRECTION_RIGHT_TO_LEFT:java.text.Bidi.DIRECTION_LEFT_TO_RIGHT);lineLevels.set(i,shiraz.runtime.TextGeometry.levels(ellipsisBidi,0,result.length()));advances.set(i,paint.measureText(result));if(text instanceof Spanned)styledLines.set(i,ellipsizedSpans(value,lineStarts.get(i),result,cut[0],cut[1]));}}
        }
        Paint.FontMetricsInt metrics=paint.getFontMetricsInt();if(hasMetrics()){Spanned spans=(Spanned)text;for(int at=0;at<text.length();){int next=spans.nextSpanTransition(at,text.length(),android.text.style.MetricAffectingSpan.class);TextPaint run=metricPaint(at,next);Paint.FontMetricsInt m=run.getFontMetricsInt();metrics.top=Math.min(metrics.top,m.top+run.baselineShift);metrics.ascent=Math.min(metrics.ascent,m.ascent+run.baselineShift);metrics.descent=Math.max(metrics.descent,m.descent+run.baselineShift);metrics.bottom=Math.max(metrics.bottom,m.bottom+run.baselineShift);at=next;}}for(int i=0;i<lines.size();i++){android.graphics.Rect ink=new android.graphics.Rect();linePaint(i).getTextBounds(lines.get(i),0,lines.get(i).length(),ink);metrics.top=Math.min(metrics.top,ink.top);metrics.ascent=Math.min(metrics.ascent,ink.top);metrics.descent=Math.max(metrics.descent,ink.bottom);metrics.bottom=Math.max(metrics.bottom,ink.bottom);}int base=metrics.descent-metrics.ascent;
        lineStep=Math.max(0,Math.round(base*spacingMult+spacingAdd));firstBaseline=-(includePadding?metrics.top:metrics.ascent);
        int count=Math.min(lines.size(),maxLines);contentHeight=count==0?0:dimension((long)(count-1)*lineStep+firstBaseline+(includePadding?metrics.bottom:metrics.descent));
        int[] starts=new int[count+1];float[] widths=new float[count];for(int i=0;i<count;i++){starts[i]=lineStarts.get(i);widths[i]=advances.get(i);}starts[count]=rendered.length();
        int alignment=Gravity.getAbsoluteGravity(gravity,getLayoutDirection())&Gravity.HORIZONTAL_GRAVITY_MASK;
        measuredLayout=new MeasuredLayout(rendered,new TextPaint(paint),width,alignment==Gravity.CENTER_HORIZONTAL?Layout.Alignment.ALIGN_CENTER:alignment==Gravity.RIGHT?Layout.Alignment.ALIGN_OPPOSITE:Layout.Alignment.ALIGN_NORMAL,spacingMult,spacingAdd,starts,widths,firstBaseline,lineStep,contentHeight);
        ((MeasuredLayout)measuredLayout).levels=new ArrayList<byte[]>(lineLevels);((MeasuredLayout)measuredLayout).bases=new ArrayList<Integer>(lineBaseLevels);
        layoutWidth=width;layoutSize=paint.getTextSize();layoutFace=paint.getTypeface();layoutText=rendered;layoutLocale=getResources().getConfiguration().locale;
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        float widest=0;int origin=0;for(String part:displayed().split("\n",-1)){widest=Math.max(widest,advance(part,0,part.length(),origin));origin+=part.length()+1;}
        int desired=dimension((long)Math.max((int)Math.ceil(widest),Math.max(dw(compoundTop),dw(compoundBottom)))+getCompoundPaddingLeft()+getCompoundPaddingRight());
        desired=Math.max(widthLimit(textMinWidth,minWidthEms),Math.min(widthLimit(textMaxWidth,maxWidthEms),desired));
        int measured=resolveSizeAndState(Math.max(getSuggestedMinimumWidth(),desired),widthSpec,0),width=measured&MEASURED_SIZE_MASK;
        ensure(available(width));int lineCount=Math.min(lines.size(),maxLines);
        int height=dimension((long)Math.max(contentHeight,Math.max(dh(leftDrawable()),dh(rightDrawable())))+getCompoundPaddingTop()+getCompoundPaddingBottom());
        if(maxHeightPixels)height=Math.min(height,textMaxHeight);if(minHeightPixels)height=Math.max(height,textMinHeight);else if(lineCount<minLines)height=dimension((long)height+(long)getLineHeight()*(minLines-lineCount));
        setMeasuredDimension(measured,resolveSizeAndState(Math.max(getSuggestedMinimumHeight(),height),heightSpec,0));
    }
    private int verticalOffset(int height){int free=dimension((long)height-getCompoundPaddingTop()-getCompoundPaddingBottom()-contentHeight);switch(gravity&Gravity.VERTICAL_GRAVITY_MASK){case Gravity.BOTTOM:return Math.max(0,free);case Gravity.CENTER_VERTICAL:return Math.max(0,free/2);default:return 0;}}
    public int getLineCount(){return lines.size();}
    public int getLineHeight(){if(hasMetrics()){ensure(available(getWidth()));return lineStep;}Paint.FontMetricsInt m=paint.getFontMetricsInt();return Math.max(0,Math.round((m.descent-m.ascent)*spacingMult+spacingAdd));}
    @Override public int getBaseline(){ensure(available(getMeasuredWidth()));return getCompoundPaddingTop()+verticalOffset(getMeasuredHeight())+firstBaseline;}
    @Override protected void drawableStateChanged(){super.drawableStateChanged();for(Drawable d:getCompoundDrawables())if(d!=null&&d.isStateful())d.setState(getDrawableState());applyCompoundTint();invalidate();}
    public void setCursorVisible(boolean visible){cursorVisible=visible;invalidate();}
    public boolean isCursorVisible(){return cursorVisible;}
    private float lineX(int line){int alignment=Gravity.getAbsoluteGravity(gravity,getLayoutDirection())&Gravity.HORIZONTAL_GRAVITY_MASK;float x=getCompoundPaddingLeft();if(alignment==Gravity.RIGHT)x+=layoutWidth-advances.get(line);else if(alignment==Gravity.CENTER_HORIZONTAL)x+=(layoutWidth-advances.get(line))/2;return x;}
    public int getOffsetForPosition(float x,float y){
        if(ellipsize!=null)throw new UnsupportedOperationException("Ellipsized text offset mapping is not implemented");
        ensure(available(getWidth()));if(lines.isEmpty()||maxLines==0)return 0;
        int line=Math.max(0,Math.min(Math.min(lines.size(),maxLines)-1,(int)((y-getCompoundPaddingTop()-verticalOffset(getHeight()))/Math.max(1,lineStep))));String value=lines.get(line);if(hasMetrics()&&!uniformMetrics()&&java.text.Bidi.requiresBidi(value.toCharArray(),0,value.length()))throw new UnsupportedOperationException("Bidirectional metric hit testing");
        if(!hasMetrics()||uniformMetrics())return Math.min(text.length(),lineStarts.get(line)+new shiraz.runtime.TextGeometry(value,linePaint(line),lineBaseLevels.get(line)).hit(x-lineX(line)));
        float local=x-lineX(line);int previous=0;float before=0;
        for(int at=0;at<value.length();){int end=next(value,at);float after=advance(value,0,end,lineStarts.get(line));if(local<(before+after)/2)return Math.min(text.length(),lineStarts.get(line)+previous);previous=end;before=after;at=end;}
        return Math.min(text.length(),lineStarts.get(line)+value.length());
    }
    private void drawEditor(Canvas canvas,int line,float x,float baseline){
        if(!(text instanceof Editable)||!isFocused())return;int a=getSelectionStart(),b=getSelectionEnd();if(a<0||b<0)return;
        String value=lines.get(line);int start=lineStarts.get(line),end=start+value.length();Paint marker=new Paint();Paint.FontMetricsInt metrics=(uniformMetrics()?linePaint(line):paint).getFontMetricsInt();
        if(a!=b){int left=Math.max(start,Math.min(a,b)),right=Math.min(end,Math.max(a,b));if(left<right){marker.setColor(0x663399ff);if(!hasMetrics()||uniformMetrics()){float[] ranges=new shiraz.runtime.TextGeometry(value,linePaint(line),lineBaseLevels.get(line)).ranges(left-start,right-start);for(int i=0;i<ranges.length;i+=2)canvas.drawRect(x+ranges[i],baseline+metrics.ascent,x+ranges[i+1],baseline+metrics.descent,marker);}else canvas.drawRect(x+advance(value,0,left-start,start),baseline+metrics.ascent,x+advance(value,0,right-start,start),baseline+metrics.descent,marker);}}
        else if(cursorVisible&&a>=start&&a<=end&&(a!=end||line+1==lines.size()||lineStarts.get(line+1)>end)){float at=x+(hasMetrics()&&!uniformMetrics()?advance(value,0,a-start,start):new shiraz.runtime.TextGeometry(value,linePaint(line),lineBaseLevels.get(line)).caret(a-start));marker.setColor(getCurrentTextColor());canvas.drawRect(at,baseline+metrics.ascent,at+1,baseline+metrics.descent,marker);}
    }
    private CharSequence ellipsizedSpans(String original,int origin,String result,int left,int right){
        if(!displayed().equals(text.toString())||!(text instanceof Spanned))return result;
        android.text.SpannableString mapped=new android.text.SpannableString(result);if(result.isEmpty())return mapped;Spanned source=(Spanned)text;
        for(android.text.style.CharacterStyle span:source.getSpans(origin,origin+original.length(),android.text.style.CharacterStyle.class)){
            int start=Math.max(0,source.getSpanStart(span)-origin),end=Math.min(original.length(),source.getSpanEnd(span)-origin),lo=Integer.MAX_VALUE,hi=-1;
            if(start<left&&end>0){lo=start;hi=Math.min(end,left);}
            if(end>right){lo=Math.min(lo,Math.max(start,right)-right+left+1);hi=Math.max(hi,end-right+left+1);}
            int styleAt=Math.min(left,Math.max(0,original.length()-1));if(start<=styleAt&&end>styleAt){lo=Math.min(lo,left);hi=Math.max(hi,left+1);}
            if(lo<hi&&lo<result.length())mapped.setSpan(span,lo,Math.min(hi,result.length()),source.getSpanFlags(span));
        }
        return mapped;
    }
    private void drawStyledLine(Canvas canvas,int line,float x,float baseline){
        String value=lines.get(line);paint.shirazSetBidiLevels(lineLevels.get(line));if(!(text instanceof Spanned)||text.length()==0){canvas.drawText(value,x,baseline,paint);return;}
        Spanned spans=styledLines.get(line) instanceof Spanned?(Spanned)styledLines.get(line):(Spanned)text;plain(text);if(spans.getSpans(0,spans.length(),android.text.style.CharacterStyle.class).length==0){canvas.drawText(value,x,baseline,paint);return;}int start=styledLines.get(line)==null?lineStarts.get(line):0,end=Math.min(spans.length(),start+value.length());
        if(!displayed().equals(text.toString()))throw new UnsupportedOperationException("Transformed styled text");
        float position=x;for(int runStart=start;runStart<end;){int runEnd=spans.nextSpanTransition(runStart,end,android.text.style.MetricAffectingSpan.class);TextPaint measured=metricPaint(runStart,runEnd);measured.shirazSetBidiLevels(java.util.Arrays.copyOfRange(lineLevels.get(line),runStart-start,runEnd-start));String runText=value.substring(runStart-start,runEnd-start);float runWidth=measured.measureText(runText);
            for(int at=runStart;at<runEnd;){int next=spans.nextSpanTransition(at,runEnd,android.text.style.CharacterStyle.class);TextPaint draw=new TextPaint();draw.set(paint);draw.shirazSetBidiLevels(java.util.Arrays.copyOfRange(lineLevels.get(line),runStart-start,runEnd-start));draw.linkColor=linkColors.getColorForState(getDrawableState(),linkColors.getDefaultColor());for(android.text.style.CharacterStyle span:spans.getSpans(at,next,android.text.style.CharacterStyle.class))span.updateDrawState(draw);
                float[] ranges=new shiraz.runtime.TextGeometry(runText,measured).ranges(at-runStart,next-runStart);for(int r=0;r<ranges.length;r+=2){int save=canvas.save();try{canvas.clipRect(position+ranges[r],0,position+ranges[r+1],getHeight());canvas.drawText(runText,position,baseline+draw.baselineShift,draw);}finally{canvas.restoreToCount(save);}}at=next;
            }position+=runWidth;runStart=runEnd;
        }
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);drawCompounds(canvas);ensure(available(getWidth()));paint.setColor(text.length()==0&&hint!=null?getCurrentHintTextColor():getCurrentTextColor());paint.drawableState=getDrawableState();
        int save=canvas.save();try{canvas.clipRect(getCompoundPaddingLeft(),getCompoundPaddingTop(),getWidth()-getCompoundPaddingRight(),getHeight()-getCompoundPaddingBottom());
            float baseline=getCompoundPaddingTop()+verticalOffset(getHeight())+firstBaseline;int alignment=Gravity.getAbsoluteGravity(gravity,getLayoutDirection())&Gravity.HORIZONTAL_GRAVITY_MASK;
            for(int i=0;i<Math.min(lines.size(),maxLines);i++){float x=getCompoundPaddingLeft();if(alignment==Gravity.RIGHT)x+=layoutWidth-advances.get(i);else if(alignment==Gravity.CENTER_HORIZONTAL)x+=(layoutWidth-advances.get(i))/2;drawEditor(canvas,i,x,baseline);drawStyledLine(canvas,i,x,baseline);baseline+=lineStep;}
        }finally{paint.shirazSetBidiLevels(null);canvas.restoreToCount(save);}
    }
}
