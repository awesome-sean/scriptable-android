window.__runWidget = async function(script,parameter,family,widgetId) {
  try {
    await Host.ready;
    await ScriptRunner.run(script,{}, {widgetParameter:parameter || null,config:{runsInWidget:true,runsInApp:false,widgetFamily:family || 'medium',widgetId}});
    Android.widgetDone(JSON.stringify({ok:true}));
  } catch(error) { Android.widgetDone(JSON.stringify({ok:false,error:String(error.message || error)})); }
};

// Scheduled scripts use a normal, bounded script context without opening UI.
window.__runScheduledScript = async function(script, parameter) {
  const logs=[];
  try {
    await Host.ready;
    const output=await ScriptRunner.run(script,{
      onLog(level,values){
        if(logs.length>=30)return;
        logs.push(values.map(value=>typeof value==='string'?value:JSON.stringify(value)).join(' ').slice(0,1000));
      }
    },{shortcutParameter:parameter || null,config:{runsInWidget:false,runsInApp:false,runsInAutomation:true}});
    Android.widgetDone(JSON.stringify({ok:true,output:String(output ?? logs.join('\n')).slice(0,3000)}));
  }catch(error){Android.widgetDone(JSON.stringify({ok:false,error:String(error.message || error)}));}
};
