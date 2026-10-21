import type {AppData,Schedule} from './model';
import {sameValue} from './stateTransactions';
export {sameValue};

export class EditConflictError extends Error {
 constructor(label:string,removed=false){super(`${label}已在其他窗口${removed?'删除':'修改'}，当前草稿仍保留。请重新读取后再保存。`);this.name='EditConflictError';}
}
/** Run within update's transaction, against the authoritative persisted value. */
export function assertUnchanged(expected:unknown,actual:unknown,label:string){
 if(!sameValue(expected,actual))throw new EditConflictError(label,expected!==undefined&&actual===undefined);
}
type Update=(change:(previous:AppData)=>AppData)=>Promise<void>;
/** Use the existing queue to read and publish fresh data without writing or advancing its revision. */
export async function latestData(update:Update):Promise<AppData>{
 let result:AppData|undefined;await update(value=>{result=value;return value;});return result!;
}
export const scheduleContent=({term,periods,courses}:Schedule)=>({term,periods,courses});
export type ImportPreview={schedule:Schedule;base:Schedule;rebuild:(current:Schedule)=>Schedule};
export function createImportPreview(base:Schedule,rebuild:ImportPreview['rebuild']):ImportPreview{return {schedule:rebuild(base),base,rebuild};}
