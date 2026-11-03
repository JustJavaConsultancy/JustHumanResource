(() => {
 'use strict';
 const api='/api/request-monetary',get=id=>document.getElementById(id),esc=v=>String(v ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 // ---- Small confirmation modal (replaces window.confirm). Resolves true on confirm, false on cancel/Escape/backdrop. ----
 let activeModal=null,modalSeq=0;
 function modalEl(tag,className,text){const n=document.createElement(tag);if(className)n.className=className;if(text!=null)n.textContent=text;return n;}
 function confirmModal(options){
  const o=Object.assign({title:'Are you sure?',message:'',details:'',confirmLabel:'Confirm',cancelLabel:'Cancel',tone:'primary'},options);
  if(activeModal)activeModal(false);
  return new Promise(resolve=>{
   const opener=document.activeElement,danger=o.tone==='danger',uid='mmModal'+(++modalSeq);
   let done=false,pressedOnBackdrop=false;
   const overlay=modalEl('div','mm-overlay'),dialog=modalEl('div','mm-dialog'+(o.details?' mm-dialog-wide':''));
   dialog.setAttribute('role',danger?'alertdialog':'dialog');dialog.setAttribute('aria-modal','true');dialog.setAttribute('aria-labelledby',uid+'Title');
   if(o.message)dialog.setAttribute('aria-describedby',uid+'Message');
   const body=modalEl('div','mm-dialog-body'),icon=modalEl('div','mm-dialog-icon'+(danger?' mm-dialog-icon-danger':''),danger?'!':'?');icon.setAttribute('aria-hidden','true');
   const text=modalEl('div','mm-dialog-text'),title=modalEl('h2','mm-dialog-title',o.title);title.id=uid+'Title';text.append(title);
   if(o.message){const message=modalEl('p','mm-dialog-message',o.message);message.id=uid+'Message';text.append(message);}
   if(o.details)text.append(modalEl('pre','mm-dialog-details',o.details));
   body.append(icon,text);
   const footer=modalEl('div','mm-dialog-footer'),cancel=modalEl('button','btn btn-secondary',o.cancelLabel),ok=modalEl('button','btn '+(danger?'btn-danger':'btn-primary'),o.confirmLabel);
   cancel.type='button';ok.type='button';footer.append(cancel,ok);dialog.append(body,footer);overlay.append(dialog);
   function finish(result){
    if(done)return;done=true;
    document.removeEventListener('keydown',onKey,true);overlay.remove();document.documentElement.classList.remove('mm-modal-open');activeModal=null;
    if(opener&&opener.isConnected&&typeof opener.focus==='function')opener.focus();
    resolve(result);
   }
   function onKey(event){
    if(event.key==='Escape'){event.preventDefault();event.stopPropagation();finish(false);return;}
    if(event.key==='Tab'){const order=[cancel,ok],index=order.indexOf(document.activeElement);event.preventDefault();
     if(event.shiftKey)order[index<=0?order.length-1:index-1].focus();else order[index===-1||index===order.length-1?0:index+1].focus();}
   }
   cancel.onclick=()=>finish(false);ok.onclick=()=>finish(true);
   overlay.addEventListener('mousedown',event=>{pressedOnBackdrop=event.target===overlay;});
   overlay.addEventListener('click',event=>{if(event.target===overlay&&pressedOnBackdrop)finish(false);});
   document.addEventListener('keydown',onKey,true);
   document.body.append(overlay);document.documentElement.classList.add('mm-modal-open');activeModal=finish;
   (danger?cancel:ok).focus(); // destructive dialogs start on Cancel so a stray Enter cannot confirm
  });
 }
 const badge=(text,tone)=>`<span class="mm-badge mm-badge-${tone}">${esc(text)}</span>`;
 let data,paths=[],draftId=null,version=null,editRevision=0,savedRevision=0,busy=false,next=0;
 const dirty=()=>editRevision!==savedRevision;
 const discardChanges=()=>confirmModal({title:'Discard unsaved changes?',message:'Your unsaved edits to the current draft will be lost.',confirmLabel:'Discard changes',cancelLabel:'Keep editing',tone:'danger'});
 function edited(){editRevision++;get('monetaryPreviewText').textContent='Unsaved local changes. Save before publishing this draft.';}
 async function json(url,options={}){if(options.method&&options.method!=='GET'){const token=await fetch('/api/requests/grouped-approvals/csrf').then(r=>r.json());options.headers={...options.headers,[token.headerName]:token.token};}
  const r=await fetch(url,options);if(!r.ok){let e;try{e=await r.json();}catch(_){}throw new Error(e?.detail||e?.message||'Operation failed. Refresh to check its outcome before retrying.');}return r.headers.get('content-type')?.includes('application/json')?r.json():null;}
 async function run(operation){if(busy)return;const focused=document.activeElement;busy=true;get('monetaryError').textContent='';document.querySelectorAll('#monetaryPolicyPage button').forEach(b=>b.disabled=true);
  try{await operation();}catch(e){get('monetaryError').textContent=e.message;get('monetaryError').focus();}finally{busy=false;document.querySelectorAll('#monetaryPolicyPage button').forEach(b=>b.disabled=false);if(document.activeElement===document.body&&focused?.isConnected&&!focused.disabled)focused.focus();}}
 function addBand(value={lowerBound:'0',upperBound:null,target:'LINE_MANAGER',groupedVersionId:null}){
  const id=++next,card=document.createElement('fieldset');card.dataset.band='';card.className='mm-fieldset mm-band';
  card.innerHTML=`<legend>Amount band</legend>
   <div class="mm-band-grid">
    <div class="mm-field"><label for="moneyLower${id}">From, inclusive</label><input id="moneyLower${id}" data-lower type="number" step="0.01" min="0" required value="${esc(value.lowerBound)}"></div>
    <div class="mm-field"><label for="moneyUpper${id}">Below, exclusive</label><input id="moneyUpper${id}" data-upper type="number" step="0.01" min="0" placeholder="No upper limit" value="${esc(value.upperBound ?? '')}"><p class="mm-hint">Leave empty for no upper limit.</p></div>
    <div class="mm-field"><label for="moneyTarget${id}">Approval target</label><select id="moneyTarget${id}" data-target><option value="LINE_MANAGER">Line Manager chain</option><option value="GROUPED">Published grouped path</option></select></div>
    <div class="mm-field"><label for="moneyPath${id}">Grouped path</label><select id="moneyPath${id}" data-path><option value="">Choose published path</option>${paths.filter(p=>p.state==='PUBLISHED'&&p.enabled).map(p=>`<option value="${p.id}">${esc(p.name)} - revision ${p.revision}</option>`).join('')}</select></div>
   </div>
   <div class="mm-band-actions"><button data-remove class="btn btn-danger-soft btn-sm" type="button">Remove band</button></div>`;
  card.querySelector('[data-target]').value=value.target;card.querySelector('[data-path]').value=value.groupedVersionId||'';
  card.querySelector('[data-path]').disabled=value.target!=='GROUPED';
  card.querySelector('[data-target]').onchange=e=>{card.querySelector('[data-path]').disabled=e.target.value!=='GROUPED';edited();};
  card.querySelector('[data-remove]').onclick=async()=>{if(await confirmModal({title:'Remove this band?',message:'The remaining policy must still cover all amounts.',confirmLabel:'Remove band',cancelLabel:'Keep band',tone:'danger'})){card.remove();edited();get('monetaryAddBand').focus();}};
  get('monetaryBands').append(card);
 }
 function payload(){const bands=[...get('monetaryBands').children].map(card=>({lowerBound:card.querySelector('[data-lower]').value,upperBound:card.querySelector('[data-upper]').value||null,
  target:card.querySelector('[data-target]').value,groupedVersionId:card.querySelector('[data-target]').value==='GROUPED'?Number(card.querySelector('[data-path]').value)||null:null}));
  if(!bands.length)throw new Error('Add at least one band.');return {name:get('monetaryName').value.trim(),requestType:get('monetaryType').value,currency:get('monetaryCurrency').value,bands};}
 function preview(policy){get('monetaryPreviewText').textContent=`${policy.name} - ${policy.currency}\n`+policy.bands.map(b=>`${policy.currency} ${b.lowerBound} inclusive to ${b.upperBound ?? 'no upper limit'}${b.upperBound==null?'':' exclusive'}: ${b.target==='LINE_MANAGER'?'Line Manager chain':paths.find(p=>p.id===b.groupedVersionId)?.name||'Select grouped path'}\nAt ${b.lowerBound}, this band applies.`).join('\n\n');}
 function reset(){draftId=null;version=null;get('monetaryPolicyForm').reset();get('monetaryBands').replaceChildren();get('monetaryFormTitle').textContent='New draft';addBand();savedRevision=editRevision;}
 async function load(){
  const [config,grouped,options]=await Promise.all([json(api+'/config'),json('/api/grouped-approval-paths'),json(api+'/options')]);data=config;paths=grouped.paths;
  get('monetaryStatus').textContent=`Monetary routing: ${data.enabled?'available':'disabled for new submissions'}. New funding requests: ${data.fundingEnabled?'available':'disabled'}. Existing approvals can continue.`;
  const selectedType=get('monetaryType').value,selectedCurrency=get('monetaryCurrency').value;
  if(!get('monetaryType').options.length)get('monetaryType').innerHTML=data.types.map(t=>`<option value="${esc(t.code)}">${esc(t.name)}</option>`).join('');if(selectedType)get('monetaryType').value=selectedType;
  if(!get('monetaryCurrency').options.length)get('monetaryCurrency').innerHTML=options.currencies.map(c=>`<option>${esc(c)}</option>`).join('');get('monetaryCurrency').value=selectedCurrency||'NGN';
  const policiesById=new Map(data.policies.map(p=>[String(p.id),p]));
  get('monetaryBindings').innerHTML=data.types.map(t=>{
   const rows=data.bindings.filter(b=>b.requestType===t.code).map(b=>{
    const p=policiesById.get(String(b.policyId));
    const identity=p?`${esc(p.name)} - revision ${p.revision}`:`Policy #${b.policyId} - metadata unavailable`;
    const state=!p?'Unavailable':p.state!=='PUBLISHED'?'Unpublished policy':!p.enabled?'Disabled policy':!data.enabled?'Monetary routing globally disabled':'Available';
    const note=t.monetaryRoutingEnabled?'Configured binding':'Retained binding; not used for new fixed-route requests';
    return `<li><span class="mm-chip">${esc(b.currency)}</span><span class="mm-binding-main"><strong>${identity}</strong><span class="mm-muted">${note}</span></span>${badge(state,state==='Available'?'success':'warning')}</li>`;
   }).join('');
   const typeToggle=['CASH_ADVANCE','FUND_REQUEST'].includes(t.code)?`<button data-type="${t.code}" data-enabled="${!t.enabled}" class="btn btn-sm ${t.enabled?'btn-danger-soft':'btn-secondary'}" type="button">${t.enabled?'Disable':'Enable'} request type</button>`:'';
   return `<article class="mm-item"><div class="mm-item-head"><h3>${esc(t.name)}</h3>${badge(t.monetaryRoutingEnabled?'Monetary policy routing':'Fixed routing',t.monetaryRoutingEnabled?'info':'neutral')}</div>
    ${rows?`<ul class="mm-bindings">${rows}</ul>`:'<p class="mm-muted">No policy bindings</p>'}
    <div class="money-actions"><button data-fixed="${t.code}" class="btn btn-secondary btn-sm" type="button">Use fixed routing for new requests</button>${typeToggle}</div></article>`;
  }).join('');
  get('monetaryVersions').innerHTML=data.policies.length?data.policies.map(p=>`<article class="mm-item"><div class="mm-item-head"><div><h3>${esc(p.name)}</h3><p class="mm-muted">${esc(p.requestType.replaceAll('_',' '))} &middot; ${esc(p.currency)} &middot; revision ${p.revision}</p></div><div class="mm-badges">${badge(p.state,p.state==='PUBLISHED'?'success':'warning')}${badge(p.enabled?'Enabled':'Disabled',p.enabled?'info':'neutral')}</div></div>
   <div class="money-actions">${p.state==='DRAFT'?`<button data-edit="${p.id}" class="btn btn-secondary btn-sm" type="button">Edit draft</button><button data-publish="${p.id}" class="btn btn-primary btn-sm" type="button">Preview and publish</button>`:`<button data-clone="${p.id}" class="btn btn-secondary btn-sm" type="button">Clone draft</button><button data-activate="${p.id}" class="btn btn-primary btn-sm" type="button">Activate for new requests</button>`}<button data-toggle="${p.id}" class="btn btn-sm ${p.enabled?'btn-danger-soft':'btn-secondary'}" type="button">${p.enabled?'Disable':'Enable'} policy</button></div></article>`).join(''):'<p class="mm-muted">No policies yet.</p>';
 }
 get('monetaryVersions').onclick=e=>run(async()=>{const b=e.target.closest('button');if(!b)return;const id=Number(b.dataset.edit||b.dataset.publish||b.dataset.clone||b.dataset.activate||b.dataset.toggle),p=data.policies.find(p=>p.id===id);if(!p)return;
  if(b.dataset.edit){if(dirty()&&!await discardChanges())return;draftId=id;version=p.version;get('monetaryName').value=p.name;get('monetaryType').value=p.requestType;get('monetaryCurrency').value=p.currency;get('monetaryBands').replaceChildren();p.bands.forEach(addBand);get('monetaryFormTitle').textContent=`Edit draft - revision ${p.revision}`;savedRevision=editRevision;get('monetaryName').focus();}
  else if(b.dataset.publish){if(draftId===id&&dirty())throw new Error('Save current changes before publishing this draft.');preview(p);if(await confirmModal({title:'Publish this policy?',message:'Published policies are immutable. Review the bands before publishing.',details:get('monetaryPreviewText').textContent,confirmLabel:'Publish policy',cancelLabel:'Cancel'})){const controls=draftId===id?[...get('monetaryPolicyForm').querySelectorAll('input,select,textarea')].map(c=>[c,c.disabled]):[];controls.forEach(([c])=>c.disabled=true);try{await json(`${api}/policies/${id}/publish?version=${p.version}`,{method:'POST'});await load();if(draftId===id)reset();}finally{controls.forEach(([c,disabled])=>c.disabled=disabled);}}}
  else if(b.dataset.clone){await json(`${api}/policies/${id}/clone`,{method:'POST'});await load();}
  else if(b.dataset.activate){if(await confirmModal({title:'Activate this policy?',message:`Activate "${p.name}" for ${p.requestType.replaceAll('_',' ')} in ${p.currency}. Existing monetary requests retain their policy; new requests in unconfigured currencies will be blocked.`,confirmLabel:'Activate policy',cancelLabel:'Cancel'})){await json(`${api}/policies/${id}/activate`,{method:'POST'});await load();}}
  else{await json(`${api}/policies/${id}/availability?enabled=${!p.enabled}`,{method:'POST'});await load();}
 });
 get('monetaryBindings').onclick=e=>run(async()=>{const b=e.target.closest('button');if(!b)return;
  if(b.dataset.fixed){if(await confirmModal({title:'Switch to fixed routing?',message:'New requests of this type will use fixed routing. Returned monetary requests retain their pinned policy.',confirmLabel:'Use fixed routing',cancelLabel:'Cancel'}))await json(`${api}/types/${b.dataset.fixed}/fixed-routing`,{method:'POST'});}
  else await json(`${api}/types/${b.dataset.type}/availability?enabled=${b.dataset.enabled}`,{method:'POST'});await load();
 });
 get('monetaryNew').onclick=async()=>{if(!dirty()||await discardChanges())reset();};get('monetaryAddBand').onclick=()=>{addBand({lowerBound:'',upperBound:null,target:'LINE_MANAGER'});edited();};
 get('monetaryPreview').onclick=()=>{try{preview(payload());}catch(e){get('monetaryError').textContent=e.message;get('monetaryError').focus();}};
 get('monetaryPolicyForm').addEventListener('input',edited);
 get('monetaryPolicyForm').onsubmit=e=>{e.preventDefault();run(async()=>{const revision=editRevision,body=JSON.stringify(payload());const saved=await json(draftId?`${api}/policies/${draftId}?version=${version}`:api+'/policies',{method:draftId?'PUT':'POST',headers:{'Content-Type':'application/json'},body});
  draftId=saved.id;version=saved.version;savedRevision=revision;await load();get('monetaryStatus').textContent=dirty()?'Previous values saved. Current changes are unsaved; save before publishing.':'Draft saved. Preview and publish, then explicitly activate it.';
 });};
 get('monetaryAudit').onclick=()=>run(async()=>{const rows=await json(api+'/audit');get('monetaryAuditRows').innerHTML=rows.map(r=>`<p>${esc(r.createdAt)} - ${esc(r.requestType)} - ${esc(r.action)} - employee #${r.actorId}</p>`).join('')||'<p>No configuration events yet.</p>';});
 get('monetaryPolicyForm').addEventListener('change',event=>{if(event.target.type!=='file')edited();});
 window.addEventListener('beforeunload',e=>{if(dirty()){e.preventDefault();e.returnValue='';}});run(async()=>{await load();reset();});
})();