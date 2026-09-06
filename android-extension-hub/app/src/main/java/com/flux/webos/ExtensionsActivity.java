package com.flux.webos;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;

import java.util.List;

public class ExtensionsActivity extends Activity {
    private static final int PICK_EXTENSION_ZIP = 4101;
    private TextView result;
    private LinearLayout marketList, installedList, fluxPackageList;
    private EditText marketQuery, signedUrl;
    private GeckoRuntime runtime;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); runtime = FluxRuntime.get(this);
        ScrollView scroll = new ScrollView(this); LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(18),dp(18),dp(32)); scroll.addView(root);
        TextView title=t("Flux 扩展商店",26); root.addView(title);
        TextView intro=t("直接搜索 Firefox Android 扩展并一键安装。ZIP/XPI 手动导入已移到下方高级区域。",15); intro.setPadding(0,dp(8),0,dp(12)); root.addView(intro);

        LinearLayout searchRow=new LinearLayout(this); searchRow.setOrientation(LinearLayout.HORIZONTAL);
        marketQuery=new EditText(this); marketQuery.setHint("搜索：广告拦截、Dark Reader、翻译…"); marketQuery.setSingleLine(true);
        searchRow.addView(marketQuery,new LinearLayout.LayoutParams(0,dp(52),1f));
        Button search=new Button(this); search.setText("搜索"); search.setAllCaps(false); search.setOnClickListener(v->searchMarket());
        searchRow.addView(search,new LinearLayout.LayoutParams(dp(88),dp(52))); root.addView(searchRow);
        marketList=new LinearLayout(this); marketList.setOrientation(LinearLayout.VERTICAL); root.addView(marketList);

        result=t("状态：准备就绪",14); result.setGravity(Gravity.START); result.setPadding(0,dp(14),0,dp(14)); root.addView(result);
        TextView installedTitle=t("已安装扩展",20); root.addView(installedTitle);
        installedList=new LinearLayout(this); installedList.setOrientation(LinearLayout.VERTICAL); root.addView(installedList);
        Button refresh=new Button(this); refresh.setText("刷新已安装列表"); refresh.setAllCaps(false); refresh.setOnClickListener(v->refreshInstalled()); root.addView(refresh,fullButtonParams());

        TextView advanced=t("高级 / 开发者导入",20); advanced.setPadding(0,dp(22),0,dp(6)); root.addView(advanced);
        signedUrl=new EditText(this); signedUrl.setSingleLine(true); signedUrl.setHint("签名 XPI URL：https://..."); root.addView(signedUrl,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
        Button installSigned=new Button(this); installSigned.setText("从 URL 安装签名 XPI"); installSigned.setAllCaps(false); installSigned.setOnClickListener(v->installSignedExtension()); root.addView(installSigned,fullButtonParams());
        Button importZip=new Button(this); importZip.setText("导入 Chrome / WebExtension ZIP"); importZip.setAllCaps(false); importZip.setOnClickListener(v->pickZip()); root.addView(importZip,fullButtonParams());
        TextView fluxTitle=t("Flux 兼容扩展包",18); fluxTitle.setPadding(0,dp(16),0,0); root.addView(fluxTitle);
        fluxPackageList=new LinearLayout(this); fluxPackageList.setOrientation(LinearLayout.VERTICAL); root.addView(fluxPackageList);
        setContentView(scroll); renderFluxPackages(); refreshInstalled(); marketQuery.setText("uBlock"); searchMarket();
    }

    private void searchMarket() {
        String q=marketQuery.getText().toString().trim(); if(q.isBlank()) q="recommended";
        marketList.removeAllViews(); marketList.addView(t("正在搜索 Firefox Android 插件市场…",14)); final String query=q;
        new Thread(()->{ try { List<AmoStoreClient.Addon> addons=AmoStoreClient.search(query); runOnUiThread(()->renderMarket(addons)); }
            catch(Exception e){ runOnUiThread(()->{marketList.removeAllViews();marketList.addView(t("搜索失败："+safeError(e),14));}); }}).start();
    }

    private void renderMarket(List<AmoStoreClient.Addon> addons) {
        marketList.removeAllViews(); if(addons.isEmpty()){marketList.addView(t("没有找到 Android 兼容扩展",14));return;}
        for(AmoStoreClient.Addon a:addons){ LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(10),dp(12),dp(10),dp(12));
            String badge=a.recommended()?" · Mozilla 推荐":""; TextView info=t(a.name()+badge+"\n"+a.summary()+"\n版本 "+a.version()+" · ★ "+String.format("%.1f",a.rating())+" · 约 "+a.users()+" 日活",15); card.addView(info);
            Button install=new Button(this); install.setAllCaps(false); install.setText(a.xpiUrl()==null?"暂无 Android 安装包":"一键安装"); install.setEnabled(a.xpiUrl()!=null);
            install.setOnClickListener(v->installFromMarket(a)); card.addView(install,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48))); marketList.addView(card); }
    }

    private void installFromMarket(AmoStoreClient.Addon a) {
        result.setText("正在下载、校验并安装："+a.name());
        AddonController.installSigned(runtime,a.xpiUrl(),new AddonController.ExtensionCallback(){ public void onSuccess(WebExtension e){runOnUiThread(()->{result.setText("✓ 安装成功："+displayName(e));refreshInstalled();});}
            public void onError(Throwable e){runOnUiThread(()->result.setText("安装失败："+safeError(e)));}});
    }

    private void renderFluxPackages(){ fluxPackageList.removeAllViews(); List<FluxPackageStore.PackageInfo> ps=FluxPackageStore.list(this); if(ps.isEmpty()){fluxPackageList.addView(t("暂无手动导入包",14));return;} for(FluxPackageStore.PackageInfo p:ps){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.addView(t(p.name()+" · "+p.version()+" · "+p.compatibility()+"级",14));Button r=new Button(this);r.setText("删除");r.setOnClickListener(v->{FluxPackageStore.remove(this,p);renderFluxPackages();});c.addView(r);fluxPackageList.addView(c);}}
    private void installSignedExtension(){String url=signedUrl.getText().toString().trim();if(!(url.startsWith("https://")||url.startsWith("file://"))){result.setText("请输入有效 XPI 地址");return;} AddonController.installSigned(runtime,url,new AddonController.ExtensionCallback(){public void onSuccess(WebExtension e){runOnUiThread(()->{result.setText("安装成功："+displayName(e));refreshInstalled();});}public void onError(Throwable e){runOnUiThread(()->result.setText("安装失败："+safeError(e)));}});}
    private void refreshInstalled(){installedList.removeAllViews();installedList.addView(t("正在读取…",14));AddonController.list(runtime,new AddonController.ListCallback(){public void onSuccess(List<WebExtension> es){runOnUiThread(()->renderInstalled(es));}public void onError(Throwable e){runOnUiThread(()->{installedList.removeAllViews();installedList.addView(t("读取失败："+safeError(e),14));});}});}
    private void renderInstalled(List<WebExtension> es){installedList.removeAllViews();if(es==null||es.isEmpty()){installedList.addView(t("暂无扩展",14));return;}for(WebExtension e:es){boolean enabled=e.metaData==null||e.metaData.enabled;LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);String ver=e.metaData==null?"?":e.metaData.version;c.addView(t(displayName(e)+"\n版本 "+ver+" · "+(enabled?"已启用":"已停用")+(e.isBuiltIn?" · 系统":""),15));LinearLayout a=new LinearLayout(this);Button toggle=new Button(this);toggle.setText(enabled?"停用":"启用");toggle.setOnClickListener(v->setEnabled(e,!enabled));a.addView(toggle,new LinearLayout.LayoutParams(0,dp(46),1));if(!e.isBuiltIn){Button rm=new Button(this);rm.setText("卸载");rm.setOnClickListener(v->uninstall(e));a.addView(rm,new LinearLayout.LayoutParams(0,dp(46),1));}c.addView(a);installedList.addView(c);}}
    private void setEnabled(WebExtension e,boolean enabled){AddonController.ExtensionCallback cb=new AddonController.ExtensionCallback(){public void onSuccess(WebExtension x){runOnUiThread(()->{result.setText(enabled?"已启用":"已停用");refreshInstalled();});}public void onError(Throwable x){runOnUiThread(()->result.setText("操作失败："+safeError(x)));}};if(enabled)AddonController.enable(runtime,e,cb);else AddonController.disable(runtime,e,cb);}
    private void uninstall(WebExtension e){AddonController.uninstall(runtime,e,new AddonController.VoidCallback(){public void onSuccess(){runOnUiThread(()->{result.setText("已卸载："+displayName(e));refreshInstalled();});}public void onError(Throwable x){runOnUiThread(()->result.setText("卸载失败："+safeError(x)));}});}
    private void pickZip(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/x-zip-compressed","application/octet-stream"});startActivityForResult(i,PICK_EXTENSION_ZIP);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=PICK_EXTENSION_ZIP||resultCode!=RESULT_OK||data==null)return;Uri uri=data.getData();if(uri==null)return;result.setText("正在检查扩展包…");new Thread(()->{try{ExtensionPackageValidator.Result info=ExtensionPackageValidator.inspect(this,uri);FluxPackageStore.PackageInfo stored="C".equals(info.compatibility())?null:FluxPackageStore.importPackage(this,uri,info);String text="名称："+info.name()+"\n版本："+info.version()+"\n兼容等级："+info.compatibility()+"\n"+info.reason()+"\n"+(stored==null?"未保存执行包":"已保存到 Flux 私有仓库");runOnUiThread(()->{result.setText(text);renderFluxPackages();});}catch(Exception e){runOnUiThread(()->result.setText("检查失败："+safeError(e)));}}).start();}
    private TextView t(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);return v;} private LinearLayout.LayoutParams fullButtonParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));p.topMargin=dp(8);return p;}
    private static String displayName(WebExtension e){return e!=null&&e.metaData!=null&&e.metaData.name!=null&&!e.metaData.name.isBlank()?e.metaData.name:e==null?"Unknown":e.id;} private static String safeError(Throwable e){if(e==null)return"未知错误";String m=e.getMessage();return m==null||m.isBlank()?e.getClass().getSimpleName():m;} private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
