// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
/** Editable completion model. Anchored nonmodal suggestion presentation is not yet available. */
public class AutoCompleteTextView extends EditText implements Filter.FilterListener {
 public interface Validator {boolean isValid(CharSequence text);CharSequence fixText(CharSequence invalidText);}
 public interface OnDismissListener {void onDismiss();}
 private ListAdapter adapter;private Filter filter;private int threshold=2;private boolean blocking;private Validator validator;
 private AdapterView.OnItemClickListener click;private AdapterView.OnItemSelectedListener selected;private OnDismissListener dismiss;
 private android.graphics.drawable.Drawable dropdownBackground;
 public AutoCompleteTextView(Context c){this(c,null);}public AutoCompleteTextView(Context c,AttributeSet a){this(c,a,0x0101006b);}public AutoCompleteTextView(Context c,AttributeSet a,int s){this(c,a,s,0);}public AutoCompleteTextView(Context c,AttributeSet a,int s,int r){super(c,a,s,r);shiraz.runtime.XmlAttributes attrs=new shiraz.runtime.XmlAttributes(c,a,s,r);setThreshold(attrs.integer("completionThreshold",2));}
 public <T extends ListAdapter & Filterable> void setAdapter(T value){adapter=value;filter=value==null?null:value.getFilter();}
 public ListAdapter getAdapter(){return adapter;}protected Filter getFilter(){return filter;}
 public void setThreshold(int value){threshold=Math.max(1,value);}public int getThreshold(){return threshold;}public boolean enoughToFilter(){return getText().length()>=threshold;}
 public void setText(CharSequence value,boolean filter){boolean old=blocking;blocking=!filter;try{setText(value);}finally{blocking=old;}}
 public boolean isPerformingCompletion(){return blocking;}
 @Override protected void onTextChanged(CharSequence value,int start,int before,int count){super.onTextChanged(value,start,before,count);if(!blocking&&filter!=null){if(enoughToFilter())performFiltering(value,0);else{dismissDropDown();filter.filter(null);}}}
 protected void performFiltering(CharSequence text,int keyCode){if(filter!=null)filter.filter(text,this);}
 public void onFilterComplete(int count){if(count>0&&enoughToFilter()&&hasFocus()&&isAttachedToWindow())showDropDown();else dismissDropDown();}
 protected CharSequence convertSelectionToString(Object value){return filter==null?(value==null?"":value.toString()):filter.convertResultToString(value);}
 protected void replaceText(CharSequence text){boolean old=blocking;blocking=true;try{setText(text);setSelection(getText().length());}finally{blocking=old;}}
 public void performCompletion(){if(isPopupShowing())throw new UnsupportedOperationException("Autocomplete popup selection is unavailable");}
 public void setValidator(Validator value){validator=value;}public Validator getValidator(){return validator;}
 public void performValidation(){if(validator!=null&&getText().length()>0&&!validator.isValid(getText()))setText(validator.fixText(getText()));}
 public void setOnItemClickListener(AdapterView.OnItemClickListener value){click=value;}public AdapterView.OnItemClickListener getOnItemClickListener(){return click;}
 public void setOnItemSelectedListener(AdapterView.OnItemSelectedListener value){selected=value;}public AdapterView.OnItemSelectedListener getOnItemSelectedListener(){return selected;}
 public void setOnDismissListener(OnDismissListener value){dismiss=value;}
 public void setDropDownBackgroundDrawable(android.graphics.drawable.Drawable value){dropdownBackground=value;}public android.graphics.drawable.Drawable getDropDownBackground(){return dropdownBackground;}
 public void setDropDownBackgroundResource(int resource){setDropDownBackgroundDrawable(getContext().getDrawable(resource));}
 private int dropAnchor,dropWidth=-2,dropHeight=-2,dropVertical,dropHorizontal,inputMethodMode,softInputMode;private boolean dropModal;
 public int getDropDownAnchor(){return dropAnchor;}public void setDropDownAnchor(int id){dropAnchor=id;}
 public int getDropDownWidth(){return dropWidth;}public void setDropDownWidth(int value){dropWidth=value;}
 public int getDropDownHeight(){return dropHeight;}public void setDropDownHeight(int value){dropHeight=value;}
 public int getDropDownVerticalOffset(){return dropVertical;}public void setDropDownVerticalOffset(int value){dropVertical=value;}
 public int getDropDownHorizontalOffset(){return dropHorizontal;}public void setDropDownHorizontalOffset(int value){dropHorizontal=value;}
 public int getDropDownInputMethodMode(){return inputMethodMode;}public void setDropDownInputMethodMode(int value){inputMethodMode=value;}
 public void setInputMethodMode(int value){inputMethodMode=value;}public void setDropDownAlwaysVisible(boolean value){}
 public boolean isDropDownAlwaysVisible(){return false;}public void setDropDownDismissedOnCompletion(boolean value){}
 public boolean isDropDownDismissedOnCompletion(){return true;}public void setListSelection(int position){}public int getListSelection(){return -1;}
 public void clearListSelection(){}public AdapterView getListView(){return null;}
 public boolean isPopupShowing(){return false;}
 public void dismissDropDown(){}
 public void showDropDown(){}
}
