/* Native Android companion panel; this asset is ONLY loaded by the Android package.
 * Browser source lab remains independent and does not pretend to have a JVM.
 */
(function() {
 "use strict";
 if (!window.AndroidNative) return;
 const host=document.querySelector(".head")||document.querySelector("header")||document.body;
 const card=document.createElement("section");
 card.className="card";
 card.id="android-native";
 const h=document.createElement("h2");h.textContent="Android 原生排盘 · V3";card.appendChild(h);
 const p=document.createElement("p");p.className="muted tiny";
 p.textContent="安卓本地运行旧Java引擎：奇门1080局、Maya/Ifá/Sikidy/Raml、兵占、时家与刻家、太乙四计、六壬部分三传、演禽、占星实验。不需要网络。";card.appendChild(p);
 const wrap=document.createElement("div");wrap.className="fields";card.appendChild(wrap);
 const label=document.createElement("label");label.textContent="民用时间（当地时间；不自动修正真太阳时）";wrap.appendChild(label);
 const when=document.createElement("input");when.type="datetime-local";when.id="nativeWhen";wrap.appendChild(when);
 function localIso(d) {const pad=x=>String(x).padStart(2,"0");
   return d.getFullYear()+"-"+pad(d.getMonth()+1)+"-"+pad(d.getDate())+"T"+pad(d.getHours())+":"+pad(d.getMinutes());}
 when.value=localIso(new Date());
 const controls=document.createElement("div");card.appendChild(controls);
 function button(title,fn){
   const b=document.createElement("button");b.textContent=title;controls.appendChild(b);
   b.addEventListener("click",()=>{try{fn()}catch(e){status.textContent="运行错误："+String(e.message||e)}});return b;
 }
 const status=document.createElement("p");status.className="muted tiny";card.appendChild(status);
 const details=document.createElement("details");details.open=true;details.style.marginTop="10px";card.appendChild(details);
 const summary=document.createElement("summary");summary.textContent="原生引擎结果与完整盘面";details.appendChild(summary);
 const pre=document.createElement("pre");pre.style.whiteSpace="pre-wrap";pre.style.wordBreak="break-word";
 pre.style.fontSize="12px";pre.style.maxHeight="65vh";pre.style.overflowY="auto";
 pre.style.background="#111d2b";pre.style.padding="12px";pre.style.borderRadius="8px";details.appendChild(pre);
 let nativeRecord=null;
 function read(obj){
   const raw=typeof obj==="string"?obj:JSON.stringify(obj);
   const x=JSON.parse(raw);
   if(x.error)throw Error(x.error);
   nativeRecord=x;
   pre.textContent=JSON.stringify(x,null,2);
   details.open=true;
 }
 function seed(){return document.getElementById("seed").value;}
 button("旧术数一键排盘",()=>{status.textContent="旧版原生计算完成（实验模型）";read(window.AndroidNative.runOldSystems(seed()));});
 button("太乙·六壬·奇门·演禽",()=>{read(window.AndroidNative.runCalendarSystems(when.value));status.textContent="日历盘已完成；时区以设备所填民用时间为准。";});
 button("西洋 / 印度占星",()=>{read(window.AndroidNative.runAstro(seed()));status.textContent="占星结果来自旧实验引擎的近似随机时点，并非赛事星图。";});
 button("生成新种子",()=>{const v=window.AndroidNative.newSeed();document.getElementById("seed").value=v;
   const run=document.getElementById("run")||document.getElementById("cast");if(run)run.click();status.textContent="已用安卓安全随机源更新种子";});
 button("保存当前封盘",()=>{
   const record={seed:seed(),native:nativeRecord,web_prediction:typeof last!=="undefined"?last:null,record_type:"pre_result_prediction"};
   const r=JSON.parse(window.AndroidNative.saveSnapshot(JSON.stringify(record)));
   if(r.error)throw Error(r.error);status.textContent="已保存至手机应用内部（不上传云端）";
 });
 button("查看历史封盘",()=>{
   const x=JSON.parse(window.AndroidNative.readSnapshots());
   if(x.error)throw Error(x.error);
   pre.textContent=JSON.stringify(x,null,2);details.open=true;
   status.textContent="已读取 "+x.length+" 条最近保存的历史记录";
 });
 card.insertBefore(document.createElement("hr"),controls);
 if(host.nextSibling)host.parentNode.insertBefore(card,host.nextSibling);
 else host.parentNode.appendChild(card);
 status.textContent="当前手机离线运行；所有方法的足球应用均属待验证研究。";
})();
