package dev.scriptable.android;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import org.json.*;
import java.util.Locale;
import java.util.concurrent.*;

/** Visible, user-initiated update flow. System installer always owns final consent. MIT. */
public final class UpdateActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private NativePage ui;
    private TextView state,notes;
    private Button action,cancel;
    private ProgressBar progress;
    private JSONObject release;
    private String downloadState="idle";
    private boolean visible,busy,wantInstall,waitingPermission,destroyed,installError;
    private final Runnable poll=new Runnable(){@Override public void run(){if(!visible||destroyed)return;refreshDownload();main.postDelayed(this,1500);}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);ui=new NativePage(this);ui.open("检查更新",this::finish);
        LinearLayout version=ui.group();version.addView(ui.label("Scriptable 社区版",20,false));
        try{ui.note(version,"当前版本 "+AppUpdater.installed(this).versionName);}catch(Exception e){ui.note(version,"无法读取当前版本");}
        state=ui.label("正在检查更新…",16,false);version.addView(state);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setVisibility(android.view.View.GONE);version.addView(progress,new LinearLayout.LayoutParams(-1,ui.dp(12)));
        action=ui.button(version,"正在检查…",true,v->mainAction());action.setEnabled(false);
        cancel=ui.button(version,"取消下载",false,v->new AlertDialog.Builder(this).setMessage("取消并清理本次更新下载？").setNegativeButton("继续下载",null).setPositiveButton("取消下载",(d,w)->{try{AppUpdater.discard(this);release=null;wantInstall=false;downloadState="idle";check();}catch(Exception e){fail(e);}}).show());cancel.setVisibility(android.view.View.GONE);
        notes=ui.label("",14,true);ui.body.addView(notes);
        ui.button(ui.body,"查看 GitHub 发布页",false,v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(UpdateRelease.PAGE)));}catch(Exception e){fail(e);}});
        ui.note(ui.body,"下载完成后会打开系统安装界面，需你确认安装。脚本和自动化配置会保留。若无法连接 GitHub，可稍后重试。");
        try{JSONObject current=AppUpdater.status(this);if("installed".equals(current.optString("state"))){AppUpdater.discard(this);check();}else if("idle".equals(current.optString("state")))check();else displayDownload(current);}catch(Exception e){fail(e);}
    }
    @Override public void onResume(){super.onResume();visible=true;main.post(poll);if(waitingPermission){waitingPermission=false;if(getPackageManager().canRequestPackageInstalls())install();else{busy=false;state.setText("尚未允许安装，允许后可继续更新");action.setText("继续安装");action.setEnabled(true);}}}
    @Override public void onPause(){visible=false;main.removeCallbacks(poll);super.onPause();}
    @Override public void onDestroy(){destroyed=true;main.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
    private void fail(Exception e){if(destroyed)return;busy=false;wantInstall=false;installError="downloaded".equals(downloadState);state.setText(String.valueOf(e.getMessage()));action.setText(installError?"重试安装":"重新检查");action.setEnabled(true);}
    private void check(){
        if(busy)return;busy=true;installError=false;release=null;downloadState="idle";cancel.setVisibility(android.view.View.GONE);progress.setVisibility(android.view.View.GONE);notes.setText("");state.setText("正在检查更新…");action.setText("正在检查…");action.setEnabled(false);
        io.execute(()->{try{JSONObject result=AppUpdater.check(this);main.post(()->{if(destroyed)return;busy=false;action.setEnabled(true);
            if("available".equals(result.optString("state"))){release=result.optJSONObject("release");state.setText("发现新版 "+release.optString("version"));action.setText("下载并安装 · "+size(release.optLong("size")));notes.setText(release.optString("notes",""));}
            else{state.setText("unreleased".equals(result.optString("state"))?"社区版暂未发布正式安装包":"已是最新正式版本");action.setText("重新检查");}
        });}catch(Exception e){main.post(()->fail(e));}});
    }
    private String size(long bytes){return String.format(Locale.ROOT,"%.1f MB",bytes/(1024.0*1024));}
    private void mainAction(){
        if(busy)return;installError=false;
        if("downloaded".equals(downloadState)){install();return;}
        if("failed".equals(downloadState)){try{AppUpdater.discard(this);downloadState="idle";}catch(Exception e){fail(e);return;}}
        if(release==null){check();return;}
        try{AppUpdater.download(this,release);wantInstall=true;refreshDownload();}catch(Exception e){fail(e);}
    }
    private void refreshDownload(){
        if(busy||installError)return;
        try{JSONObject current=AppUpdater.status(this);String status=current.optString("state");if("installed".equals(status)){AppUpdater.discard(this);check();}else if(!"idle".equals(status))displayDownload(current);}catch(Exception e){fail(e);}
    }
    private void displayDownload(JSONObject current){
        if(busy||destroyed)return;downloadState=current.optString("state");
        JSONObject pending=current.optJSONObject("release");if(pending!=null)release=pending;
        cancel.setVisibility("downloading".equals(downloadState)||"downloaded".equals(downloadState)||"failed".equals(downloadState)?android.view.View.VISIBLE:android.view.View.GONE);
        if("downloading".equals(downloadState)){
            long bytes=current.optLong("bytes"),total=current.optLong("total");state.setText((current.optBoolean("paused")?"等待网络或系统继续下载 · ":"正在下载 · ")+size(bytes)+(total>0?" / "+size(total):""));progress.setVisibility(android.view.View.VISIBLE);progress.setIndeterminate(total<=0);if(total>0)progress.setProgress((int)Math.min(100,bytes*100/total));action.setText("下载中…");action.setEnabled(false);
        }else if("downloaded".equals(downloadState)){
            progress.setVisibility(android.view.View.GONE);state.setText("下载完成，可以安装更新");action.setText("安装更新");action.setEnabled(true);if(wantInstall){wantInstall=false;install();}
        }else if("failed".equals(downloadState)){
            progress.setVisibility(android.view.View.GONE);state.setText(current.optString("message","下载失败，请重试"));action.setText("重新下载");action.setEnabled(true);
        }else if("installed".equals(downloadState)){
            release=null;progress.setVisibility(android.view.View.GONE);cancel.setVisibility(android.view.View.GONE);state.setText("更新已安装");action.setText("重新检查");action.setEnabled(true);
        }
    }
    private void install(){
        if(busy)return;
        if(!getPackageManager().canRequestPackageInstalls()){
            waitingPermission=true;state.setText("请允许安装此应用提供的更新，然后返回继续");
            try{startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(Exception e){waitingPermission=false;fail(e);}return;
        }
        busy=true;action.setEnabled(false);cancel.setVisibility(android.view.View.GONE);state.setText("正在校验安装包…");
        io.execute(()->{try{AppUpdater.prepare(this);main.post(()->{if(destroyed)return;busy=false;action.setEnabled(true);state.setText("请在系统界面确认安装");action.setText("再次打开安装");
            Intent install=new Intent(Intent.ACTION_VIEW).setDataAndType(AppUpdater.installUri(this),"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).setClipData(ClipData.newRawUri("更新安装包",AppUpdater.installUri(this)));
            try{startActivity(install);}catch(Exception e){fail(e);}
        });}catch(Exception e){main.post(()->fail(e));}});
    }
}
