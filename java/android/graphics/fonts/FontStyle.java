// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.fonts;
/** Immutable font matching request. */
public final class FontStyle {
 public static final int FONT_SLANT_UPRIGHT=0,FONT_SLANT_ITALIC=1;
 private final int weight,slant;
 public FontStyle(){this(400,0);}
 public FontStyle(int weight,int slant){if(weight<1||weight>1000||slant<0||slant>1)throw new IllegalArgumentException("Font style");this.weight=weight;this.slant=slant;}
 public int getWeight(){return weight;}public int getSlant(){return slant;}
 public int getMatchScore(FontStyle other){return Math.abs(weight-other.weight)/100+(slant==other.slant?0:2);}
 @Override public boolean equals(Object o){return o instanceof FontStyle&&weight==((FontStyle)o).weight&&slant==((FontStyle)o).slant;}
 @Override public int hashCode(){return weight*31+slant;}
}
