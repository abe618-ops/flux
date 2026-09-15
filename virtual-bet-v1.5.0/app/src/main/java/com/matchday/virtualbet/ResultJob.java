package com.matchday.virtualbet;

import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;

/** Best-effort background sync; the OS controls execution time. */
public class ResultJob extends JobService {
    ExecutorService worker;DataClient.Scope scope;
    public static void schedule(Context c){
        if(android.os.Build.VERSION.SDK_INT<24)return;
        try{JobScheduler j=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if(j==null)return;for(JobInfo job:j.getAllPendingJobs())if(job.getId()==13009)return;
            j.schedule(new JobInfo.Builder(13009,new ComponentName(c,ResultJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000).setPersisted(true).build());
        }catch(Exception ignored){}
    }
    @Override public boolean onStartJob(JobParameters params){scope=new DataClient.Scope();worker=Executors.newSingleThreadExecutor();worker.execute(()->{new TicketStore(this).sync(scope,null);jobFinished(params,false);worker.shutdown();});return true;}
    @Override public boolean onStopJob(JobParameters params){if(scope!=null)scope.cancel();if(worker!=null)worker.shutdownNow();return true;}
}
