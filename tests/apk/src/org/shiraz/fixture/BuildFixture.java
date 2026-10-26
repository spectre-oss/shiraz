// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.os.Build;
/** Read actual SDK fields; no replacement APK or host identity is involved. */
public final class BuildFixture {
 public static void check(String expectedVersion){
  String expected="Shiraz/shiraz/linux:8.1/shiraz/"+expectedVersion+":host/unsigned";
  if(!expected.equals(Build.FINGERPRINT)||!("Shiraz "+expectedVersion).equals(Build.DISPLAY)||!expectedVersion.equals(Build.VERSION.INCREMENTAL))throw new AssertionError("Runtime build identity/version");
  if(!Build.TYPE.equals("host")||!Build.TAGS.equals("unsigned")||!Build.ID.equals("shiraz")||Build.VERSION.SDK_INT!=27)throw new AssertionError("Runtime compatibility profile");
  System.out.println("SHIRAZ_FIXTURE_BUILD_IDENTITY "+Build.FINGERPRINT);
 }
}
