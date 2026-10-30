// 后台只触发学校原有的课表查询；不读取 Cookie、密码或登录令牌。
function prepareAcademicCheck(expectedTerm, maximumWeek) {
  if (location.origin !== 'https://jwxt.gdcmxy.edu.cn:1004') return {error:'login'};
  if (document.readyState !== 'complete') return {waiting:true};
  if (document.querySelector('input[type="password"]') || /会话已过期|请重新登录|登录已失效/.test(document.body.innerText)) return {error:'login'};
  const start=document.querySelector('#startWeek'), stop=document.querySelector('#stopWeek'), big=document.querySelector('#oddOrDouble');
  if (!start || !stop || !big) return {waiting:true};
  const title=document.body.innerText.match(/(\d{4}-\d{4}-[12])学年学期/);
  if (!title) return {waiting:true};
  if (title[1] !== expectedTerm) return {error:'term'};
  const form=start.closest('form') || document;
  const query=[...form.querySelectorAll('button')].filter(e=>e.textContent.replace(/\s/g,'')==='查询');
  if (query.length!==1 || query[0].disabled) return {waiting:true};
  // 仅记录查询请求是否结束，不接触请求参数、响应正文或登录信息。
  const state=window.__dolphinReadOnlyQuery={pending:0,completed:0,failed:false};
  const isQuery=url=>{try{return new URL(url,location.href).pathname==='/api/arrange/CourseScheduleAllQuery/studentCourseSchedule';}catch{return false;}};
  const open=XMLHttpRequest.prototype.open, send=XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open=function(method,url,...rest){this.__dolphinScheduleQuery=isQuery(url);return open.call(this,method,url,...rest);};
  XMLHttpRequest.prototype.send=function(...args){
    if(this.__dolphinScheduleQuery){state.pending++;this.addEventListener('loadend',()=>{state.pending--;state.completed++;if(this.status<200 || this.status>=300)state.failed=true;},{once:true});}
    return send.apply(this,args);
  };
  const fetchOriginal=window.fetch;
  window.fetch=function(input,...args){
    if(!isQuery(typeof input==='string'?input:input.url))return fetchOriginal.call(this,input,...args);
    state.pending++;
    return fetchOriginal.call(this,input,...args).then(response=>{if(!response.ok)state.failed=true;return response;},error=>{state.failed=true;throw error;}).finally(()=>{state.pending--;state.completed++;});
  };
  const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;
  const set=(input,value)=>{ setter.call(input,String(value));input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true})); };
  set(start,1);set(stop,maximumWeek);
  if (big.checked) big.click();
  query[0].click();
  return {queried:true};
}
