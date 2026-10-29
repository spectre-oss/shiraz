// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.res.Resources;
import android.text.*;
import android.text.method.*;
import android.view.*;
import android.widget.*;
public final class NumericInputSmoke {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        android.os.Looper.prepare();
        Context context=new ContextWrapper(null){public Resources getResources(){return Resources.getSystem();}public Object getSystemService(String name){return null;}};
        EditText field=new EditText(context);
        int flags=InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED|InputType.TYPE_NUMBER_FLAG_DECIMAL;
        field.setInputType(flags|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        check(field.getInputType()==(flags|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES),"class-specific unrelated flags preserved");
        field.setInputType(flags);
        check(field.getInputType()==flags&&field.getKeyListener() instanceof DigitsKeyListener,"numeric listener selected");
        field.setText("-12.5");
        Editable text=field.getText();text.append("x.6-");
        check(text.toString().equals("-12.56"),"invalid characters, second point and trailing sign filtered");
        text.insert(0,"9");check(text.toString().equals("-12.56"),"retained sign cannot move into middle");
        text.replace(0,text.length(),"+3.25");check(text.toString().equals("+3.25"),"full replacement can replace sign and point");
        text.replace(2,3,".");check(text.toString().equals("+3.25"),"replacing existing point permitted");
        text.replace(0,text.length(),"");Selection.setSelection(text,0);
        field.getKeyListener().onKeyOther(field,text,new KeyEvent(1,"-5.2x",0,0));
        check(text.toString().equals("-5.2"),"committed text uses filter");
        field.getKeyListener().onKeyDown(field,text,KeyEvent.KEYCODE_DEL,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL));
        check(text.toString().equals("-5."),"numeric backspace");
        field.setInputType(InputType.TYPE_CLASS_NUMBER);field.setText("a-1.2+3");
        check(field.getText().toString().equals("123"),"unsigned integer filter");
        field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        check(field.getTransformationMethod() instanceof PasswordTransformationMethod,"numeric password masks");
        field.setInputType(InputType.TYPE_CLASS_TEXT);field.setText("letters");
        check(field.getText().toString().equals("letters")&&field.getTransformationMethod()==null,"switch removes numeric filter and password");
        int before=field.getInputType();try{field.setInputType(InputType.TYPE_CLASS_PHONE);throw new AssertionError("unsupported type");}catch(UnsupportedOperationException expected){}
        check(field.getInputType()==before,"unsupported type preserves state");
        System.out.println("NUMERIC_INPUT_FILTER_EDIT_OK");
    }
}
