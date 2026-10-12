const fs=require('fs'),vm=require('vm'),assert=require('assert');
class Element {
 constructor(tag){this.tag=tag;this.children=[];this.events={};this.textContent='';this.hidden=false;}
 append(...children){this.children.push(...children);}
 appendChild(child){this.append(child);}
 replaceChildren(...children){this.children=children;}
 addEventListener(type,fn){this.events[type]=fn;}
 setAttribute(){}
 querySelector(selector){const id=selector.slice(1);const walk=n=>n.id===id?n:n.children.map(walk).find(Boolean);return walk(this);}
}
async function main(){
 const pending=[],timers=new Map();let timerId=0,confirmed=false,confirmationCalls=0;
 const ctx=vm.createContext({document:{createElement:tag=>new Element(tag),hidden:false},window:{setInterval:fn=>{timers.set(++timerId,fn);return timerId;},clearInterval:id=>timers.delete(id),confirm:()=>{confirmationCalls++;return confirmed;}},fetch:(url,options)=>new Promise(resolve=>pending.push({url,options,resolve}))});
 vm.runInContext(fs.readFileSync('src/main/resources/static/java-process-manager/app.js','utf8'),ctx);
 const m=ctx.window.JavaProcessManager,old=new Element('main'),current=new Element('main');
 const rows=[{pid:111,name:'<img onerror=x>',main:'fixture',executable:'java.exe',startTime:1000,canKill:true},{pid:222,name:'toolbox',executable:'java.exe',startTime:2000,canKill:false,reason:'self'}];
 const resolve=(i,data)=>pending[i].resolve({ok:true,status:200,json:async()=>data});
 const flush=()=>new Promise(r=>setImmediate(r));
 m.mount(old);assert.equal(timers.size,1);m.unmount();assert.equal(timers.size,0);m.mount(current);assert.equal(timers.size,1);
 resolve(1,{success:true,supported:true,token:'new-token',processes:rows});await flush();
 resolve(0,{success:true,supported:true,token:'old-token',processes:[]});await flush();
 assert.equal(current.querySelector('#jpm-body').children.length,2,'Old response must not overwrite new mount');
 assert.equal(current.querySelector('#jpm-body').children[0].children[1].children[0].textContent,'<img onerror=x>','Process names must remain literal text');
 const actions=current.querySelector('#jpm-body').children.map(r=>r.children[4].children[0]);assert.equal(actions[1].disabled,true,'Self action disabled');
 const count=pending.length;await actions[0].events.click();assert.equal(confirmationCalls,1);assert.equal(pending.length,count,'Cancel confirmation must not call kill API');
 confirmed=true;const killing=actions[0].events.click();assert.equal(pending.length,count+1);assert.equal(pending[count].url,'/api/system/java-processes/111/kill');
 assert.equal(pending[count].options.headers['X-Process-Token'],'new-token');assert.equal(pending[count].options.body,'{"startTime":1000}');
 resolve(count,{success:true,message:'fixture ended'});await killing;assert.equal(pending.length,count+2,'Success triggers refresh');
 resolve(count+1,{success:true,supported:true,token:'new-token',processes:[rows[1]]});await flush();
 const search=current.children[2].children[0];search.value='no-match';search.events.input();assert.equal(current.querySelector('#jpm-count').textContent,'显示 0 / 1 个 Java 进程');
 ctx.document.hidden=true;const n=pending.length;await [...timers.values()][0]();assert.equal(pending.length,n,'Hidden document must not poll');
 ctx.document.hidden=false;const refreshing=[...timers.values()][0]();assert.equal(pending.length,n+1);m.unmount();resolve(n,{success:true,supported:true,token:'x',processes:[]});await refreshing;assert.equal(timers.size,0);
 console.log(JSON.stringify({success:true,staleResponseIgnored:true,singleTimer:true,unmountStopsTimer:true,hiddenPageDoesNotPoll:true,search:true,selfActionDisabled:true,confirmationCancelNoPost:true,confirmedPostIdentity:true,refreshAfterKill:true,literalNames:true}));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
