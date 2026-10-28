// SPDX-License-Identifier: GPL-3.0-only
package android.app;

/** Type discovery for engines that distinguish Java and native activities.
 * The ANativeActivity lifecycle bridge is not implemented. */
public class NativeActivity extends Activity {
 public static final String META_DATA_LIB_NAME="android.app.lib_name";
 public static final String META_DATA_FUNC_NAME="android.app.func_name";
 @Override protected void onCreate(android.os.Bundle savedState){
  throw new UnsupportedOperationException("NativeActivity lifecycle is not implemented");
 }
}
