import {goTab} from './check-navigation.mjs';

export async function editFixture(browser,{base=process.env.TEST_URL??'http://127.0.0.1:5173',motion='no-preference',book=false,broadcast=true,native=false,errors=[]}={}){
 const context=await browser.newContext({viewport:{width:390,height:844},reducedMotion:motion});
 await context.route('https://api.github.com/**',route=>route.fulfill({status:503,body:'edit regression fixture'}));
 if(!broadcast)await context.addInitScript(()=>{window.BroadcastChannel=undefined;});
 if(native)await context.addInitScript(()=>{window.__nativeCommands=[];window.Dolphin={postMessage(raw){window.__nativeCommands.push(JSON.parse(raw));}};});
 const page=await context.newPage(),other=await context.newPage();
 page.on('pageerror',error=>errors.push(error.message));other.on('pageerror',error=>errors.push(error.message));
 try{
  await page.goto(base);await page.locator('.load-note').waitFor({state:'hidden'});
  await page.evaluate(async book=>{
   const {initialData}=await import('/src/lib/model.ts'),{saveData}=await import('/src/lib/storage.ts'),s=initialData();
   s.onboarded=true;s.settings.autoUpdate=false;s.schedule.term={name:'基线学期',startDate:'2026-10-05',weeks:20};
   s.schedule.courses=[{id:'edit-course',name:'编辑验证课程',teacher:'原教师',room:'原教室',day:1,start:1,end:2,weeks:[1,2],color:'sage',notes:''}];
   if(book)s.books['编辑验证课程']={title:'原教材',publisher:'',edition:'',text:'',source:'manual'};
   await saveData(s);
  },book);
  await page.reload();await page.locator('.load-note').waitFor({state:'hidden'});await other.goto(base);await other.locator('.load-note').waitFor({state:'hidden'});
 }catch(error){await context.close();throw error;}
 const read=()=>page.evaluate(async()=>{const {loadData}=await import('/src/lib/storage.ts');return loadData();});
 const snapshot=()=>page.evaluate(async()=>{
  const db=await new Promise((resolve,reject)=>{const r=indexedDB.open('dolphin-calendar',1);r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
  try{return await new Promise((resolve,reject)=>{const tx=db.transaction('state'),store=tx.objectStore('state'),data=store.get('app'),revision=store.get('appRevision');tx.oncomplete=()=>resolve({data:data.result,revision:revision.result});tx.onerror=tx.onabort=()=>reject(tx.error);});}finally{db.close();}
 });
 return {context,page,other,read,snapshot};
}
export async function manage(page){await goTab(page,'列表');await page.getByRole('button',{name:'课表管理',exact:true}).click();}
export async function editCourse(page){await manage(page);await page.locator('.screen.active .course-disclosure').click();await page.locator('.screen.active .course-management .link-row').filter({hasText:'编辑验证课程'}).click();return page.getByRole('dialog',{name:'编辑课程',exact:true});}
export async function editBook(page){await goTab(page,'搜索');await page.locator('.screen.active .result-card').filter({hasText:'编辑验证课程'}).click();await page.getByRole('button',{name:'编辑教材',exact:true}).click();return page.getByRole('dialog',{name:'编辑教材',exact:true});}
export async function externalCourse(other){await other.evaluate(async()=>{const {commitData}=await import('/src/lib/storage.ts');await commitData(s=>({...s,schedule:{...s.schedule,term:{...s.schedule.term,name:'另一窗口的新学期'},courses:[...s.schedule.courses,{...s.schedule.courses[0],id:'edit-external',name:'另一窗口的新课程',start:3,end:4}]}}));});}
