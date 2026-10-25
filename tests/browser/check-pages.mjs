import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {launchBrowser} from '../helpers/check-browser.mjs';
import {goTab,pasteJSON} from '../helpers/check-navigation.mjs';
import {sampleSchedule} from '../fixtures/schedule.mjs';

const root=path.resolve('ui/dist'),basePath=process.env.DOLPHIN_BASE_PATH||'/Dolphin-Schedule/';
const version=(await readFile('ui/src/meta.ts','utf8')).match(/APP_VERSION\s*=\s*'([^']+)'/)[1];
assert.ok(basePath.startsWith('/')&&basePath.endsWith('/'),'Pages 路径必须以 / 开头和结尾');
const misses=[],errors=[],checks=[];
const server=createServer(async(req,res)=>{
 try {
  const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
  if(!pathname.startsWith(basePath))throw Error('outside deployment path');
  const relative=pathname.slice(basePath.length)||'index.html',target=path.resolve(root,relative);
  if(!target.startsWith(root+path.sep))throw Error('outside output');
  const data=await readFile(target);
  res.setHeader('Content-Type',({'.html':'text/html','.js':'text/javascript','.css':'text/css','.webp':'image/webp','.svg':'image/svg+xml'})[path.extname(target)]||'application/octet-stream');
  res.end(data);
 } catch {misses.push(req.url);res.statusCode=404;res.end();}
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await launchBrowser(),url=`http://127.0.0.1:${server.address().port}${basePath}`;
try {
 const page=await browser.newPage({viewport:{width:390,height:844}});
 page.on('pageerror',error=>errors.push(error.message));
 await page.route('https://api.github.com/**',route=>route.fulfill({json:{tag_name:'v1.5.1',assets:[]}}));
 await page.goto(url);assert.equal(await page.title(),'Dolphin Schedule');
 await page.getByRole('button',{name:'先逛一逛',exact:true}).click();
 await page.locator('.screen.active').getByRole('button',{name:'导入课表',exact:true}).click();
 await pasteJSON(page);await page.getByRole('textbox',{name:'课表内容',exact:true}).fill(JSON.stringify(sampleSchedule()));
 await page.getByRole('button',{name:'解析并预览',exact:true}).click();
 await page.getByRole('dialog',{name:'确认这份课表',exact:true}).getByRole('button',{name:'确认导入',exact:true}).click();
 await goTab(page,'搜索');await page.locator('.screen.active .result-card').first().waitFor();
 assert.ok(await page.locator('.screen.active .result-card').count()>0);checks.push('子路径首页、静态资源与延迟导入可用');
 await page.reload();await goTab(page,'搜索');await page.locator('.screen.active .result-card').first().waitFor();
 checks.push('浏览器课表持久化且刷新可访问');
 await goTab(page,'设置');await page.locator('[data-setting="about"]').click();
 assert.ok((await page.locator('.about-hero').innerText()).includes('Dolphin Schedule'));
 assert.equal(await page.locator('.version-tag').innerText(),`V${version}`);
 assert.equal(await page.locator('.app-background').getAttribute('data-source'),'none');
 checks.push('新名称、真实版本与无内置背景状态正确');
 assert.deepEqual(misses,[]);assert.deepEqual(errors,[]);
 console.log(checks.map(check=>'PASS '+check).join('\n'));
} finally {
 await mkdir('build/evidence',{recursive:true});await writeFile('build/evidence/pages-results.json',JSON.stringify({basePath,checks,misses,errors,success:checks.length===3&&!misses.length&&!errors.length},null,2));
 await browser.close();await new Promise(resolve=>server.close(resolve));
}
