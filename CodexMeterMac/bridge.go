package main

import (
  "bufio"
  "crypto/rand"
  "encoding/base64"
  "encoding/json"
  "fmt"
  "io"
  "log"
  "net"
  "net/http"
  "os"
  "os/exec"
  "strconv"
  "strings"
  "sync"
  "time"
)

type RPC struct {
  cmd *exec.Cmd
  in io.WriteCloser
  out *bufio.Reader
  mu sync.Mutex
  next int
}

func NewRPC(codex string) (*RPC, error) {
  cmd := exec.Command(codex, "-s", "read-only", "-a", "never", "app-server")
  in, err := cmd.StdinPipe(); if err != nil { return nil, err }
  stdout, err := cmd.StdoutPipe(); if err != nil { return nil, err }
  cmd.Stderr = os.Stderr
  if err := cmd.Start(); err != nil { return nil, err }
  r := &RPC{cmd:cmd, in:in, out:bufio.NewReader(stdout), next:1}
  if _, err := r.Call("initialize", map[string]any{
    "clientInfo":map[string]any{"name":"codexmeter-bridge","title":"CodexMeter Bridge","version":"0.5.0"},
    "capabilities":map[string]any{},
  }); err != nil { return nil, err }
  r.write(map[string]any{"method":"initialized","params":map[string]any{}})
  return r,nil
}
func (r *RPC) write(v any) error { b,_:=json.Marshal(v); _,err:=fmt.Fprintf(r.in,"%s\n",b); return err }
func (r *RPC) Call(method string, params any) (map[string]any,error) {
  r.mu.Lock(); defer r.mu.Unlock()
  id:=r.next; r.next++
  if err:=r.write(map[string]any{"method":method,"id":id,"params":params}); err!=nil{return nil,err}
  for {
    line,err:=r.out.ReadString('\n'); if err!=nil{return nil,err}
    var m map[string]any
    if json.Unmarshal([]byte(line),&m)!=nil { continue }
    mid,ok:=m["id"].(float64); if !ok || int(mid)!=id { continue }
    if e,ok:=m["error"]; ok { return nil,fmt.Errorf("%v",e) }
    if res,ok:=m["result"].(map[string]any); ok { return res,nil }
    return map[string]any{},nil
  }
}

func rnd(n int) string { b:=make([]byte,n); rand.Read(b); return base64.RawURLEncoding.EncodeToString(b) }
func mget(m map[string]any,k string) map[string]any { if x,ok:=m[k].(map[string]any); ok{return x}; return map[string]any{} }
func fget(m map[string]any,k string) float64 { if x,ok:=m[k].(float64); ok{return x}; return 0 }
func sget(m map[string]any,k string) string { if x,ok:=m[k].(string); ok{return x}; return "" }
func normWindow(w map[string]any) map[string]any {
  used:=fget(w,"usedPercent"); mins:=fget(w,"windowDurationMins"); reset:=fget(w,"resetsAt")
  return map[string]any{"used_percent":used,"remaining_percent":100-used,"window_minutes":mins,"resets_at":reset}
}
func chooseSnapshot(rate map[string]any) map[string]any {
  by:=mget(rate,"rateLimitsByLimitId")
  if c:=mget(by,"codex"); len(c)>0 { return c }
  return mget(rate,"rateLimits")
}
func chooseWindows(s map[string]any)(map[string]any,map[string]any){
  ws:=[]map[string]any{}
  if p:=mget(s,"primary");len(p)>0{ws=append(ws,p)}
  if q:=mget(s,"secondary");len(q)>0{ws=append(ws,q)}
  pick:=func(target float64)map[string]any{
    if len(ws)==0{return map[string]any{}}
    best:=ws[0]; bd:=abs(fget(best,"windowDurationMins")-target)
    for _,w:=range ws[1:] { d:=abs(fget(w,"windowDurationMins")-target); if d<bd {best=w;bd=d} }
    return best
  }
  return pick(300),pick(10080)
}
func abs(x float64)float64{if x<0{return -x};return x}

func normalize(r *RPC)(map[string]any,error){
  acc,err:=r.Call("account/read",map[string]any{"refreshToken":false}); if err!=nil{return nil,err}
  rate,err:=r.Call("account/rateLimits/read",map[string]any{}); if err!=nil{return nil,err}
  usage,_:=r.Call("account/usage/read",map[string]any{})
  snap:=chooseSnapshot(rate); five,week:=chooseWindows(snap)
  a:=mget(acc,"account")
  summary:=mget(usage,"summary")
  daily:=[]any{}; if x,ok:=usage["dailyUsageBuckets"].([]any);ok{daily=x}
  today:=time.Now()
  sumDays:=func(days int)int64{
    var sum int64
    cut:=time.Date(today.Year(),today.Month(),today.Day(),0,0,0,0,today.Location()).AddDate(0,0,-days+1)
    for _,v:=range daily{
      b,ok:=v.(map[string]any); if !ok{continue}
      ds:=sget(b,"startDate"); if len(ds)>=10 { ds=ds[:10] }
      d,e:=time.Parse("2006-01-02",ds); if e==nil && !d.Before(cut) { sum+=int64(fget(b,"tokens")) }
    }
    return sum
  }
  return map[string]any{
    "account":map[string]any{"email":sget(a,"email"),"plan":sget(a,"planType")},
    "limits":map[string]any{"five_hour":normWindow(five),"weekly":normWindow(week)},
    "tokens":map[string]any{
      "today":sumDays(1),"week":sumDays(7),"thirty_days":sumDays(30),
      "lifetime":fget(summary,"lifetimeTokens"),"input":0,"cached_input":0,"output":0,"daily":daily,
    },
    "credits":map[string]any{"reset_credits":0},
    "tasks":[]any{},"source":"Mac Codex Bridge","updatedAt":time.Now().Unix(),
  },nil
}

func beacon(port int,nonce string){
  addr,_:=net.ResolveUDPAddr("udp4","255.255.255.255:38765")
  c,_:=net.DialUDP("udp4",nil,addr); if c==nil{return}; defer c.Close()
  host,_:=os.Hostname()
  for {
    b,_:=json.Marshal(map[string]any{"magic":"CODEXMETER_DISCOVERY_V1","name":host,"port":port,"nonce":nonce,"version":"0.5.0"})
    c.Write(b); time.Sleep(1500*time.Millisecond)
  }
}

func main(){
  codex:="codex"; if len(os.Args)>1 {codex=os.Args[1]}
  rpc,err:=NewRPC(codex); if err!=nil{log.Fatal(err)}
  token:=rnd(32); nonce:=rnd(24); port:=8765
  go beacon(port,nonce)
  http.HandleFunc("/healthz",func(w http.ResponseWriter,r *http.Request){json.NewEncoder(w).Encode(map[string]any{"ok":true,"version":"0.5.0"})})
  http.HandleFunc("/v1/pair",func(w http.ResponseWriter,r *http.Request){
    if r.URL.Query().Get("nonce")!=nonce {http.Error(w,"forbidden",403);return}
    json.NewEncoder(w).Encode(map[string]any{"token":token,"bridge":"CodexMeter Bridge","version":"0.5.0"})
  })
  http.HandleFunc("/v1/usage",func(w http.ResponseWriter,rq *http.Request){
    if rq.Header.Get("Authorization")!="Bearer "+token {http.Error(w,"unauthorized",401);return}
    x,e:=normalize(rpc); if e!=nil{http.Error(w,e.Error(),500);return}
    w.Header().Set("Content-Type","application/json"); json.NewEncoder(w).Encode(x)
  })
  log.Printf("CodexMeter Bridge listening on :%d",port)
  log.Fatal(http.ListenAndServe("0.0.0.0:"+strconv.Itoa(port),nil))
}

var _ = strings.Builder{}
