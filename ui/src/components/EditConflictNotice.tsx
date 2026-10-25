import {useState} from 'react';
import {Dialog} from './Dialog';

export function EditConflictNotice({message,busy,onReload}:{message:string;busy:boolean;onReload:()=>Promise<void>}){
 const [confirm,setConfirm]=useState(false);
 return <><div className="error-box"><p role="alert">{message}</p><button type="button" className="secondary" disabled={busy} onClick={()=>setConfirm(true)}>重新读取</button></div>
  <Dialog open={confirm} title="重新读取最新内容？" onClose={()=>{if(!busy)setConfirm(false);}}><p>重新读取会替换当前未保存的草稿。取消可继续保留和查看这些输入。</p><div className="button-row"><button type="button" className="secondary" disabled={busy} onClick={()=>setConfirm(false)}>取消</button><button type="button" className="primary" disabled={busy} onClick={()=>{setConfirm(false);void onReload();}}>放弃草稿并重新读取</button></div></Dialog></>;
}
