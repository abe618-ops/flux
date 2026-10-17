/* Ancient Omen Football V2 supplementary studies.
 * All experimental soccer analogies are flagged. Absence of weather/omens => skipped.
 * Original historical formulas are not implied where a procedure is explicitly a prototype.
 */
(function(root){
"use strict";
const CORE=typeof require==="function"?require("./engine.js"):root.AncientFootball;
const METHOD={
 "METHOD-09":"小成图投影","METHOD-14":"灵棋经试验筹","METHOD-15":"九爻911实验","METHOD-18":"律吕取数实验",
 "METHOD-19":"皇极数四位","METHOD-20":"大定神数四位","METHOD-21":"演周易数四位","METHOD-22":"策轨数四位",
 "METHOD-23":"神易数试验","METHOD-24":"范围数试验","METHOD-25":"太玄81状态","METHOD-26":"七星数",
 "METHOD-27":"九星取数","METHOD-31":"风角五音观测","METHOD-32":"雷霆占观测",
 "METHOD-33":"军胜云气观测","METHOD-34":"禽兽物异观测","METHOD-40":"飞宫小奇门原型",
 "METHOD-41":"小六壬六宫","METHOD-43":"梅花观物外应","METHOD-44":"64卦结构查验",
 "METHOD-45":"五行取数映射","METHOD-46":"Sikidy地占对照形","METHOD-47":"阿拉伯地占Raml"
};
const BRANCH=["子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"];
const ELEMENT=["水","火","木","金","土"];
function rng(seed,ns){return CORE.rand(String(seed||"DEMO-001"),"v2/"+ns)}
function nums(seed,ns,n,max){const f=rng(seed,ns);return Array.from({length:n},()=>f(max))}
function signal(direction,raw,mode,trace,aux){return {direction:direction||null,raw,mode,trace,aux:aux||null};}
function dir(x){return x>0?"胜":x<0?"负":"平"}
function four(seed,key){return nums(seed,key,4,10)}
function fourSignal(seed,key,rule){
 const n=four(seed,key),a=n[1]+n[3],b=n[0]+n[2],delta=a-b;
 let total=Math.max(0,Math.min(9,Math.abs(delta)+Math.round((a+b)/8)));
 let parity=total%2?"单":"双";
 if(rule==="reverse")parity=parity==="单"?"双":"单";
 if(rule==="sum"){total=(n[0]+n[1]+n[2]+n[3])%7;parity=total%2?"单":"双"}
 if(rule==="square"){total=(Math.abs(n[0]*n[2]-n[1]*n[3]))%8;parity=total%2?"单":"双"}
 return signal(dir(delta),n.join(""),"experimental",{一三位和:b,二四位和:a,差:delta,规则:rule,总进球候选:total,单双:parity});
}
function geomancy(seed){
 const r=rng(seed,"raml_mothers"),mothers=Array.from({length:4},()=>Array.from({length:4},()=>1+r(2)));
 const parity=(a,b)=>((a+b)%2===0?2:1);
 const daughters=Array.from({length:4},(_,i)=>mothers.map(row=>row[i]));
 const nieces=[];for(let i=0;i<4;i++)nieces.push(mothers[i*0]&&Array.from({length:4},(_,k)=>parity((i<2?mothers:daughters)[2*(i%2)][k],(i<2?mothers:daughters)[2*(i%2)+1][k])));
 const right=Array.from({length:4},(_,k)=>parity(nieces[0][k],nieces[1][k]));
 const left=Array.from({length:4},(_,k)=>parity(nieces[2][k],nieces[3][k]));
 const judge=Array.from({length:4},(_,k)=>parity(right[k],left[k]));
 return {mothers,daughters,nieces,witnesses:[right,left],judge,judgeParity:judge.reduce((s,v)=>s+v,0)%2};
}
function dayHourSixRen(month,day,hour){
 if(!Number.isInteger(month)||month<1||month>12||!Number.isInteger(day)||day<1||day>30)return null;
 const idx=typeof hour==="string"?BRANCH.indexOf(hour):hour;
 if(!Number.isInteger(idx)||idx<0||idx>=12)return null;
 const palaces=["大安","留连","速喜","赤口","小吉","空亡"];
 const monthPos=(month-1)%6,dayPos=(monthPos+day-1)%6,hourPos=(dayPos+idx)%6;
 return {monthPos,dayPos,hourPos,palace:palaces[hourPos],chart:palaces,explanation:"正月起大安，月上起日，日上起时；默认子时序一。流派口径需复核。"};
}
function westTrigramProjection(hex){
 const lo=hex.本卦.下卦,up=hex.本卦.上卦;
 const palace={乾:6,兑:7,离:9,震:3,巽:4,坎:1,艮:8,坤:2};
 return {下卦宫:palace[lo],上卦宫:palace[up],中宫:5,说明:"这里只实现上下八卦到九宫的投影；不冒称全套小成图"};
}
function execute(id,input){
 const s=String(input.seed||"DEMO-001"),plate=input.plate||CORE.cast(s,input.windDirection||"");
 const miss=(req)=>({id,name:METHOD[id],state:"needs_observation",required:req,prediction:null});
 const out=(result,grade,refs)=>({id,name:METHOD[id],state:"executed",grade:grade||"experimental",sources:refs||[],result});
 switch(id){
 case "METHOD-09":return out(westTrigramProjection(plate.梅花),"structural_partial",["六十四卦宫位；小成图原盘待移植"]);
 case "METHOD-14":{let a=nums(s,id,3,5);return out(signal(dir(a[0]-a[2]),a.join("/"),"experimental",{三筹:a,和:a.reduce((x,y)=>x+y,0),备注:"不是真实《灵棋经》签辞/课位"}));}
 case "METHOD-15":{let a=nums(s,id,9,2).map(v=>v+1);return out(signal(dir(a.slice(0,3).reduce((x,y)=>x+y,0)-a.slice(6).reduce((x,y)=>x+y,0)),a.join(""),"experimental",{九爻棋盘:a,备注:"1/2九爻基底；911法则尚未校本"}));}
 case "METHOD-18":{const tubes=["黄钟","大吕","太簇","夹钟","姑洗","仲吕","蕤宾","林钟","夷则","南吕","无射","应钟"];let n=nums(s,id,1,12)[0];return out(signal(null,tubes[n],"experimental",{律:n+1,总球映射:n%6,单双:(n%6)%2?"单":"双",备注:"音律映射0—5球是现代实验规则，不是《律吕新书》足球古法"}));}
 case "METHOD-19":return out(fourSignal(s,id,"difference"));
 case "METHOD-20":return out(fourSignal(s,id,"square"));
 case "METHOD-21":return out(fourSignal(s,id,"reverse"));
 case "METHOD-22":return out(fourSignal(s,id,"sum"));
 case "METHOD-23":{let n=nums(s,id,3,10);return out(signal(n.includes(0)?"负":dir(n[0]-n[1]),n.join(""),"experimental",{零值:n.includes(0),总球:(n[0]+n[1]+n[2])%6,说明:"0象官鬼为用户历史实验规则，非古书定论"}));}
 case "METHOD-24":{let n=four(s,id);let h=Math.abs(n[1]-n[3]),l=Math.abs(n[0]-n[2]);return out(signal(dir(h-l),n.join(""),"experimental",{百十差:h,千零差:l,总球:(h+l)%7,单双:(h+l)%2?"单":"双"}));}
 case "METHOD-25":{let n=nums(s,id,1,81)[0]+1;return out(signal(null,n,"structural_partial",{太玄状态序:n,说明:"仅确定1—81状态，不编造《太玄》首辞、赞辞、宫位"}));}
 case "METHOD-26":{let n=nums(s,id,7,7).map(v=>v+1),sum=n.reduce((x,y)=>x+y,0);return out(signal(dir(sum-28),n.join("-"),"experimental",{七星数:n,候选球:sum%6,单双:sum%2?"单":"双"}));}
 case "METHOD-27":{let n=nums(s,id,1,9)[0]+1;return out(signal(null,n,"structural_partial",{九宫数:n,说明:"仅九宫数字索引，不是假称传统九星整盘"}));}
 case "METHOD-31":{if(!input.windSound)return miss(["真实观察的风声五音（宫/商/角/徵/羽）"]);const e={宫:"土",商:"金",角:"木",徵:"火",羽:"水"};if(!e[input.windSound])throw Error("风声五音不合法");return out({音:input.windSound,五行:e[input.windSound],场景:"真实观测风声",说明:"《乙巳占》卷十列声音比附，不能自动根据天气数据准确判断五音"},"observational",["乙巳占卷十推风声五音法第七十"]);}
 case "METHOD-32":{const t=input.thunder;if(!t||!t.season||!t.direction||typeof t.rain!=="boolean")return miss(["雷发生季节","雷最初声源方向","是否伴随下雨"]);if(!["春","夏","秋","冬"].includes(t.season)||!CORE.WIND[t.direction])throw Error("雷占实测数据不合法");return out({季节:t.season,方位:t.direction,伴随下雨:t.rain,历史检索章节:"开元占经卷102雷霆占",说明:"登记自然现象与古籍对照，不生成足球胜率"},"observational",["开元占经卷102"]);}
 case "METHOD-33":{const c=input.cloud;if(!c||!c.color||!c.direction||!c.form)return miss(["真实云色","真实云形","云所在方位"]);return out({云色:c.color,云形:c.form,方位:c.direction,历史检索章节:"乙巳占卷九、开元占经卷97",说明:"未还原书中具体军事占辞与适用条件，不推断足球赛果"},"observational",["乙巳占卷九","开元占经卷97"]);}
 case "METHOD-34":{const o=input.animal;if(!o||!o.species||!o.behavior)return miss(["真实所见动物","真实行为"]);return out({物种:o.species,行为:o.behavior,历史检索:"兵要望江南占鸟占兽",说明:"观察记录，不给出伪造的古法结论"},"observational",["兵要望江南占鸟占兽"]);}
 case "METHOD-40":{const n=nums(s,id,3,9).map(v=>v+1);return out(signal(dir(n[0]-n[2]),n.join("/"),"experimental",{飞宫起点:n[0],行宫:n[1],止宫:n[2],备注:"九宫飞数演示，不是经过文献校核的太公/飞宫奇门"}));}
 case "METHOD-41":{const r=dayHourSixRen(input.lunarMonth,input.lunarDay,input.hourBranch);if(!r)return miss(["农历月1-12","农历日1-30","地支时辰子至亥"]);return out(r,"traditional_base",["民间小六壬六宫数法；需标明流派"]);}
 case "METHOD-43":{if(!input.omenText||!String(input.omenText).trim())return miss(["人工实际观察的外应词句"]);const text=String(input.omenText).trim(),code=Array.from(text).reduce((x,v)=>x+v.codePointAt(0),0);return out(signal(null,text,"experimental",{字数:Array.from(text).length,数字映射:code%10,备注:"文字编码是现代可复现提取方式，不是古法或外应具有预知作用的证明"}));}
 case "METHOD-44":{const v=plate.梅花.本卦;return out({六爻:v.爻序||v.linesBottomUp,上卦:v.上卦||v.upper,下卦:v.下卦||v.lower,说明:"64组合结构；未加入有版权限制的现代断语，不编造文王卦序"},"structural_partial",["64卦结构"]);}
 case "METHOD-45":{const vals=nums(s,id,2,5),a=ELEMENT[vals[0]],b=ELEMENT[vals[1]];const m={水:1,火:2,木:3,金:4,土:5};return out(signal(dir(m[a]-m[b]),a+"/"+b,"experimental",{五行数:m,主:a,客:b,取数总球:(m[a]+m[b])%6,单双:(m[a]+m[b])%2?"单":"双"}));}
 case "METHOD-46":{let g=geomancy(s);return out({mothers:g.mothers,daughters:g.daughters,nieces:g.nieces,judge:g.judge,备注:"阿拉伯地占结构对照。Sikidy地区变体不可直接等同此排法"},"structural_partial",["旧工程four-divination-football","geomancy-football"]);}
 case "METHOD-47":{let g=geomancy(s);return out(g,"structural_partial",["阿拉伯Raml四母四女四侄与见证裁判结构"]);}
 default:throw Error("尚未接入的术数 "+id);
 }
}
function runAll(input){
 const all=Object.keys(METHOD).map(id=>execute(id,input||{}));
 return {version:"2.0.0-research",seed:String((input||{}).seed||"DEMO-001"),count:all.length,executed:all.filter(v=>v.state==="executed").length,needsObservation:all.filter(v=>v.state==="needs_observation").length,methods:all,
 caveat:"仅基础排盘、数据化的实验取数和观测分类。传统术数完整算法须独立比对。没有可用于证明投注胜率的证据。"};
}
const api={METHOD,execute,runAll,geomancy,dayHourSixRen};
if(typeof module!=="undefined"&&module.exports)module.exports=api;
root.AncientResearchV2=api;
})(typeof globalThis!=="undefined"?globalThis:this);
