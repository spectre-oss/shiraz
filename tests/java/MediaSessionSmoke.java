// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.os.Looper;
import android.content.ContextWrapper;
public final class MediaSessionSmoke {
 public static void main(String[] args){
  Looper.prepareMainLooper();
  MediaSessionFixture.check(new ContextWrapper(null){public String getPackageName(){return "org.shiraz.media";}});
 }
}
