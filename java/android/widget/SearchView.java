package android.widget;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;

/**
 * Minimal SearchView: a horizontal layout holding one EditText. Text changes
 * and the IME/enter action reach the OnQueryTextListener. No suggestions,
 * voice search or iconified collapse animation.
 */
public class SearchView extends LinearLayout {
    public interface OnQueryTextListener {boolean onQueryTextSubmit(String query);boolean onQueryTextChange(String newText);}
    public interface OnCloseListener {boolean onClose();}

    private final EditText query;
    private OnQueryTextListener listener;
    private OnCloseListener closeListener;
    private boolean iconified,submitEnabled;
    private CharSequence hint;

    public SearchView(Context context){this(context,null);}
    public SearchView(Context context,AttributeSet attrs){this(context,attrs,0);}
    public SearchView(Context context,AttributeSet attrs,int style){this(context,attrs,style,0);}
    public SearchView(Context context,AttributeSet attrs,int style,int resource){
        super(context,attrs,style,resource);
        setOrientation(HORIZONTAL);
        query=new EditText(context);
        query.setSingleLine(true);
        addView(query,new LinearLayout.LayoutParams(0,-2,1f));
        query.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){}
            public void afterTextChanged(Editable s){if(listener!=null)listener.onQueryTextChange(s.toString());}
        });
        query.setOnEditorActionListener(new TextView.OnEditorActionListener(){
            public boolean onEditorAction(TextView view,int actionId,KeyEvent event){
                if(event!=null&&event.getAction()!=KeyEvent.ACTION_DOWN)return true;
                submit();return true;
            }
        });
    }
    private void submit(){if(listener!=null)listener.onQueryTextSubmit(query.getText().toString());}
    public void setOnQueryTextListener(OnQueryTextListener value){listener=value;}
    public void setOnCloseListener(OnCloseListener value){closeListener=value;}
    public CharSequence getQuery(){return query.getText();}
    public void setQuery(CharSequence value,boolean submit){query.setText(value==null?"":value);if(submit)submit();}
    public void setQueryHint(CharSequence value){hint=value;query.setHint(value);}
    public CharSequence getQueryHint(){return hint;}
    public void setImeOptions(int value){query.setImeOptions(value);}
    public int getImeOptions(){return query.getImeOptions();}
    public void setInputType(int value){query.setInputType(value);}
    public void setSubmitButtonEnabled(boolean value){submitEnabled=value;}
    public boolean isSubmitButtonEnabled(){return submitEnabled;}
    public void setIconified(boolean value){if(value&&!iconified&&closeListener!=null&&closeListener.onClose())return;iconified=value;}
    public boolean isIconified(){return iconified;}
    public void setIconifiedByDefault(boolean value){iconified=value;}
    public void setMaxWidth(int value){}
}
