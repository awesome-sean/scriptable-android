package dev.scriptable.android;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import org.json.*;

/** Alarm wakeup followed by a bounded background script job. Added by awesome-sean; MIT. */
public final class AutomationScheduler extends BroadcastReceiver {
    private static final String FIRE="dev.scriptable.android.AUTOMATION_FIRE";
    private static PendingIntent alarmIntent(Context c,JSONObject task,long when)throws Exception {
        Intent intent=new Intent(c,AutomationScheduler.class).setAction(FIRE)
            .setData(Uri.parse("scriptable-automation://task/"+task.getString("id")))
            .putExtra("id",task.getString("id")).putExtra("revision",task.getString("revision")).putExtra("when",when);
        return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static boolean exact(Context c){return Build.VERSION.SDK_INT<31||((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();}
    static void schedule(Context c,String id)throws Exception {
        synchronized(AutomationStore.LOCK){
            JSONObject task=AutomationStore.find(c,id);
            AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
            JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            JobInfo old=jobs.getPendingJob(task.getInt("jobId"));
            if(old!=null&&!task.getString("revision").equals(old.getExtras().getString("revision")))jobs.cancel(task.getInt("jobId"));
            if(!task.optBoolean("enabled")){manager.cancel(alarmIntent(c,task,0));task.put("nextRun",0);AutomationStore.replace(c,task);return;}
            long when=AutomationStore.next(task,System.currentTimeMillis());task.put("nextRun",when);
            if(!AutomationStore.replace(c,task))return;
            PendingIntent alarm=alarmIntent(c,task,when);
            try{
                if(Build.VERSION.SDK_INT<31||manager.canScheduleExactAlarms())manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,alarm);
                else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,alarm);
            }catch(SecurityException revoked){manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,alarm);}
        }
    }
    static void remove(Context c,String id)throws Exception {
        synchronized(AutomationStore.LOCK){JSONObject task=AutomationStore.find(c,id);AutomationStore.remove(c,id);
            ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(alarmIntent(c,task,0));
            ((JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(task.getInt("jobId"));}
    }
    static void enqueue(Context c,JSONObject task,boolean manual,long when)throws Exception {
        synchronized(AutomationStore.LOCK){
        PersistableBundle extras=new PersistableBundle();extras.putString("id",task.getString("id"));extras.putString("revision",task.getString("revision"));extras.putBoolean("manual",manual);extras.putLong("when",when);
        JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobs.getPendingJob(task.getInt("jobId"))!=null)throw new Exception("上一次任务仍在等待或执行，本次未重复加入");
        JobInfo job=new JobInfo.Builder(task.getInt("jobId"),new ComponentName(c,AutomationJob.class)).setMinimumLatency(1).setExtras(extras).build();
        if(jobs.schedule(job)!=JobScheduler.RESULT_SUCCESS)throw new Exception("安卓系统拒绝安排后台任务");
        }
    }
    static void restore(Context c)throws Exception {
        synchronized(AutomationStore.LOCK){
        JSONArray tasks=AutomationStore.all(c);for(int i=0;i<tasks.length();i++){
            JSONObject task=tasks.getJSONObject(i);try{
                long due=task.optLong("nextRun",0),now=System.currentTimeMillis();
                schedule(c,task.getString("id"));
                // On restart, catch up only a recent occurrence using a fresh script/API request.
                if(task.optBoolean("enabled")&&due>0&&due<=now&&task.optLong("lastClaim",-1)<due){
                    if(now-due<=15*60*1000){
                        JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                        if(jobs.getPendingJob(task.getInt("jobId"))==null)enqueue(c,task,false,due);
                    }
                    else AutomationStore.result(c,task.getString("id"),task.getString("revision"),"已错过","设备关闭或后台受限，已安排下一次；过期任务未补发通知。");
                }
            }
            catch(Exception error){AutomationStore.result(c,task.getString("id"),task.getString("revision"),"调度失败",error.getMessage());}
        }
        }
    }
    @Override public void onReceive(Context c,Intent intent){
        PendingResult pending=goAsync();new Thread(()->{try{
            if(!FIRE.equals(intent.getAction())){restore(c);return;}
            synchronized(AutomationStore.LOCK){
                JSONObject task=AutomationStore.find(c,intent.getStringExtra("id"));long when=intent.getLongExtra("when",-1);
                if(!task.optBoolean("enabled")||!task.getString("revision").equals(intent.getStringExtra("revision"))||when!=task.optLong("nextRun",0))return;
                if(when>System.currentTimeMillis()){schedule(c,task.getString("id"));return;}
                schedule(c,task.getString("id"));
                enqueue(c,task,false,when);
            }
        }catch(Exception error){c.getSharedPreferences("scheduled-scripts-v1",0).edit().putString("schedulerError",String.valueOf(error.getMessage())).apply();}finally{pending.finish();}},"script-automation-alarm").start();
    }
}
