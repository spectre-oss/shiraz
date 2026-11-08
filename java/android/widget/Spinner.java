// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Dropdown Spinner: shows the selected adapter view; a tap opens a
 * PopupWindow list of drop-down views. Dialog mode, prompts and entries
 * arrays from XML are not supported.
 */
public class Spinner extends AdapterView<SpinnerAdapter> {
    public static final int MODE_DIALOG=0,MODE_DROPDOWN=1;
    private SpinnerAdapter adapter;
    private int selected=INVALID_POSITION;
    private View current;
    private PopupWindow popup;
    private final DataSetObserver observer=new DataSetObserver(){
        @Override public void onChanged(){if(adapter!=null&&selected>=adapter.getCount())selected=adapter.getCount()-1;if(selected<0&&adapter!=null&&adapter.getCount()>0)selected=0;refresh(false);}
        @Override public void onInvalidated(){refresh(false);}
    };

    public Spinner(Context context){this(context,null);}
    public Spinner(Context context,int mode){this(context,null,0,0,mode);}
    public Spinner(Context context,AttributeSet attrs){this(context,attrs,0);}
    public Spinner(Context context,AttributeSet attrs,int style){this(context,attrs,style,0);}
    public Spinner(Context context,AttributeSet attrs,int style,int mode){this(context,attrs,style,0,mode);}
    public Spinner(Context context,AttributeSet attrs,int style,int resource,int mode){
        super(context,attrs,style,resource);
        if(attrs!=null){
            shiraz.runtime.XmlAttributes a=new shiraz.runtime.XmlAttributes(context,attrs);
            if(a.has("entries"))setAdapter(new Entries(context,context.getResources().getTextArray(a.reference("entries",0))));
        }
        setClickable(true);setFocusable(true);
    }
    /** android:entries rows: plain TextViews (no framework spinner item layout). */
    private static final class Entries extends BaseAdapter {
        private final Context context;private final CharSequence[] values;
        Entries(Context context,CharSequence[] values){this.context=context;this.values=values;}
        public int getCount(){return values.length;}
        public Object getItem(int position){return values[position];}
        public long getItemId(int position){return position;}
        public View getView(int position,View reused,ViewGroup parent){return row(position,reused,8);}
        @Override public View getDropDownView(int position,View reused,ViewGroup parent){return row(position,reused,16);}
        private View row(int position,View reused,int padDp){
            TextView view=reused instanceof TextView?(TextView)reused:new TextView(context);int pad=(int)(padDp*context.getResources().getDisplayMetrics().density);
            view.setText(values[position]);view.setTextSize(16);view.setPadding(pad,pad,pad,pad);view.setSingleLine(true);return view;
        }
    }
    @Override public SpinnerAdapter getAdapter(){return adapter;}
    @Override public void setAdapter(SpinnerAdapter value){
        if(adapter!=null)adapter.unregisterDataSetObserver(observer);
        adapter=value;selected=adapter!=null&&adapter.getCount()>0?0:INVALID_POSITION;
        if(adapter!=null)adapter.registerDataSetObserver(observer);
        refresh(true);
    }
    @Override public View getSelectedView(){return current;}
    @Override public void setSelection(int position){setSelection(position,false);}
    public void setSelection(int position,boolean animate){
        if(adapter==null||position<0||position>=adapter.getCount())return;
        boolean changed=position!=selected;selected=position;refresh(changed);
    }
    @Override public int getSelectedItemPosition(){return selected;}
    @Override public long getSelectedItemId(){return adapter==null||selected<0?INVALID_ROW_ID:adapter.getItemId(selected);}
    @Override public Object getSelectedItem(){return adapter==null||selected<0?null:adapter.getItem(selected);}
    @Override public int getCount(){return adapter==null?0:adapter.getCount();}
    private boolean pendingNotify;
    private void refresh(boolean notify){
        removeAllViewsInLayout();current=null;
        if(adapter!=null&&selected>=0&&selected<adapter.getCount()){
            current=adapter.getView(selected,null,this);
            ViewGroup.LayoutParams p=current.getLayoutParams();if(p==null)p=new ViewGroup.LayoutParams(-2,-2);
            addViewInLayout(current,0,p);
        }
        requestLayout();invalidate();
        if(notify&&!pendingNotify){pendingNotify=true;post(new Runnable(){public void run(){pendingNotify=false;OnItemSelectedListener l=getOnItemSelectedListener();if(l==null)return;if(selected>=0)l.onItemSelected(Spinner.this,current,selected,getSelectedItemId());else l.onNothingSelected(Spinner.this);}});}
    }
    @Override public boolean performClick(){
        if(adapter==null||adapter.getCount()==0)return false;
        if(popup!=null){popup.dismiss();return true;}
        LinearLayout list=new LinearLayout(getContext());list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(getContext());scroll.addView(list);
        for(int i=0;i<adapter.getCount()&&i<512;i++){
            final int position=i;View row=adapter.getDropDownView(i,null,list);
            row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(popup!=null)popup.dismiss();setSelection(position);}});
            list.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        int maxHeight=getRootView().getHeight()/2;
        popup=new PopupWindow(scroll,Math.max(getWidth(),(int)(160*getResources().getDisplayMetrics().density)),-2,true);
        if(maxHeight>0){scroll.measure(MeasureSpec.makeMeasureSpec(popup.getWidth(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));if(scroll.getMeasuredHeight()>maxHeight)popup.setHeight(maxHeight);}
        popup.setBackgroundDrawable(new ColorDrawable(0xffffffff));popup.setOutsideTouchable(true);
        popup.setOnDismissListener(new PopupWindow.OnDismissListener(){public void onDismiss(){popup=null;}});
        popup.showAsDropDown(this);
        return true;
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int w=getPaddingLeft()+getPaddingRight(),h=getPaddingTop()+getPaddingBottom();
        if(current!=null){measureChild(current,widthSpec,heightSpec);w+=current.getMeasuredWidth();h+=current.getMeasuredHeight();}
        setMeasuredDimension(resolveSize(Math.max(w,getSuggestedMinimumWidth()),widthSpec),resolveSize(Math.max(h,getSuggestedMinimumHeight()),heightSpec));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        if(current==null)return;int left=getPaddingLeft(),top=getPaddingTop();
        int height=Math.min(current.getMeasuredHeight(),b-t-top-getPaddingBottom());int y=top+Math.max(0,(b-t-top-getPaddingBottom()-height)/2);
        current.layout(left,y,left+Math.min(current.getMeasuredWidth(),r-l-left-getPaddingRight()),y+height);
    }
}
