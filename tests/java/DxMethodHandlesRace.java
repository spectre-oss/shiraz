// SPDX-License-Identifier: GPL-3.0-only
// First-party concurrency regression for the pinned host DX tool.
import com.android.dx.dex.DexOptions;
import com.android.dx.dex.file.*;
import com.android.dx.rop.cst.*;
import com.android.dx.rop.type.Type;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
public final class DxMethodHandlesRace {
  public static void main(String[] args) throws Exception {
    final int threads=8, each=1024, total=threads*each;
    final CstMethodHandle[] handles=new CstMethodHandle[total];
    CstType owner=CstType.intern(Type.intern("Lstress/Owner;"));
    for(int i=0;i<total;i++) handles[i]=CstMethodHandle.make(CstMethodHandle.METHOD_HANDLE_TYPE_INVOKE_STATIC,
      new CstMethodRef(owner,new CstNat(new CstString("m"+i),new CstString("()V"))));
    for(int round=0;round<100;round++) {
      DexOptions options=new DexOptions();options.minSdkVersion=27;
      final MethodHandlesSection section=new DexFile(options).getMethodHandles();
      final CyclicBarrier start=new CyclicBarrier(threads);
      final AtomicReference<Throwable> failure=new AtomicReference<>();
      Thread[] workers=new Thread[threads];
      for(int t=0;t<threads;t++) { final int id=t;workers[t]=new Thread(()->{
        try { start.await();for(int i=0;i<each;i++)section.intern(handles[((id*each+i)*4051)&(total-1)]); }
        catch(Throwable ex){failure.compareAndSet(null,ex);}
      });workers[t].setDaemon(true);workers[t].start(); }
      for(Thread worker:workers){worker.join(5000);if(worker.isAlive()){System.out.println("DX_RACE_REPRO worker_hang=1 round="+round);System.exit(11);}}
      int visited=0;
      for(Item item:section.items()) { if(++visited>total)break; }
      if(failure.get()!=null || section.items().size()!=total || visited!=total) {
        System.out.println("DX_RACE_REPRO round="+round+" expected="+total+" size="+section.items().size()+" visited="+visited+" exception="+(failure.get()==null?"none":failure.get().getClass().getSimpleName()));System.exit(11);
      }
    }
    System.out.println("DX_METHOD_HANDLES_PASS rounds=100 threads=8 unique_per_round=8192");
  }
}
