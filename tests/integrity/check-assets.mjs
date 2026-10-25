import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';

const manifest=JSON.parse(await readFile('ui/src/assets/icons/manifest.json','utf8'));
const component=await readFile('ui/src/components/Icon.tsx','utf8');
function validateIcon(svg){assert.match(svg,/<svg[^>]+viewBox=/);assert.match(svg,/<path /);assert.doesNotMatch(svg,/<(?:script|image|foreignObject)\b|https?:\/\/(?!www\.w3\.org)/i);}
for(const name of [...Object.keys(manifest.icons),...(manifest.projectIcons??[])]){
  const svg=await readFile(`ui/src/assets/icons/${name}.svg`,'utf8');validateIcon(svg);
  assert.match(component,new RegExp(`assets/icons/${name}\\.svg\\?raw`));
  assert.throws(()=>validateIcon(svg.replace('<path ','<script ')));
}
const images=JSON.parse(await readFile('ui/src/assets/manifest.json','utf8'));
for(const asset of images.assets){
  const buffer=await readFile(asset.path);
  assert.equal(buffer.subarray(0,4).toString(),'RIFF',asset.path);
  assert.equal(buffer.subarray(8,12).toString(),'WEBP',asset.path);
  assert.equal(createHash('sha256').update(buffer).digest('hex'),asset.sha256,asset.path);
}
const gradle=await readFile('app/build.gradle.kts','utf8');
assert.match(gradle,/from\(rootProject.file\("ui\/src\/assets\/brand\/app-icon.webp"\)\)/);
assert.match(gradle,/dependsOn\(syncUi, syncBrand\)/);
const css=await readFile('ui/src/theme/app.css','utf8');assert.match(css,/\.brand-art\{object-fit:contain;aspect-ratio:1/);
await mkdir('build/evidence',{recursive:true});
await writeFile('build/evidence/assets-results.json',JSON.stringify({materialSymbols:Object.keys(manifest.icons).length,local:true,images:images.assets,canonicalBrandSource:true},null,2));
console.log(`PASS ${Object.keys(manifest.icons).length} 个 Material 图标和 ${manifest.projectIcons?.length??0} 个项目图标 / 无损品牌图校验 / 统一品牌源文件`);
