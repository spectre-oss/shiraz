// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;import android.view.*;import android.widget.*;
public final class RelativeFixture {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static void size(View v,int w,int h){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);}
 private static final class HeightProbe extends View {
  int flexible,unspecified;
  HeightProbe(Context c){super(c);}
  protected void onMeasure(int w,int h){if(MeasureSpec.getMode(h)==MeasureSpec.AT_MOST)flexible++;if(MeasureSpec.getMode(h)==MeasureSpec.UNSPECIFIED)unspecified++;super.onMeasure(w,h);}
 }
 public static void check(Context context){RelativeLayout root=new RelativeLayout(context);root.setPadding(4,5,6,7);View anchor=new View(context);anchor.setId(1);RelativeLayout.LayoutParams ap=new RelativeLayout.LayoutParams(20,10);ap.leftMargin=2;ap.topMargin=3;
  View next=new View(context);next.setId(2);RelativeLayout.LayoutParams np=new RelativeLayout.LayoutParams(30,15);np.addRule(RelativeLayout.RIGHT_OF,1);np.addRule(RelativeLayout.BELOW,1);np.leftMargin=3;np.topMargin=2;root.addView(next,np);root.addView(anchor,ap);size(root,120,80);check(anchor.getLeft()==6&&anchor.getTop()==8&&next.getLeft()==29&&next.getTop()==20,"Dependency order and margins");
  View edge=new View(context);RelativeLayout.LayoutParams ep=new RelativeLayout.LayoutParams(10,12);ep.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);ep.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);root.addView(edge,ep);size(root,120,80);check(edge.getLeft()==104&&edge.getTop()==61,"Parent trailing edges");size(root,200,100);check(edge.getLeft()==184&&edge.getTop()==81,"Resize repositions edges");
  View stretch=new View(context);RelativeLayout.LayoutParams sp=new RelativeLayout.LayoutParams(-2,4);sp.addRule(RelativeLayout.ALIGN_LEFT,1);sp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);root.addView(stretch,sp);size(root,120,80);check(stretch.getLeft()==6&&stretch.getWidth()==108,"Opposing edges constrain width");
  RelativeLayout rtl=new RelativeLayout(context);View start=new View(context);RelativeLayout.LayoutParams rp=new RelativeLayout.LayoutParams(10,10);rp.addRule(RelativeLayout.ALIGN_PARENT_START);rtl.addView(start,rp);size(rtl,100,40);check(start.getLeft()==0,"LTR start");rtl.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);size(rtl,100,40);check(start.getLeft()==90,"RTL start");rtl.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);size(rtl,100,40);check(start.getLeft()==0,"Direction round trip");
  RelativeLayout wrap=new RelativeLayout(context);View one=new View(context);one.setId(4);View two=new View(context);RelativeLayout.LayoutParams p1=new RelativeLayout.LayoutParams(20,10),p2=new RelativeLayout.LayoutParams(30,15);p2.addRule(RelativeLayout.RIGHT_OF,4);p2.addRule(RelativeLayout.BELOW,4);wrap.addView(two,p2);wrap.addView(one,p1);wrap.measure(View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.AT_MOST));check(wrap.getMeasuredWidth()==50&&wrap.getMeasuredHeight()==25,"Wrap sibling chain");
  RelativeLayout gone=new RelativeLayout(context);View a=new View(context),b=new View(context),c=new View(context);a.setId(11);b.setId(12);b.setVisibility(View.GONE);RelativeLayout.LayoutParams pa=new RelativeLayout.LayoutParams(10,10),pb=new RelativeLayout.LayoutParams(10,10),pc=new RelativeLayout.LayoutParams(10,10);pb.addRule(RelativeLayout.RIGHT_OF,11);pc.addRule(RelativeLayout.RIGHT_OF,12);gone.addView(a,pa);gone.addView(b,pb);gone.addView(c,pc);size(gone,100,30);check(c.getLeft()==10,"Gone anchor follows rule chain");
  RelativeLayout baseline=new RelativeLayout(context);TextView big=new TextView(context),small=new TextView(context);big.setId(31);big.setText("Big");big.setTextSize(24);small.setText("small");small.setTextSize(12);RelativeLayout.LayoutParams bp=new RelativeLayout.LayoutParams(-2,-2),smallp=new RelativeLayout.LayoutParams(-2,-2);smallp.addRule(RelativeLayout.ALIGN_BASELINE,31);smallp.addRule(RelativeLayout.RIGHT_OF,31);baseline.addView(small,smallp);baseline.addView(big,bp);size(baseline,200,60);check(big.getTop()+big.getBaseline()==small.getTop()+small.getBaseline(),"Text baseline alignment");
  View fallback=new View(context);RelativeLayout.LayoutParams fp=new RelativeLayout.LayoutParams(10,10);fp.alignWithParent=true;fp.addRule(RelativeLayout.ALIGN_RIGHT,999);fp.addRule(RelativeLayout.ALIGN_BOTTOM,999);baseline.addView(fallback,fp);size(baseline,200,60);check(fallback.getLeft()==190&&fallback.getTop()==50,"Missing edge anchor uses parent");
  p1.addRule(RelativeLayout.RIGHT_OF,5);two.setId(5);try{size(wrap,100,100);throw new AssertionError("Cycle accepted");}catch(IllegalStateException expected){}
  // A MATCH_PARENT viewport must not see a content-sizing pass with the full
  // parent height before its sibling constraints are resolved. RecyclerView
  // auto-measures children in such a pass, changing its scroll anchor.
  RelativeLayout chat=new RelativeLayout(context);HeightProbe viewport=new HeightProbe(context);
  View composer=new View(context);composer.setId(41);
  RelativeLayout.LayoutParams composeParams=new RelativeLayout.LayoutParams(-1,120);composeParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
  RelativeLayout.LayoutParams viewportParams=new RelativeLayout.LayoutParams(-1,-1);viewportParams.addRule(RelativeLayout.ABOVE,41);
  chat.addView(viewport,viewportParams);chat.addView(composer,composeParams);size(chat,400,300);
  check(viewport.getHeight()==180&&viewport.flexible==0&&viewport.unspecified==0,"Bounded viewport has exact height throughout measurement");
  composer.requestLayout();size(chat,400,300);check(viewport.getHeight()==180&&viewport.flexible==0,"Sibling relayout does not content-measure viewport");
  composeParams.height=160;composer.setLayoutParams(composeParams);size(chat,400,300);check(viewport.getHeight()==140&&viewport.flexible==0,"Viewport still responds to genuine size changes");
  RelativeLayout natural=new RelativeLayout(context);HeightProbe wrapped=new HeightProbe(context);natural.addView(wrapped,new RelativeLayout.LayoutParams(-1,-2));size(natural,400,300);check(wrapped.flexible>0,"Wrap content retains flexible measurement");
  RelativeLayout unbounded=new RelativeLayout(context);HeightProbe unlimited=new HeightProbe(context);unbounded.addView(unlimited,new RelativeLayout.LayoutParams(-1,-1));unbounded.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));check(unlimited.unspecified>0,"Unbounded parent remains unspecified");
  System.out.println("RELATIVE_RULES_LAYOUT_OK");
 }
}
