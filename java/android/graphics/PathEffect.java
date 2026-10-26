// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
/** Base for immutable geometry effects. Unsupported effect kinds reject at Paint assignment. */
public class PathEffect {
    public static void shirazValidate(PathEffect value) { if(value!=null && !(value instanceof DashPathEffect) && !(value instanceof CornerPathEffect)) throw new UnsupportedOperationException("Only dash and corner path effects are implemented"); }
}
