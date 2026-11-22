package dev.scriptable.android;

import android.app.job.JobScheduler;
import android.content.Context;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.time.Instant;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class AutomationRegressionTest {
    private Context c;
    @Before public void setup()throws Exception{
        c=RuntimeEnvironment.getApplication();c.getSharedPreferences("scheduled-scripts-v1",0).edit().clear().commit();
        new ScriptRepository(c).save(new JSONObject().put("id","automation-test").put("name","测试脚本").put("code","console.log('完成')").put("color","#245BBD").put("icon","code"));
    }
    private JSONObject input()throws Exception{return new JSONObject().put("name","每日任务").put("scriptId","automation-test").put("timeZone","Asia/Shanghai").put("times",new JSONArray().put("23:00").put("00:00").put("09:00")).put("parameter","notify").put("enabled",true);}
    @Test public void beijingMidnightRollsAcrossUtcMonthBoundary()throws Exception{
        long value=AutomationStore.next(input(),Instant.parse("2026-11-30T15:59:59Z").toEpochMilli());
        assertEquals(Instant.parse("2026-11-30T16:00:00Z").toEpochMilli(),value);
        assertEquals(Instant.parse("2026-12-01T01:00:00Z").toEpochMilli(),AutomationStore.next(input(),value));
    }
    @Test public void timezoneIsIndependentOfDeviceTimezone()throws Exception{
        java.util.TimeZone old=java.util.TimeZone.getDefault();try{
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Honolulu"));
            assertEquals(Instant.parse("2026-11-22T15:00:00Z").toEpochMilli(),AutomationStore.next(input(),Instant.parse("2026-11-22T10:00:00Z").toEpochMilli()));
        }finally{java.util.TimeZone.setDefault(old);}
    }
    @Test public void duplicateAndMalformedTimesAndMissingScriptsAreRejected()throws Exception{
        for(JSONArray bad:new JSONArray[]{new JSONArray(),new JSONArray().put("9:00"),new JSONArray().put("24:00"),new JSONArray().put("09:00").put("09:00")}){
            try{AutomationStore.save(c,input().put("times",bad));fail("Invalid time accepted");}catch(Exception expected){assertNotNull(expected.getMessage());}
        }
        try{AutomationStore.save(c,input().put("scriptId","deleted"));fail("Missing script accepted");}catch(Exception expected){assertNotNull(expected.getMessage());}
    }
    @Test public void editsFenceOldCallbacksAndCancelQueuedOldRevision()throws Exception{
        JSONObject task=AutomationStore.save(c,input());AutomationScheduler.enqueue(c,task,true,System.currentTimeMillis());
        JSONObject edited=AutomationStore.save(c,input().put("id",task.getString("id")).put("enabled",false));
        assertNotEquals(task.getString("revision"),edited.getString("revision"));AutomationScheduler.schedule(c,edited.getString("id"));
        assertNull(((JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).getPendingJob(task.getInt("jobId")));
        AutomationStore.result(c,task.getString("id"),task.getString("revision"),"过期结果","不能覆盖");
        assertFalse(AutomationStore.find(c,task.getString("id")).has("lastState"));
    }
    @Test public void duplicateJobsDoNotReplaceOrRestartQueuedWork()throws Exception{
        JSONObject task=AutomationStore.save(c,input());AutomationScheduler.enqueue(c,task,true,123);
        try{AutomationScheduler.enqueue(c,task,false,456);fail("Duplicate work accepted");}catch(Exception expected){assertTrue(expected.getMessage().contains("上一次"));}
        assertEquals(123,((JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).getPendingJob(task.getInt("jobId")).getExtras().getLong("when"));
        AutomationScheduler.remove(c,task.getString("id"));assertEquals(0,AutomationStore.all(c).length());assertNull(((JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).getPendingJob(task.getInt("jobId")));
    }
    @Test public void nextRunIsRestoredAndDisabledSchedulesStayOff()throws Exception{
        JSONObject task=AutomationStore.save(c,input());AutomationScheduler.restore(c);
        assertTrue(AutomationStore.find(c,task.getString("id")).getLong("nextRun")>System.currentTimeMillis());
        JSONObject edited=AutomationStore.save(c,input().put("id",task.getString("id")).put("enabled",false));AutomationScheduler.restore(c);
        assertEquals(0,AutomationStore.find(c,edited.getString("id")).getLong("nextRun"));
    }
}
