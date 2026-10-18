import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {launchBrowser} from './check-browser.mjs';
import {goTab,pasteJSON} from './check-navigation.mjs';
import {editFixture,manage,externalCourse} from './edit-conflict-fixture.mjs';

const base=process.env.TEST_URL??'http://127.0.0.1:5173',browser=await launchBrowser(),checks=[],failures=[],errors=[];
async function scenario(name,run,options={}){
 let fixture;
 try{fixture=await editFixture(browser,{base,errors,...options});await run(fixture.page,fixture.other,fixture.read,fixture.snapshot);checks.push(name);console.log('PASS '+name);}
 catch(error){failures.push({name,error:error.message});console.error('FAIL '+name+': '+error.message);}
 finally{await fixture?.context.close();}
}
try{
 await scenario('过期配置预览拒绝覆盖最新课程与学期，重新预览后才能提交',async(page,other,read)=>{
  await page.locator('.screen.active').getByRole('button',{name:'导入课表',exact:true}).click();await pasteJSON(page);
  await page.getByRole('textbox',{name:'课表内容',exact:true}).fill(JSON.stringify({courseCount:8}));await page.getByRole('button',{name:'解析并预览',exact:true}).click();
  const preview=page.getByRole('dialog',{name:'确认这份课表',exact:true});await preview.waitFor();await externalCourse(other);await page.waitForTimeout(250);
  const before=await read(),confirm=preview.getByRole('button',{name:'确认导入',exact:true});if(await confirm.isEnabled())await confirm.click();await page.waitForTimeout(150);
  assert.deepEqual(await read(),before,'过期配置预览不能删除另一窗口的课程或回退学期');
  await preview.getByRole('button',{name:'重新预览',exact:true}).click();await preview.getByRole('button',{name:'确认导入',exact:true}).click();await preview.waitFor({state:'hidden'});
  const after=await read();assert.deepEqual(after.schedule.courses,before.schedule.courses);assert.deepEqual(after.schedule.term,before.schedule.term);assert.equal(after.schedule.periods.length,8);
 });
 await scenario('课表设置收到外部更新仍保留草稿，冲突保存不覆盖新时间',async(page,other,read,snapshot)=>{
  await manage(page);await page.locator('.screen.active').getByRole('button',{name:/开学日期与课程节数/}).click();const input=page.getByRole('textbox',{name:'课表名称',exact:true});await input.fill('本地未保存名称');
  await other.evaluate(async()=>{const {commitData}=await import('/src/lib/storage.ts');await commitData(s=>({...s,schedule:{...s.schedule,periods:s.schedule.periods.map((p,i)=>i===0?{...p,start:'07:59'}:p)}}));});await page.waitForTimeout(300);
  assert.equal(await input.inputValue(),'本地未保存名称');const before=await read(),beforeCommit=await snapshot();await page.getByRole('button',{name:'保存课表设置',exact:true}).click();await page.waitForTimeout(120);assert.deepEqual(await read(),before);assert.deepEqual(await snapshot(),beforeCommit);
  await page.setViewportSize({width:320,height:568});await other.evaluate(async()=>{const {commitData}=await import('/src/lib/storage.ts');await commitData(s=>({...s,settings:{...s.settings,mode:'dark',scale:1.35}}));});await page.waitForTimeout(200);
  const notice=page.locator('.screen.active .error-box');await notice.scrollIntoViewIfNeeded();assert.equal(await page.locator('.screen.active').evaluate(el=>el.scrollWidth<=el.clientWidth+1),true);await page.screenshot({path:'build/evidence/edit-settings-conflict-320-dark.png'});
  await notice.getByRole('button',{name:'重新读取',exact:true}).click();const confirm=page.getByRole('button',{name:'放弃草稿并重新读取',exact:true});await confirm.scrollIntoViewIfNeeded();const box=await confirm.boundingBox();assert.ok(box.x>=0&&box.x+box.width<=321);assert.ok(box.y>=0&&box.y+box.height<=569);await page.screenshot({path:'build/evidence/edit-reload-320-dark.png'});
  await confirm.click();await page.waitForTimeout(120);assert.equal(await input.inputValue(),'基线学期');
 });
 await scenario('旧课程编辑不能复活另一窗口已删除的课程，草稿仍保留',async(page,other,read)=>{
  await manage(page);await page.locator('.screen.active .course-disclosure').click();await page.locator('.screen.active .course-management .link-row').filter({hasText:'编辑验证课程'}).click();
  const editor=page.getByRole('dialog',{name:'编辑课程',exact:true});await editor.getByRole('textbox',{name:'教师',exact:true}).fill('本地草稿教师');
  await other.evaluate(async()=>{const {commitData}=await import('/src/lib/storage.ts');await commitData(s=>({...s,schedule:{...s.schedule,courses:[]}}));});await page.waitForTimeout(250);
  const before=await read();await editor.getByRole('button',{name:'保存课程',exact:true}).click();await page.waitForTimeout(150);assert.deepEqual(await read(),before);assert.equal(await editor.getByRole('textbox',{name:'教师',exact:true}).inputValue(),'本地草稿教师');
 });
 await scenario('教材草稿不能覆盖另一窗口新教材，重新读取经过明确确认',async(page,other,read)=>{
  await goTab(page,'搜索');await page.locator('.screen.active .result-card').filter({hasText:'编辑验证课程'}).click();await page.getByRole('button',{name:'编辑教材',exact:true}).click();
  const editor=page.getByRole('dialog',{name:'编辑教材',exact:true});await editor.getByRole('textbox',{name:'书名',exact:true}).fill('本地教材草稿');
  await other.evaluate(async()=>{const {commitData}=await import('/src/lib/storage.ts');await commitData(s=>({...s,books:{...s.books,'编辑验证课程':{...s.books['编辑验证课程'],title:'另一窗口的新教材'}}}));});await page.waitForTimeout(250);
  const before=await read();await editor.getByRole('button',{name:'保存并标记',exact:true}).click();await page.waitForTimeout(150);assert.deepEqual(await read(),before);assert.equal(await editor.getByRole('textbox',{name:'书名',exact:true}).inputValue(),'本地教材草稿');
  await editor.getByRole('button',{name:'重新读取',exact:true}).click();await page.getByRole('button',{name:'放弃草稿并重新读取',exact:true}).click();await page.waitForTimeout(120);assert.equal(await editor.getByRole('textbox',{name:'书名',exact:true}).inputValue(),'另一窗口的新教材');
 },{book:true});
 await scenario('减少动态效果覆盖开关伪元素，正常模式保留反馈',async page=>{
  await goTab(page,'设置');await page.locator('.screen.active').getByRole('button',{name:/主页显示/}).click();const toggle=page.locator('.screen.active .switch').first();await toggle.waitFor();
  const duration=()=>toggle.evaluate(el=>parseFloat(getComputedStyle(el,'::after').transitionDuration)*1000);assert.ok(await duration()<=1,'减少动效时滑块不应继续执行180ms过渡');
  await page.emulateMedia({reducedMotion:'no-preference'});assert.equal(await duration(),180);await page.emulateMedia({reducedMotion:'reduce'});assert.ok(await duration()<=1);
 },{motion:'reduce'});
}finally{
 await mkdir('build/evidence',{recursive:true});await writeFile('build/evidence/edit-conflicts.json',JSON.stringify({checks,failures,errors,success:!failures.length&&!errors.length},null,2));await browser.close();
}
assert.deepEqual(errors,[]);assert.deepEqual(failures,[]);assert.equal(checks.length,5);
