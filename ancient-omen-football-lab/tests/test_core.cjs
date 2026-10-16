const assert=require("node:assert/strict");
const e=require("../engine.js");
const a=e.predict({seed:"fixture",lambdaHome:1.4,lambdaAway:1.1});
assert.deepEqual(a,e.predict({seed:"fixture",lambdaHome:1.4,lambdaAway:1.1}));
assert.equal(a.盘.六爻.六爻自下而上.length,6);
assert.equal(a.盘.梅花.本卦.爻序.length,6);
for(let i=0;i<6;i++){let x=a.盘.六爻.六爻自下而上[i],o=a.盘.六爻.本卦.爻序[i],c=a.盘.六爻.变卦.爻序[i];assert.ok(x>=6&&x<=9);assert.equal(c,(x===6||x===9)?1-o:o)}
let m=a.盘.梅花,b=m.本卦.爻序;assert.deepEqual(m.互卦.爻序,[b[1],b[2],b[3],b[2],b[3],b[4]]);assert.deepEqual(m.错卦.爻序,b.map(v=>1-v));assert.deepEqual(m.综卦.爻序,b.slice().reverse());
let s=a.统计;assert.ok(Math.abs(s.胜+s.平+s.负-1)<1e-9);assert.ok(Math.abs(Object.values(s.半全场).reduce((x,y)=>x+y,0)-1)<1e-9);
assert.equal(e.wind("").是否起占,false);assert.equal(e.wind("东").古籍主客,"主");assert.equal(e.wind("西北").古籍主客,"客");
assert.equal(e.reveal(a,1,1).方向,"平");assert.throws(()=>e.football(-1,1));
console.log("PASS: deterministic casts, correct transforms, valid distribution, weather gating and reveal");
