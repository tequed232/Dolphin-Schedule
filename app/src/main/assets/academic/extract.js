// 同一份只读提取器由 Android 打包为资源，也由浏览器合成页面测试执行。
export default async function extractAcademicDocument() {
  const fail = message => { throw new Error(message); };
  try {
    if (location.origin !== 'https://jwxt.gdcmxy.edu.cn:1004' || location.hash.split('?')[0] !== '#/jwxt/js/student/personal/studentSchedule') fail('请先登录并进入个人学期课表');
    if (document.readyState !== 'complete' || [...document.querySelectorAll('[aria-busy="true"],.ant-spin-spinning')].some(e => e.getClientRects().length)) fail('页面仍在加载，请稍后再识别');
    const text = e => (e?.innerText ?? '').trim();
    // 学校同时渲染屏幕课表和 display:none 的打印副本；隐藏节点的
    // innerText 仍可能返回文本，必须先排除，不能用文本有无判断可见性。
    const visible = e => e.getClientRects().length > 0 && getComputedStyle(e).visibility === 'visible';
    const tables = [...document.querySelectorAll('table')].filter(visible);
    const header = tables.flatMap(t => [...t.rows]).find(r => ['一','二','三','四','五','六','日'].every(d => [...r.cells].some(c => text(c) === `星期${d}`)));
    if (!header || header.cells.length !== 8) fail('未找到完整的星期表头，请切换到个人学期课表');
    const days = [...header.cells].slice(1).map(c => '一二三四五六日'.indexOf(text(c).slice(-1)) + 1);
    if (days.some((day, i) => day !== i + 1)) fail('星期列顺序发生变化，暂时无法识别');
    if (document.querySelector('#oddOrDouble')?.checked) fail('请取消勾选“大节次”，点击查询后再识别，以读取每节准确时间');
    // 选中主体后校验它的每一行，不能只提取有合法时间的行，静默漏掉异常节次。
    const bodies = tables.map(t => [...t.rows]).filter(rows => rows.some(r => /^第.+节/.test(text(r.cells[0]))));
    if (bodies.length !== 1) fail('课表结构发生变化，请使用文件导入');
    const startWeek = Number(document.querySelector('#startWeek')?.value);
    const endWeek = Number(document.querySelector('#stopWeek')?.value);
    const titles = (document.body.innerText.match(/\b\d{4}-\d{4}-[12](?=学年学期)/g) ?? []);
    if (new Set(titles).size !== 1) fail('无法确定当前课表学期，请重新查询');
    let blocks = 0;
    const rows = bodies[0].map(row => {
      const match = text(row.cells[0]).match(/^第((?:中午)?\d+)节\s*(\d{1,2}:\d{2})\s*[-–—~至]\s*(\d{1,2}:\d{2})$/);
      if (!match) fail('请取消勾选“大节次”并查询，必须读取逐节作息');
      if (row.cells.length !== 8 || [...row.cells].some(c => c.rowSpan !== 1 || c.colSpan !== 1)) fail('课表存在未适配的合并单元格，已停止识别以避免星期错位');
      const cells = [...row.cells].slice(1).map(cell => {
        const boxes = [...cell.querySelectorAll('[class*="styles-courseBox-"]')];
        if (!boxes.length && text(cell)) fail('存在无法识别的课程内容，原课表保留不变');
        return boxes.map(box => {
          if (++blocks > 2000) fail('课程块过多，请按学期导入');
          const field = alt => text(box.querySelector(`img[alt="${alt}"]`)?.parentElement);
          const timing = field('时间').match(/^(.*?)\s*周\s*[（(]第(.+?)节[）)]$/);
          if (!timing || timing[2].trim() !== match[1]) fail('课程节次与时间轴不一致，请重新查询完整课表');
          const name = text(box.querySelector('[class*="styles-subject-"]'));
          if (!name || name.length > 300) fail('课程名缺失或过长');
          return {name, weeks:timing[1].trim(), teacher:field('教师'), room:field('地点')};
        });
      });
      return {label:match[1], start:match[2].padStart(5,'0'), end:match[3].padStart(5,'0'), cells};
    });
    if (!blocks) fail('本次没有识别到课程，原课表保留不变；请检查学期、查询范围和登录状态');
    const snapshot = {schema:1, school:'gdcmxy', term:titles[0], startWeek, endWeek, rows};
    // 只请求已验证的同源学期元数据，不读取密码或登录令牌。
    // 此接口只代表当前学期；选其他学期时不能套用它的日期。
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 2500);
    try {
      const response = await fetch('/api/baseInfo/semester/selectCurrentXnXq', {credentials:'same-origin', redirect:'error', signal:controller.signal});
      if (response.ok) {
        const metadata = await response.json();
        const value = metadata?.data;
        const date = value?.ksrq;
        if (metadata.code === 200 && value?.semester === snapshot.term && typeof date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(date) && Number.isFinite(Date.parse(date)) && new Date(date).toISOString().slice(0,10) === date) snapshot.termStartDate = date;
      }
    } catch { /* 日期不可用时交给首步日期确认，仍保留已读取的课程。 */ }
    finally { clearTimeout(timeout); }
    return snapshot;
  } catch (error) { return {error:error instanceof Error ? error.message : '课表读取失败，请重新查询后再试'}; }
}
