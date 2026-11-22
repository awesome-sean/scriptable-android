package dev.scriptable.android;

import android.app.DownloadManager;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** User-initiated releases, persistent system downloads and verified APK handoff. MIT. */
final class AppUpdater {
    private static final String STORE="community-updates-v1";
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(STORE,0);}
    static PackageInfo installed(Context c)throws Exception{return c.getPackageManager().getPackageInfo(c.getPackageName(),Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);}
    static long code(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    static JSONObject check(Context c)throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(UpdateRelease.API).openConnection();
        connection.setConnectTimeout(15000);connection.setReadTimeout(15000);connection.setRequestProperty("Accept","application/vnd.github+json");connection.setRequestProperty("User-Agent","Scriptable-Android-Community");
        try{
            int status=connection.getResponseCode();
            if(status==404)return new JSONObject().put("state","unreleased");
            if(status==403||status==429)throw new IOException("检查过于频繁或 GitHub 暂时受限，请稍后重试");
            if(status!=200)throw new IOException("发布服务暂不可用（"+status+"），请稍后重试");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=connection.getInputStream()){
                byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(bytes.size()+n>1024*1024)throw new IOException("更新信息过大");bytes.write(buffer,0,n);}
            }
            JSONObject candidate=UpdateRelease.candidate(new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())),installed(c).versionName);
            prefs(c).edit().putLong("checkedAt",System.currentTimeMillis()).apply();
            return new JSONObject().put("state",candidate==null?"current":"available").put("release",candidate==null?JSONObject.NULL:candidate);
        }catch(java.net.SocketTimeoutException e){throw new IOException("连接 GitHub 超时，请检查网络后重试",e);}
        finally{connection.disconnect();}
    }
    static synchronized long download(Context c,JSONObject release)throws Exception {
        JSONObject current=status(c);
        if("downloading".equals(current.optString("state"))||"downloaded".equals(current.optString("state")))throw new Exception("已有更新正在下载或等待安装");
        UpdateRelease.validateAsset(release);discard(c);
        File external=c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);if(external==null)throw new IOException("设备下载存储暂不可用");
        File folder=new File(external,"updates");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建更新下载目录");
        File target=new File(folder,"update-"+UUID.randomUUID()+".apk");
        DownloadManager.Request request=new DownloadManager.Request(Uri.parse(release.getString("url")))
            .setTitle("Scriptable Android "+release.getString("version")).setDescription("下载完成后可在检查更新中安装")
            .setMimeType("application/vnd.android.package-archive").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true).setAllowedOverRoaming(false).setDestinationUri(Uri.fromFile(target));
        DownloadManager manager=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);long id=manager.enqueue(request);
        if(!prefs(c).edit().putLong("downloadId",id).putString("target",target.getAbsolutePath()).putString("release",release.toString()).remove("readyFor").commit()){
            manager.remove(id);throw new IOException("无法保存下载记录");
        }
        return id;
    }
    static synchronized JSONObject status(Context c)throws Exception {
        long id=prefs(c).getLong("downloadId",-1);if(id<0)return new JSONObject().put("state","idle");
        DownloadManager manager=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);
        try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(id))){
            if(cursor==null||!cursor.moveToFirst())return new JSONObject().put("state","failed").put("message","下载记录已移除，请重新下载");
            int status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            JSONObject out=new JSONObject().put("id",id).put("release",new JSONObject(prefs(c).getString("release","{}")));
            if(status==DownloadManager.STATUS_SUCCESSFUL){
                File ready=readyFile(c);if(ready.isFile())try{PackageInfo apk=c.getPackageManager().getPackageArchiveInfo(ready.getAbsolutePath(),0);if(apk!=null&&code(apk)<=code(installed(c)))return out.put("state","installed");}catch(Exception ignored){}
                return out.put("state","downloaded");
            }
            if(status==DownloadManager.STATUS_FAILED)return out.put("state","failed").put("message","下载失败（"+cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))+"），请检查网络和剩余空间");
            return out.put("state","downloading").put("bytes",cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)))
                .put("total",cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
                .put("paused",status==DownloadManager.STATUS_PAUSED);
        }
    }
    static File readyFile(Context c){return new File(new File(c.getFilesDir(),"updates"),"ready.apk");}
    static synchronized void discard(Context c)throws Exception {
        long id=prefs(c).getLong("downloadId",-1);if(id>=0)((DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE)).remove(id);
        File ready=readyFile(c);if(ready.exists()&&!ready.delete())throw new IOException("无法清理旧安装包");
        prefs(c).edit().remove("downloadId").remove("target").remove("release").remove("readyFor").commit();
    }
    private static Set<String> signers(Signature[] values)throws Exception {
        Set<String> result=new HashSet<>();if(values!=null)for(Signature value:values)result.add(android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()),android.util.Base64.NO_WRAP));return result;
    }
    static void verifyPackage(PackageInfo local,PackageInfo update,String expectedPackage)throws Exception {
        if(update==null||!expectedPackage.equals(update.packageName))throw new SecurityException("安装包不是此社区版应用");
        if(code(update)<=code(local))throw new SecurityException("安装包版本不高于当前版本");
        if(Build.VERSION.SDK_INT>=28){
            if(local.signingInfo==null||update.signingInfo==null)throw new SecurityException("无法读取安装包签名");
            Set<String> current=signers(local.signingInfo.getApkContentsSigners());
            Set<String> candidate=signers(update.signingInfo.getApkContentsSigners());
            if(current.isEmpty()||candidate.isEmpty())throw new SecurityException("安装包缺少签名");
            if(local.signingInfo.hasMultipleSigners()||update.signingInfo.hasMultipleSigners()){
                if(!current.equals(candidate))throw new SecurityException("安装包签名与当前应用不一致");
            }else if(!signers(update.signingInfo.getSigningCertificateHistory()).containsAll(current))throw new SecurityException("安装包签名与当前应用不一致");
        }else if(signers(local.signatures).isEmpty()||!signers(local.signatures).equals(signers(update.signatures)))throw new SecurityException("安装包签名与当前应用不一致");
    }
    static synchronized File prepare(Context c)throws Exception {
        if(!"downloaded".equals(status(c).optString("state")))throw new IOException("安装包尚未下载完成");
        JSONObject release=new JSONObject(prefs(c).getString("release","{}"));UpdateRelease.validateAsset(release);
        File source=new File(prefs(c).getString("target",""));
        if(!source.isFile()||source.length()!=release.getLong("size"))throw new IOException("安装包大小不匹配，请重新下载");
        File target=readyFile(c),folder=target.getParentFile();if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法准备安装目录");
        File temp=new File(folder,"ready.tmp");
        try{
            MessageDigest sha=MessageDigest.getInstance("SHA-256");long total=0;
            try(InputStream input=new FileInputStream(source);OutputStream output=new FileOutputStream(temp)){
                byte[] buffer=new byte[32768];int n;while((n=input.read(buffer))!=-1){total+=n;if(total>release.getLong("size"))throw new IOException("安装包大小异常");sha.update(buffer,0,n);output.write(buffer,0,n);}
            }
            if(total!=release.getLong("size"))throw new IOException("安装包不完整");
            StringBuilder digest=new StringBuilder();for(byte b:sha.digest())digest.append(String.format(Locale.ROOT,"%02x",b&255));
            String expected=release.optString("digest","");if(!expected.isEmpty()&&!expected.equalsIgnoreCase("sha256:"+digest))throw new SecurityException("安装包校验失败，请重新下载");
            int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
            verifyPackage(installed(c),c.getPackageManager().getPackageArchiveInfo(temp.getAbsolutePath(),flags),c.getPackageName());
            Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            prefs(c).edit().putLong("readyFor",prefs(c).getLong("downloadId",-1)).commit();return target;
        }finally{if(temp.exists())temp.delete();}
    }
    static Uri installUri(Context c){return new Uri.Builder().scheme("content").authority(c.getPackageName()+".updates").appendPath("apk").build();}
}
