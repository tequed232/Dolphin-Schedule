import fs from 'node:fs/promises';
import path from 'node:path';
import * as XLSX from '@e965/xlsx';

// 仅由 androidTest 构建调用；合成文件不进入主 APK 或 Git。
const output=path.resolve(process.argv[2]);
await fs.mkdir(output,{recursive:true});
const book=XLSX.utils.book_new();
XLSX.utils.book_append_sheet(book,XLSX.utils.aoa_to_sheet([
  ['课程名','星期','节次','周次','教室','教师'],
  ['合成数学','周一','1-2','1-16','16-203','合成教师'],
  ['无效课程','8','3','1-16','A101',''],
]),'课程明细');
const grid=XLSX.utils.aoa_to_sheet([
  ['合成课表'],['节次','周一','周二'],
  ['1','设计实践\n教师：合成导师\n周次：1-18',''],
  ['2','','午间阅读\n教室：B202\n周次：双周'],['3','',''],
]);
grid['!merges']=[{s:{r:2,c:1},e:{r:3,c:1}}];
XLSX.utils.book_append_sheet(book,grid,'合并网格');
XLSX.utils.book_append_sheet(book,XLSX.utils.aoa_to_sheet([
  ['节次','上课时间','下课时间'],['1','08:00','08:45'],['2','08:55','09:40'],['午休','09:40','10:30'],['3','10:30','11:15'],
]),'作息');
XLSX.utils.book_append_sheet(book,XLSX.utils.aoa_to_sheet(Array.from({length:500},(_,i)=>['合成说明 '+i+' 中文长文本用于验证分块传输，不包含真实课表。'.repeat(8)])),'分块说明');
for(const type of ['xlsx','xls'])await fs.writeFile(path.join(output,`synthetic.${type}`),XLSX.write(book,{type:'buffer',bookType:type,compression:true}));
