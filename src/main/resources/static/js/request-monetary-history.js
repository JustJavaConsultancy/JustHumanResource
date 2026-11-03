(() => {
 'use strict';
 const id=location.pathname.match(/\/requests\/(\d+)\/?$/)?.[1];if(!id)return;
 const esc=v=>String(v ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 function routeSummary(raw){try{return JSON.parse(raw).map(l=>`${l.name} (${l.policy}): ${(l.members||[]).map(m=>m.name).join(', ')}`).join('; ');}catch(_){return '';}}
 const mobile=location.pathname.startsWith('/mobile/'),base=mobile?'/mobile/requests':'/employee/requests';
 let panel, generation=0;
 async function load(){const current=++generation;try{
  if(panel&&!panel.isConnected)panel=null;
  if(panel)panel.querySelectorAll('button').forEach(button=>button.disabled=true);
  const response=await fetch(`/api/request-monetary/requests/${id}/history`);if(!response.ok)throw new Error('Monetary details could not load. Retry or refresh the page.');const result=await response.json();
  if(current!==generation)return;
  const routing=result.routing||{};
  if(!result.funding&&!result.snapshots.length&&!routing.monetaryApplicable){panel?.remove();panel=null;return;}
  if(!panel){panel=document.createElement('section');panel.className='money-card';panel.setAttribute('aria-label','Monetary request details');(document.querySelector('main')||document.querySelector('[layout\\:fragment]')||document.body).prepend(panel);}
  const detail=result.typeDetails,r=result.request;
  panel.innerHTML='<h2>Monetary request</h2><p>Approval does not initiate payment. Advance settlement and payment tracking are not part of this request workflow.</p>'+
   (result.funding?`<p><strong>${esc(detail.currency)} ${esc(detail.amount)}</strong></p><p>${esc(detail.purpose)}</p><p>Cost centre: ${esc(detail.costCenter)} | Required: ${esc(detail.requiredDate)}</p>
    ${detail.expenseStartDate?`<p>Expense period: ${esc(detail.expenseStartDate)} to ${esc(detail.expenseEndDate)} | Settlement due: ${esc(detail.settlementDueDate)}</p>`:`<p>Beneficiary: ${esc(detail.beneficiaryName)}</p>`}`:'')+
   '<h3>Monetary routing history</h3>'+(result.snapshots.length?result.snapshots.map((s,i)=>`<article><h4>Submission ${s.submissionNo} - ${esc(s.currency)} ${esc(s.amount)}</h4>
    <p>${esc(s.policyName)}: from ${esc(s.lowerBound)} inclusive ${s.upperBound==null?'with no upper limit':`to below ${esc(s.upperBound)}`} - ${s.target==='GROUPED'?'Grouped approval path':'Line Manager chain'}</p>
    <p>${esc(routeSummary(s.resolvedRoute))}</p>
    ${i>0?`<p>Previous amount: ${esc(result.snapshots[i-1].currency)} ${esc(result.snapshots[i-1].amount)}. This submission requires fresh approvals.</p>`:''}</article>`).join(''):(routing.monetaryApplicable?'<p>No monetary submission yet.</p>':'<p>No monetary policy snapshot. This request uses its configured fixed route or an authorized alternate flow.</p>'))+
   '<p>Finance and Free Route retain their existing authority. The request activity and Finance history show any intervention.</p>';
  const context=result.funding?await fetch('/api/requests/context').then(r=>r.ok?r.json():{}):{};
  if(current!==generation)return;
  if(result.funding&&r.requesterEmployeeId===context.employeeId&&['DRAFT','RETURNED_FOR_CORRECTION'].includes(r.status)){
   const link=document.createElement('a');link.href=`${base}/funding/${id}/edit`;link.textContent='Edit funding request';panel.append(link);
  }
  if(routing.monetaryApplicable&&routing.monetaryRoutingAvailable===false){const warning=document.createElement('p');warning.textContent='Monetary routing is globally disabled. New monetary submissions are unavailable.';panel.append(warning);}
  if(routing.canPreviewSavedRoute){
   const button=document.createElement('button');button.type='button';button.textContent='Preview saved monetary route';button.onclick=async()=>{
    button.disabled=true;output.textContent='Loading saved approval route...';try{const response=await fetch(`/api/request-monetary/requests/${id}/preview`);const p=await response.json();if(current!==generation)return;if(!response.ok)throw new Error(p.detail||p.message||'Preview unavailable.');
     output.textContent=p.monetary?`${p.currency} ${p.amount}: ${p.policy} revision ${p.policyVersion}. From ${p.lowerBound} inclusive to ${p.upperBound}${p.upperBound==='No upper limit'?'':' exclusive'}. ${p.routeLabel||(p.target==='GROUPED'?'Grouped approval path':'Line Manager chain')}. ${routeSummary(p.resolvedRoute)}. Final routing is checked again on submission.`:p.message;
    }catch(e){if(current===generation)output.textContent=e.message+' Use Preview saved monetary route to retry.';}finally{if(current===generation)button.disabled=false;}};
   const output=document.createElement('p');output.setAttribute('role','status');panel.append(button,output);
  }
 }catch(e){if(current!==generation)return;if(!panel){panel=document.createElement('section');panel.className='money-card';(document.querySelector('main')||document.body).prepend(panel);}panel.replaceChildren();const error=document.createElement('p');error.setAttribute('role','alert');error.textContent=e.message||'Monetary details could not refresh.';const retry=document.createElement('button');retry.type='button';retry.textContent='Retry monetary details';retry.onclick=()=>{retry.disabled=true;load();};panel.append(error,retry);}}
 window.addEventListener('request-monetary:refresh',load);
 const style=document.createElement('link');style.rel='stylesheet';style.href='/css/request-monetary.css';document.head.append(style);
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',()=>setTimeout(load,600));else setTimeout(load,600);
})();
