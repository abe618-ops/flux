from pathlib import Path
p=Path('app/src/main/java/app/p2psearchernext/android/MainActivity.java')
s=p.read_text()
R={
'Button settingsButton = compactButton("服务设置", Color.rgb(37, 99, 235));':'Button settingsButton = compactButton("复制全部", Color.rgb(37, 99, 235));',
'TextView subtitle = text("公开目录 · DHT/文件级深搜 · RSS/Torznab · 下载控制", 13,':'TextView subtitle = text("P2PSearcher 原生源 · eD2K Server + Kad2", 13,',
'"开放目录", "自建 DHT", "聚合/订阅", "全部深搜"':'"P2PSearcher 原生"',
'Button latestButton = compactButton("最新", Color.rgb(13, 148, 136));':'Button latestButton = compactButton("导出全部", Color.rgb(13, 148, 136));',
'TextView empty = text("输入关键词搜索，或点“最新”读取实时条目\\n可连接自己的 Rats、Torznab 与 RSS/Atom 服务", 15,':'TextView empty = text("输入关键词后搜索\\n仅显示 eD2K Server 与 Kad2 原生结果", 15,',
'status = text("就绪 · 搜索元数据；下载交给本机 BT 客户端或自己的 Rats", 12, Color.rgb(71, 85, 105));':'status = text("就绪 · 仅搜索 P2PSearcher 原生 eD2K/Kad2 元数据", 12, Color.rgb(71, 85, 105));',
'@Override public void onClick(View v) { performRealtime(); }':'@Override public void onClick(View v) { exportAllResults(); }',
'@Override public void onClick(View v) { showSettings(); }':'@Override public void onClick(View v) { copyAllResults(); }',
'"p2p-search-results-" + stamp + ".txt"':'"p2psearcher-native-results-" + stamp + ".txt"',
'b.append("P2P Searcher Next 搜索结果\\n");':'b.append("P2PSearcher 原生 eD2K/Kad2 搜索结果\\n");'
}
for a,b in R.items(): s=s.replace(a,b)
a=s.index('    private void startSearch(final String query, final boolean realtime) {')
b=s.index('    private void showResultActions',a)
core=r'''    private void startSearch(final String query, final boolean realtime) {
        String rejection = SafetyPolicy.rejectionReason(query);
        if (rejection.length() > 0) {
            showError("无法执行该查询", rejection + "\n\n本工具仅面向公开、获授权或用户自有内容。");
            return;
        }
        final String keyword = query == null ? "" : query.trim();
        if (keyword.length() == 0) { toast("请先输入关键词"); return; }
        hideKeyboard(); rememberQuery(keyword);
        final int generation = searchGeneration.incrementAndGet();
        setBusy(true, "P2PSearcher 原生搜索中 · eD2K Server + Kad2…");
        executor.execute(new Runnable() {
            @Override public void run() {
                final ArrayList<SearchResult> found = new ArrayList<SearchResult>();
                final ArrayList<String> errors = new ArrayList<String>();
                ArrayList<Callable<SourceBatch>> tasks = new ArrayList<Callable<SourceBatch>>();
                tasks.add(new Callable<SourceBatch>() { @Override public SourceBatch call() {
                    LegacyEd2kSearch.SearchBatch r = LegacyEd2kSearch.search(keyword);
                    return new SourceBatch(r.items, r.errors);
                }});
                tasks.add(new Callable<SourceBatch>() { @Override public SourceBatch call() {
                    NativeKadSearch.SearchBatch r = NativeKadSearch.search(getApplicationContext(), keyword);
                    return new SourceBatch(r.items, r.errors);
                }});
                ExecutorService pool = Executors.newFixedThreadPool(2);
                try {
                    for (Future<SourceBatch> f : pool.invokeAll(tasks)) { SourceBatch x=f.get(); found.addAll(x.items); errors.addAll(x.errors); }
                } catch (Exception e) { errors.add("P2PSearcher 原生检索：" + cleanError(e)); }
                finally { pool.shutdownNow(); }
                int blocked=0; ArrayList<SearchResult> allowed=new ArrayList<SearchResult>();
                for (SearchResult item:found) { if (SafetyPolicy.allowResult(item)) allowed.add(item); else blocked++; }
                final int blockedCount=blocked;
                final ArrayList<SearchResult> merged=mergeAndDeduplicate(allowed);
                Collections.sort(merged,new Comparator<SearchResult>() { @Override public int compare(SearchResult x, SearchResult y) { return Long.compare(y.rank(),x.rank()); }});
                final ArrayList<SearchResult> visible=merged.size()<=500?merged:new ArrayList<SearchResult>(merged.subList(0,500));
                final String errorText=summarizeErrors(errors);
                runOnUiThread(new Runnable(){ @Override public void run(){
                    if(generation!=searchGeneration.get()) return;
                    results.clear(); results.addAll(visible); adapter.notifyDataSetChanged();
                    String m="P2PSearcher 原生结果 "+results.size()+" 条";
                    if(blockedCount>0) m+=" · 已过滤 "+blockedCount+" 条";
                    if(errorText.length()>0) m+=" · 部分节点/服务器无响应";
                    setBusy(false,m);
                    if(results.isEmpty() && errorText.length()>0) showError("暂未取得结果",errorText);
                }});
            }
        });
    }

    private static final class SourceBatch {
        final ArrayList<SearchResult> items=new ArrayList<SearchResult>();
        final ArrayList<String> errors=new ArrayList<String>();
        SourceBatch(List<SearchResult> a,List<String> b){ if(a!=null)items.addAll(a); if(b!=null)errors.addAll(b); }
    }

'''
s=s[:a]+core+s[b:]
s=s.replace('actions.add("粘贴文字或磁力链接");\n        actions.add("导入本地 .torrent");','actions.add("粘贴关键词");')
s=s.replace('        if (configuredRatsUrl().length() > 0) actions.add("查看 Rats 下载任务");\n','')
s=s.replace('if ("粘贴文字或磁力链接".equals(action)) {\n                                    pasteAndAct();\n                                } else if ("导入本地 .torrent".equals(action)) {\n                                    startTorrentImport();','if ("粘贴关键词".equals(action)) {\n                                    pasteAndAct();')
s=s.replace('                                } else if ("查看 Rats 下载任务".equals(action)) {\n                                    fetchRatsDownloads();','')
start=s.index('    private void showAbout() {')
end=s.index('    private void showMetadata',start)
about='''    private void showAbout() {\n        String body = "P2PSearcher 原生源专版。\\n\\n" +\n                "• 只启用 eD2K Server Search 与 Kad2 关键词搜索\\n" +\n                "• 不显示 Internet Archive、Torznab、RSS、Rats Search 或其他聚合来源\\n" +\n                "• eD2K 与 Kad2 结果按 MD4 + 文件大小合并去重\\n" +\n                "• 顶部可一键复制全部结果、导出全部结果\\n" +\n                "• 工具菜单保留搜索历史、批量复制、批量导出与清空结果\\n\\n" +\n                "本工具仅用于元数据检索与外部客户端链接交接，请只检索、获取和分享你有权使用的内容。";\n        TextView view = text(body, 14, Color.rgb(30, 41, 59));\n        view.setPadding(dp(24), dp(8), dp(24), dp(8));\n        view.setTextIsSelectable(true);\n        new AlertDialog.Builder(this).setTitle("关于与使用边界").setView(view).setPositiveButton("知道了", null).show();\n    }\n\n'''
s=s[:start]+about+s[end:]
p.write_text(s)
