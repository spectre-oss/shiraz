// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;
public class AllCapsTransformationMethod implements TransformationMethod2 {
 private final android.content.Context context;private boolean allow;
 public AllCapsTransformationMethod(android.content.Context context){if(context==null)throw new NullPointerException("context");this.context=context;}
 public void setLengthChangesAllowed(boolean value){allow=value;}
 public CharSequence getTransformation(CharSequence source,android.view.View view){if(source==null||!allow)return source;return source.toString().toUpperCase(context.getResources().getConfiguration().locale);}
 public void onFocusChanged(android.view.View view,CharSequence source,boolean focused,int direction,android.graphics.Rect previous){}
}
