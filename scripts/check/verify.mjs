import {spawnSync} from 'node:child_process';
for(const args of [['scripts/check/check-source-files.mjs'],['scripts/check/check-source-files.mjs','--self-test'],['scripts/check/check-source-push.mjs','--self-test'],['tests/integrity/check-native.mjs']]){
 const result=spawnSync(process.execPath,args,{stdio:'inherit'});if(result.status!==0)process.exit(result.status??1);
}
