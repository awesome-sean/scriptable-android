/* Inline community automations. MIT. */
window.AutomationUI=(()=>{
  let ctx=null,tasks=[],permissions={},generation=0;
  const stamp=(value,zone)=>{if(!value)return '由设备安排';try{return new Intl.DateTimeFormat('zh-CN',{timeZone:zone,month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(value));}catch{return '时间待确认';}};
  function page(){return ctx?.main.querySelector('.automation-page');}
  function render(context){ctx=context;list();}
  function list(){
    const {main,heading,icon}=ctx;
    main.innerHTML='<section class="page automation-page">'+heading+'<div class="automation-permissions" id="automation-permissions"><p class="automation-help">正在检查运行条件…</p></div><div class="automation-toolbar"><span class="automation-count" id="automation-count">正在读取…</span><button class="primary-button" id="new-automation">'+icon('plus')+'新建</button></div><div id="automation-list" aria-live="polite"></div><p class="automation-help">到点执行脚本，是否发送通知由脚本决定。</p></section>';
    main.querySelector('#new-automation').onclick=()=>edit();refresh();
  }
  async function refresh(){
    if(!page()||!ctx.main.querySelector('#automation-list'))return;
    const token=++generation;
    try{
      const result=await ctx.hostCall('automation.list',{});if(token!==generation||!page()||!ctx.main.querySelector('#automation-list'))return;
      tasks=result.tasks||[];permissions=result.permissions||{};paintList();
    }catch(error){if(token!==generation||!page())return;const list=ctx.main.querySelector('#automation-list');if(list){list.innerHTML='<div class="notice" role="alert">'+ctx.escape(error.message)+'</div>';ctx.main.querySelector('#automation-count').textContent='读取失败';}}
  }
  function paintList(){
    const {main,escape:e,icon}=ctx;
    main.querySelector('#automation-count').textContent=tasks.length+' 个任务';
    main.querySelector('#automation-list').innerHTML=tasks.length?tasks.map(task=>{
      const script=ctx.scripts.find(item=>item.id===task.scriptId);
      return '<article class="automation-card"><div class="automation-card-top"><span class="automation-symbol">'+icon('clock')+'</span><div class="automation-title"><h2>'+e(task.name)+'</h2><p>'+e(script?.name||'脚本已删除')+'</p></div><button class="task-switch" role="switch" aria-checked="'+!!task.enabled+'" aria-label="'+e(task.name)+' 定时执行" data-toggle="'+e(task.id)+'"><span></span></button></div><p class="automation-time">'+e(task.times.join(' · '))+'</p><p class="automation-schedule">每天 · '+e(task.timeZone)+(task.enabled?' · 下次 '+e(stamp(task.nextRun,task.timeZone)):' · 已暂停')+'</p><div class="automation-card-footer"><span class="task-state">'+e(task.lastState||'未运行')+'</span><button class="text-button" data-result="'+e(task.id)+'">结果</button><button class="text-button" data-edit="'+e(task.id)+'">编辑</button><button class="automation-run" data-run="'+e(task.id)+'" aria-label="运行一次 '+e(task.name)+'">'+icon('playRounded')+'</button></div><p class="automation-result" id="result-'+e(task.id)+'" hidden></p></article>';
    }).join(''):'<div class="automation-empty"><span>'+icon('clock')+'</span><h2>还没有自动化</h2><p>选一个脚本，安排每天的执行时间。</p><button class="secondary-button" id="empty-automation">创建第一个任务</button></div>';
    main.querySelector('#empty-automation')?.addEventListener('click',()=>edit());
    for(const button of main.querySelectorAll('[data-edit]'))button.onclick=()=>edit(tasks.find(x=>x.id===button.dataset.edit));
    for(const button of main.querySelectorAll('[data-toggle]'))button.onclick=async()=>{
      button.disabled=true;try{const task=tasks.find(x=>x.id===button.dataset.toggle);await ctx.hostCall('automation.save',{...task,enabled:!task.enabled});await refresh();}catch(e){ctx.notify(e.message);button.disabled=false;}
    };
    for(const button of main.querySelectorAll('[data-result]'))button.onclick=()=>{
      const task=tasks.find(x=>x.id===button.dataset.result),output=main.querySelector('#result-'+CSS.escape(task.id));
      output.textContent=(task.lastRun?stamp(task.lastRun,task.timeZone)+'\n':'')+(task.lastResult||'尚无运行结果。“已完成”表示脚本已结束。');output.hidden=!output.hidden;
    };
    for(const button of main.querySelectorAll('[data-run]'))button.onclick=async()=>{
      button.disabled=true;try{const result=await ctx.hostCall('automation.run',{id:button.dataset.run});ctx.notify(result?.message||'已请求执行，稍后刷新查看结果');}catch(e){ctx.notify(e.message);}finally{button.disabled=false;}
    };
    main.querySelector('#automation-permissions').innerHTML='<div class="automation-prerequisites"><div class="prerequisites-heading">'+icon('shield')+'<h2>先完成运行设置</h2></div><p>要准时执行并收到提醒，请先允许以下权限，并确认手机允许应用在后台运行。</p><div class="settings-group"><div class="settings-row">'+icon('bell')+'<span class="settings-row-label">通知<small>允许脚本发送提醒</small></span><button data-permission="notifications">'+(permissions.notifications?'已允许':'去开启')+'</button></div><div class="settings-row">'+icon('clock')+'<span class="settings-row-label">精确定时<small>按设定时间触发任务</small></span><button data-permission="alarm">'+(permissions.exact?'已允许':'去开启')+'</button></div><div class="settings-row">'+icon('device')+'<span class="settings-row-label">后台运行<small>检查自启动与省电限制</small></span><button data-permission="background">检查设置</button></div></div><p class="permission-footnote">定位等额外权限，只在脚本需要时授权。厂商省电策略仍可能延后任务。</p></div><button id="refresh-automations" class="text-button refresh-automations">'+icon('sync')+'刷新权限与运行状态</button>';
    for(const button of main.querySelectorAll('[data-permission]'))button.onclick=()=>ctx.hostCall('automation.settings',{kind:button.dataset.permission}).then(value=>{if(value?.preview)ctx.notify('浏览器预览不修改手机系统权限');}).catch(e=>ctx.notify(e.message));
    main.querySelector('#refresh-automations').onclick=refresh;
  }
  function edit(task){
    generation++;
    const {main,escape:e,icon}=ctx;
    let zone=Intl.DateTimeFormat().resolvedOptions().timeZone||'UTC';
    main.innerHTML='<section class="page automation-page automation-editor"><header class="screen-heading"><button class="heading-button" id="automation-back" aria-label="返回自动化">'+icon('back')+'</button><h1>'+ (task?'编辑自动化':'新建自动化')+'</h1></header><form id="automation-form"><div class="automation-form-group"><label for="task-name">名称</label><input id="task-name" maxlength="100" required value="'+e(task?.name||'每日任务')+'"><label for="task-script">执行脚本</label><select id="task-script" required>'+ctx.scripts.map(script=>'<option value="'+e(script.id)+'"'+(task?.scriptId===script.id?' selected':'')+'>'+e(script.name)+'</option>').join('')+'</select></div><div class="automation-form-group"><label for="task-times">每天运行时间</label><input id="task-times" required value="'+e(task?.times?.join(',')||'09:00')+'"><p class="field-help">24 小时制，多个时间用逗号分隔。</p><label for="task-zone">时区</label><input id="task-zone" required value="'+e(task?.timeZone||zone)+'"><p class="field-help">按所选时区安排执行时间。</p><label for="task-parameter">脚本参数</label><input id="task-parameter" value="'+e(task?.parameter||'')+'" placeholder="可留空"></div><div class="automation-enable"><label for="task-enabled">启用定时执行</label><input type="checkbox" id="task-enabled" class="setting-switch" role="switch" '+(task?.enabled?'checked':'')+'></div><p class="automation-help">先手动运行脚本并完成所需授权。后台任务不能等待弹窗输入。</p><p id="task-error" class="form-error" role="alert"></p><button type="submit" class="primary-button automation-save">保存</button>'+(task?'<button type="button" class="delete-automation" id="delete-automation">删除自动化</button><div id="delete-confirmation"></div>':'')+'</form></section>';
    main.querySelector('#automation-back').onclick=list;
    main.querySelector('#automation-form').onsubmit=async event=>{
      event.preventDefault();const button=main.querySelector('.automation-save');button.disabled=true;const error=main.querySelector('#task-error');error.textContent='';
      try{
        const times=main.querySelector('#task-times').value.replace(/，/g,',').split(',').map(x=>x.trim());
        if(!times.length||times.length>24||times.some(x=>!/^([01]\d|2[0-3]):[0-5]\d$/.test(x))||new Set(times).size!==times.length)throw new Error('请填写不重复的 HH:mm 时间，例如 09:00,18:00');
        const timeZone=main.querySelector('#task-zone').value.trim();new Intl.DateTimeFormat('zh-CN',{timeZone}).format();
        const data={name:main.querySelector('#task-name').value.trim(),scriptId:main.querySelector('#task-script').value,times,timeZone,parameter:main.querySelector('#task-parameter').value,enabled:main.querySelector('#task-enabled').checked};if(task)data.id=task.id;
        await ctx.hostCall('automation.save',data);list();ctx.notify('自动化已保存');
      }catch(failure){error.textContent=failure.message;button.disabled=false;}
    };
    main.querySelector('#delete-automation')?.addEventListener('click',()=>{
      main.querySelector('#delete-confirmation').innerHTML='<div class="notice delete-confirmation"><p>删除此任务？脚本文件会保留。</p><button type="button" class="danger-button" id="confirm-task-delete">确认删除</button></div>';
      main.querySelector('#confirm-task-delete').onclick=async()=>{try{await ctx.hostCall('automation.remove',{id:task.id});list();ctx.notify('自动化已删除');}catch(e){ctx.notify(e.message);}};
    });
  }
  return {render,refresh};
})();
