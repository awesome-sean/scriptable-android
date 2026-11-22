/* Launch/resume update checks. No network error is shown during ordinary app use. MIT. */
window.UpdatePrompt={create({check,canPresent,present,now=()=>Date.now()}){
  let running=false,lastAttempt=-Infinity,pending=null;
  const shown=new Set();
  function flush(){
    if(!pending||shown.has(pending.version)||!canPresent())return;
    const release=pending;pending=null;shown.add(release.version);present(release);
  }
  return {flush,async check(){
    if(running||now()-lastAttempt<60000)return;
    running=true;lastAttempt=now();
    try{const result=await check();if(result?.state==='available'&&result.release?.version){pending=result.release;flush();}}
    catch(_){}finally{running=false;}
  }};
}};
