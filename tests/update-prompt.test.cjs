const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
function factory(){const context={window:{}};vm.runInNewContext(fs.readFileSync('app/src/main/assets/update-prompt.js','utf8'),context);return context.window.UpdatePrompt;}
test('launch prompts defer during editing, show once per version and refresh on resume',async()=>{
  let idle=false,time=0,calls=0;const versions=[];
  const prompt=factory().create({now:()=>time,check:async()=>{calls++;return {state:'available',release:{version:'v0.3.0'}};},canPresent:()=>idle,present:r=>versions.push(r.version)});
  await prompt.check();assert.equal(calls,1);assert.equal(versions.length,0);
  idle=true;prompt.flush();assert.deepEqual(versions,['v0.3.0']);
  await prompt.check();assert.equal(calls,1);
  time=60001;await prompt.check();assert.equal(calls,2);assert.equal(versions.length,1);
});
test('launch checks do not show errors or current-version dialogs',async()=>{
  for(const check of [async()=>({state:'current'}),async()=>{throw Error('offline');}]){
    let shown=0;await factory().create({check,canPresent:()=>true,present:()=>shown++}).check();assert.equal(shown,0);
  }
});
test('concurrent resume does not duplicate network requests',async()=>{
  let finish,calls=0;const prompt=factory().create({check:()=>{calls++;return new Promise(resolve=>finish=resolve);},canPresent:()=>true,present:()=>{}});
  const first=prompt.check();await prompt.check();assert.equal(calls,1);finish({state:'current'});await first;
});
