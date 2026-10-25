import {spawn} from 'node:child_process';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
const wait=ms=>new Promise(r=>setTimeout(r,ms));
const failed=[];
const files=['tests/browser/check-solid-navigation.mjs','tests/browser/check-appearance.mjs','tests/browser/check-navigation-visual.mjs','tests/browser/check-grid-scroll-date.mjs','tests/browser/check-list-card-scroll.mjs','tests/browser/check-temporary-date.mjs','tests/browser/check-ui-interaction-review.mjs','tests/browser/check-state-recovery.mjs','tests/browser/check-update-races.mjs','tests/browser/check-performance.mjs','tests/browser/check-responsive-navigation.mjs','tests/browser/check-date-strip-spacing.mjs','tests/browser/check-date-rail.mjs','tests/browser/check-day-timetable-layout.mjs','tests/browser/check-course-theme.mjs','tests/browser/check-time-settings-layout.mjs','tests/browser/check-calendar-swipe.mjs','tests/browser/check-current-time-line.mjs','tests/browser/check-toolbar-import.mjs','tests/browser/check-primary-navigation.mjs','tests/browser/check-file-times-ui.mjs','tests/browser/check-home-interaction.mjs','tests/browser/check-schedule-ui.mjs','tests/browser/check-updates.mjs','tests/browser/check-ui.mjs','tests/browser/check-import-ui.mjs','tests/browser/check-experience.mjs','tests/browser/check-onboarding.mjs','tests/browser/check-course-experience.mjs','tests/browser/check-home-search-experience.mjs','tests/browser/check-background.mjs','tests/browser/check-calendar-picker.mjs','tests/browser/check-holidays.mjs','tests/browser/check-layout.mjs'];
const version=(await readFile('ui/src/meta.ts','utf8')).match(/APP_VERSION\s*=\s*'([^']+)'/)[1];
files.splice(1,0,'tests/browser/check-edit-conflicts.mjs','tests/browser/check-edit-conflict-races.mjs','tests/browser/check-settings-autosave.mjs','tests/browser/check-import-loading.mjs','tests/browser/check-multi-window.mjs','tests/browser/check-responsive-start.mjs','tests/browser/check-system-calendar-count.mjs');
const failFast=process.argv.includes('--fail-fast');
const only=process.argv.find(arg=>arg.startsWith('--only='))?.slice(7).split(',');
if(only){
  const selected=only.map(name=>files.find(file=>file===name||file===`tests/browser/${name}`));
  if(selected.some(file=>!file))throw new Error('未知浏览器测试组；请使用 tests/browser 下的文件名');
  files.splice(0,files.length,...new Set(selected));
}
const server=spawn(process.execPath,['node_modules/vite/bin/vite.js','--config','ui/vite.config.ts','--host','127.0.0.1','--port','5183','--strictPort'],{stdio:'inherit'});
const report={version,startedAt:new Date().toISOString(),expectedGroups:files.length,groups:[],completed:false,success:false};
try{
  let ready=false;for(let i=0;i<30;i++){try{await fetch('http://127.0.0.1:5183');ready=true;break;}catch{await wait(300);}}
  if(!ready)throw new Error('测试服务器未启动');
  for(const file of files){
    console.log(`GROUP START ${file}`);
    // Twelve isolated two-window scenarios include fresh database setup and real file reads.
    const timeoutMs=file==='tests/browser/check-edit-conflict-races.mjs'?300_000:180_000;
    const start=Date.now(),result=await new Promise(resolve=>{
      let output='',errorMessage='',timedOut=false;
      const p=spawn(process.execPath,[file],{stdio:['ignore','pipe','pipe'],env:{...process.env,TEST_URL:'http://127.0.0.1:5183'}});
      p.stdout.on('data',chunk=>{output+=chunk.toString();process.stdout.write(chunk);});
      p.stderr.on('data',chunk=>{output+=chunk.toString();process.stderr.write(chunk);});
      const timer=setTimeout(()=>{timedOut=true;errorMessage=`测试组超过 ${timeoutMs/1000} 秒`;if(process.platform==='win32')spawn('taskkill',['/PID',String(p.pid),'/T','/F'],{stdio:'ignore'});else p.kill('SIGKILL');},timeoutMs);
      p.on('error',error=>{errorMessage=error.message;});
      p.on('close',(exitCode,signal)=>{clearTimeout(timer);resolve({file,exitCode,signal,timedOut,error:errorMessage||undefined,passMessages:(output.match(/^PASS /gm)||[]).length,durationMs:Date.now()-start});});
    });
    report.groups.push(result);
    if(result.exitCode!==0||result.error)failed.push(`${file}: ${result.error||result.exitCode}`);
    console.log(`GROUP ${result.exitCode===0&&!result.error?'PASS':'FAIL'} ${file} (${result.passMessages} PASS messages, ${result.durationMs} ms)`);
    if(failFast&&failed.length)break;
  }
  report.completed=report.groups.length===files.length;report.success=report.completed&&failed.length===0;
  console.log(`浏览器回归：${report.groups.filter(group=>group.exitCode===0&&!group.error).length}/${files.length} 组通过，${report.groups.reduce((sum,group)=>sum+group.passMessages,0)} 条 PASS 输出。`);
  if(failed.length)throw new Error(`浏览器回归失败：${failed.join(', ')}`);
}finally{server.kill();report.finishedAt=new Date().toISOString();await mkdir('build/evidence',{recursive:true});await writeFile(`build/evidence/browser-ci${only?'-rerun':''}-results.json`,JSON.stringify(report,null,2));}
