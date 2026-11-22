const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{spawn}=require('node:child_process');
const project=path.resolve(__dirname,'..'),base='http://127.0.0.1:4211';
const server=spawn(process.execPath,['tools/preview.cjs'],{cwd:project,env:{...process.env,PORT:'4211'},stdio:'ignore'});
let browser;
(async()=>{
  for(let i=0;i<60;i++){try{if((await fetch(base)).ok)break;}catch{}await new Promise(r=>setTimeout(r,100));}
  const chrome='C:/Program Files/Google/Chrome/Application/chrome.exe';browser=await chromium.launch({headless:true,...(fs.existsSync(chrome)?{executablePath:chrome}:{})});
  const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];page.on('pageerror',e=>errors.push(e.message));await page.goto(base);await page.locator('.card-open').first().click();
  const cdp=await page.context().newCDPSession(page);await cdp.send('Emulation.setCPUThrottlingRate',{rate:6});
  // Exercise paste handlers without reading or overwriting the OS clipboard.
  async function paste(text){return page.locator('#code-input').evaluate((input,text)=>{
    input.focus();input.select();const data=new DataTransfer();data.setData('text/plain',text);
    const event=new ClipboardEvent('paste',{bubbles:true,cancelable:true,clipboardData:data});const start=performance.now();
    const accepted=input.dispatchEvent(event);if(accepted){input.setRangeText(text,input.selectionStart,input.selectionEnd,'end');input.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertFromPaste',data:text}));}
    return {accepted,ms:Math.round(performance.now()-start),matches:input.value===text,mode:input.closest('.editor-area').classList.contains('plain-editor'),nodes:document.querySelectorAll('.editor-area *').length};
  },text);}
  const cases=[['multiline',('const value = "脚本内容"; console.log(value);\n').repeat(14000)],['single-line','/*'+'字'.repeat(250000)+'*/'],['many-short-lines','//\n'.repeat(2000)]];
  const timings=[];
  for(const [name,code] of cases){
    assert.ok(Buffer.byteLength(code)<1024*1024);const result=await paste(code);assert.ok(result.accepted&&result.matches&&result.mode,JSON.stringify(result));assert.ok(result.nodes<30,JSON.stringify(result));
    assert.ok(result.ms<2500,'Paste blocked for too long: '+JSON.stringify(result));timings.push({name,...result});
    assert.equal(await page.locator('#code-input').evaluate(n=>getComputedStyle(n).color===getComputedStyle(n).webkitTextFillColor),true);
    console.log(name,JSON.stringify(result));await page.waitForFunction(code=>Host.sync('repo.list').some(s=>s.code===code),code,{polling:200});
    await page.locator('#editor-back').click();await page.locator('.card-open').first().click();assert.equal(await page.locator('#code-input').inputValue(),code);
  }
  const original=await page.locator('#code-input').inputValue();const rejected=await paste('字'.repeat(400000));assert.equal(rejected.accepted,false);assert.equal(await page.locator('#code-input').inputValue(),original);assert.match(await page.locator('#toast').textContent(),/1 MB/);
  // Native typing and undo continue to work after the large paste.
  await page.locator('#code-input').focus();await page.locator('#code-input').evaluate(n=>n.setSelectionRange(n.value.length,n.value.length));await page.keyboard.insertText('x');await page.keyboard.press('Control+z');assert.equal(await page.locator('#code-input').inputValue(),original);
  // Replacing a long script restores ordinary highlighting and completion.
  await paste('const alert = new Alert();\nconsole.log("short");');assert.equal(await page.locator('.editor-area.plain-editor').count(),0);assert.ok(await page.locator('.syntax-keyword').count()>0);assert.ok(await page.locator('.line-number-content span').count()>0);
  await paste('console.log("saved and executed");');await page.locator('#editor-run').click();await page.waitForFunction(()=>!ScriptRunner.isRunning());assert.match(await page.locator('#output-content').textContent(),/saved and executed/);
  await page.locator('#editor-back').click();await page.locator('[data-page-action="settings"]').click();await page.locator('#theme-setting').selectOption('dark');await page.locator('[data-tab="scripts"]').click();await page.locator('.card-open').first().click();await paste(cases[0][1]);
  const color=await page.locator('#code-input').evaluate(n=>getComputedStyle(n).webkitTextFillColor);assert.notEqual(color,'rgba(0, 0, 0, 0)');assert.equal(await page.locator('.completion-popup').count(),0);
  assert.deepEqual(errors,[]);console.log('PASS: long-document paste, wrapped text, bounded DOM, exact save/reopen, oversize rejection, undo, execution and dark theme; CPU x6:',JSON.stringify(timings));
})().catch(e=>{console.error(e);process.exitCode=1;}).finally(async()=>{if(browser)await browser.close();server.kill();});
