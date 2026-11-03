(() => {
 'use strict';
 const api='/api/request-monetary', byId=id=>document.getElementById(id);
 const mobile=location.pathname.startsWith('/mobile/'), base=mobile?'/mobile/requests':'/employee/requests';
 let id=location.pathname.match(/\/funding\/(\d+)\/edit/)?.[1], editRevision=0, savedRevision=0, busy=false, uploaded=0, savedHeader={}, available=true;
 const dirty=()=>editRevision!==savedRevision;
 function edited(){editRevision++;byId('fundingPreviewText').textContent='Changes are unsaved. Save and preview again.';}
 const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 async function json(url,options={}){
  if(options.method&&options.method!=='GET'){
   const token=await fetch('/api/requests/grouped-approvals/csrf').then(r=>r.json());options.headers={...options.headers,[token.headerName]:token.token};
  }
  const r=await fetch(url,options);if(!r.ok){let e;try{e=await r.json();}catch(_){}throw new Error(e?.detail||e?.message||'Operation failed. Refresh to check its outcome before retrying.');}
  return r.headers.get('content-type')?.includes('application/json')?r.json():null;
 }
 async function run(operation){if(busy)return;const focused=document.activeElement;busy=true;byId('fundingError').textContent='';document.querySelectorAll('#fundingForm button').forEach(b=>b.disabled=true);
  try{await operation();}catch(e){byId('fundingError').textContent=e.message;byId('fundingError').focus();}finally{busy=false;document.querySelectorAll('#fundingForm button').forEach(b=>b.disabled=!available);if(document.activeElement===document.body&&focused?.isConnected&&!focused.disabled)focused.focus();}}
 function typeChanged(){const advance=byId('fundingType').value==='CASH_ADVANCE';byId('fundingAdvance').hidden=!advance;byId('fundingFund').hidden=advance;
  ['fundingStart','fundingEnd','fundingDue'].forEach(k=>{byId(k).required=advance;byId(k).disabled=!advance;});byId('fundingBeneficiary').required=!advance;byId('fundingBeneficiary').disabled=advance;}
 function payload(){
  const type=byId('fundingType').value;
  const data={amount:byId('fundingAmount').value,currency:byId('fundingCurrency').value,purpose:byId('fundingPurpose').value.trim(),costCenter:byId('fundingCostCenter').value.trim(),requiredDate:byId('fundingRequired').value};
  if(type==='CASH_ADVANCE')Object.assign(data,{expenseStartDate:byId('fundingStart').value,expenseEndDate:byId('fundingEnd').value,settlementDueDate:byId('fundingDue').value});
  else data.beneficiaryEmployeeId=Number(byId('fundingBeneficiary').value);
  // Amount remains a decimal string; the server parses BigDecimal without a JavaScript floating-point conversion.
  return {requestType:type,title:byId('fundingTitle').value.trim(),priority:byId('fundingPriority').value,description:byId('fundingDescription').value,departmentId:savedHeader.departmentId,[type==='CASH_ADVANCE'?'cashAdvance':'fundRequest']:data};
 }
 byId('fundingCurrency').onchange=()=>{const whole=byId('fundingCurrency').value==='JPY';byId('fundingAmount').step=whole?'1':'0.01';byId('fundingAmount').min=whole?'1':'0.01';};
 byId('fundingBack').href=base;byId('fundingType').onchange=()=>{typeChanged();};byId('fundingForm').addEventListener('input',event=>{if(event.target.type!=='file')edited();});
 byId('fundingForm').onsubmit=event=>{event.preventDefault();run(async()=>{
  const revision=editRevision, body=JSON.stringify(payload()), creating=!id;
  const wasTypeDisabled=byId('fundingType').disabled;byId('fundingType').disabled=true;
  let saved;try{saved=await json(id?`${api}/funding/${id}`:`${api}/funding`,{method:id?'PUT':'POST',headers:{'Content-Type':'application/json'},body});}catch(e){if(creating)byId('fundingType').disabled=wasTypeDisabled;throw e;}
  savedRevision=revision;if(saved.departmentId!==undefined)savedHeader.departmentId=saved.departmentId;
  id=String(saved.id);byId('fundingType').disabled=true;history.replaceState(null,'',`${base}/funding/${id}/edit`);
  byId('fundingStatus').textContent=dirty()?'Previous values saved. Current changes are unsaved; save them before previewing or submitting.':`Saved ${saved.requestNumber}. Upload supporting documents, preview, then submit.`;
 });};
 function requireSaved(){if(!id||dirty())throw new Error('Save the draft and all changes before this action.');}
 byId('fundingUpload').onclick=()=>run(async()=>{requireSaved();const file=byId('fundingAttachment').files[0];if(!file)throw new Error('Choose a supporting document.');
  const data=new FormData();data.append('file',file);data.append('attachmentType','SUPPORTING_DOCUMENT');
  await json(`/api/requests/${id}/attachments`,{method:'POST',body:data});uploaded++;byId('fundingAttachment').value='';byId('fundingDocuments').textContent=`${uploaded} supporting document(s) uploaded in this session. Existing documents are visible on the request detail page.`;
 });
 byId('fundingPreview').onclick=()=>run(async()=>{requireSaved();const revision=editRevision;const result=await json(`${api}/requests/${id}/preview`);if(revision!==editRevision){byId('fundingPreviewText').textContent='Preview is out of date. Save changes and preview again.';return;}let levels='';if(result.resolvedRoute){try{levels='\n'+JSON.parse(result.resolvedRoute).map(l=>`${l.name} (${l.policy}): ${(l.members||[]).map(m=>m.name).join(', ')}`).join('\n');}catch(_){}}byId('fundingPreviewText').textContent=result.monetary?`${result.currency} ${result.amount}: ${result.policy} (revision ${result.policyVersion}). From ${result.lowerBound} inclusive to ${result.upperBound}. ${result.routeLabel||(result.target==='GROUPED'?'Grouped approval path':'Line Manager chain')}. Final routing is checked on submission.${levels}`:result.message;});
 byId('fundingSubmit').onclick=()=>run(async()=>{requireSaved();if(!confirm('Submit the saved request for approval? Approval does not initiate payment.'))return;
  const controls=[...byId('fundingForm').querySelectorAll('input,select,textarea')].map(c=>[c,c.disabled]);controls.forEach(([c])=>c.disabled=true);try{await json(`${api}/requests/${id}/submit`,{method:'POST'});savedRevision=editRevision;location.href=`${base}/${id}`;}finally{controls.forEach(([c,disabled])=>c.disabled=disabled);}
 });
 byId('fundingForm').addEventListener('change',event=>{if(event.target.type!=='file')edited();});
 window.addEventListener('beforeunload',event=>{if(dirty()){event.preventDefault();event.returnValue='';}});
 run(async()=>{
  const options=await json(api+'/options');byId('fundingCurrency').innerHTML=options.currencies.map(c=>`<option>${esc(c)}</option>`).join('');byId('fundingCurrency').value='NGN';
  byId('fundingBeneficiary').innerHTML='<option value="">Select employee</option>'+options.beneficiaries.map(p=>`<option value="${p.value}">${esc(p.label)}</option>`).join('');
  byId('fundingType').innerHTML=options.types.map(t=>`<option value="${esc(t.code)}">${esc(t.name)}</option>`).join('');
  if(id){const saved=await json(`${api}/funding/${id}`);savedHeader=saved;const history=await json(`${api}/requests/${id}/history`);if(history.snapshots.length){byId('fundingCurrency').disabled=true;byId('fundingCurrencyHint').textContent='Currency is locked after the first monetary submission. Create a new request to use a different currency.';}if(!options.types.some(t=>t.code===saved.requestType))byId('fundingType').add(new Option(saved.requestType,saved.requestType));
   byId('fundingType').value=saved.requestType;byId('fundingType').disabled=true;byId('fundingTitle').value=saved.title;byId('fundingPriority').value=saved.priority;byId('fundingDescription').value=saved.description||'';
   const p=saved.cashAdvance||saved.fundRequest;for(const [key,field] of Object.entries({amount:'fundingAmount',currency:'fundingCurrency',purpose:'fundingPurpose',costCenter:'fundingCostCenter',requiredDate:'fundingRequired',expenseStartDate:'fundingStart',expenseEndDate:'fundingEnd',settlementDueDate:'fundingDue',beneficiaryEmployeeId:'fundingBeneficiary'}))if(p[key]!=null)byId(field).value=p[key];
  }else{const selected=new URLSearchParams(location.search).get('type');if(selected)byId('fundingType').value=selected;}
  available=options.enabled&&(!!id||options.types.length>0);typeChanged();byId('fundingCurrency').onchange();savedRevision=editRevision;byId('fundingStatus').textContent=available?'Save a draft before previewing or submitting.':options.enabled?'No funding request types are enabled. Contact an administrator.':'New funding submissions are disabled. Contact an administrator.';
 });
})();
