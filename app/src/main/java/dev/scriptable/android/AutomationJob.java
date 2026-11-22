package dev.scriptable.android;

import android.app.job.*;
import org.json.*;
import java.util.*;

/** Serial background execution; never opens an Activity. Added by awesome-sean; MIT. */
public final class AutomationJob extends JobService {
    private final Queue<JobParameters> waiting=new ArrayDeque<>();
    private JobParameters active;private HeadlessWidgetRunner runner;private int generation;
    @Override public boolean onStartJob(JobParameters p){if(active!=null){waiting.add(p);return true;}return begin(p);}
    private boolean begin(JobParameters p){
        String id=p.getExtras().getString("id"),revision=p.getExtras().getString("revision");
        try{
            synchronized(AutomationStore.LOCK){
                JSONObject task=AutomationStore.find(this,id);
                if(!task.getString("revision").equals(revision)||(!p.getExtras().getBoolean("manual")&&!task.optBoolean("enabled")))return false;
                long when=p.getExtras().getLong("when");
                if(!p.getExtras().getBoolean("manual")&&task.optLong("lastClaim",-1)>=when)return false;
                if(!p.getExtras().getBoolean("manual"))task.put("lastClaim",when);
                task.put("lastRun",System.currentTimeMillis()).put("lastState","运行中").put("lastResult","");AutomationStore.replace(this,task);
                active=p;int token=++generation;
                JSONObject config=new JSONObject().put("scriptId",task.getString("scriptId")).put("parameter",task.optString("parameter","")).put("automation",true);
                runner=new HeadlessWidgetRunner(this,config,error->{
                    if(token!=generation)return;
                    try{AutomationStore.result(this,id,revision,error==null?"已完成":"执行失败",error==null?runner.output():error);}catch(Exception ignored){}
                    runner=null;JobParameters finished=active;active=null;if(finished!=null)jobFinished(finished,false);next();
                });runner.start();return true;
            }
        }catch(Exception error){try{AutomationStore.result(this,id,revision,"执行失败",error.getMessage());}catch(Exception ignored){}active=null;return false;}
    }
    private void next(){while(active==null&&!waiting.isEmpty()){JobParameters p=waiting.poll();if(!begin(p))jobFinished(p,false);}}
    @Override public boolean onStopJob(JobParameters p){
        if(active==null||active.getJobId()!=p.getJobId()){waiting.removeIf(job->job.getJobId()==p.getJobId());return false;}
        generation++;active=null;if(runner!=null)runner.stop();runner=null;
        try{AutomationStore.result(this,p.getExtras().getString("id"),p.getExtras().getString("revision"),"被系统停止","未自动重试，避免已发送的通知重复出现；请查看通知与下一次运行时间。");}catch(Exception ignored){}
        next();return false;
    }
}
