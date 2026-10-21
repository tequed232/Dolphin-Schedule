import {spawnSync} from 'node:child_process';
import {copyFileSync,mkdirSync,mkdtempSync,readFileSync,rmSync,writeFileSync} from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const sha=/^(?:[a-f0-9]{40}|[a-f0-9]{64})$/i,zero=/^0+$/;
function git(args){
  const result=spawnSync('git',args,{encoding:'utf8',maxBuffer:16*1024*1024});
  if(result.error)throw result.error;
  if(result.status!==0)throw new Error(`git ${args[0]} 失败，请先获取目标远端：${result.stderr.trim()}`);
  return result.stdout.trim();
}
function check(ref,pathsOnly=false){
  const args=['scripts/check-source-files.mjs','--ref',ref];
  if(pathsOnly)args.push('--paths-only');
  const result=spawnSync(process.execPath,args,{stdio:'inherit'});
  if(result.status!==0)throw new Error(`拒绝推送：提交 ${ref} 未通过源码准入`);
}
function range(tip,baseline){
  check(tip);
  const args=['rev-list',tip];
  if(baseline.length)args.push('--not',...baseline);
  return git(args).split('\n').filter(Boolean);
}
function sharedBaseline(remote,tip,targetRef){
  // Only the live default branch is trusted; local tracking refs and other
  // remote feature branches must not hide newly introduced artifacts.
  const advertised=spawnSync('git',['ls-remote','--symref',remote,'HEAD'],{encoding:'utf8'});
  if(advertised.status!==0)return [];
  const defaultRef=advertised.stdout.match(/^ref: (refs\/heads\/\S+)\s+HEAD$/m)?.[1];
  const head=advertised.stdout.match(/^([a-f0-9]+)\s+HEAD$/m)?.[1];
  if(!defaultRef||defaultRef===targetRef||!head||!sha.test(head))return [];
  if(spawnSync('git',['cat-file','-e',`${head}^{commit}`],{stdio:'ignore'}).status!==0){
    const fetched=spawnSync('git',['fetch','--no-tags','--no-write-fetch-head',remote,head],{encoding:'utf8'});
    if(fetched.status!==0)return [];
  }
  const common=spawnSync('git',['merge-base',tip,head],{encoding:'utf8'});
  const base=common.stdout?.trim();return common.status===0&&sha.test(base)?[base]:[];
}
function selfTest(){
  const original=process.cwd(),root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
  const temp=mkdtempSync(path.join(os.tmpdir(),'dolphin-source-history-'));
  try{
    mkdirSync(path.join(temp,'scripts'));
    for(const file of ['check-source-files.mjs','check-source-push.mjs'])copyFileSync(path.join(root,'scripts',file),path.join(temp,'scripts',file));
    const policy=JSON.parse(readFileSync(path.join(root,'source-policy.json'),'utf8'));
    policy.requiredPaths=['README.md','source-policy.json','scripts/check-source-files.mjs','scripts/check-source-push.mjs'];
    writeFileSync(path.join(temp,'source-policy.json'),JSON.stringify(policy));
    writeFileSync(path.join(temp,'README.md'),'# Synthetic admission test\n');
    process.chdir(temp);git(['init','--quiet']);git(['add','.']);
    const commit=message=>{git(['-c','user.name=Admission test','-c','user.email=test@example.invalid','-c','commit.gpgSign=false','commit','--quiet','-m',message]);return git(['rev-parse','HEAD']);};
    const baseline=commit('synthetic base');
    const eventPath=path.join(temp,'event.json');
    writeFileSync(eventPath,JSON.stringify({before:baseline}));
    const run=()=>spawnSync(process.execPath,['scripts/check-source-push.mjs','--ci'],{encoding:'utf8',env:{...process.env,GITHUB_EVENT_PATH:eventPath}});
    let result=run();if(result.status!==0)throw new Error('合成纯源码提交未能通过');
    console.log('PASS 推送完整源码提交通过');
    const media='docs/import-guide/import-demo.webm';mkdirSync(path.join(temp,'docs/import-guide'),{recursive:true});writeFileSync(path.join(temp,media),'PK renamed synthetic archive');git(['add','-f',media]);const invalidMedia=commit('synthetic invalid tutorial media');git(['rm','--quiet',media]);commit('synthetic media deletion');
    result=run();if(result.status===0||!result.stderr.includes(invalidMedia))throw new Error('未能拒绝后续删除的伪装 WebM 历史 blob');console.log('PASS 伪装 WebM 已删除，新增历史实际 blob 仍被拒绝');
    git(['reset','--hard',baseline]);
    writeFileSync(path.join(temp,'payload.apk'),'synthetic artifact, not an APK');git(['add','-f','payload.apk']);const bad=commit('synthetic forbidden file');
    git(['rm','--quiet','payload.apk']);commit('synthetic deletion');
    result=run();if(result.status===0||!result.stderr.includes(bad))throw new Error('未能拒绝已在后续删除的历史 APK');
    console.log('PASS APK 已在端点删除，新增历史仍被拒绝');
    // Deleted remote branches can leave stale tracking refs; they are not a trusted new-branch baseline.
    git(['update-ref','refs/remotes/origin/stale',bad]);
    const tip=git(['rev-parse','HEAD']);
    result=spawnSync(process.execPath,['scripts/check-source-push.mjs','origin'],{encoding:'utf8',input:`refs/heads/new ${tip} refs/heads/new ${'0'.repeat(40)}\n`});
    if(result.status===0||!result.stderr.includes(bad))throw new Error('过期远端跟踪引用绕过了新分支历史检查');
    console.log('PASS 新分支忽略过期远端跟踪引用，完整历史中的 APK 仍被拒绝');
    const remote=path.join(temp,'shared.git');git(['init','--bare','--initial-branch=main',remote]);
    git(['checkout','--quiet','--detach',baseline]);
    mkdirSync(path.join(temp,'docs'),{recursive:true});writeFileSync(path.join(temp,'docs/historical-review.md'),'old shared document');
    git(['add','docs/historical-review.md']);const shared=commit('synthetic previously shared document');
    git(['push','--quiet',remote,`${shared}:refs/heads/main`]);git(['rm','--quiet','docs/historical-review.md']);const clean=commit('synthetic source cleanup');
    const prepush=ref=>spawnSync(process.execPath,['scripts/check-source-push.mjs',remote],{encoding:'utf8',input:`refs/heads/feature ${ref} refs/heads/feature ${'0'.repeat(40)}\n`});
    result=prepush(clean);if(result.status!==0)throw new Error('新分支错误重审已共享主分支的历史材料，阻断清理后的源码');
    console.log('PASS 新分支使用实时默认分支的共同祖先，允许移除旧文档后的干净源码');
    git(['push','--quiet',remote,`${bad}:refs/heads/decoy`]);result=prepush(tip);
    if(result.status===0||!result.stderr.includes(bad))throw new Error('远端非默认分支掩盖了新增 APK 历史');
    console.log('PASS 非默认远端分支不能成为新增禁入文件的豁免基线');
    git(['remote','add','origin',remote]);writeFileSync(eventPath,JSON.stringify({before:'0'.repeat(40),ref:'refs/heads/feature'}));
    result=run();if(result.status!==0)throw new Error('新分支 CI 未使用实时共享基线');
    console.log('PASS 新分支 CI 与 pre-push 使用相同的实时共享基线');
  }finally{
    process.chdir(original);
    const target=path.resolve(temp),prefix=path.resolve(os.tmpdir())+path.sep;
    if(!target.startsWith(prefix)||!path.basename(target).startsWith('dolphin-source-history-'))throw new Error('拒绝清理未核对的历史测试目录');
    rmSync(target,{recursive:true,force:true});
  }
}
try{
  if(process.argv[2]==='--self-test'){selfTest();process.exit(0);}
  const commits=new Set();
  if(process.argv[2]==='--ci'){
    const tip=git(['rev-parse','HEAD']);
    const event=process.env.GITHUB_EVENT_PATH?JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH,'utf8')):{};
    const baseline=event.pull_request?.base?.sha??event.before;
    const shared=baseline&&sha.test(baseline)&&!zero.test(baseline)?[baseline]:sharedBaseline('origin',tip,event.ref);
    for(const ref of range(tip,shared))commits.add(ref);
  }else{
    const remote=process.argv[2];
    if(!remote||remote.startsWith('-'))throw new Error('缺少目标远端名称');
    const lines=readFileSync(0,'utf8').trim().split('\n').filter(Boolean);
    for(const line of lines){
      const fields=line.trim().split(/\s+/),tip=fields[1],before=fields[3];
      if(fields.length!==4||!sha.test(tip)||!sha.test(before))throw new Error('无效的 Git 推送条目');
      if(zero.test(tip))continue;
      const baseline=!zero.test(before)?[before]:sharedBaseline(remote,tip,fields[2]);
      for(const ref of range(tip,baseline))commits.add(ref);
    }
  }
  for(const ref of commits)check(ref,true);
  console.log(`源码推送准入通过：已检查 ${commits.size} 个新增提交，禁止将已删除的违规文件留在推送历史中。`);
}catch(error){console.error(error.message);process.exitCode=1;}
