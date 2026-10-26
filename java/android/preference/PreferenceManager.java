// SPDX-License-Identifier: GPL-3.0-only
package android.preference;
import android.content.Context;
import android.content.SharedPreferences;
/** Default preference file lookup only; no preference screen widgets. */
public class PreferenceManager {
 private PreferenceManager(){}
 public static String getDefaultSharedPreferencesName(Context context){return context.getPackageName()+"_preferences";}
 public static SharedPreferences getDefaultSharedPreferences(Context context){return context.getSharedPreferences(getDefaultSharedPreferencesName(context),Context.MODE_PRIVATE);}
}
