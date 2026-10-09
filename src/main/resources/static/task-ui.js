// Navigation is an explicit task catalog, independent of GET /api/tools discovery.
let helpCatalog, currentPage = null;
const drafts = {}, lastModes = {};
let lastOutput = '', outputFile = 'result.txt', requestSequence = 0;

fetch('/api/help').then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
  .then(data => { helpCatalog = data; tools = data.tools.map(t => ({name:t.name,displayName:t.title,description:t.summary})); renderToolList(); goHome(); })
  .catch(e => { document.getElementById('main').innerHTML = '<h2>工具入口加载失败</h2><p>' + escapeHtml(e.message) + '</p><button class="btn" onclick="location.reload()">重试</button>'; });

function pages() { return [...helpCatalog.pages, ...helpCatalog.advancedPages, helpCatalog.environment]; }
function saveDraft() {
  if (currentTool && TOOL_CONFIGS[currentTool.name]) {
    drafts[currentTool.name] = {...drafts[currentTool.name], ...getFormValues(TOOL_CONFIGS[currentTool.name])};
  }
  if(currentTool && currentTool.name==='excel-viewer') saveExcelDraft();
}
function resetView() { requestSequence++; document.getElementById('main').scrollTop = 0; }
function goHome() {
  if (!helpCatalog) return;
  saveDraft(); currentTool = null; currentPage = null; resetView(); renderToolList();
  document.getElementById('main').innerHTML = '<h2>选择你要完成的任务</h2><p class="subtitle">常用工具按任务聚合。数据库生成和诊断位于高级工具；所有 HTTP 能力可查阅 API 帮助。</p>' +
    '<input class="task-search" type="search" aria-label="搜索任务" placeholder="搜索任务、输入来源或工具名称" oninput="filterTasks(this.value)">' +
    '<div class="task-grid" id="taskCards">' + helpCatalog.pages.map(p =>
      '<button class="task-card" data-search="' + escapeHtml(p.title+' '+p.description+' '+p.labels.join(' ')+' '+p.tools.join(' ')) + '" onclick="selectPage(\''+p.id+'\')"><strong>'+escapeHtml(p.title)+'</strong><span>'+escapeHtml(p.description)+'</span></button>').join('') + '</div>' +
    '<p class="home-note">程序调用：<code>GET /api/help</code> 获取完整帮助，<code>GET /api/tools/{name}/help</code> 获取单项帮助。</p>' +
    '<button class="btn" onclick="showApiCatalog()">查看 API 调用说明</button>';
}
function filterTasks(value) {
  document.querySelectorAll('.task-card').forEach(el => { el.hidden = !el.dataset.search.toLowerCase().includes(value.toLowerCase()); });
}
function renderToolList() {
  const nav = p => '<button class="tool-item'+(currentPage && currentPage.id===p.id?' active':'')+'" data-page="'+p.id+'" onclick="selectPage(\''+p.id+'\')">'+escapeHtml(p.title)+'</button>';
  document.getElementById('toolList').innerHTML = '<button class="tool-item" onclick="goHome()">任务首页</button><div class="nav-label">常用任务</div>' +
    helpCatalog.pages.map(nav).join('') + '<details class="nav-advanced"'+(currentPage && helpCatalog.advancedPages.includes(currentPage)?' open':'')+'><summary>高级工具</summary>'+helpCatalog.advancedPages.map(nav).join('')+'</details>' +
    '<div class="nav-label">调用与环境</div><button class="tool-item" onclick="showApiCatalog()">API 帮助</button>' + nav(helpCatalog.environment);
}
function selectPage(id) {
  const page = pages().find(p => p.id===id); if (!page) return;
  saveDraft(); currentPage = page;
  selectTool(lastModes[id] || page.tools[0]);
}
function selectTool(name) {
  saveDraft();
  const page = pages().find(p => p.tools.includes(name));
  if (!page) return;
  currentPage = page; currentTool = tools.find(t => t.name===name); lastModes[page.id] = name;
  unmountHeavyTools(); resetView(); renderToolList(); renderMain();
}
// 重图形工具在切走时主动释放，避免 canvas 与事件监听常驻内存。
function unmountHeavyTools() {
  if (window.LineageViewer) {
    try { window.LineageViewer.unmount(); } catch (e) { /* 忽略 */ }
  }
}
function taskHeader() {
  return '<h2>'+escapeHtml(currentPage.title)+'</h2><p class="subtitle">'+escapeHtml(currentPage.description)+'</p>' +
    '<div class="task-tabs" role="group" aria-label="选择操作">'+currentPage.tools.map((name,i)=>
      '<button class="mode-tab'+(currentTool.name===name?' active':'')+'" onclick="selectTool(\''+name+'\')">'+escapeHtml(currentPage.labels[i])+'</button>').join('')+'</div>';
}
function renderMain() {
  const main=document.getElementById('main');
  if (currentTool.name==='system-info') {
    main.innerHTML=taskHeader()+'<p class="server-note">以下信息属于运行服务的机器。</p><div id="systemInfo">加载中…</div><div id="apiHelp"></div>';
    loadSystemInfo(); renderApiHelp(currentTool.name); return;
  }
  if (currentTool.name==='excel-viewer') { renderExcelViewer(main); return; }
  if (currentTool.name==='lineage-viewer') { renderLineageViewer(main); return; }
  const cfg=TOOL_CONFIGS[currentTool.name];
  const values={}; cfg.fields.forEach(f=>{ if(f.default!==undefined) values[f.key]=f.default; else if(f.type==='select') values[f.key]=f.options[0].value; });
  Object.assign(values,drafts[currentTool.name]||{});
  main.innerHTML=taskHeader()+(['jdbc-query-to-java','mybatis-generator','ip-port-checker'].includes(currentTool.name)?'<p class="server-note">此操作发生在服务端；驱动、输出路径或连通性均以服务所在机器为准。</p>':'')+
    '<div id="formArea">'+renderForm(cfg,values)+'</div><button id="executeButton" class="btn" onclick="execute()">执行</button><div class="result" id="result" hidden></div><div id="apiHelp"></div>';
  bindFormEvents(cfg); renderApiHelp(currentTool.name);
}
function getFormValues(cfg) {
  const values={}; cfg.fields.forEach(f=>{ const el=document.getElementById('field-'+f.key); if(el) values[f.key]=f.type==='checkbox'?el.checked:el.value; }); return values;
}
function bindFormEvents(cfg) {
  document.querySelectorAll('#formArea select').forEach(el=>el.addEventListener('change',()=>{
    const values={...drafts[currentTool.name],...getFormValues(cfg)}; drafts[currentTool.name]=values;
    document.getElementById('formArea').innerHTML=renderForm(cfg,values); bindFormEvents(cfg);
  }));
}
function renderForm(cfg,values) {
  const advancedKeys=['schema','modelPackage','mapperPackage','xmlPackage','outputDir','overwrite','addRemarkComments','forceBigDecimals','trimStrings','useActualColumnNames'];
  const field=f=>{
    if(f.showWhen && !f.showWhen(values)) return '';
    const val=values[f.key]!==undefined?values[f.key]:(f.default!==undefined?f.default:'');
    const id='field-'+f.key, label='<label for="'+id+'">'+escapeHtml(f.label)+'</label>';
    if(f.type==='checkbox') return '<div class="form-group checkbox-group"><input type="checkbox" id="'+id+'"'+(val?' checked':'')+'>'+label+'</div>';
    const start='<div class="form-group">'+label;
    if(f.type==='select') return start+'<select id="'+id+'">'+f.options.map(o=>'<option value="'+escapeHtml(o.value)+'"'+(o.value===val?' selected':'')+'>'+escapeHtml(o.label)+'</option>').join('')+'</select></div>';
    if(f.type==='textarea') return start+'<textarea id="'+id+'" placeholder="'+escapeHtml(f.placeholder||'')+'">'+escapeHtml(val)+'</textarea></div>';
    return start+'<input type="'+(f.type==='password'?'password':'text')+'" id="'+id+'" value="'+escapeHtml(val)+'" placeholder="'+escapeHtml(f.placeholder||'')+'"></div>';
  };
  const normal=cfg.fields.filter(f=>!advancedKeys.includes(f.key)), advanced=cfg.fields.filter(f=>advancedKeys.includes(f.key));
  return normal.map(field).join('')+(advanced.length?'<details class="advanced-form"><summary>生成与输出配置</summary>'+advanced.map(field).join('')+'</details>':'');
}
function execute() {
  const name=currentTool.name, cfg=TOOL_CONFIGS[name], params=getFormValues(cfg), sequence=++requestSequence;
  drafts[name]={...drafts[name],...params};
  cfg.fields.forEach(f=>{ if(f.showWhen && !f.showWhen(params)) delete params[f.key]; });
  const btn=document.getElementById('executeButton'); btn.disabled=true; btn.textContent='执行中…';
  fetch('/api/tools/'+name+'/execute',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(params)})
    .then(r=>r.json()).then(res=>{ if(sequence===requestSequence) showResult(res); })
    .catch(e=>{ if(sequence===requestSequence) showResult({success:false,message:'请求失败: '+e.message}); })
    .finally(()=>{ if(btn.isConnected) { btn.disabled=false; btn.textContent='执行'; } });
}
function rawDetails(data,label='API 原始响应') { return '<details class="raw-details"><summary>'+label+'</summary><pre class="result-box">'+escapeHtml(JSON.stringify(data,null,2))+'</pre></details>'; }
function sourceResult(data) {
  lastOutput=data.output; outputFile=(data.className||'GeneratedEntity')+'.java';
  return '<div class="result-actions"><button class="btn btn-sm" onclick="copyOutput()">复制源码</button><button class="btn btn-sm" onclick="downloadOutput()">下载源码</button></div><pre class="result-box source-output">'+escapeHtml(data.output)+'</pre>';
}
function copyOutput() { copyText(lastOutput); }
function downloadOutput() {
  const url=URL.createObjectURL(new Blob([lastOutput],{type:'text/plain;charset=utf-8'}));
  const link=document.createElement('a'); link.href=url; link.download=outputFile; link.click(); setTimeout(()=>URL.revokeObjectURL(url),1000);
}
function section(title,value) {
  if(value==null || (Array.isArray(value)&&!value.length)) return '';
  return '<section class="analysis-section"><h3>'+title+'</h3><pre class="result-box">'+escapeHtml(typeof value==='string'?value:JSON.stringify(value,null,2))+'</pre></section>';
}
function sqlResult(data) {
  const item=r=>section('状态 / 语句', [r.status, ...(r.statementTypes||[])].filter(Boolean).join(' · '))+
    section('读取表',r.readTables||r.sourceTables)+section('写入表',r.writeTables)+section('字段',r.columns)+
    section('指标候选',r.metrics)+section('维度',r.dimensions)+section('警告',r.warnings)+section('解析错误',r.errors);
  if(!data.results) return item(data);
  return '<p class="batch-summary">共 '+data.total+' 项；成功 '+data.success+'，部分 '+data.partial+'，失败 '+data.failed+'，不支持 '+data.unsupported+'</p>'+section('读取表汇总',data.readTables)+section('写入表汇总',data.writeTables)+section('表依赖关系',data.tableLineageEdges)+section('重复计算',data.duplicateGroups)+
    data.results.map((r,i)=>'<details class="batch-item"><summary>'+escapeHtml(r.sqlId||'SQL '+(i+1))+' · '+escapeHtml(r.status)+'</summary>'+item(r)+'</details>').join('');
}
function showResult(res) {
  const box=document.getElementById('result'); if(!box) return; box.hidden=false;
  if(!res.success) { box.innerHTML='<h3>执行失败</h3><pre class="result-box error">'+escapeHtml(res.message||'执行失败')+'</pre>'; return; }
  const data=res.data; let content='';
  if(data && typeof data.output==='string' && currentPage.id==='entity') content=sourceResult(data);
  else if(currentTool.name==='json-format') content='<pre class="result-box'+(data.valid===false?' error':'')+'">'+escapeHtml(data.output)+'</pre>';
  else if(currentTool.name==='timestamp') content=section('时间', '秒：'+data.timestampSec+'\n毫秒：'+data.timestampMs+'\n日期：'+data.datetime);
  else if(currentTool.name==='text-diff') content=renderTextDiff(data);
  else if(currentTool.name==='json-compare') content=renderJsonCompare(data);
  else if(currentTool.name==='ip-port-checker') content=renderPortChecker(data);
  else if(['sql','metric'].includes(currentPage.id)) content=sqlResult(data);
  else content=section('结果',data);
  box.innerHTML='<h3>处理结果</h3>'+content+rawDetails(res);
}
function shellQuote(text) { return "'"+String(text).replace(/'/g,"'\"'\"'")+"'"; }
// ========== SQL 血缘展示工具 ==========
function renderLineageViewer(main) {
  if (!window.LineageViewer) {
    // index.html 的内联脚本里不能出现字面量闭合标签，这里的 \x3C 同样是刻意的转义。
    main.innerHTML = taskHeader() + '<div class="result-box error">前端脚本未加载成功，请检查 /lineage-viewer/app.js 是否可访问。\x3C/div>';
    return;
  }
  main.innerHTML = taskHeader() + '<div id="lv-root" class="lv-root"></div>';
  window.LineageViewer.mount(document.getElementById('lv-root'));
}
function curlExample(endpoint,example) {
  let path=endpoint.path.replace('{sheetIndex}','0');
  if(example.query) path+='?'+new URLSearchParams(example.query).toString();
  let command='curl -X '+endpoint.method+' '+shellQuote(location.origin+path);
  const separator=' '+String.fromCharCode(92,10)+'  ';
  if(endpoint.contentType==='multipart/form-data') command+=separator+'-F '+shellQuote('file=@'+example.file);
  // Windows curl variants may encode argv with the local code page. JSON Unicode
  // escapes keep the shell payload ASCII while preserving the original values.
  if(example.body) {
    const body=JSON.stringify(example.body).replace(/[^\x00-\x7f]/g,ch=>'\\u'+ch.charCodeAt(0).toString(16).padStart(4,'0'));
    command+=separator+'-H '+shellQuote('Content-Type: application/json')+separator+'--data-binary '+shellQuote(body);
  }
  return command;
}
function helpHtml(help) {
  const params=endpoint=>'<div class="table-scroll"><table class="api-table"><thead><tr><th>参数</th><th>位置 / 类型</th><th>必填 / 默认值</th><th>说明 / 可选值</th></tr></thead><tbody>'+endpoint.parameters.map(p=>
    '<tr><td><code>'+escapeHtml(p.name)+'</code></td><td>'+escapeHtml(p.in+' / '+p.type)+'</td><td>'+(p.required?'是':'否')+' / '+escapeHtml(p.default===null?'—':JSON.stringify(p.default))+'</td><td>'+escapeHtml(p.description)+(p.enum.length?'<br>'+escapeHtml(p.enum.join(' / ')):'')+'</td></tr>').join('')+'</tbody></table></div>';
  return '<p>单项帮助：<a href="/api/tools/'+help.name+'/help" target="_blank" rel="noopener"><code>GET /api/tools/'+help.name+'/help</code></a></p>'+help.notes.map(n=>'<p class="api-note">'+escapeHtml(n)+'</p>').join('')+
    help.endpoints.map(endpoint=>'<section class="api-endpoint"><h3>'+escapeHtml(endpoint.method+' '+endpoint.path)+'</h3><p>Content-Type：'+escapeHtml(endpoint.contentType||'无需请求体')+'</p>'+params(endpoint)+
      endpoint.examples.map(example=>'<h4>'+escapeHtml(example.title)+'</h4>'+(example.body?'<p>JSON 请求体</p><pre class="result-box">'+escapeHtml(JSON.stringify(example.body,null,2))+'</pre>':'')+
        '<div class="example-code"><button class="btn btn-sm copy-example">复制 curl</button><pre class="result-box">'+escapeHtml(curlExample(endpoint,example))+'</pre></div>').join('')+
      '<p>返回：'+escapeHtml(endpoint.response)+'</p></section>').join('')+'<p>'+escapeHtml(helpCatalog.responseContract)+'</p>';
}
function bindCopyExamples(root) { root.querySelectorAll('.copy-example').forEach(btn=>btn.addEventListener('click',()=>copyText(btn.nextElementSibling.textContent))); }
function renderApiHelp(name) {
  const root=document.getElementById('apiHelp'), help=helpCatalog.tools.find(t=>t.name===name); if(!root || !help) return;
  root.innerHTML='<details class="api-help"><summary>API 调用与参数说明</summary>'+helpHtml(help)+'</details>'; bindCopyExamples(root);
}
function showApiCatalog() {
  saveDraft(); currentTool=null; currentPage=null; resetView(); renderToolList();
  const main=document.getElementById('main');
  main.innerHTML='<h2>API 帮助</h2><p class="subtitle">所有 HTTP 能力的调用说明。前端任务聚合不会改变原有 API。</p><p><a href="/api/help" target="_blank" rel="noopener">GET /api/help · 完整机器可读帮助</a></p><p>'+escapeHtml(helpCatalog.responseContract)+'</p>'+helpCatalog.tools.map(t=>'<details class="api-help"><summary>'+escapeHtml(t.title)+' · '+escapeHtml(t.name)+'</summary>'+helpHtml(t)+'</details>').join('');
  bindCopyExamples(main);
}
