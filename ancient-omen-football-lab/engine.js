(function(root){
"use strict";
const EIGHT=[["乾","111"],["兑","110"],["离","101"],["震","100"],["巽","011"],["坎","010"],["艮","001"],["坤","000"]];
const WIND={西北:["乾","客"],北:["坎","客"],东北:["艮","客"],东:["震","主"],东南:["巽","主"],南:["离","主"],西南:["坤","主"],西:["兑","客"]};
function hash(s){let h=2166136261;for(const c of String(s)){h=Math.imul(h^c.charCodeAt(0),16777619)}return h>>>0}
function rand(seed,section){let n=hash(seed+"|"+section)||123456789;return function(limit){n^=n<<13;n^=n>>>17;n^=n<<5;n>>>=0;return n%limit}}
function trigram(bits){let t=EIGHT.find(v=>v[1]===bits.join(""));if(!t)throw Error("八卦爻序无效");return t[0]}
function hex(bits){return {下卦:trigram(bits.slice(0,3)),上卦:trigram(bits.slice(3,6)),爻序:bits.slice()}}
function sixYao(seed){const r=rand(seed,"liuyao"),lines=Array.from({length:6},()=>6+r(2)+r(2)+r(2)),b=lines.map(v=>v===7||v===9?1:0),changed=b.map((x,i)=>lines[i]===6||lines[i]===9?1-x:x);
return {流派:"三钱随机模拟",六爻自下而上:lines,动爻:lines.flatMap((x,i)=>x===6||x===9?[i+1]:[]),本卦:hex(b),变卦:hex(changed)}}
function meiHua(seed){const r=rand(seed,"meihua"),nums=[1+r(999),1+r(999),1+r(999)],upper=(nums[0]%8)||8,lower=(nums[1]%8)||8,moving=(nums.reduce((a,b)=>a+b,0)%6)||6;
const b=(EIGHT[lower-1][1]+EIGHT[upper-1][1]).split("").map(Number),change=b.slice();change[moving-1]=1-change[moving-1];return {流派:"三数起卦实验口径",三数:nums,动爻:moving,本卦:hex(b),变卦:hex(change),互卦:hex([b[1],b[2],b[3],b[2],b[3],b[4]]),错卦:hex(b.map(x=>1-x)),综卦:hex(b.slice().reverse())}}
function wind(direction){if(!WIND[direction])return {是否起占:false,说明:"缺少真实风向观测，不生成风角结果"};return {是否起占:true,来风:direction,八卦:WIND[direction][0],古籍主客:WIND[direction][1],来源:"《乙巳占》卷十末《占风图占八风知主客胜负法》；同卷《八方暴风占》存在不同主客分法，不能混同",边界:"古代军队主客不等于足球主客；现代比附需独立盲测"}}
function factorial(n){let x=1;for(let i=2;i<=n;i++)x*=i;return x}
function p(k,l){return Math.exp(-l)*l**k/factorial(k)}
function result(h,a){return h>a?"胜":h<a?"负":"平"}
function football(lh,la){lh=Number(lh);la=Number(la);if(!Number.isFinite(lh)||!Number.isFinite(la)||lh<0||la<0||lh>7||la>7)throw Error("预期进球须在0—7");
let W=0,D=0,L=0,over=0,odd=0,btts=0,mass=0,scores=[],totals=Array(12).fill(0);
for(let h=0;h<=16;h++)for(let a=0;a<=16;a++){const q=p(h,lh)*p(a,la);mass+=q;scores.push({比分:h+":"+a,概率:q});if(h>a)W+=q;else if(h===a)D+=q;else L+=q;if(h+a>=3)over+=q;if((h+a)%2)odd+=q;if(h>0&&a>0)btts+=q;totals[Math.min(11,h+a)]+=q}
const half={};let hm=0;for(let h1=0;h1<=9;h1++)for(let a1=0;a1<=9;a1++)for(let h2=0;h2<=9;h2++)for(let a2=0;a2<=9;a2++){let q=p(h1,lh/2)*p(a1,la/2)*p(h2,lh/2)*p(a2,la/2),key=result(h1,a1)+result(h1+h2,a1+a2);half[key]=(half[key]||0)+q;hm+=q}
for(let k in half)half[k]/=hm;
return {胜:W/mass,平:D/mass,负:L/mass,大2点5:over/mass,单:odd/mass,双方进球:btts/mass,比分候选:scores.sort((a,b)=>b.概率-a.概率).slice(0,5).map(v=>({比分:v.比分,概率:v.概率/mass})),半全场:half,总球分布:totals.map(v=>v/mass)}}
function cast(seed,direction){if(typeof seed!=="string"||!seed.trim())throw Error("请输入封盘种子");const a=sixYao(seed),b=meiHua(seed),w=wind(direction),yang=a.本卦.爻序.reduce((x,y)=>x+y,0),low=b.本卦.爻序.slice(0,3).reduce((x,y)=>x+y,0),up=b.本卦.爻序.slice(3,6).reduce((x,y)=>x+y,0);
const methods=[{法:"六爻",方向:yang>=4?"胜":yang<=2?"负":"平",依据:"阳爻计数现代实验映射"},{法:"梅花",方向:low>up?"胜":low<up?"负":"平",依据:"上下卦阳爻计数现代实验映射"}];if(w.是否起占)methods.push({法:"风角",方向:w.古籍主客==="主"?"胜":"负",依据:"古代兵占主客类比足球主客（未经检验）"});
const votes={胜:0,平:0,负:0};methods.forEach(v=>votes[v.方向]++);
return {seed,六爻:a,梅花:b,风角:w,实验方向:methods,票数:votes,提示:"合参票数不是统计胜率；不能反复抽到一致再宣称提高准确率"}}
function predict(input){const opt=input||{};return {封盘时间:opt.timestamp||null,赛事:opt.match||"手工示例",盘:cast(String(opt.seed||"DEMO-001"),opt.wind),统计:football(opt.lambdaHome===undefined?1.4:opt.lambdaHome,opt.lambdaAway===undefined?1.1:opt.lambdaAway),说明:"比赛数据、风向须为赛前真实采集。统计与术数平行展示，不以术数改写真实概率"}}
function reveal(f,h,a){if(!Number.isInteger(h)||!Number.isInteger(a)||h<0||a<0)throw Error("请输入合法比分");const act=result(h,a),s=f.统计,pred=Object.entries({胜:s.胜,平:s.平,负:s.负}).sort((a,b)=>b[1]-a[1])[0][0];return {最终比分:h+":"+a,方向:act,统计方向命中:act===pred,大小命中:(h+a>=3)===(s.大2点5>=.5),单双命中:(h+a)%2===(s.单>=.5?1:0),首选比分命中:s.比分候选[0].比分===h+":"+a,术数命中:f.盘.实验方向.map(x=>({法:x.法,命中:x.方向===act}))}}
const api={EIGHT,WIND,hash,rand,hex,sixYao,meiHua,wind,football,cast,predict,reveal};
if(typeof module!=="undefined"&&module.exports)module.exports=api;root.AncientFootball=api;
})(typeof globalThis!=="undefined"?globalThis:this);
