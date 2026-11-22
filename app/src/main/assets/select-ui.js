/* Shared rounded picker, backed by the original select for form compatibility. */
(() => {
  let active = null;
  function close(){if(!active)return;active.menu.remove();active.button.setAttribute('aria-expanded','false');active=null;}
  function enhance(select){
    if(select.dataset.pickerReady)return;select.dataset.pickerReady='true';
    const wrap=document.createElement('span');wrap.className='rounded-select '+(select.classList.contains('sort-select')?'sort-picker':'');
    select.before(wrap);wrap.append(select);select.classList.add('picker-native');select.tabIndex=-1;
    const button=document.createElement('button');button.type='button';button.className='picker-trigger';button.setAttribute('aria-haspopup','listbox');button.setAttribute('aria-expanded','false');
    button.setAttribute('aria-label',select.getAttribute('aria-label')||select.labels?.[0]?.textContent||'选择选项');wrap.append(button);
    const sync=()=>{button.textContent=(select.selectedOptions[0]?.textContent||'请选择')+'  ⌄';button.disabled=select.disabled;};sync();select.addEventListener('change',sync);
    button.onclick=()=>{
      if(active?.button===button)return close();close();sync();
      const menu=document.createElement('div');menu.className='picker-menu';menu.setAttribute('role','listbox');
      [...select.options].forEach(option=>{const item=document.createElement('button');item.type='button';item.textContent=option.textContent;item.setAttribute('role','option');item.setAttribute('aria-selected',String(option.selected));item.disabled=option.disabled;item.onclick=()=>{select.value=option.value;select.dispatchEvent(new Event('change',{bubbles:true}));close();button.focus();};menu.append(item);});
      document.body.append(menu);const rect=button.getBoundingClientRect();const width=Math.min(innerWidth-24,Math.max(176,rect.width));menu.style.width=width+'px';menu.style.left=Math.max(12,Math.min(innerWidth-width-12,rect.right-width))+'px';menu.style.top=Math.max(12,Math.min(innerHeight-menu.offsetHeight-12,rect.bottom+6))+'px';
      active={button,menu};button.setAttribute('aria-expanded','true');(menu.querySelector('[aria-selected="true"]')||menu.firstChild)?.focus();
      menu.onkeydown=event=>{const items=[...menu.querySelectorAll('button:not(:disabled)')],i=items.indexOf(document.activeElement);if(event.key==='ArrowDown'||event.key==='ArrowUp'){event.preventDefault();items[(i+(event.key==='ArrowDown'?1:-1)+items.length)%items.length]?.focus();}};
    };
  }
  const scan=()=>{if(active&&!active.button.isConnected)close();document.querySelectorAll('select:not([data-picker-ready])').forEach(enhance);};
  new MutationObserver(scan).observe(document.body,{childList:true,subtree:true});scan();
  document.addEventListener('pointerdown',event=>{if(active&&!active.menu.contains(event.target)&&!active.button.contains(event.target))close();});
  document.addEventListener('keydown',event=>{if(event.key==='Escape'&&active){const button=active.button;close();button.focus();}});
  window.addEventListener('resize',close);document.addEventListener('scroll',event=>{if(active&&!active.menu.contains(event.target))close();},true);
})();
