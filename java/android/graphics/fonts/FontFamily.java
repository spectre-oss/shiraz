// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.fonts;
/** Immutable font alternatives selected by weight and slant. */
public final class FontFamily {
 private final Font[] fonts;
 private FontFamily(Font[] fonts){this.fonts=fonts;}
 public int getSize(){return fonts.length;}
 public Font getFont(int index){return fonts[index];}
 public static final class Builder {
  private final java.util.ArrayList<Font> fonts=new java.util.ArrayList<Font>();
  public Builder(Font font){addFont(font);}
  public Builder addFont(Font font){
   if(font==null)throw new NullPointerException("font");
   if(fonts.size()>=64)throw new IllegalArgumentException("Font family limit");
   for(Font old:fonts)if(old.getStyle().equals(font.getStyle()))throw new IllegalArgumentException("Duplicate font style");
   fonts.add(font);return this;
  }
  public FontFamily build(){return new FontFamily(fonts.toArray(new Font[fonts.size()]));}
 }
}
