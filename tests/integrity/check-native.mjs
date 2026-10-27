import {readFile,access,mkdir,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import vm from 'node:vm';

const read=file=>readFile(file,'utf8');
const [manifest,gradle,properties,pkg,reader,extract,decoder]=await Promise.all([
 'app/src/main/AndroidManifest.xml','app/build.gradle.kts','gradle.properties','package.json',
 'app/src/main/java/com/dolphin/schedule/data/LegacyDataReader.kt',
 'app/src/main/assets/academic/extract.js','app/src/main/java/com/dolphin/schedule/importing/SpreadsheetDecoder.kt',
].map(read));
const version=properties.match(/^dolphinVersion=(\d+\.\d+\.\d+)\s*$/m)?.[1];
assert.ok(version,'缺少统一应用版本');
assert.match(gradle,/providers\.gradleProperty\("dolphinVersion"\)/);
assert.match(gradle,/dependsOn\(syncLegal, syncSpreadsheet\)/);
assert.doesNotMatch(gradle,/buildUi|syncUi|vite|ui\/src/);
assert.deepEqual(Object.keys(JSON.parse(pkg).dependencies),['@e965/xlsx']);
const permissions=[...manifest.matchAll(/<uses-permission\b[^>]*android:name="([^"]+)"/g)].map(m=>m[1]).sort();
assert.deepEqual(permissions,['android.permission.INTERNET','android.permission.POST_NOTIFICATIONS','android.permission.POST_PROMOTED_NOTIFICATIONS']);
assert.equal([...manifest.matchAll(/android.intent.category.LAUNCHER/g)].length,1);
assert.match(manifest,/<activity android:name="\.NativeActivity"[\s\S]*?<category android:name="android.intent.category.LAUNCHER"/);
assert.doesNotMatch(manifest,/MainActivity|ReminderReceiver|Calendar|BOOT_COMPLETED|SCHEDULE_EXACT_ALARM/);
for(const retired of ['ui/src','app/src/main/java/com/dolphin/schedule/MainActivity.kt','app/src/main/java/com/dolphin/schedule/CalendarSync.kt','app/src/main/java/com/dolphin/schedule/SystemHolidays.kt','app/src/main/java/com/dolphin/schedule/ReminderScheduler.kt']) {
 assert.equal(await access(retired).then(()=>true,()=>false),false,`废弃实现仍存在：${retired}`);
}
assert.match(reader,/indexedDB\.open\('dolphin-schedule'\)/);
assert.match(reader,/allowFileAccess=false/);
assert.doesNotMatch(reader,/addJavascriptInterface|addWebMessageListener/);
assert.match(decoder,/allowFileAccess=false;web.settings.allowContentAccess=false/);
assert.match(decoder,/WebResourceResponse\("text\/plain","utf-8",403/);
assert.doesNotMatch(decoder,/addJavascriptInterface|addWebMessageListener/);
// 提取器拒绝错误站点和未完成页面，不触碰用户凭据。
const script=new vm.Script(extract.replace('export default async function','async function')+'\nextractAcademicDocument()');
const wrongOrigin=await script.runInNewContext({location:{origin:'https://example.invalid',hash:''}});
assert.match(wrongOrigin.error,/个人学期课表/);
const loading=await script.runInNewContext({location:{origin:'https://jwxt.gdcmxy.edu.cn:1004',hash:'#/jwxt/js/student/personal/studentSchedule'},document:{readyState:'loading'}});
assert.match(loading.error,/仍在加载/);
assert.doesNotMatch(extract,/document\.cookie|localStorage\.getItem|password|addJavascriptInterface/);
const icon=await readFile('app/src/main/res/drawable-nodpi/app_icon.webp');
assert.equal(icon.subarray(0,4).toString(),'RIFF');assert.equal(icon.subarray(8,12).toString(),'WEBP');
for(const license of ['LICENSE','THIRD_PARTY_NOTICES.md','legal/APACHE-2.0.txt'])assert.ok((await read(license)).length>200);
await mkdir('build/evidence',{recursive:true});
await writeFile('build/evidence/native-source-results.json',JSON.stringify({version,permissions,nativeLauncher:true,retiredRuntimeRemoved:true,localMigration:true,extractorGuards:true,localExcelDecoder:true},null,2));
console.log(`PASS 原生入口、三项权限、已删功能、版本、迁移、教务提取边界与本地 Excel 解码器（${version}）`);
