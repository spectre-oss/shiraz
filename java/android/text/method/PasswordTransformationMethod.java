// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;
/** Non-editable masking; transient character revelation and editable span watchers are unsupported. */
public class PasswordTransformationMethod implements TransformationMethod {
 private static final PasswordTransformationMethod INSTANCE=new PasswordTransformationMethod();
 public static PasswordTransformationMethod getInstance(){return INSTANCE;}
 public CharSequence getTransformation(CharSequence source,android.view.View view){if(source==null)return null;if(source.length()>32768)throw new IllegalArgumentException("Text limit");char[] mask=new char[source.length()];java.util.Arrays.fill(mask,'\u2022');return new String(mask);}
 public void onFocusChanged(android.view.View view,CharSequence source,boolean focused,int direction,android.graphics.Rect previous){}
}
