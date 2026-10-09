// Pure request-example contract checks. Run: node src/test/js/task-ui.test.cjs
const fs = require('fs'), vm = require('vm'), assert = require('assert');
const context = vm.createContext({location:{origin:'http://127.0.0.1:18091'},URLSearchParams,
  fetch:()=>new Promise(()=>{}),console});
vm.runInContext(fs.readFileSync('src/main/resources/static/task-ui.js','utf8'),context);
const catalog=JSON.parse(fs.readFileSync('src/main/resources/tool-help.json','utf8'));
let checked=0;
for(const tool of catalog.tools) for(const endpoint of tool.endpoints) for(const example of endpoint.examples) {
  context.endpoint=endpoint; context.example=example;
  const command=vm.runInContext('curlExample(endpoint,example)',context);
  assert(!command.includes('\n+'), 'Copied curl must not contain diff markers: '+tool.name);
  assert(command.includes('curl -X '+endpoint.method));
  if(example.body) assert(command.includes('--data-binary '));
  if(example.body) assert(!/[^\x00-\x7f]/.test(command),'JSON curl payload must survive Windows shell argument encoding');
  checked++;
}
context.quoteInput="SELECT 'DONE'";
assert.strictEqual(vm.runInContext('shellQuote(quoteInput)',context),"'SELECT '\"'\"'DONE'\"'\"''");
console.log(JSON.stringify({success:true,curlExamples:checked,shellQuoting:true}));

async function uploadRace() {
  const pending=[], box={innerHTML:''};
  const race=vm.createContext({console,URLSearchParams,
    document:{getElementById:()=>box},FormData:class { append(){} },
    fetch:(url)=>{ if(url.includes('/upload')) return new Promise(resolve=>pending.push(resolve)); return new Promise(()=>{}); }});
  const html=fs.readFileSync('src/main/resources/static/index.html','utf8');
  vm.runInContext(html.match(/<script>([\s\S]*?)<\/script>/)[1],race);
  vm.runInContext(fs.readFileSync('src/main/resources/static/task-ui.js','utf8'),race);
  vm.runInContext('escapeHtml=String; renderExcelWorkspace=()=>{}; loadExcelSheet=()=>{}; handleExcelFile({name:"A.xlsx"}); requestSequence++; handleExcelFile({name:"B.xlsx"});',race);
  const resolve=(index,fileId)=>pending[index]({json:async()=>({success:true,fileId,fileName:fileId+'.xlsx',sheets:[]})});
  resolve(1,'B'); await new Promise(r=>setImmediate(r));
  resolve(0,'A'); await new Promise(r=>setImmediate(r));
  assert.equal(vm.runInContext('excelState.fileId',race),'B','Older upload must not overwrite newer upload');
  console.log(JSON.stringify({success:true,excelUploadRace:true,latestFileId:'B'}));
}
uploadRace().catch(e=>{console.error(e);process.exitCode=1;});
