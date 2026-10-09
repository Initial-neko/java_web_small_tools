// Execute the same curl examples that the UI renders. No npm dependencies.
const fs=require('fs'),vm=require('vm'),assert=require('assert'),cp=require('child_process');
const base=new URL(process.argv[2]||'http://127.0.0.1:8088');
assert(['127.0.0.1','localhost','[::1]'].includes(base.hostname),'Acceptance runner only calls a local test service');
async function main() {
  const catalog=await (await fetch(base.origin+'/api/help')).json();
  assert.equal(catalog.pages.length,7);
  const discovery=await (await fetch(base.origin+'/api/tools')).json();
  assert(discovery.every(t=>catalog.tools.some(h=>h.name===t.name)));
  const unknown=await fetch(base.origin+'/api/tools/not-a-tool/help'); assert.equal(unknown.status,404);
  const context=vm.createContext({location:{origin:base.origin},URLSearchParams,fetch:()=>new Promise(()=>{})});
  vm.runInContext(fs.readFileSync('src/main/resources/static/task-ui.js','utf8'),context);
  let examples=0;
  for(const tool of catalog.tools) {
    const single=await (await fetch(base.origin+'/api/tools/'+tool.name+'/help')).json();
    assert.deepEqual(single,tool);
    for(const endpoint of tool.endpoints) if(endpoint.testable) for(const example of endpoint.examples) {
      context.endpoint=endpoint; context.example=example;
      const command=vm.runInContext('curlExample(endpoint,example)',context);
      const run=cp.spawnSync(process.env.SH||'sh',['-s'],{input:command+'\n',encoding:'utf8',timeout:15000});
      assert.equal(run.status,0,run.stderr||String(run.error));
      const result=JSON.parse(run.stdout); assert.equal(result.success,true,tool.name+': '+result.message);
      if(result.data && result.data.output) assert(result.data.output.length>0);
      console.log(JSON.stringify({name:tool.name,example:example.title,success:true,curlExecuted:true})); examples++;
    }
  }
  console.log(JSON.stringify({success:true,localExamples:examples,documentedCapabilities:catalog.tools.length,taskPages:catalog.pages.length,unknownHelpStatus:404}));
}
main().catch(e=>{ console.error(e); process.exitCode=1; });
