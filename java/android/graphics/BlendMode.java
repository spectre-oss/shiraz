// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
/** Modern blend identifiers. The legacy compositor supports the Porter-Duff subset. */
public enum BlendMode {
    CLEAR, SRC, DST, SRC_OVER, DST_OVER, SRC_IN, DST_IN, SRC_OUT, DST_OUT,
    SRC_ATOP, DST_ATOP, XOR, PLUS, MODULATE, SCREEN, OVERLAY, DARKEN, LIGHTEN,
    COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION,
    MULTIPLY, HUE, SATURATION, COLOR, LUMINOSITY;
    PorterDuff.Mode legacy(){
        switch(this){
            case PLUS:return PorterDuff.Mode.ADD;
            case MODULATE:return PorterDuff.Mode.MULTIPLY;
            case COLOR_DODGE:case COLOR_BURN:case HARD_LIGHT:case SOFT_LIGHT:
            case DIFFERENCE:case EXCLUSION:case MULTIPLY:case HUE:case SATURATION:
            case COLOR:case LUMINOSITY:throw new UnsupportedOperationException("Blend mode is not implemented: "+this);
            default:return PorterDuff.Mode.valueOf(name());
        }
    }
    static BlendMode fromLegacy(PorterDuff.Mode mode){
        if(mode==PorterDuff.Mode.ADD)return PLUS;
        if(mode==PorterDuff.Mode.MULTIPLY)return MODULATE;
        return valueOf(mode.name());
    }
}
