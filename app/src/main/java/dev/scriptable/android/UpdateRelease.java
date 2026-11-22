package dev.scriptable.android;

import org.json.*;
import java.net.URI;
import java.util.regex.*;

/** Stable community release policy, independent of UI and download transport. MIT. */
final class UpdateRelease {
    static final String REPOSITORY="awesome-sean/scriptable-android";
    static final String PAGE="https://github.com/"+REPOSITORY+"/releases";
    static final String API="https://api.github.com/repos/"+REPOSITORY+"/releases/latest";
    static final String ASSET="scriptable-community-release.apk";
    static final long MAX_SIZE=300L*1024*1024;
    private static final Pattern VERSION=Pattern.compile("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");
    static boolean newer(String remote,String installed)throws Exception {
        Matcher a=VERSION.matcher(remote),b=VERSION.matcher(installed);
        if(!a.matches()||!b.matches())throw new Exception("版本号格式不支持，请到发布页查看");
        if(a.group(4)!=null)return false;
        for(int i=1;i<=3;i++){
            java.math.BigInteger x=new java.math.BigInteger(a.group(i)),y=new java.math.BigInteger(b.group(i));
            int comparison=x.compareTo(y);if(comparison!=0)return comparison>0;
        }
        return b.group(4)!=null;
    }
    static void validateAsset(JSONObject asset)throws Exception {
        URI uri=new URI(asset.getString("url"));
        String prefix="/"+REPOSITORY+"/releases/download/";
        if(!"https".equals(uri.getScheme())||!"github.com".equals(uri.getHost())||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null
            ||!uri.getPath().startsWith(prefix)||!uri.getPath().endsWith("/"+ASSET)||uri.getPath().contains("/../"))throw new Exception("更新下载地址不属于本项目发布页");
        long size=asset.getLong("size");if(size<=0||size>MAX_SIZE)throw new Exception("安装包大小异常");
        String digest=asset.optString("digest","");if(!digest.isEmpty()&&!digest.matches("sha256:[a-fA-F0-9]{64}"))throw new Exception("安装包校验信息异常");
    }
    static JSONObject candidate(JSONObject release,String installed)throws Exception {
        if(release.optBoolean("draft")||release.optBoolean("prerelease"))return null;
        String tag=release.getString("tag_name");if(!newer(tag,installed))return null;
        JSONArray assets=release.optJSONArray("assets");JSONObject selected=null;
        if(assets!=null)for(int i=0;i<assets.length();i++){
            JSONObject asset=assets.getJSONObject(i);
            if(!ASSET.equals(asset.optString("name"))||!"uploaded".equals(asset.optString("state")))continue;
            if(selected!=null)throw new Exception("发布页包含重复安装包，请联系维护者");
            selected=new JSONObject().put("version",tag).put("url",asset.getString("browser_download_url")).put("size",asset.getLong("size")).put("digest",asset.isNull("digest")?"":asset.optString("digest",""))
                .put("notes",release.isNull("body")?"":release.optString("body","")).put("assetId",asset.getLong("id"));
            validateAsset(selected);
        }
        if(selected==null)throw new Exception("新版已发布，但尚未提供社区版安装包，请稍后重试");
        return selected;
    }
}
