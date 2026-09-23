// YiXueFootballLab v1.2 randomization core
// Drop-in logic used by the v1.2 random-fix build.

function hash32(s){
  let h=2166136261>>>0;
  for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619)}
  return h>>>0;
}

function xorshift(seed){
  let x=seed>>>0||123456789;
  return()=>{x^=x<<13;x^=x>>>17;x^=x<<5;return(x>>>0)/4294967296};
}

function secure32(){
  try{
    const a=new Uint32Array(1);
    crypto.getRandomValues(a);
    return a[0]>>>0;
  }catch(e){
    return hash32(Date.now()+"|"+Math.random());
  }
}

function deriveSeed(master,id,extra=""){
  return hash32(String(master)+"|"+id+"|"+extra);
}

function randomDigits(n,seed){
  const r=xorshift(seed);
  let s="";
  for(let i=0;i<n;i++) s+=Math.floor(r()*10);
  return s;
}

let matchCounter=parseInt(localStorage.getItem("yxf_match_counter")||"0",10)||0;
let matchNonce=parseInt(localStorage.getItem("yxf_match_nonce")||"0",10)>>>0;
if(!matchNonce){
  matchNonce=secure32();
  localStorage.setItem("yxf_match_nonce",String(matchNonce));
}

function newMatchNonce(){
  matchCounter++;
  matchNonce=(secure32()^hash32(Date.now()+"|"+matchCounter+"|"+Math.random()))>>>0;
  localStorage.setItem("yxf_match_counter",String(matchCounter));
  localStorage.setItem("yxf_match_nonce",String(matchNonce));
  return matchNonce;
}

function buildMatchSeed(home,away,kickoff){
  return hash32(home+"|"+away+"|"+kickoff+"|"+matchNonce+"|"+matchCounter);
}

function buildBaseRandoms(matchSeed){
  return {
    n3: randomDigits(3,deriveSeed(matchSeed,"base3")),
    n4: randomDigits(4,deriveSeed(matchSeed,"base4")),
    n9: randomDigits(9,deriveSeed(matchSeed,"base9"))
  };
}

function buildModuleSeeds(matchSeed,inputs={}){
  const ids=["xlr","xmg","xjk","gzy","gg","sys","qhj","qksx","psz","zq","zg","bs"];
  const out={};
  for(const id of ids){
    out[id]=deriveSeed(matchSeed,id,JSON.stringify(inputs));
  }
  return out;
}

function moduleDisplayCode(seed){
  return String(seed%1000000).padStart(6,"0");
}

// Expected next-match flow:
// const nonce = newMatchNonce();
// const matchSeed = buildMatchSeed(home, away, kickoff);
// const base = buildBaseRandoms(matchSeed);
// const moduleSeeds = buildModuleSeeds(matchSeed, base);
// Each divination engine MUST use moduleSeeds[itsId], not the shared matchSeed.
