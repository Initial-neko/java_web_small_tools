// Deterministic local HTTP acceptance; no npm packages or external services.
const fs=require('fs'),path=require('path'),assert=require('assert');
const base=new URL(process.argv[2]||'http://127.0.0.1:18093');
assert(['127.0.0.1','localhost'].includes(base.hostname));
const out=process.argv[3]||'target/lineage-evidence'; fs.mkdirSync(out,{recursive:true});
const job=(name,inputTables,outputTables)=>({name,source:'acceptance',description:'测试 '+name,sql:'select 1 -- '+name,inputTables,outputTables});
const cases=[
 ['array-diamond',[job('j1',['ods_a','ods_b'],['dwd_c']),job('j2',['dwd_c'],['ads_d']),job('j3',['ods_a'],['dwd_c'])]],
 ['wrapper-delimiters',{records:[job('j1','a，b;c','d'),job('j2','d','e')]}],
 ['cycle',[job('j1',['a'],['b']),job('j2',['b'],['c']),job('j3',['c'],['a'])]],
 ['unicode',[job('中文作业',['库.原始 表'],['库.结果表'])]],
 ['disconnected',[job('j1',['a'],['b']),job('j2',['c'],['d']),job('j3',[],['isolated'])]],
 ['empty',[]],
 ['self-loop',[job('j1',['a'],['a'])]],
 ['wide-10000',Array.from({length:10000},(_,i)=>job('j'+i,['ods_'+i],['ads_'+i]))],
 ['chain-10000',Array.from({length:10000},(_,i)=>job('j'+i,['t'+i],['t'+(i+1)]))]
];
function tables(value){return [...new Set((Array.isArray(value)?value:String(value||'').split(/[,;，；]/)).filter(x=>x!=null).map(x=>String(x).trim().replace(/`/g,'')).filter(x=>x&&x.toLowerCase()!=='null'))];}
function oracle(rows){
 const nodes=new Set(),edges=new Map(),prod=new Map(),cons=new Map();
 for(const r of rows){const ins=tables(r.inputTables),outs=tables(r.outputTables);for(const t of [...ins,...outs])nodes.add(t);
  for(const t of ins){if(!cons.has(t))cons.set(t,[]);cons.get(t).push(r.name);}
  for(const t of outs){if(!prod.has(t))prod.set(t,[]);prod.get(t).push(r.name);}
  for(const a of ins)for(const b of outs)if(a.toLowerCase()!==b.toLowerCase()){const k=JSON.stringify([a,b]);if(!edges.has(k))edges.set(k,{a,b,sql:new Set()});edges.get(k).sql.add(r.name);}}
 const incoming=new Map(),outgoing=new Map();for(const t of nodes){incoming.set(t,[]);outgoing.set(t,[]);}for(const e of edges.values()){incoming.get(e.b).push(e.a);outgoing.get(e.a).push(e.b);}
 let clusters=0;const seen=new Set();for(const t of nodes)if(!seen.has(t)){clusters++;const q=[t];seen.add(t);for(let i=0;i<q.length;i++)for(const u of [...incoming.get(q[i]),...outgoing.get(q[i])])if(!seen.has(u)){seen.add(u);q.push(u);}}
 return {nodes,edges,prod,cons,incoming,outgoing,clusters};
}
async function call(url,body){const start=performance.now();const response=await fetch(base.origin+'/api/lineage-viewer'+url,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(120000)});const data=await response.json();assert(response.ok);assert(data.success,JSON.stringify(data));return {data:data.data,ms:performance.now()-start};}
const emit=value=>{console.log(JSON.stringify(value));fs.appendFileSync(path.join(out,'results.jsonl'),JSON.stringify(value)+'\n');};
async function main(){
 fs.writeFileSync(path.join(out,'results.jsonl'),'');
 for(const [name,input] of cases){
  fs.writeFileSync(path.join(out,name+'.json'),JSON.stringify(input));
  const rows=Array.isArray(input)?input:input.records,o=oracle(rows),rebuild=await call('/rebuild',Array.isArray(input)?{json:JSON.stringify(input)}:input);
  const overview=(await call('/overview')).data;
  assert.equal(overview.tableCount,o.nodes.size,name);assert.equal(overview.edgeCount,o.edges.size,name);assert.equal(overview.clusterCount,o.clusters,name);
  const times=[];let cards;
  for(let i=0;i<3;i++){const r=await call('/tables/leaves?scope=all');cards=r.data;times.push(r.ms);}
  assert.equal(cards.length,o.nodes.size);for(const c of cards){assert.equal(c.inDegree,o.incoming.get(c.name).length);assert.equal(c.outDegree,o.outgoing.get(c.name).length);assert.equal(c.producingSqlCount,(o.prod.get(c.name)||[]).length);assert.equal(c.consumingSqlCount,(o.cons.get(c.name)||[]).length);}
  for(const scope of ['leaves','sources']){const actual=(await call('/tables/leaves?scope='+scope)).data.map(x=>x.name).sort();const expected=[...o.nodes].filter(t=>(scope==='leaves'?o.outgoing:o.incoming).get(t).length===0).sort();assert.deepEqual(actual,expected);}
  const focus=rows.length?tables(rows[rows.length-1].outputTables)[0]:null;
  let lineageMs=0;
  if(focus){
   const detail=(await call('/tables/'+encodeURIComponent(focus))).data;assert.deepEqual(detail.producers.map(x=>x.name).sort(),(o.prod.get(focus)||[]).sort());
   assert.deepEqual((await call('/sqls/'+encodeURIComponent(focus))).data.map(x=>x.name).sort(),(o.prod.get(focus)||[]).sort());
   const limited=await call('/tables/'+encodeURIComponent(focus)+'/lineage?depth=1');lineageMs=limited.ms;
   const expectedNodes=new Set([focus,...o.incoming.get(focus),...o.outgoing.get(focus)]);assert.deepEqual(limited.data.nodes.map(x=>x.name).sort(),[...expectedNodes].sort());
   const full=(await call('/tables/'+encodeURIComponent(focus)+'/lineage?depth=0')).data;
   const reach=new Set([focus]),q=[focus];for(let i=0;i<q.length;i++)for(const t of o.incoming.get(q[i]))if(!reach.has(t)){reach.add(t);q.push(t);}for(const t of o.outgoing.get(focus))reach.add(t);
   assert.deepEqual(full.nodes.map(x=>x.name).sort(),[...reach].sort());
   const expectedEdges=[...o.edges.values()].filter(e=>reach.has(e.a)&&reach.has(e.b)).map(e=>JSON.stringify([e.a,e.b])).sort();assert.deepEqual(full.edges.map(e=>JSON.stringify([e.source,e.target])).sort(),expectedEdges);
   assert.equal((await call('/tables?limit=2')).data.length,Math.min(2,o.nodes.size));
  }
  for(const e of [...o.edges.values()].slice(0,3)){const edge=(await call('/edges/detail?'+new URLSearchParams({from:e.a,to:e.b}))).data;assert(edge.exists);assert.equal(edge.weight,e.sql.size);assert.deepEqual(edge.sqls.map(x=>x.name).sort(),[...e.sql].sort());}
  if(rows.length){assert.equal((await call('/sqls/'+encodeURIComponent(rows[0].name))).data[0].sql,rows[0].sql);}
  times.sort((a,b)=>a-b);emit({case:name,success:true,records:rows.length,tables:o.nodes.size,edges:o.edges.size,jsonBytes:Buffer.byteLength(JSON.stringify(input)),rebuildMs:rebuild.ms,listMedianMs:times[1],listMaxMs:times[2],depth1Ms:lineageMs});
 }
 const before=(await call('/overview')).data;
 for(const json of ['{}','null','42','{"records":{}}','[42]','',' ',JSON.stringify([job('same',['a'],['b']),job('same',['c'],['d'])])]) {
   const response=await fetch(base.origin+'/api/lineage-viewer/rebuild',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({json})});
   const result=await response.json();assert.equal(result.success,false,json);assert.deepEqual((await call('/overview')).data,before);
 }
 await call('/rebuild',{json:fs.readFileSync('src/main/resources/lineage-viewer/sqls.json','utf8')});
 emit({success:true,datasets:cases.length,rejectedInputs:8,previousGraphPreserved:true,allEightEndpointsChecked:true,demoRestored:true});
}
main().catch(e=>{emit({success:false,error:e.stack});process.exitCode=1;});
