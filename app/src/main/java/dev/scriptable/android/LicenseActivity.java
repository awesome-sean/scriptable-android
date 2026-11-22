package dev.scriptable.android;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Fork attribution and bundled upstream/third-party license texts. MIT. */
public final class LicenseActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); setTitle("上游与开源许可");
        NativePage ui=new NativePage(this);ui.open("开源许可",this::finish);
        TextView text=new TextView(this);
        text.setPadding(0,0,0,0); text.setTextSize(14); text.setTextColor(ui.text);text.setTextIsSelectable(true);
        StringBuilder body=new StringBuilder();
        for(String file:new String[]{"licenses/FORK-NOTICE.txt","licenses/UPSTREAM-MIT.txt",
                "licenses/THIRD-PARTY-NOTICES.txt","icons/NOTICE.txt","icons/MDI-LICENSE.txt",
                "icons/APACHE-2.0.txt","icons/SF-NAMES-LICENSE.txt"}) {
            body.append("\n\n—— ").append(file).append(" ——\n\n");
            try(InputStream input=getAssets().open(file); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int count;
                while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
                body.append(new String(output.toByteArray(),StandardCharsets.UTF_8));
            }catch(Exception error){body.append("许可文件读取失败：").append(error.getMessage());}
        }
        text.setText(body.toString()); ui.body.addView(text);
    }
}
