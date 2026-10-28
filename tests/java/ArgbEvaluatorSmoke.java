import android.animation.ArgbEvaluator;
public final class ArgbEvaluatorSmoke {
 public static void main(String[] args) {
  ArgbEvaluator e = new ArgbEvaluator();
  check((Integer)e.evaluate(0f,0x12345678,0xabcdef12)==0x12345678);
  check((Integer)e.evaluate(1f,0x12345678,0xabcdef12)==0xabcdef12);
  check((Integer)e.evaluate(.5f,0xff000000,0xffffffff)==0xffbababa);
  check((Integer)e.evaluate(.5f,0x00000000,0xff000000)==0x80000000);
  check(ArgbEvaluator.getInstance()==ArgbEvaluator.getInstance());
  System.out.println("ARGB_EVALUATOR_OK");
 }
 private static void check(boolean value){if(!value)throw new AssertionError();}
}
