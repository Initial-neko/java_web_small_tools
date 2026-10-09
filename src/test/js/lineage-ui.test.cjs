const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync('src/main/resources/static/lineage-viewer/app.js','utf8').replace('return { mount: mount, unmount: unmount };','return { mount, unmount, STATE, loadTableList, loadOverview, loadLineageGraph, loadTableDetail, highlightSql, applyLevelOfDetail };');
async function main(){
 const pending=[],els={};
 const ctx=vm.createContext({document:{getElementById:id=>els[id]||(els[id]={innerHTML:'',textContent:'',style:{},querySelectorAll:()=>[],classList:{add(){},remove(){}}})},fetch:()=>new Promise(resolve=>pending.push(resolve)),setTimeout,clearTimeout});
 vm.runInContext(source,ctx);const lv=ctx.LineageViewer;
 lv.STATE.root={};
 const old=lv.loadTableList(),recent=lv.loadTableList();
 pending[1]({ok:true,json:async()=>({success:true,data:[{name:'new',shortName:'new',layer:'ODS'}]})});await recent;
 pending[0]({ok:true,json:async()=>({success:true,data:[{name:'old',shortName:'old',layer:'ODS'}]})});await old;
 assert(els['lv-tableGrid'].innerHTML.includes('new'),'Older list must not overwrite latest search');
 const before=els['lv-tableGrid'].innerHTML,late=lv.loadTableList();lv.unmount();
 pending[2]({ok:true,json:async()=>({success:true,data:[]})});await late;
 assert.equal(els['lv-tableGrid'].innerHTML,before,'Unmounted tool must not modify DOM');
 lv.STATE.root={};const previous=lv.loadOverview();lv.unmount();lv.STATE.root={};
 pending[3]({ok:true,json:async()=>({success:true,data:{tableCount:999}})});await previous;
 assert.equal(els['lv-statInline']?.textContent||'','','Previous mount cannot write to a new mount');
 let reads=0;const edges=Array.from({length:500},(_,i)=>({data:key=>{reads++;return key==='source'?'t'+i:'t'+(i+1);}}));
 const nodes=Array.from({length:501},(_,i)=>({id:()=> 't'+i,addClass(){},removeClass(){},toggleClass(){}}));
 lv.STATE.currentTable='t0';lv.STATE.cy={zoom:()=>0.4,edges:()=>edges,nodes:()=>nodes,style:()=>({update(){}})};lv.applyLevelOfDetail();
 assert(reads<=edges.length*2,'Zoom must scan each edge once, not once per node; reads='+reads);
 for(const sql of ["SELECT 'SELECT' AS v, 12 -- customer source", "SELECT '<x>&'", "/* SELECT 12 */ SELECT 'it''s ok'"]) {
   const html=lv.highlightSql(sql);
   const text=html.replace(/<[^>]*>/g,'').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&quot;/g,'"').replace(/&#39;/g,"'").replace(/&amp;/g,'&');
   assert.equal(text,sql,'Highlight must preserve exact SQL text');
 }
 assert(!lv.highlightSql('<img src=x onerror=alert(1)>').includes('<img'), 'Unmatched punctuation must also be escaped');
 console.log(JSON.stringify({success:true,olderSearchIgnored:true,unmountIgnored:true,remountIgnored:true,zoomEdgeReads:reads}));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
