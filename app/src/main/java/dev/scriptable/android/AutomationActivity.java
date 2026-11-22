package dev.scriptable.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** User-owned daily automations. Community changes by awesome-sean; MIT. */
public final class AutomationActivity extends Activity {
    private NativePage ui;
    private boolean editing;
    private void error(Exception e){new AlertDialog.Builder(this).setTitle("无法完成").setMessage(String.valueOf(e.getMessage())).setPositiveButton("知道了",null).show();}
    @Override public void onCreate(Bundle state){super.onCreate(state);showList();}
    @Override public void onResume(){super.onResume();if(ui!=null&&!editing)showList();}
    @Override public void onBackPressed(){if(editing)leaveEditor();else super.onBackPressed();}
    private void leaveEditor(){new AlertDialog.Builder(this).setMessage("放弃未保存的修改？").setNegativeButton("继续编辑",null).setPositiveButton("放弃",(d,w)->showList()).show();}
    private String stamp(long value,String zone){return value<=0?"未安排":Instant.ofEpochMilli(value).atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));}
    private void showList(){
        editing=false;ui=new NativePage(this);ui.open("自动化",this::finish);
        ui.button(ui.body,"＋ 新建自动化",true,v->edit(null));
        try{
            JSONArray tasks=AutomationStore.all(this);
            if(tasks.length()==0){LinearLayout empty=ui.group();empty.addView(ui.label("还没有自动化",20,false));ui.note(empty,"选择一个脚本，设定每天的执行时间。");ui.button(empty,"使用天气提醒时间",false,v->edit(null));}
            for(int i=0;i<tasks.length();i++){
                JSONObject task=tasks.getJSONObject(i);String id=task.getString("id"),zone=task.getString("timeZone");
                LinearLayout group=ui.group();
                Switch enabled=new Switch(this);enabled.setText(task.getString("name"));enabled.setTextSize(18);enabled.setTextColor(ui.text);enabled.setMinHeight(ui.dp(56));enabled.setChecked(task.optBoolean("enabled"));enabled.setContentDescription(task.getString("name")+"，启用定时执行");group.addView(enabled);
                enabled.setOnCheckedChangeListener((b,on)->{try{JSONObject current=AutomationStore.find(this,id);current.put("enabled",on);JSONObject saved=AutomationStore.save(this,current);AutomationScheduler.schedule(this,saved.getString("id"));showList();}catch(Exception e){error(e);showList();}});
                String name;try{name=new ScriptRepository(this).find(task.getString("scriptId")).getString("name");}catch(Exception e){name="脚本已删除，请重新选择";}
                ui.note(group,name+"\n每天 "+joinTimes(task.getJSONArray("times")," · ")+"\n"+zone+"\n"+(task.optBoolean("enabled")?"下次 "+stamp(task.optLong("nextRun"),zone):"定时执行已关闭"));
                ui.divider(group);group.addView(ui.label(task.optString("lastState","未运行"),15,false));
                if(task.optLong("lastRun")>0)ui.note(group,stamp(task.optLong("lastRun"),zone));
                if(!task.optString("lastResult").isEmpty())ui.button(group,"查看运行结果",false,v->new AlertDialog.Builder(this).setTitle("运行结果").setMessage(task.optString("lastResult")).setPositiveButton("完成",null).show());
                LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);group.addView(actions);
                Button edit=ui.button(actions,"编辑",false,v->edit(task));edit.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
                Button run=ui.button(actions,"运行一次",false,v->{try{AutomationScheduler.enqueue(this,AutomationStore.find(this,id),true,System.currentTimeMillis());Toast.makeText(this,"已请求后台执行，稍后刷新查看结果",Toast.LENGTH_LONG).show();}catch(Exception e){error(e);}});run.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
            }
            ui.button(ui.body,"刷新运行结果",false,v->showList());
            LinearLayout permissions=ui.group();
            boolean notifications=((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled();
            ui.button(permissions,"通知 · "+(notifications?"已允许":"未允许"),false,v->{
                if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},40);
                else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
            });
            ui.divider(permissions);
            ui.button(permissions,"精确定时 · "+(AutomationScheduler.exact(this)?"已允许":"未允许"),false,v->{if(Build.VERSION.SDK_INT>=31)startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));else Toast.makeText(this,"此系统无需额外授权",Toast.LENGTH_SHORT).show();});
            ui.button(permissions,"应用与后台设置",false,v->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
            ui.note(ui.body,"到点在后台执行脚本，是否通知由脚本判断。“已完成”表示脚本结束。系统省电限制可能延后运行；强行停止后需重新打开应用。");
            String schedulerError=getSharedPreferences("scheduled-scripts-v1",0).getString("schedulerError","");if(!schedulerError.isEmpty())ui.note(ui.body,"最近调度提示："+schedulerError);
        }catch(Exception e){error(e);}
    }
    private void edit(JSONObject existing){
        editing=true;ui=new NativePage(this);ui.open(existing==null?"新建自动化":"编辑自动化",this::leaveEditor);
        try{
            JSONArray scripts=new ScriptRepository(this).list();
            if(scripts.length()==0){ui.note(ui.body,"请先创建或导入脚本。");ui.button(ui.body,"返回",false,v->showList());return;}
            LinearLayout group=ui.group();EditText name=ui.field(group,"名称",existing==null?"天气预约提醒":existing.getString("name"));
            TextView label=ui.label("执行脚本",14,true);label.setPadding(0,ui.dp(16),0,ui.dp(4));group.addView(label);
            Spinner selected=new Spinner(this);List<String> names=new ArrayList<>();for(int i=0;i<scripts.length();i++)names.add(scripts.getJSONObject(i).getString("name"));
            selected.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names){@Override public View getView(int position,View convert,ViewGroup parent){TextView view=(TextView)super.getView(position,convert,parent);view.setTextColor(ui.text);view.setTextSize(16);return view;}});selected.setMinimumHeight(ui.dp(48));selected.setContentDescription("执行脚本");group.addView(selected);
            if(existing!=null)for(int i=0;i<scripts.length();i++)if(scripts.getJSONObject(i).getString("id").equals(existing.getString("scriptId")))selected.setSelection(i);
            EditText times=ui.field(group,"每天运行时间",existing==null?"00:00,09:00,11:00,15:00,19:00,23:00":joinTimes(existing.getJSONArray("times"),","));
            times.setSingleLine(false);ui.note(group,"24 小时制，用逗号分隔，例如 09:00,19:00。");
            EditText zone=ui.field(group,"时区",existing==null?"Asia/Shanghai":existing.getString("timeZone"));ui.note(group,"Asia/Shanghai 固定按北京时间运行。");
            EditText parameter=ui.field(group,"脚本参数",existing==null?"notify":existing.optString("parameter",""));
            Switch enabled=new Switch(this);enabled.setText("启用定时执行");enabled.setTextColor(ui.text);enabled.setMinHeight(ui.dp(56));enabled.setChecked(existing!=null&&existing.optBoolean("enabled"));group.addView(enabled);
            ui.note(ui.body,"请先手动运行脚本并完成权限授权。后台任务不能显示弹窗或等待输入。");
            ui.button(ui.body,"保存",true,v->{try{
                JSONArray clockTimes=new JSONArray();for(String time:times.getText().toString().replace('，',',').split(","))clockTimes.put(time.trim());
                JSONObject input=new JSONObject().put("name",name.getText().toString()).put("scriptId",scripts.getJSONObject(selected.getSelectedItemPosition()).getString("id"))
                    .put("times",clockTimes).put("timeZone",zone.getText().toString().trim()).put("parameter",parameter.getText().toString()).put("enabled",enabled.isChecked());
                if(existing!=null)input.put("id",existing.getString("id"));
                JSONObject task=AutomationStore.save(this,input);AutomationScheduler.schedule(this,task.getString("id"));showList();
            }catch(Exception e){error(e);}});
            if(existing!=null)ui.button(ui.body,"删除自动化",false,v->new AlertDialog.Builder(this).setMessage("删除此任务？脚本文件会保留。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{try{AutomationScheduler.remove(this,existing.getString("id"));showList();}catch(Exception e){error(e);}}).show()).setTextColor(0xFFB72E43);
        }catch(Exception e){error(e);}
    }
    private String joinTimes(JSONArray times,String separator)throws Exception{StringBuilder out=new StringBuilder();for(int i=0;i<times.length();i++){if(i>0)out.append(separator);out.append(times.getString(i));}return out.toString();}
}
