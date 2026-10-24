(()=>{
'use strict';
const items=JSON.parse(document.getElementById('embedded-audio-map').textContent);
const normalize=s=>s.replace(/[_…]+|\.{3,}/g,' BLANK ').replace(/[‘’]/g,"'").replace(/\s+/g,' ').trim();
const byText=new Map(items.map(x=>[normalize(x.text),x.id]));
let active=null;
const status=document.createElement('div');status.setAttribute('role','status');status.id='audio-status';
async function play(ids){
 if(active){active.pause();active.currentTime=0;}
 for(const id of ids){
  const a=document.getElementById('audio_'+id);if(!a||!a.src){status.textContent='فایل صوتی پیدا نشد';return;}
  active=a;a.currentTime=0;
  try{await a.play();status.textContent='در حال پخش';await new Promise((resolve,reject)=>{a.onended=resolve;a.onerror=reject;a.onpause=resolve;});}
  catch(e){status.textContent='پخش ممکن نشد؛ دوباره دکمه را بزنید.';return;}
  if(active!==a)return;
 }
 status.textContent='پخش پایان یافت';
}
function bind(b,ids){b.dataset.embeddedIds=JSON.stringify(ids);b.disabled=false;b.setAttribute('aria-label','پخش صوت');}
for(const b of document.querySelectorAll('[data-audio]')){
 if(document.getElementById('audio_'+b.dataset.audio)?.src)bind(b,[b.dataset.audio]);
}
for(const card of document.querySelectorAll('[data-tts-text]')){
 const id=byText.get(normalize(card.dataset.ttsText));if(!id)continue;
 let b=card.querySelector('.audio-pending');if(b){const replacement=document.createElement('button');replacement.type='button';replacement.className='speak';replacement.textContent='▶';b.replaceWith(replacement);bind(replacement,[id]);}
}
for(const b of document.querySelectorAll('.spk:not([data-embedded-ids]),.speak:not([data-embedded-ids])')){
 const parent=b.closest('.sentence,.example-row,.listen-card');if(!parent)continue;
 const text=parent.querySelector('.txt,.example-en')?.textContent;
 if(!text)continue;
 const id=byText.get(normalize(text));if(id)bind(b,[id]);
 else{const ids=text.split(/\s*[—→·]\s*/).map(t=>byText.get(normalize(t)));if(ids.length>1&&ids.every(Boolean))bind(b,ids);}
}
// Original visual speaker icons without a manifest recording must not pretend
// to be playable. Keep the example text and remove the inert control.
for(const b of document.querySelectorAll('.spk:not([data-embedded-ids]),.speak:not([data-embedded-ids])')){
 if(b.id==='popupPlay')continue;
 const label=document.createElement('span');label.className='audio-not-in-manifest';label.title='این مثال در فهرست صوت JSON نیست';label.textContent='';b.replaceWith(label);
}
// The complete searchable bank also exposes entries without original controls.
const bank=document.createElement('details');bank.id='complete-audio-library';bank.dir='rtl';
bank.style.cssText='margin:24px;padding:16px;border:1px solid #888;border-radius:12px';
const title=document.createElement('summary');title.textContent='بانک کامل صداها — '+items.length+' مورد';bank.append(title);
const search=document.createElement('input');search.type='search';search.placeholder='جست‌وجوی متن انگلیسی';search.setAttribute('aria-label','جست‌وجوی صدا');bank.append(search,status);
const stop=document.createElement('button');stop.type='button';stop.textContent='توقف پخش';stop.onclick=()=>{if(active){active.pause();active=null;}status.textContent='متوقف شد';};bank.append(stop);
const list=document.createElement('div');bank.append(list);
for(const item of items){const row=document.createElement('div');row.style.cssText='padding:6px;display:flex;gap:10px;align-items:center';row.dataset.search=item.text.toLowerCase();const b=document.createElement('button');b.type='button';b.textContent='▶';bind(b,[item.id]);const text=document.createElement('span');text.dir='auto';text.textContent=item.text;row.append(b,text);list.append(row);}
search.oninput=()=>{for(const row of list.children)row.hidden=!row.dataset.search.includes(search.value.toLowerCase());};
document.body.append(bank);
document.addEventListener('click',e=>{const b=e.target.closest('[data-embedded-ids]');if(!b)return;e.preventDefault();e.stopImmediatePropagation();play(JSON.parse(b.dataset.embeddedIds));},true);
})();
