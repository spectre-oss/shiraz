// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
public class PorterDuffXfermode extends Xfermode {
 final PorterDuff.Mode mode;
 public PorterDuffXfermode(PorterDuff.Mode mode){if(mode==null)throw new NullPointerException("mode");this.mode=mode;}
}
