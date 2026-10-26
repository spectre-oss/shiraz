// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.text.*;
public final class EditableFixture {
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void check(){
  SpannableStringBuilder text=new SpannableStringBuilder("abcd");
  Object inclusive=new Object(),exclusive=new Object();
  text.setSpan(inclusive,1,3,Spanned.SPAN_INCLUSIVE_INCLUSIVE);
  text.setSpan(exclusive,1,3,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  Selection.setSelection(text,2);
  text.insert(1,"X");text.insert(4,"Y");
  check(text.toString().equals("aXbcYd"),"Insertion text");
  check(text.getSpanStart(inclusive)==1&&text.getSpanEnd(inclusive)==5,"Inclusive endpoints");
  check(text.getSpanStart(exclusive)==2&&text.getSpanEnd(exclusive)==4,"Exclusive endpoints");
  check(Selection.getSelectionStart(text)==3&&Selection.getSelectionEnd(text)==3,"Cursor follows insertion");
  text.delete(2,4);check(text.getSpanStart(exclusive)==-1,"Deleted exclusive span removed");
  check(Selection.getSelectionStart(text)==2,"Cursor follows deletion");
  text.clearSpans();check(text.getSpans(0,text.length(),Object.class).length==0,"Clear spans retains text");
  check(text.toString().equals("aXYd"),"Clear spans text");
  Object low=new Object(),high=new Object();text.setSpan(low,0,2,Spanned.SPAN_MARK_MARK);text.setSpan(high,0,2,Spanned.SPAN_MARK_MARK|(3<<Spanned.SPAN_PRIORITY_SHIFT));
  Object[] sorted=text.getSpans(0,2,Object.class);check(sorted[0]==high&&sorted[1]==low,"Span priority");
  check(text.nextSpanTransition(0,text.length(),Object.class)==2,"Span transition");
  NoCopySpan marker=new NoCopySpan.Concrete();text.setSpan(marker,0,1,Spanned.SPAN_MARK_MARK);Selection.selectAll(text);
  SpannableStringBuilder copy=new SpannableStringBuilder(text);check(copy.getSpanStart(marker)==-1&&Selection.getSelectionStart(copy)==-1&&copy.getSpanStart(high)==0,"Copy spans without selection/watch markers");
  SpannableStringBuilder slice=(SpannableStringBuilder)text.subSequence(1,3);check(slice.toString().equals("XY")&&slice.getSpanStart(high)==0&&slice.getSpanEnd(high)==1,"Slice clips spans");
  SpannableStringBuilder paragraph=new SpannableStringBuilder("a\nb\n");Object para=new Object();paragraph.setSpan(para,0,2,Spanned.SPAN_PARAGRAPH);
  try{paragraph.setSpan(new Object(),1,2,Spanned.SPAN_PARAGRAPH);throw new AssertionError("Invalid paragraph accepted");}catch(RuntimeException expected){}
  final StringBuilder trace=new StringBuilder();final SpannableStringBuilder watched=new SpannableStringBuilder("cat");
  TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){trace.append("B:"+s+":"+start+":"+count+":"+after+";");}public void onTextChanged(CharSequence s,int start,int before,int count){trace.append("O:"+s+":"+start+":"+before+":"+count+";");}public void afterTextChanged(Editable s){trace.append("A:"+s+";");}};
  watched.setSpan(watcher,0,watched.length(),Spanned.SPAN_INCLUSIVE_INCLUSIVE);watched.replace(1,2,"oo");check(trace.toString().equals("B:cat:1:1:2;O:coot:1:1:2;A:coot;"),"Watcher text and ranges");
  final int[] events={0,0,0};SpanWatcher sw=new SpanWatcher(){public void onSpanAdded(Spannable s,Object w,int a,int b){events[0]++;}public void onSpanChanged(Spannable s,Object w,int a,int b,int c,int d){events[1]++;}public void onSpanRemoved(Spannable s,Object w,int a,int b){events[2]++;}};
  watched.setSpan(sw,0,watched.length(),Spanned.SPAN_INCLUSIVE_INCLUSIVE);events[0]=0;Object span=new Object();watched.setSpan(span,1,2,Spanned.SPAN_MARK_MARK);watched.setSpan(span,2,3,Spanned.SPAN_MARK_MARK);watched.removeSpan(span);check(events[0]==1&&events[1]==1&&events[2]==1,"Span change notifications");
  SpannableStringBuilder filtered=new SpannableStringBuilder("a");filtered.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});filtered.append("\ud83d\ude00Z");check(filtered.toString().equals("a\ud83d\ude00"),"Length filter retains whole supplementary character");filtered.clear();filtered.append("ab\ud83d\ude00");check(filtered.toString().equals("ab"),"Length filter avoids split surrogate");
  filtered.setFilters(new InputFilter[]{new InputFilter(){public CharSequence filter(CharSequence s,int a,int b,Spanned dest,int x,int y){return s.subSequence(a,b).toString().toUpperCase(java.util.Locale.ROOT);}},new InputFilter.LengthFilter(4)});filtered.replace(0,2,"abcde");check(filtered.toString().equals("ABCD"),"Filter chain");
  final boolean[] nested={false};SpannableStringBuilder recursive=new SpannableStringBuilder();recursive.setSpan(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable s){if(!nested[0]){nested[0]=true;s.append("!");}}},0,0,Spanned.SPAN_INCLUSIVE_INCLUSIVE);recursive.append("ok");check(recursive.toString().equals("ok!"),"After-change nested edit");
  java.util.Random random=new java.util.Random(17);StringBuilder expected=new StringBuilder();SpannableStringBuilder actual=new SpannableStringBuilder();
  for(int i=0;i<500;i++){int a=random.nextInt(expected.length()+1),b=a+random.nextInt(expected.length()-a+1);String value=i%3==0?"":"p"+i;expected.replace(a,b,value);actual.replace(a,b,value);check(actual.toString().equals(expected.toString()),"Gap buffer edit sequence "+i);char[] chars=new char[actual.length()+2];actual.getChars(0,actual.length(),chars,1);check(new String(chars,1,actual.length()).equals(expected.toString()),"Gap character copy");}
  SpannableStringBuilder proportion=new SpannableStringBuilder("abcdef");Selection.setSelection(proportion,2,4);proportion.replace(1,5,"XY");check(proportion.toString().equals("aXYf")&&Selection.getSelectionStart(proportion)==1&&Selection.getSelectionEnd(proportion)==2,"Selection proportional replacement");
  Editable made=Editable.Factory.getInstance().newEditable("factory");made.append('!');check(made.toString().equals("factory!"),"Editable factory");
  System.out.println("EDITABLE_SPANS_FILTERS_SELECTION_OK");
 }
}
