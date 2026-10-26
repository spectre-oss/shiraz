// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.util.Property;
import android.util.NoSuchPropertyException;
public final class PropertyFixture {
 public static class Bean {
  public int field=2;
  private float value;
  public float getValue(){return value;}
  public void setValue(float v){value=v;}
  public boolean isEnabled(){return true;}
  public String getBroken(){throw new IllegalStateException("getter");}
  public int getField(){return 7;}
 }
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void check(){
  Bean b=new Bean();Property<Bean,Float> p=Property.of(Bean.class,Float.class,"value");
  p.set(b,3.5f);check(p.get(b)==3.5f&&!p.isReadOnly()&&p.getType()==Float.class&&p.getName().equals("value"),"Primitive accessor and metadata");
  Property<Bean,Boolean> enabled=Property.of(Bean.class,Boolean.class,"enabled");check(enabled.get(b)&&enabled.isReadOnly(),"Boolean getter read-only");
  try{enabled.set(b,false);throw new AssertionError("Read-only write");}catch(UnsupportedOperationException expected){}
  Property<Bean,Integer> field=Property.of(Bean.class,Integer.class,"field");check(field.get(b)==7&&field.isReadOnly(),"Getter takes precedence over public field");
  Property<Bean,Float> direct=new Property<Bean,Float>(Float.class,"direct") {public Float get(Bean bean){return bean.value;}public void set(Bean bean,Float value){bean.value=value;}};
  direct.set(b,9f);check(direct.get(b)==9f,"Typed subclass dispatch");
  Property<Holder,Integer> publicField=Property.of(Holder.class,Integer.class,"number");Holder holder=new Holder();publicField.set(holder,42);check(publicField.get(holder)==42&&!publicField.isReadOnly(),"Public field access");
  try{Property.of(Bean.class,Integer.class,"value");throw new AssertionError("Type mismatch accepted");}catch(NoSuchPropertyException expected){}
  try{Property.of(Bean.class,String.class,"missing");throw new AssertionError("Missing property accepted");}catch(NoSuchPropertyException expected){}
  try{Property.of(Bean.class,String.class,"broken").get(b);throw new AssertionError("Getter error swallowed");}catch(RuntimeException expected){check(expected.getCause() instanceof IllegalStateException,"Getter cause preserved");}
  System.out.println("PROPERTY_REFLECTION_OK");
 }
 public static class Holder {public int number;}
}
