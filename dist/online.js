(()=>{'use strict';
const API='https://fnknttplbqwkzarbkbzb.supabase.co/functions/v1/game';
const PUBLISHABLE='sb_publishable_GBg5psPV1z00NELZRoK0-Q_ABaenMyc';
const PLAYER_KEY='uw1931-player-key',AUTO_KEY='uw1931-cloud-auto';
let key=localStorage.getItem(PLAYER_KEY)||'',account=null,profile=null,serverVersion=0,conflict=false,syncing=false,syncTimer=null,activeRoom=null,pollTimer=null,clockTimer=null;

const safe=s=>typeof esc==='function'?esc(s):String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const makeKey=()=>{const b=crypto.getRandomValues(new Uint8Array(32));return 'uw1_'+Array.from(b,x=>x.toString(16).padStart(2,'0')).join('')};
const versionKey=()=>account?'uw1931-cloud-v:'+account.id:'uw1931-cloud-v:unknown';
const localVersion=()=>Number(localStorage.getItem(versionKey())||0);
const setLocalVersion=v=>localStorage.setItem(versionKey(),String(v||0));
const autoEnabled=()=>localStorage.getItem(AUTO_KEY)!=='0';
const setAuto=v=>localStorage.setItem(AUTO_KEY,v?'1':'0');
function onlineShow(t,h){
  modalTitle.textContent=t;
  modalBody.innerHTML=h;
  if(!modal.open)modal.showModal();
}

async function api(body,overrideKey){
  const token=overrideKey||key;
  if(!token)throw Error('Kein Online-Konto eingerichtet.');
  const r=await fetch(API,{
    method:'POST',
    headers:{'Content-Type':'application/json','apikey':PUBLISHABLE,'x-player-key':token},
    body:JSON.stringify(body)
  });
  let data={};
  try{data=await r.json()}catch{}
  if(!r.ok){
    const e=new Error(data.error||'Online-Dienst nicht erreichbar.');
    e.status=r.status;
    throw e;
  }
  return data;
}

function copyText(text){
  if(navigator.clipboard?.writeText)return navigator.clipboard.writeText(text).then(()=>toast('Kopiert.'));
  const a=document.createElement('textarea');
  a.value=text;document.body.append(a);a.select();document.execCommand('copy');a.remove();toast('Kopiert.');
  return Promise.resolve();
}

async function refreshProfile(){
  if(!key)return null;
  try{
    const d=await api({op:'profile'});
    account=d.account;profile=d;serverVersion=d.save?.version||0;
    conflict=serverVersion!==localVersion()&&(serverVersion>0||localVersion()>0);
    return d;
  }catch(e){
    account=null;profile=null;
    throw e;
  }
}

function statusText(){
  if(!account)return 'Nicht angemeldet';
  if(conflict)return '⚠ Cloud und dieses Gerät haben unterschiedliche Versionen.';
  if(serverVersion===0)return 'Noch kein Cloud-Spielstand.';
  return 'Cloud-Version '+serverVersion+(profile?.save?.updated_at?' · '+new Date(profile.save.updated_at).toLocaleString('de-DE'):'');
}

async function register(){
  const name=$('#onlineName')?.value.trim();
  if(!name)return toast('Bitte Spielernamen eingeben.');
  const next=key||makeKey();
  try{
    const d=await api({op:'register',name},next);
    key=next;
    localStorage.setItem(PLAYER_KEY,key);
    account=d.account;
    setAuto(true);
    await refreshProfile();
    if(!serverVersion)await cloudPut(false,true);
    renderHub();
  }catch(e){toast(e.message)}
}

async function useKey(){
  const raw=($('#onlineKey')?.value||'').trim().toLowerCase();
  if(!/^uw1_[a-f0-9]{64}$/.test(raw))return toast('Der Spielercode ist ungültig.');
  const old=key;
  try{
    const d=await api({op:'profile'},raw);
    key=raw;
    localStorage.setItem(PLAYER_KEY,key);
    account=d.account;profile=d;serverVersion=d.save?.version||0;
    conflict=serverVersion!==localVersion()&&(serverVersion>0||localVersion()>0);
    renderHub();
  }catch(e){key=old;toast(e.message)}
}

function forgetAccount(){
  if(!confirm('Online-Konto auf diesem Gerät abmelden? Der private Spielercode wird hier entfernt. Dein Cloud-Stand bleibt erhalten.'))return;
  key='';account=null;profile=null;serverVersion=0;conflict=false;
  localStorage.removeItem(PLAYER_KEY);
  renderHub();
}

async function cloudGet(){
  try{
    const d=await api({op:'cloud.get'});
    if(!d.save)return toast('In der Cloud ist noch kein Spielstand.');
    const next=upgrade(d.save.state);
    localStorage.setItem('uw1931',JSON.stringify(next));
    serverVersion=d.save.version;
    setLocalVersion(serverVersion);
    conflict=false;
    toast('Cloud-Spielstand geladen.');
    setTimeout(()=>location.reload(),350);
  }catch(e){toast(e.message)}
}

async function cloudPut(force=false,silent=false){
  if(!account||syncing)return;
  if(conflict&&!force){
    if(!silent)toast('Cloud-Konflikt: erst laden oder bewusst überschreiben.');
    return;
  }
  syncing=true;
  try{
    if(force){
      const latest=await api({op:'cloud.get'});
      serverVersion=latest.save?.version||0;
    }
    const v=force?serverVersion:localVersion();
    const d=await api({op:'cloud.put',state:S,version:v});
    serverVersion=d.version;
    setLocalVersion(d.version);
    conflict=false;
    profile={...(profile||{}),save:{version:d.version,updated_at:d.updated_at}};
    if(!silent)toast('Spielstand in der Cloud gespeichert.');
  }catch(e){
    if(e.status===409){
      conflict=true;
      if(!silent)toast('Cloud-Konflikt: Auf einem anderen Gerät wurde gespeichert.');
    }else if(!silent)toast(e.message);
  }finally{syncing=false}
}

function queueCloud(){
  if(!account||!autoEnabled()||conflict)return;
  clearTimeout(syncTimer);
  syncTimer=setTimeout(()=>cloudPut(false,true),1400);
}

function renderLogin(){
  onlineShow('Online & Cloud',`
    <div class="online-card">
      <h3>Kostenloses Online-Konto</h3>
      <p>Kein Abo, keine E-Mail nötig. Dein privater Spielercode ist gleichzeitig dein Wiederherstellungsschlüssel für andere Geräte.</p>
      <label class="online-label">Spielername<input id="onlineName" maxlength="24" autocomplete="nickname" placeholder="z. B. Dennis"></label>
      <button class="primary online-wide" id="onlineCreate">Online-Konto erstellen</button>
    </div>
    <div class="online-card">
      <h3>Vorhandenes Konto</h3>
      <label class="online-label">Privater Spielercode<input id="onlineKey" autocomplete="off" autocapitalize="none" spellcheck="false" placeholder="uw1_…"></label>
      <button class="online-wide" id="onlineUseKey">Auf diesem Gerät anmelden</button>
    </div>`);
  $('#onlineCreate').onclick=register;
  $('#onlineUseKey').onclick=useKey;
}

async function openHub(){
  stopRoom();activeRoom=null;
  if(key&&!account){try{await refreshProfile()}catch{}}
  renderHub();
}

function renderHub(){
  if(!account)return renderLogin();
  onlineShow('Online & Cloud',`
    <div class="online-card account-card">
      <div><span class="eyebrow">ONLINE-KONTO</span><h3>${safe(account.name)}</h3></div>
      <span class="online-dot">● verbunden</span>
    </div>
    <div class="online-card">
      <h3>Privater Wiederherstellungscode</h3>
      <p>Bewahre ihn privat auf. Damit kannst du denselben Cloud-Spielstand auf Handy, Tablet oder PC öffnen.</p>
      <textarea id="recoveryCode" readonly rows="3">${safe(key)}</textarea>
      <div class="online-row"><button id="copyRecovery">Code kopieren</button><button id="forgetOnline">Konto wechseln</button></div>
    </div>
    <div class="online-card">
      <h3>☁ Cloud-Spielstand</h3>
      <p id="cloudStatus" class="${conflict?'online-warn':''}">${safe(statusText())}</p>
      <label class="online-toggle"><input type="checkbox" id="autoCloud" ${autoEnabled()?'checked':''}> Nach Änderungen automatisch synchronisieren</label>
      <div class="online-row">
        <button class="primary" id="cloudSave">Jetzt speichern</button>
        <button id="cloudLoad">Cloud laden</button>
        ${conflict?'<button id="cloudForce">Lokalen Stand überschreiben lassen</button>':''}
      </div>
    </div>
    <div class="online-card">
      <h3>♟ Mehrspieler</h3>
      <p>Private, rundenbasierte Partien für 2–4 Spieler. Nur der Spieler am Zug darf Aktionen ausführen; Regeln und Zufall werden serverseitig geprüft.</p>
      <div class="online-row">
        <button class="primary" id="roomCreate">Neue private Runde</button>
        <input id="roomCode" maxlength="19" placeholder="16-stelliger Raumcode">
        <button id="roomJoin">Beitreten</button>
      </div>
      <div id="roomList"><p class="muted">Runden werden geladen …</p></div>
    </div>`);
  $('#copyRecovery').onclick=()=>copyText(key);
  $('#forgetOnline').onclick=forgetAccount;
  $('#autoCloud').onchange=e=>{setAuto(e.target.checked);if(e.target.checked)queueCloud()};
  $('#cloudSave').onclick=()=>cloudPut(false,false);
  $('#cloudLoad').onclick=cloudGet;
  if($('#cloudForce'))$('#cloudForce').onclick=()=>{if(confirm('Lokalen Spielstand bewusst über die aktuelle Cloud-Version schreiben?'))cloudPut(true,false)};
  $('#roomCreate').onclick=createRoom;
  $('#roomJoin').onclick=joinRoom;
  loadRooms();
}

async function loadRooms(){
  const box=$('#roomList');if(!box)return;
  try{
    const d=await api({op:'rooms.list'});
    if(!$('#roomList'))return;
    box.innerHTML=d.rooms.length
      ?d.rooms.map(r=>`<button class="room-entry" data-room="${r.id}"><span><b>${safe(r.code)}</b><small>${safe(r.state.status)} · ${r.state.players.length} Spieler · Runde ${r.state.round}</small></span><b>Öffnen ›</b></button>`).join('')
      :'<p class="muted">Noch keine privaten Runden.</p>';
    box.querySelectorAll('[data-room]').forEach(b=>b.onclick=async()=>{
      try{const d=await api({op:'rooms.get',room:b.dataset.room});openRoom(d.room)}
      catch(e){toast(e.message)}
    });
  }catch(e){box.innerHTML='<p class="online-warn">'+safe(e.message)+'</p>'}
}

async function createRoom(){
  try{const d=await api({op:'rooms.create'});openRoom(d.room)}
  catch(e){toast(e.message)}
}

async function joinRoom(){
  const code=($('#roomCode')?.value||'').replace(/[\s-]/g,'').toUpperCase();
  try{const d=await api({op:'rooms.join',code});openRoom(d.room)}
  catch(e){toast(e.message)}
}

function stopRoom(){
  clearInterval(pollTimer);clearInterval(clockTimer);
  pollTimer=clockTimer=null;
}

function openRoom(room){
  activeRoom=room;
  renderRoom();
  stopRoom();
  pollTimer=setInterval(pollRoom,15000);
  clockTimer=setInterval(updateClock,1000);
}

async function pollRoom(){
  if(!activeRoom)return;
  try{
    const d=await api({op:'rooms.get',room:activeRoom.id});
    if(!activeRoom)return;
    if(d.room.version!==activeRoom.version){activeRoom=d.room;renderRoom()}
  }catch(e){
    if(e.status===403){stopRoom();activeRoom=null;openHub()}
  }
}

function updateClock(){
  const el=$('#roomClock');if(!el||!activeRoom)return;
  const d=activeRoom.state.deadline;
  if(!d){el.textContent='';return}
  const sec=Math.max(0,Math.ceil((d-Date.now())/1000));
  el.textContent=sec?Math.floor(sec/60)+':'+String(sec%60).padStart(2,'0'):'abgelaufen';
  const timeout=$('#timeoutRoom');if(timeout)timeout.disabled=Date.now()<d;
}

async function roomAction(action,arg={}){
  if(!activeRoom)return;
  try{
    const d=await api({op:'rooms.action',room:activeRoom.id,version:activeRoom.version,action,arg});
    activeRoom=d.room;
    renderRoom();
  }catch(e){
    if(e.status===409){toast('Runde wurde aktualisiert.');await pollRoom()}
    else toast(e.message);
  }
}

async function leaveRoom(){
  if(!activeRoom)return;
  const g=activeRoom.state;
  try{
    if(g.status==='lobby'){
      await api({op:'rooms.leave',room:activeRoom.id,version:activeRoom.version});
    }else if(g.status==='playing'){
      if(!confirm('Partie wirklich aufgeben?'))return;
      await roomAction('resign');
    }
    stopRoom();activeRoom=null;openHub();
  }catch(e){toast(e.message)}
}

function playerValue(g,p){
  return p.score*100+p.cash-p.debt+g.districts.filter(d=>d.owner===p.id).length*500;
}

function actionButton(id,label,detail=''){
  return `<button data-ract="${id}">${label}${detail?'<small>'+detail+'</small>':''}</button>`;
}

function renderRoom(){
  if(!activeRoom)return;
  const r=activeRoom,g=r.state,me=g.players.find(p=>p.id===account.id);
  const current=g.status==='playing'?g.players[g.turn]:null;
  const isMine=current?.id===account.id,host=r.host===account.id;
  const players=g.players.map(p=>{
    const owned=g.districts.filter(d=>d.owner===p.id).length;
    return `<div class="mp-player ${current?.id===p.id?'active':''} ${p.resigned?'resigned':''}">
      <div><b>${safe(p.name)}</b>${current?.id===p.id?'<span class="turn-badge">AM ZUG</span>':''}</div>
      <small>$${p.cash} · ${p.score} Pkt · Fahndung ${p.heat}% · Crew ${p.crew} · Reviere ${owned} · Wert ${playerValue(g,p)}</small>
    </div>`;
  }).join('');

  let body=`<div class="room-head"><div><span class="eyebrow">PRIVATE RUNDE</span><h3>${safe(r.code)}</h3></div><button id="copyRoom">Code kopieren</button></div>
    <div class="online-row room-meta"><span>Runde <b>${g.round}/12</b></span><span>${current?'Aktiv: <b>'+safe(current.name)+'</b>':'Lobby'}</span><span id="roomClock"></span></div>
    <div class="mp-players">${players}</div>`;

  if(g.status==='lobby'){
    body+=`<div class="online-card"><p>Teile den Raumcode mit bis zu drei weiteren Spielern. Start ab 2 Spielern.</p>
      ${host?'<button class="primary online-wide" id="startRoom" '+(g.players.length<2?'disabled':'')+'>Partie starten</button>':'<p>Warte auf den Gastgeber …</p>'}
    </div>`;
  }else if(g.status==='playing'){
    body+=`<div class="online-card"><h3>${isMine?'Dein Zug':'Zug von '+safe(current.name)}</h3>
      <p>${isMine?'Du hast '+me.ap+' Aktionen. Nach 3 Minuten kann jeder den abgelaufenen Zug weitergeben.':'Du kannst die Lage beobachten; Änderungen werden automatisch abgefragt.'}</p>
      <div class="mp-actions">
        ${actionButton('job','Kleiner Auftrag','+$120 · +1 Punkt')}
        ${actionButton('hire','Gangster anwerben','$240')}
        ${actionButton('weapon','Bewaffnung verbessern','ab $220')}
        ${actionButton('car','Ford kaufen','$480')}
        ${actionButton('buy','Alkohol kaufen','$65')}
        ${actionButton('sell','Alkohol verkaufen','+$145 · +1 Punkt')}
        ${actionButton('bribe','Polizei bestechen','$120')}
        ${actionButton('loan','Kredit aufnehmen','+$600 / $780 Schuld')}
        ${actionButton('repay','Kredit tilgen','bis $500')}
        ${actionButton('bank','Bank überfallen','riskant')}
        ${actionButton('end','Zug beenden')}
      </div></div>
      <div class="online-card"><h3>Reviere</h3><div class="district-grid">
        ${g.districts.map((d,i)=>{
          const owner=g.players.find(p=>p.id===d.owner),own=d.owner===account.id;
          return `<button data-district="${i}" ${own?'disabled':''}><b>${safe(d.name)}</b><small>${own?'Dein Revier':owner?'Besetzt: '+safe(owner.name)+' · Angriff $150':'Frei · Übernahme $350'}</small></button>`;
        }).join('')}
      </div></div>`;
    body+='<button id="timeoutRoom" class="online-wide" '+(g.deadline&&Date.now()<g.deadline?'disabled':'')+'>Abgelaufenen Zug weitergeben</button>';
  }else{
    const winners=(g.winners||[]).map(id=>g.players.find(p=>p.id===id)?.name).filter(Boolean);
    body+=`<div class="online-card"><h3>Partie beendet</h3><p>${winners.length?'Sieger: <b>'+winners.map(safe).join(', ')+'</b>':'Runde beendet.'}</p><p>Wertung: Punkte × 100 + Bargeld − Schulden + $500 je Revier.</p></div>`;
  }

  body+=`<div class="online-card"><h3>Chronik</h3><div class="mp-log">${g.log.map(x=>'<div>'+safe(x)+'</div>').join('')}</div></div>
    <div class="online-row"><button id="roomBack">← Online-Menü</button><button id="roomRefresh">Jetzt aktualisieren</button><button id="roomLeave">${g.status==='playing'?'Aufgeben':'Runde verlassen'}</button></div>`;

  onlineShow('Mehrspieler',body);
  updateClock();
  $('#copyRoom').onclick=()=>copyText(r.code);
  $('#roomBack').onclick=()=>{stopRoom();activeRoom=null;openHub()};
  $('#roomRefresh').onclick=pollRoom;
  $('#roomLeave').onclick=leaveRoom;
  if($('#startRoom'))$('#startRoom').onclick=()=>roomAction('start');
  if($('#timeoutRoom'))$('#timeoutRoom').onclick=()=>roomAction('timeout');
  modalBody.querySelectorAll('[data-ract]').forEach(b=>{
    b.disabled=!isMine;
    b.onclick=()=>roomAction(b.dataset.ract);
  });
  modalBody.querySelectorAll('[data-district]').forEach(b=>{
    b.disabled=b.disabled||!isMine;
    b.onclick=()=>roomAction('district',{index:+b.dataset.district});
  });
}

const originalPersist=persist;
persist=function(){originalPersist();queueCloud()};
const originalSave=save;
save=function(){originalSave();queueCloud()};

const onlineButton=$('#online');
if(onlineButton)onlineButton.onclick=openHub;
modal.addEventListener('close',()=>{if(activeRoom){stopRoom();activeRoom=null}});

if(key)refreshProfile().then(()=>{
  if(autoEnabled()&&!conflict&&serverVersion===0)queueCloud();
}).catch(()=>{});
})();