// SPDX-License-Identifier: GPL-3.0-only
public final class EditableSmoke {
 public static void main(String[] args){org.shiraz.fixture.EditableFixture.check();char[] chars=new char[32768];java.util.Arrays.fill(chars,'x');android.text.SpannableStringBuilder text=new android.text.SpannableStringBuilder(new String(chars));try{text.append('x');throw new AssertionError("Editable size limit");}catch(IllegalArgumentException expected){}if(text.length()!=32768)throw new AssertionError("Failed edit mutated text");try{text.replace(0,1,"x",0,2);throw new AssertionError("Source bounds");}catch(IndexOutOfBoundsException expected){}if(text.length()!=32768)throw new AssertionError("Invalid source mutated text");}
}
