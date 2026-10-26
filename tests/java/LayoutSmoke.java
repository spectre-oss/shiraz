// SPDX-License-Identifier: GPL-3.0-only
/** Direct VM coverage, independent of unrelated APK fixture services. */
public final class LayoutSmoke {
 public static void main(String[] args){
  org.shiraz.fixture.BoringLayoutFixture.check();
  org.shiraz.fixture.StaticLayoutFixture.check();
  android.text.TextPaint paint=new android.text.TextPaint();paint.setTextSize(20);
  android.text.SpannableString text=new android.text.SpannableString("center\nnormal");
  text.setSpan(new android.text.style.AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER),0,7,android.text.Spanned.SPAN_PARAGRAPH);
  android.text.StaticLayout layout=new android.text.StaticLayout(text,paint,300,android.text.Layout.Alignment.ALIGN_NORMAL,1,0,false);
  if(layout.getParagraphAlignment(0)!=android.text.Layout.Alignment.ALIGN_CENTER||layout.getParagraphAlignment(1)!=android.text.Layout.Alignment.ALIGN_NORMAL)throw new AssertionError("paragraph alignment span boundaries");
  if(layout.getLineLeft(0)<=0||layout.getLineLeft(1)!=0)throw new AssertionError("paragraph horizontal alignment");
  System.out.println("LAYOUT_GEOMETRY_REPLACEMENT_OK");
 }
}
