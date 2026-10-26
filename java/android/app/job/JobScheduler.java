// SPDX-License-Identifier: GPL-3.0-only
package android.app.job;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/** Records requested jobs. The desktop host does not run jobs on a schedule yet. */
public class JobScheduler {
 public static final int RESULT_FAILURE=0,RESULT_SUCCESS=1;
 private final Map<Integer,JobInfo> pending=new LinkedHashMap<Integer,JobInfo>();
 public JobScheduler forNamespace(String namespace){return this;}
 public synchronized int schedule(JobInfo job){if(job==null)throw new NullPointerException("job");pending.put(job.getId(),job);return RESULT_SUCCESS;}
 public synchronized void cancel(int id){pending.remove(id);}
 public synchronized void cancelAll(){pending.clear();}
 public synchronized List<JobInfo> getAllPendingJobs(){return new ArrayList<JobInfo>(pending.values());}
 public synchronized JobInfo getPendingJob(int id){return pending.get(id);}
}
