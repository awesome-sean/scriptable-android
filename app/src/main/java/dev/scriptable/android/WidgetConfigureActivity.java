package dev.scriptable.android;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.util.ArrayList;

/** Launcher configuration with a small default and per-instance settings. MIT. */
public final class WidgetConfigureActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        int widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
        Intent result=new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId);setResult(RESULT_CANCELED,result);
        if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return;}
        NativePage ui=new NativePage(this);ui.open("小组件",this::finish);LinearLayout group=ui.group();
        TextView hint=ui.label("默认添加为 2×2，可按需要调整大小。",14,true);group.addView(hint);
        try {
            JSONArray scripts=new ScriptRepository(this).list();ArrayList<String> names=new ArrayList<>();for(int i=0;i<scripts.length();i++)names.add(scripts.getJSONObject(i).optString("name","Untitled"));
            if(names.isEmpty()){hint.setText("请先创建或导入脚本，再添加小组件。");return;}
            ui.note(group,"执行脚本");Spinner script=new Spinner(this);script.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));script.setMinimumHeight(ui.dp(48));script.setContentDescription("执行脚本");group.addView(script);
            EditText parameter=ui.field(group,"组件参数（可留空）",getSharedPreferences("widget-preview",MODE_PRIVATE).getString("parameter."+widgetId,""));parameter.setSingleLine(false);
            ui.note(group,"内容尺寸");String[] values={"small","medium","large","extraLarge","accessoryCircular","accessoryRectangular","accessoryInline"};
            Spinner family=new Spinner(this);family.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"小 · 默认","中","大","特大","圆形","矩形","单行"}));family.setMinimumHeight(ui.dp(48));family.setContentDescription("内容尺寸");group.addView(family);
            for(int i=0;i<scripts.length();i++)if(scripts.getJSONObject(i).optString("id").equals(getSharedPreferences("widget-preview",MODE_PRIVATE).getString("script."+widgetId,"")))script.setSelection(i);
            String selected=getSharedPreferences("widget-preview",MODE_PRIVATE).getString("family."+widgetId,"small");for(int i=0;i<values.length;i++)if(selected.equals(values[i]))family.setSelection(i);
            ui.note(ui.body,"脚本需生成小组件。定时执行和通知请在“自动化”里单独设置，桌面刷新不会自动代替通知检查。");
            ui.button(ui.body,"保存小组件",true,v->{try{JSONObject chosen=scripts.getJSONObject(script.getSelectedItemPosition());ScriptWidgetProvider.configure(this,widgetId,chosen.getString("id"),parameter.getText().toString(),values[family.getSelectedItemPosition()]);setResult(RESULT_OK,result);finish();}catch(Exception e){hint.setText("无法保存："+e.getMessage());}});
        }catch(Exception e){hint.setText("无法读取脚本："+e.getMessage());}
    }
}
