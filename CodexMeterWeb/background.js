
const ALARM="codexmeter-refresh";
chrome.alarms.create(ALARM,{periodInMinutes:5});

async function findChatGPTTab(){
  const tabs=await chrome.tabs.query({url:"https://chatgpt.com/*"});
  if(!tabs.length) return null;
  return tabs.find(t=>t.active)||tabs[0];
}

async function fetchNow(){
  const tab=await findChatGPTTab();
  if(!tab || !tab.id) return {ok:false,error:"请先打开并登录 chatgpt.com"};
  try{
    const data=await chrome.tabs.sendMessage(tab.id,{type:"CODEXMETER_FETCH"});
    if(data && data.ok){
      await chrome.storage.local.set({codexMeterData:data,codexMeterError:null});
      return data;
    }
    await chrome.storage.local.set({codexMeterError:(data&&data.error)||"读取失败"});
    return data;
  }catch(e){
    const out={ok:false,error:"请刷新一次 ChatGPT 页面后再试"};
    await chrome.storage.local.set({codexMeterError:out.error});
    return out;
  }
}

chrome.runtime.onMessage.addListener((msg,_sender,sendResponse)=>{
  if(!msg || msg.type!=="CODEXMETER_REFRESH") return false;
  fetchNow().then(sendResponse);
  return true;
});
chrome.alarms.onAlarm.addListener(a=>{if(a.name===ALARM)fetchNow();});
chrome.runtime.onInstalled.addListener(()=>fetchNow());
chrome.runtime.onStartup.addListener(()=>fetchNow());
