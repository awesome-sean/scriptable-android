package dev.scriptable.android;

import android.content.Context;
import org.json.*;
import java.time.*;
import java.util.*;

/** Durable user-configured schedules. Added by awesome-sean; MIT. */
final class AutomationStore {
    static final Object LOCK=new Object();
    private static final String PREFS="scheduled-scripts-v1";
    static JSONArray all(Context c)throws Exception {
        synchronized(LOCK){return new JSONArray(c.getSharedPreferences(PREFS,0).getString("tasks","[]"));}
    }
    static JSONObject find(Context c,String id)throws Exception {
        JSONArray tasks=all(c);for(int i=0;i<tasks.length();i++)if(id.equals(tasks.getJSONObject(i).getString("id")))return tasks.getJSONObject(i);
        throw new Exception("自动化任务已删除");
    }
    static void write(Context c,JSONArray tasks)throws Exception {
        if(!c.getSharedPreferences(PREFS,0).edit().putString("tasks",tasks.toString()).commit())throw new Exception("自动化任务保存失败");
    }
    static JSONObject save(Context c,JSONObject input)throws Exception {
        synchronized(LOCK){
            String name=input.getString("name").trim(), scriptId=input.getString("scriptId");
            String parameter=input.optString("parameter",""),zone=input.getString("timeZone");
            if(name.isEmpty()||name.length()>100||parameter.length()>4096)throw new Exception("任务名称为空或内容过长");
            new ScriptRepository(c).find(scriptId); ZoneId.of(zone);
            JSONArray times=input.getJSONArray("times");
            if(times.length()==0||times.length()>24)throw new Exception("每天需设置 1 至 24 个时间");
            SortedSet<String> sorted=new TreeSet<>();
            for(int i=0;i<times.length();i++){
                String time=times.getString(i);
                if(!time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")||!sorted.add(time))throw new Exception("时间需为不重复的 HH:mm，如 09:00");
            }
            JSONArray tasks=all(c);String id=input.optString("id","");JSONObject old=null;int index=-1;
            for(int i=0;i<tasks.length();i++)if(id.equals(tasks.getJSONObject(i).getString("id"))){old=tasks.getJSONObject(i);index=i;break;}
            if(!id.isEmpty()&&old==null)throw new Exception("任务已删除，请重新创建");
            if(old==null&&tasks.length()>=64)throw new Exception("最多创建 64 个自动化任务");
            int jobId;
            if(old==null){
                int next=c.getSharedPreferences(PREFS,0).getInt("nextJob",100000);
                if(next>=Integer.MAX_VALUE-1)throw new Exception("任务编号已耗尽");
                if(!c.getSharedPreferences(PREFS,0).edit().putInt("nextJob",next+1).commit())throw new Exception("任务编号保存失败");
                jobId=next;id=UUID.randomUUID().toString();
            }else jobId=old.getInt("jobId");
            JSONObject task=new JSONObject().put("id",id).put("name",name).put("scriptId",scriptId)
                .put("parameter",parameter).put("timeZone",zone).put("times",new JSONArray(sorted))
                .put("enabled",input.optBoolean("enabled",false)).put("revision",UUID.randomUUID().toString()).put("jobId",jobId);
            if(old!=null)for(String key:new String[]{"lastClaim","lastRun","lastState","lastResult"})if(old.has(key))task.put(key,old.get(key));
            if(index<0)tasks.put(task);else tasks.put(index,task);write(c,tasks);return task;
        }
    }
    static void remove(Context c,String id)throws Exception {
        synchronized(LOCK){JSONArray tasks=all(c),keep=new JSONArray();for(int i=0;i<tasks.length();i++)if(!id.equals(tasks.getJSONObject(i).getString("id")))keep.put(tasks.getJSONObject(i));write(c,keep);}
    }
    static boolean replace(Context c,JSONObject task)throws Exception {
        synchronized(LOCK){JSONArray tasks=all(c);for(int i=0;i<tasks.length();i++){
            JSONObject old=tasks.getJSONObject(i);
            if(old.getString("id").equals(task.getString("id"))&&old.getString("revision").equals(task.getString("revision"))){tasks.put(i,task);write(c,tasks);return true;}
        }return false;}
    }
    static long next(JSONObject task,long after)throws Exception {
        ZoneId zone=ZoneId.of(task.getString("timeZone"));LocalDate start=Instant.ofEpochMilli(after).atZone(zone).toLocalDate();
        JSONArray times=task.getJSONArray("times");long best=Long.MAX_VALUE;
        for(int day=0;day<3;day++)for(int i=0;i<times.length();i++){
            long value=start.plusDays(day).atTime(LocalTime.parse(times.getString(i))).atZone(zone).toInstant().toEpochMilli();
            if(value>after&&value<best)best=value;
        }
        if(best==Long.MAX_VALUE)throw new Exception("无法计算下一次运行时间");return best;
    }
    static void result(Context c,String id,String revision,String state,String result)throws Exception {
        synchronized(LOCK){JSONObject task=find(c,id);if(!revision.equals(task.getString("revision")))return;
            task.put("lastRun",System.currentTimeMillis()).put("lastState",state).put("lastResult",result==null?"":result.substring(0,Math.min(3000,result.length())));replace(c,task);}
    }
}
