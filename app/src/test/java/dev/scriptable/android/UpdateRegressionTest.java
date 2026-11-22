package dev.scriptable.android;

import android.content.pm.*;
import android.net.Uri;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class UpdateRegressionTest {
    private JSONObject asset()throws Exception{return new JSONObject().put("id",10).put("name",UpdateRelease.ASSET).put("state","uploaded").put("browser_download_url","https://github.com/awesome-sean/scriptable-android/releases/download/v0.3.0/"+UpdateRelease.ASSET).put("size",1000).put("digest","sha256:"+"a".repeat(64));}
    private JSONObject release()throws Exception{return new JSONObject().put("tag_name","v0.3.0").put("body","新版说明").put("assets",new JSONArray().put(asset()));}
    @Test public void numericVersionsHandleDevBuildsWithoutDowngrades()throws Exception{
        assertTrue(UpdateRelease.newer("v0.2.2","0.2.2-dev"));assertTrue(UpdateRelease.newer("v0.2.10","0.2.9"));assertTrue(UpdateRelease.newer("v1.0.0","0.99.99"));
        assertFalse(UpdateRelease.newer("v0.2.2","0.2.2"));assertFalse(UpdateRelease.newer("v0.2.1","0.2.2-dev"));assertFalse(UpdateRelease.newer("v0.3.0-beta","0.2.2"));
        try{UpdateRelease.newer("latest","0.2.2");fail("Invalid tag accepted");}catch(Exception expected){assertTrue(expected.getMessage().contains("版本"));}
    }
    @Test public void onlyPublishedStableCommunityApkIsSelected()throws Exception{
        JSONObject selected=UpdateRelease.candidate(release(),"0.2.2-dev");assertEquals("v0.3.0",selected.getString("version"));assertEquals("新版说明",selected.getString("notes"));
        assertNull(UpdateRelease.candidate(release().put("prerelease",true),"0.2.2"));assertNull(UpdateRelease.candidate(release().put("draft",true),"0.2.2"));
        assertNull(UpdateRelease.candidate(release(),"0.3.0"));
        try{UpdateRelease.candidate(release().put("assets",new JSONArray().put(asset().put("name","app-debug.apk"))),"0.2.2");fail("Debug artifact accepted");}catch(Exception expected){assertTrue(expected.getMessage().contains("安装包"));}
    }
    @Test public void foreignHostsRepoPathsAndMalformedDigestsAreRejected()throws Exception{
        String original=asset().getString("browser_download_url");
        for(String url:new String[]{original.replace("https:","http:"),original.replace("github.com/","github.com.evil.example/"),original.replace("awesome-sean","EdenleeGitHub"),original+"?redirect=evil",original.replace("github.com","user@github.com")}){
            try{UpdateRelease.candidate(release().put("assets",new JSONArray().put(asset().put("browser_download_url",url))),"0.2.2");fail("Foreign URL accepted: "+url);}catch(Exception expected){assertTrue(expected.getMessage().contains("地址"));}
        }
        try{UpdateRelease.candidate(release().put("assets",new JSONArray().put(asset().put("digest","sha256:bad"))),"0.2.2");fail("Bad digest accepted");}catch(Exception expected){assertTrue(expected.getMessage().contains("校验"));}
        try{UpdateRelease.candidate(release().put("assets",new JSONArray().put(asset().put("size",UpdateRelease.MAX_SIZE+1))),"0.2.2");fail("Oversize accepted");}catch(Exception expected){assertTrue(expected.getMessage().contains("大小"));}
    }
    @Test public void duplicateAssetsAndUnparseablePackagesDoNotOpenInstaller()throws Exception{
        try{UpdateRelease.candidate(release().put("assets",new JSONArray().put(asset()).put(asset())),"0.2.2");fail("Duplicate accepted");}catch(Exception expected){assertTrue(expected.getMessage().contains("重复"));}
        PackageInfo local=new PackageInfo();local.versionCode=4;local.packageName="dev.scriptable.android.community";
        try{AppUpdater.verifyPackage(local,null,local.packageName);fail("Missing APK accepted");}catch(SecurityException expected){assertTrue(expected.getMessage().contains("不是"));}
        PackageInfo foreign=new PackageInfo();foreign.packageName="dev.scriptable.android";foreign.versionCode=5;
        try{AppUpdater.verifyPackage(local,foreign,local.packageName);fail("Upstream package accepted");}catch(SecurityException expected){assertTrue(expected.getMessage().contains("不是"));}
        foreign.packageName=local.packageName;foreign.versionCode=4;
        try{AppUpdater.verifyPackage(local,foreign,local.packageName);fail("Downgrade accepted");}catch(SecurityException expected){assertTrue(expected.getMessage().contains("版本"));}
    }
    @Test @Config(sdk=27) public void legacyInstallRequiresSameSigningCertificate()throws Exception{
        PackageInfo local=new PackageInfo();local.packageName="community";local.versionCode=4;local.signatures=new Signature[]{new Signature("1234")};
        PackageInfo update=new PackageInfo();update.packageName="community";update.versionCode=5;update.signatures=new Signature[]{new Signature("1234")};
        AppUpdater.verifyPackage(local,update,"community");update.signatures=new Signature[]{new Signature("5678")};
        try{AppUpdater.verifyPackage(local,update,"community");fail("Foreign signature accepted");}catch(SecurityException expected){assertTrue(expected.getMessage().contains("签名"));}
    }
    @Test public void updateProviderHasNoWriteOrArbitraryFileAccess()throws Exception{
        UpdateFileProvider provider=Robolectric.buildContentProvider(UpdateFileProvider.class).create().get();
        try{provider.openFile(Uri.parse("content://dev.scriptable.android.community.updates/documents/private.js"),"r");fail("Arbitrary path exposed");}catch(java.io.FileNotFoundException expected){assertTrue(expected.getMessage().contains("Unknown"));}
        try{provider.openFile(AppUpdater.installUri(RuntimeEnvironment.getApplication()),"rw");fail("Writable update grant");}catch(java.io.FileNotFoundException expected){assertTrue(expected.getMessage().contains("Read only"));}
    }
}
