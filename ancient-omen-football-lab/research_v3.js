/* Eighteen remaining registered studies: exact input-gated computation, original JVM adapters, or manuscripts.
 * V3 never fabricates ancient divinatory rules. Scores (where present) remain independent experiments.
 */
(function(root){
"use strict";
const CORE=typeof require==="function"?require("./engine.js"):root.AncientFootball;
const B="子丑寅卯辰巳午未申酉戌亥".split("");
const G="甲乙丙丁戊己庚辛壬癸".split("");
const P="寅卯辰巳午未申酉戌亥子丑".split("");
const E={甲:"木",乙:"木",寅:"木",卯:"木",丙:"火",丁:"火",巳:"火",午:"火",戊:"土",己:"土",辰:"土",戌:"土",丑:"土",未:"土",庚:"金",辛:"金",申:"金",酉:"金",壬:"水",癸:"水",亥:"水",子:"水"};
const O={
 "乾":{in:["甲子","甲寅","甲辰"],out:["壬午","壬申","壬戌"]},
 "坤":{in:["乙未","乙巳","乙卯"],out:["癸丑","癸亥","癸酉"]},
 "震":{in:["庚子","庚寅","庚辰"],out:["庚午","庚申","庚戌"]},
 "巽":{in:["辛丑","辛亥","辛酉"],out:["辛未","辛巳","辛卯"]},
 "坎":{in:["戊寅","戊辰","戊午"],out:["戊申","戊戌","戊子"]},
 "离":{in:["己卯","己丑","己亥"],out:["己酉","己未","己巳"]},
 "艮":{in:["丙辰","丙午","丙申"],out:["丙戌","丙子","丙寅"]},
 "兑":{in:["丁巳","丁卯","丁丑"],out:["丁亥","丁酉","丁未"]}
};
const MANSIONS="角亢氐房心尾箕斗牛女虚危室壁奎娄胃昴毕觜参井鬼柳星张翼轸".split("");
const THEMES={
 "METHOD-06":{name:"时家、刻家与分秒奇门",state:"jvm_ready",cmd:"bash ancient-omen-football-lab/tools/run-traditional.sh 2026-10-17T13:00",notes:"原Java支持时家、10分钟刻家、12分钟刻家、日/月/年家。分/秒不是确证古法，另留实验标记"},
 "METHOD-07":{name:"大六壬天地盘四课三传",state:"jvm_partial",cmd:"bash ancient-omen-football-lab/tools/run-traditional.sh 2026-10-17T13:00",notes:"原Java给12支天地盘、四课及唯一贼克/元首的三传；涉害/比用/遥克等复杂情况返回null"},
 "METHOD-08":{name:"太乙九宫四计",state:"jvm_ready",cmd:"bash ancient-omen-football-lab/tools/run-traditional.sh 2026-10-17T13:00",notes:"沿用原Java四计七十二局基础模型，完整占辞尚未复原"},
 "METHOD-10":{name:"金口诀四位",state:"partial_js"},
 "METHOD-11":{name:"七政四余星位",state:"input_gated"},
 "METHOD-12":{name:"西洋占星实验盘",state:"jvm_experimental",cmd:"bash ancient-omen-football-lab/tools/run-astro.sh DEMO-001",notes:"旧引擎采用随机日期与近似天文，不是赛事实时星历"},
 "METHOD-13":{name:"印度占星实验盘",state:"jvm_experimental",cmd:"bash ancient-omen-football-lab/tools/run-astro.sh DEMO-001",notes:"旧引擎以近似岁差和月宿随机盘做实验，不是精密星历"},
 "METHOD-16":{name:"文王八卦纳甲六爻",state:"partial_js"},
 "METHOD-17":{name:"六爻穿禽",state:"input_gated"},
 "METHOD-28":{name:"演禽二十八宿",state:"jvm_partial",cmd:"bash ancient-omen-football-lab/tools/run-traditional.sh 2026-10-17T13:00",notes:"仅值日二十八宿、禽星、星期七曜；未实现翻禽倒将"},
 "METHOD-29":{name:"古灵神课",state:"source_gated"},
 "METHOD-30":{name:"紫微斗数宫位骨架",state:"partial_js"},
 "METHOD-35":{name:"清华简《筮法》",state:"manuscript_numeric"},
 "METHOD-36":{name:"王家台秦简《归藏》",state:"manuscript_annotation"},
 "METHOD-37":{name:"五兆卜",state:"provenance_gated"},
 "METHOD-38":{name:"燕秦四象",state:"provenance_gated"},
 "METHOD-39":{name:"汲冢《易繇阴阳卦》",state:"manuscript_annotation"},
 "METHOD-42":{name:"太公奇门",state:"school_variant_gated"}
};
function done(id,grade,result,sources){return {id,name:THEMES[id].name,phase:grade,result,sources:sources||[],warning:"文献校核或可运行结构不等于预测足球的能力"}}
function need(id,fields,details){return {id,name:THEMES[id].name,phase:"needs_input_or_source",required:fields,notes:details||THEMES[id].notes||null,results:null}}
function mod(a,b){return (a%b+b)%b}
function ERel(up,down){let a=E[up],b=E[down];const make={木:"火",火:"土",土:"金",金:"水",水:"木"},control={木:"土",土:"水",水:"火",火:"金",金:"木"};return a===b?"比和":make[a]===b?"上生下":make[b]===a?"下生上":control[a]===b?"上克下":control[b]===a?"下克上":"未知"}
function nacjia(plate){
 const src=plate && (plate.六爻||plate.liuyao);
 if(!src)throw Error("必须先生成六爻盘");
 const h=src.本卦||src.original,c=src.变卦||src.changed;
 if(!h||!O[h.下卦||h.lower]||!O[h.上卦||h.upper])throw Error("无效六爻盘");
 const branches=[...O[h.下卦||h.lower].in,...O[h.上卦||h.upper].out];
 const bits=h.爻序||h.linesBottomUp,move=src.动爻||src.moveLines||[];
 return {original_hex:h,changed_hex:c,lines:branches.map((stembranch,i)=>({from_bottom:i+1,stembranch,yang:bits[i]===1,move:move.includes(i+1),element:E[stembranch[1]]})),notes:"八卦纳甲纳支基础层；尚未实现按八宫定世应、六亲六神、旬空、月日旺衰，不得按完整纳甲六爻预测足球"};
}
function jinkou(v){
 if(!v || !G.includes(v.dayStem)||!B.includes(v.earthBranch)||!B.includes(v.hourBranch)||!B.includes(v.monthGeneral))
 return need("METHOD-10",["日干","地分地支","时支","月将地支"],"贵神需另按昼夜、日干和流派安位；无值时不编造");
 const start={"甲":0,"己":0,"乙":2,"庚":2,"丙":4,"辛":4,"丁":6,"壬":6,"戊":8,"癸":8}[v.dayStem];
 const pos=B.indexOf(v.earthBranch),hour=B.indexOf(v.hourBranch),mg=B.indexOf(v.monthGeneral);
 const human=G[(start+pos)%10],general=B[(mg+pos-hour+12)%12];
 const noble=v.nobleSpirit||null;
 if(noble&&!B.includes(noble))throw Error("贵神地支必须是十二支");
 return done("METHOD-10","partial_four_positions",{
    人元:human,贵神:noble,月将:general,地分:v.earthBranch,
    relationships:{人元与地分:ERel(human,v.earthBranch),将与地分:ERel(general,v.earthBranch),人元与将:ERel(human,general)},
    calculation:"五鼠遁子位起人元，月将加时旋转至地分；贵神无独立可核实起法则留空",
    caution:"金口诀四位并非四课三传；贵神为空时不声称完整排课"
 },["六壬神课金口诀四位法"]);
}
function sevenBodies(raw){
 const keys=["太阳","月亮","火星","水星","木星","金星","土星","紫气","月孛","罗睺","计都"];
 const missing=keys.filter(x=>!raw||!Number.isFinite(raw[x])||raw[x]<0||raw[x]>=360);
 if(missing.length)return need("METHOD-11",missing.map(x=>"黄经 "+x),"需带历元、时区及黄道坐标制的可信星历；本模块不伪造七政四余的天体位置");
 const signs="白羊金牛双子巨蟹狮子处女天秤天蝎射手摩羯水瓶双鱼".match(/.{2}/g);
 return done("METHOD-11","observed_longitudes",keys.map(name=>({name,longitude:raw[name],sign:signs[Math.floor(raw[name]/30)],degree_in_sign:Number((raw[name]%30).toFixed(4))})),["星历数据由调用者提供，尚无传统宫位、用神和足球映射"]);
}
function mingShen(month,hour){
 if(!Number.isInteger(month)||month<1||month>12||!B.includes(hour))return need("METHOD-30",["出生或实验农历月1—12","出生或实验时支"],"真正命盘还需性别、年干支、历法、命宫局数与诸星安宫规则");
 const basis=mod(2+month-1,12),h=B.indexOf(hour),life=mod(basis-h,12),body=mod(basis+h,12);
 const palaceNames=["命宫","兄弟","夫妻","子女","财帛","疾厄","迁移","仆役","官禄","田宅","福德","父母"];
 return done("METHOD-30","twelve_palaces_only",{month,hour,命宫:B[life],身宫:B[body],十二宫:palaceNames.map((name,i)=>({name,branch:B[mod(life-i,12)]})),note:"寅起正月顺行至月，再由子时逆行安命顺行安身。没有紫微天府与其他星曜，不生成整张紫微斗数命盘"},["紫微十二宫安命身基础口诀"]);
}
function numberManuscript(input){
 const xs=input&&input.lines;
 if(!Array.isArray(xs)||xs.length!==12||!xs.every(x=>[1,4,5,6,8,9].includes(x)))
 return need("METHOD-35",["12个已考证的数字爻，每项须是1/4/5/6/8/9"],"清华简数字卦与后世周易阴阳爻不同，不转换为普通六爻吉凶");
 return done("METHOD-35","paired_numeric_hexagrams",{first:xs.slice(0,6),second:xs.slice(6),notes:"两组六位数字卦仅作结构录入；简文三位卦还存在一六数式，需要保留不同资料类型"},["清华简《筮法》研究","清华大学哲学系数字卦研究"]);
}
function manuscript(id,input){
 const note={ "METHOD-29":"名称与古籍归属尚缺可信一手证据，切勿制造所谓古灵神课条目",
 "METHOD-36":"王家台《归藏》内容含卦画、卦名、繇辞；传世《归藏》与汲冢文献同异争议须分开",
 "METHOD-37":"五兆卜不是已证实的一种可用整数算法；研究龟卜五兆具体传本文献后才可录规则",
 "METHOD-38":"燕秦四象名称、文献归属尚未确认；仅接受带实物/文献证据的研究札记",
 "METHOD-39":"汲冢《易繇阴阳卦》与王家台《归藏》是否同源有学术争议，禁止直接等同"};
 if(!input||!input.citation||!input.text)return need(id,["具体文献篇章及版本","准确的原文释文或观察记录"],note[id]);
 return done(id,"manual_source_annotation",{citation:String(input.citation).slice(0,300),text:String(input.text).slice(0,2000),interpretation:"未自动推断吉凶；须进行文献校勘"},id==="METHOD-36"?["王家台秦简《易占》研究"]:["待核来源"]);
}
function execute(id,input){
 const v=input||{},seed=String(v.seed||"DEMO-001");
 if(!THEMES[id])throw Error("未知方法 "+id);
 const entry=THEMES[id];
 if(entry.state.startsWith("jvm"))return {id,name:entry.name,phase:entry.state,runner:entry.cmd,notes:entry.notes,requires:"GitHub Actions 或本地 Java 17 CLI",result:null};
 switch(id){
 case "METHOD-10":return jinkou(v.jinkou);
 case "METHOD-11":return sevenBodies(v.longitudes);
 case "METHOD-16":return done(id,"najia_branch_base",nacjia(v.plate||CORE.cast(seed,v.windDirection||"")),["纳甲装支对照，流派校核中"]);
 case "METHOD-17": {
   if(!v.mansion||!MANSIONS.includes(v.mansion))return need(id,["可靠来源的值日二十八宿名称（单个宿字）"],"六爻穿禽须确认具体流派与爻位映射规则");
   return done(id,"combined_annotation",{mansion:v.mansion,context:v.plate?{hex:(v.plate.六爻||{}).本卦||null}:null,notes:"禽宿登记与六爻关联，不凭宿名捏造六爻穿禽完整禽星安位"},["二十八宿禽星基础资料"]);
 }
 case "METHOD-29":case "METHOD-36":case "METHOD-37":case "METHOD-38":case "METHOD-39":return manuscript(id,v.documentEvidence&&v.documentEvidence[id]);
 case "METHOD-30":return mingShen(v.lunarMonth,v.hourBranch);
 case "METHOD-35":return numberManuscript(v.qinghua);
 case "METHOD-42":return v.originalQimen?done(id,"comparison_only",{qimen:v.originalQimen,notes:"这是既有奇门基盘的比较容器，不是文献不明的太公奇门独立起局，不能额外投票"},["旧奇门原始盘"]):need(id,["既有奇门完整盘面 JSON","太公奇门可考流派规则"],"流派未核实前不能伪造第二套奇门局");
 default:throw Error("没有模块");
 }
}
function all(input){return {version:"3.0-research",methods:Object.keys(THEMES).map(id=>execute(id,input||{})),methodCount:18,note:"Java模块只提供命令行入口；网页中不声称执行本机Java。出土文献缺少可靠算法时只显示研究表单。"}}
const api={THEMES,execute,all,nacjia,jinkou,sevenBodies,mingShen,numberManuscript};
if(typeof module!=="undefined"&&module.exports)module.exports=api;root.AncientResearchV3=api;
})(typeof globalThis!=="undefined"?globalThis:this);
