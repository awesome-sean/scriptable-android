'use strict';
const {chromium}=require('playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {spawn}=require('node:child_process');
const root=path.resolve(__dirname,'..'),base='http://127.0.0.1:4199';
const server=spawn(process.execPath,['tools/preview.cjs'],{cwd:root,env:{...process.env,PORT:'4199'},stdio:'ignore'});
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
let browser;
(async()=>{
  for(let i=0;i<60;i++){try{if((await fetch(base)).ok)break;}catch{}await delay(100);}
  const chrome='C:/Program Files/Google/Chrome/Application/chrome.exe';
  browser=await chromium.launch({headless:true,...(fs.existsSync(chrome)?{executablePath:chrome}:{})});
  const page=await browser.newPage({viewport:{width:390,height:844},locale:'zh-CN'});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(base);await page.locator('.script-card').first().waitFor();
  assert.deepEqual(await page.locator('.bottom-nav [data-tab]').evaluateAll(nodes=>nodes.map(n=>n.dataset.tab)),['scripts','automation','docs']);
  assert.equal(await page.locator('.nav-item.active .nav-icon').evaluate(n=>getComputedStyle(n).backgroundColor),'rgba(0, 0, 0, 0)');
  const geometry=await page.locator('.script-card').first().evaluate(card=>{
    const play=card.querySelector('.card-run'),left=card.querySelector('.card-icon');const round=getComputedStyle(play,'::before');
    return {circle:play.getBoundingClientRect().width-parseFloat(round.left)-parseFloat(round.right),left:left.getBoundingClientRect().width,icon:play.querySelector('svg').getBoundingClientRect().width,color:getComputedStyle(play).color};
  });assert.equal(geometry.circle,geometry.left);assert.ok(Math.abs(geometry.icon-29.4)<.02);assert.equal(geometry.color,'rgb(255, 255, 255)');
  await page.locator('#explore-examples').click();assert.ok(await page.locator('.example-card').count()>4);
  await page.locator('[data-page-action="settings"]').click();await page.locator('#check-update').waitFor();
  await page.locator('#check-update').click();assert.match(await page.locator('#toast').textContent(),/Android/);
  await page.locator('#show-about').click();assert.match(await page.getByRole('dialog').textContent(),/EdenleeGitHub/);await page.locator('#modal-close').click();
  await page.locator('[data-page-action="back"]').click();
  for(const width of [320,360,390,768]){
    await page.setViewportSize({width,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  }
  const headless=await browser.newPage();
  await headless.addInitScript(()=>{
    window.results=[];window.operations=[];
    window.Android={
      widgetDone(raw){results.push(JSON.parse(raw));},
      callSync(action){return JSON.stringify({ok:true,result:{scripts:[],files:{},keychain:{},device:{locale:'zh-CN'},nativeRuntime:false}});},
      call(raw){const call=JSON.parse(raw);operations.push(call);setTimeout(()=>{
        if(call.action==='alert')return window.__nativeResolve(call.id,false,'后台无法显示界面');
        const result=call.action==='request'?{body:'{"ok":true}',statusCode:200,headers:{},url:'https://example.com',mimeType:'application/json'}:true;
        window.__nativeResolve(call.id,true,result);
      },0);}
    };
  });
  await headless.goto(base+'/headless.html');await headless.waitForFunction(()=>typeof __runScheduledScript==='function');
  await headless.evaluate(()=>__runScheduledScript({id:'scheduled-test',name:'后台任务',code:'if(!config.runsInAutomation||config.runsInWidget||config.runsInApp)throw new Error("错误上下文"); if(args.shortcutParameter!=="notify")throw new Error("参数丢失");const r=new Request("https://example.com");if((await r.loadJSON()).ok){const n=new Notification();n.title="条件成立";await n.schedule();}Script.setShortcutOutput("已检查并通知");'},'notify'));
  assert.deepEqual(await headless.evaluate(()=>results.at(-1)),{ok:true,output:'已检查并通知'});
  assert.equal(await headless.evaluate(()=>operations.filter(x=>x.action==='notification.schedule').length),1);
  await headless.evaluate(()=>__runScheduledScript({id:'scheduled-false',name:'不通知',code:'const r=new Request("https://example.com");if(!(await r.loadJSON()).ok){const n=new Notification();await n.schedule();}console.log("条件未满足，不通知");'},'notify'));
  assert.equal(await headless.evaluate(()=>operations.filter(x=>x.action==='notification.schedule').length),1);
  assert.match(await headless.evaluate(()=>results.at(-1).output),/条件未满足/);
  await headless.evaluate(()=>__runScheduledScript({id:'scheduled-error',name:'失败',code:'throw new Error("接口失败");'},'notify'));
  assert.deepEqual(await headless.evaluate(()=>results.at(-1)),{ok:false,error:'接口失败'});
  await headless.evaluate(()=>__runScheduledScript({id:'scheduled-ui',name:'禁止界面',code:'const a=new Alert();a.addAction("确认");await a.presentAlert();'},'notify'));
  assert.equal(await headless.evaluate(()=>results.at(-1).ok),false);
  assert.deepEqual(errors,[]);console.log('PASS: community navigation, update entry, tile geometry, responsive layout, background context/parameters, conditional notifications, output and errors');
})().catch(e=>{console.error(e);process.exitCode=1;}).finally(async()=>{if(browser)await browser.close();server.kill();});
