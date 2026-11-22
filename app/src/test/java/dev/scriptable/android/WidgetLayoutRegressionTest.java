package dev.scriptable.android;

import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class WidgetLayoutRegressionTest {
    @Test public void launcherDimensionsUseTheCurrentOrientationPair(){
        android.os.Bundle options=new android.os.Bundle();
        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,140);options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,240);
        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,100);options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,180);
        assertArrayEquals(new int[]{140,180},ScriptWidgetProvider.dimensions(options,"small",false));
        assertArrayEquals(new int[]{240,100},ScriptWidgetProvider.dimensions(options,"small",true));
        assertArrayEquals(new int[]{160,160},ScriptWidgetProvider.dimensions(new android.os.Bundle(),"small",false));
    }
    private JSONObject text(String value,String link)throws Exception{return new JSONObject().put("type","text").put("text",value).put("url",link).put("font",new JSONObject().put("size",11));}
    @Test public void flexibleHeaderUsesFullWidthInSquareAndWideWidgets()throws Exception{
        JSONObject row=new JSONObject().put("type","stack").put("layout","horizontal").put("children",new JSONArray().put(text("重庆大学","label")).put(new JSONObject().put("type","spacer")).put(text("11/24","date")));
        JSONObject tree=new JSONObject().put("type","widget").put("padding",new JSONArray().put(12).put(12).put(12).put(12)).put("children",new JSONArray().put(row));
        for(int width:new int[]{140,160,240,320}){
            WidgetRenderer.Result result=WidgetRenderer.render(tree,width,160,1);
            WidgetRenderer.Hit label=result.hits.get(0),date=result.hits.get(1);
            assertEquals(12,label.rectangle.left,.1);assertEquals(width-12,date.rectangle.right,.1);
            assertTrue("Header must keep a gap",date.rectangle.left-label.rectangle.right>10);
            assertEquals(label.rectangle.top,date.rectangle.top,.1);
        }
    }
    @Test public void explicitRowWidthRemainsIntrinsic()throws Exception{
        JSONObject row=new JSONObject().put("type","stack").put("layout","horizontal").put("size",new JSONObject().put("width",90)).put("children",new JSONArray().put(text("A","a")).put(new JSONObject().put("type","spacer")).put(text("B","b")));
        JSONObject tree=new JSONObject().put("type","widget").put("children",new JSONArray().put(row));
        WidgetRenderer.Result result=WidgetRenderer.render(tree,240,160,1);assertEquals(106,result.hits.get(1).rectangle.right,.1);
    }
}
