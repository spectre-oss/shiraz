// SPDX-License-Identifier: GPL-3.0-only
import android.os.Parcel;
import android.view.inputmethod.*;
public final class InputMethodMetadataSmoke {
    public static void main(String[] args){
        InputMethodManager manager=InputMethodManager.shirazHardwareOnly();
        if(!manager.getEnabledInputMethodList().isEmpty()||!manager.getInputMethodList().isEmpty()||manager.getCurrentInputMethodSubtype()!=null||manager.getLastInputMethodSubtype()!=null)throw new AssertionError("synthetic Android IME advertised");
        InputMethodSubtype subtype=new InputMethodSubtype.InputMethodSubtypeBuilder().setSubtypeLocale("fa_IR").setLanguageTag("fa-IR").setSubtypeMode("keyboard").setSubtypeExtraValue("AsciiCapable,layout=qwerty").setIsAsciiCapable(true).build();
        if(!"fa_IR".equals(subtype.getLocale())||!"fa-IR".equals(subtype.getLanguageTag())||!"keyboard".equals(subtype.getMode())||!"fa".equals(subtype.getLocaleObject().getLanguage())||!subtype.containsExtraValueKey("AsciiCapable")||!"qwerty".equals(subtype.getExtraValueOf("layout")))throw new AssertionError("subtype metadata");
        Parcel parcel=Parcel.obtain();subtype.writeToParcel(parcel,0);parcel.setDataPosition(0);InputMethodSubtype restored=InputMethodSubtype.CREATOR.createFromParcel(parcel);if(!restored.equals(subtype)||restored.hashCode()!=subtype.hashCode())throw new AssertionError("subtype parcel");parcel.recycle();
        InputMethodInfo info=new InputMethodInfo("org.shiraz.ime","org.shiraz.ime.Service","Metadata","Settings");
        if(info.getSubtypeCount()!=0||!manager.getEnabledInputMethodSubtypeList(info,true).isEmpty()||!info.getId().equals("org.shiraz.ime/.Service"))throw new AssertionError("IME identity");
        System.out.println("INPUT_METHOD_METADATA_OK");
    }
}
