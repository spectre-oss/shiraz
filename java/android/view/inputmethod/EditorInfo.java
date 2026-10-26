// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;
/** Editor metadata and action constants. An input-method connection is not implemented. */
public class EditorInfo implements android.text.InputType {
 public static final int IME_NULL=0,IME_MASK_ACTION=0xff,IME_ACTION_UNSPECIFIED=0,IME_ACTION_NONE=1,IME_ACTION_GO=2,IME_ACTION_SEARCH=3,IME_ACTION_SEND=4,IME_ACTION_NEXT=5,IME_ACTION_DONE=6,IME_ACTION_PREVIOUS=7;
 public static final int IME_FLAG_NO_PERSONALIZED_LEARNING=0x1000000,IME_FLAG_NO_FULLSCREEN=0x2000000,IME_FLAG_NAVIGATE_PREVIOUS=0x4000000,IME_FLAG_NAVIGATE_NEXT=0x8000000,IME_FLAG_NO_EXTRACT_UI=0x10000000,IME_FLAG_NO_ACCESSORY_ACTION=0x20000000,IME_FLAG_NO_ENTER_ACTION=0x40000000,IME_FLAG_FORCE_ASCII=0x80000000;
 public android.os.LocaleList hintLocales;public String[] contentMimeTypes;
 public int inputType,imeOptions,actionId,initialSelStart=-1,initialSelEnd=-1,initialCapsMode,fieldId;
 public CharSequence actionLabel,hintText;public String privateImeOptions,packageName,fieldName;public android.os.Bundle extras;
}
