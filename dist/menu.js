(()=>{
'use strict';
const overlay=$('#mainMenu'),content=$('#mainMenuContent'),menuButton=$('#gameMenu');
if(!overlay||!content)return;
const cleanName=v=>(v||'').replace(/[<>"'&]/g,'').trim().slice(0,18)||'Du';
const closeSituations=()=>{
  try{if(modal.open)modal.close()}catch(e){}
  try{if(battle.open)battle.close()}catch(e){}
};
const archiveCurrent=(key='uw1931-backup')=>{
  if(!campaignActive)return;
  try{localStorage.setItem(key,JSON.stringify(S))}catch(e){}
};
const deactivateCampaign=(archiveKey='uw1931-backup')=>{
  archiveCurrent(archiveKey);
  try{localStorage.removeItem('uw1931')}catch(e){}
  campaignActive=false;
  closeSituations();
};
const saveSummary=()=>{
  if(!campaignActive)return '';
  const territories=S.empire?.districts?.filter?.(d=>d.owner==='player').length??(Array.isArray(S.territories)?S.territories.reduce((a,n)=>a+(Number(n)||0),0):0);
  const mode=S.empire?.endless?'Endlosmodus':S.empire?.ng?'New Game+ '+S.empire.ng:'Kampagne';
  return `<div class="menu-save-card"><b>Aktive Kampagne</b><span>${esc(month())} ${S.y} · ${esc(S.crew?.[0]?.name||'Du')} · ${esc(mode)}</span><div class="menu-save-grid"><div><small>Bargeld</small><strong>${cash(S.cash)}</strong></div><div><small>Rang</small><strong>${esc(rankName())}</strong></div><div><small>Bezirke</small><strong>${territories}</strong></div></div></div>`;
};
function showMainMenu(message=''){
  closeSituations();
  overlay.hidden=false;
  document.body.classList.add('menu-open');
  content.innerHTML=`${message?`<p class="menu-note">${esc(message)}</p>`:''}${saveSummary()}
    ${campaignActive?`<button class="menu-primary" id="continueGame">Fortsetzen <span>Zurück in deine laufende Kampagne</span></button>`:''}
    <button class="menu-secondary" id="createGame">Neues Spiel erstellen <span>Neue Einzelspieler-Kampagne ab Januar 1931</span></button>
    <button class="menu-secondary" id="menuOnline">Online & Mehrspieler <span>Cloud-Spielstand und private Partien</span></button>
    ${campaignActive?`<div class="menu-divider"></div><button class="menu-danger" id="endCampaign">Aktive Partie beenden <span>Spielstand lokal sichern und Kampagne abschließen</span></button>`:''}`;
  $('#continueGame')?.addEventListener('click',resumeGame);
  $('#createGame')?.addEventListener('click',showCreateGame);
  $('#menuOnline')?.addEventListener('click',()=>$('#online')?.click());
  $('#endCampaign')?.addEventListener('click',abortCampaign);
}
function showCreateGame(){
  const currentName=campaignActive?(S.crew?.[0]?.name||'Du'):'Du';
  content.innerHTML=`<div class="menu-create">
    <div class="menu-campaign-card">
      <div><small>Modus</small><b>Einzelspieler</b></div>
      <div><small>Zeitraum</small><b>1931–1933</b></div>
      <div><small>Startkapital</small><b>$450</b></div>
      <div><small>Ziel</small><b>100 Punkte + 2 Spezialaufträge</b></div>
    </div>
    <label>Dein Name / Spitzname<input id="playerAlias" maxlength="18" autocomplete="off" value=""></label>
    <label>Schwierigkeit<select id="gameDifficulty">
      <option value="easy">Leicht</option>
      <option value="normal" selected>Normal</option>
      <option value="hard">Schwer</option>
      <option value="hardcore">1931 Hardcore</option>
    </select></label>
    <p class="difficulty-help" id="difficultyHelp">Normal: ausgewogene Wirtschaft, Rivalen, Polizei und Kämpfe.</p>
    <p class="menu-note">Das Spiel wird automatisch lokal gespeichert. Wenn bereits eine Kampagne läuft, wird sie beim Start der neuen Partie vorher als Sicherung abgelegt.</p>
    <button class="menu-primary" id="startGame">Spiel starten <span>Januar 1931 · South Side</span></button>
    <button class="menu-secondary" id="backMenu">Zurück zum Hauptmenü</button>
  </div>`;
  const alias=$('#playerAlias'),difficulty=$('#gameDifficulty'),help=$('#difficultyHelp');if(alias){alias.value=currentName;setTimeout(()=>alias.focus(),0)}
  const diffText={easy:'Leicht: +15 % Einnahmen, schwächere Rivalen, Polizei und Gegner.',normal:'Normal: ausgewogene Wirtschaft, Rivalen, Polizei und Kämpfe.',hard:'Schwer: weniger Einnahmen, stärkere Rivalen, Polizei und Gegner.',hardcore:'1931 Hardcore: höchste Gefahr; gefallene Crewmitglieder können dauerhaft sterben.'};
  if(difficulty)difficulty.onchange=()=>{if(help)help.textContent=diffText[difficulty.value]||diffText.normal};
  $('#backMenu').onclick=()=>showMainMenu();
  $('#startGame').onclick=()=>{
    if(campaignActive&&!confirm('Neue Kampagne starten? Die aktuelle Partie wird vorher lokal gesichert.'))return;
    archiveCurrent('uw1931-backup');
    const chosenDifficulty=difficulty?.value||'normal';
    S=upgrade(fresh());
    if(window.UNTERWELT_EMPIRE?.ensure)window.UNTERWELT_EMPIRE.ensure(S);
    S.empire=S.empire||{};S.empire.difficulty=chosenDifficulty;
    S.crew[0].name=cleanName(alias?.value);
    S.log=[`Januar 1931: ${S.crew[0].name} wird aus dem Gefängnis entlassen.`];
    selected=S.loc;
    campaignActive=true;
    try{localStorage.setItem('uw1931',JSON.stringify(S))}catch(e){}
    save();
    resumeGame();
  };
}
function resumeGame(){
  if(!campaignActive)return showCreateGame();
  overlay.hidden=true;
  document.body.classList.remove('menu-open');
  render();
  if(S.encounter)drawTactics();
  else if(S.control)controlUI();
  else if(S.cards)cardUI();
  else if(S.over)endScreen();
}
function abortCampaign(){
  if(!campaignActive)return showMainMenu();
  if(!confirm('Diese Partie wirklich vorzeitig beenden? Der aktuelle Stand wird als lokale Sicherung behalten.'))return;
  deactivateCampaign('uw1931-aborted-backup');
  S=upgrade(fresh());selected=S.loc;
  render();
  showMainMenu('Die Partie wurde beendet. Du kannst jetzt ein neues Spiel erstellen.');
}
function pauseMenu(){
  if(!campaignActive)return showMainMenu();
  show('Spielmenü',`<div class="list">
    <div class="item"><b>${esc(month())} ${S.y}</b><span>${esc(S.crew?.[0]?.name||'Du')} · ${esc(rankName())} · ${cash(S.cash)}</span></div>
    <button id="pauseContinue">Weiterspielen</button>
    <button id="pauseMain">Zum Hauptmenü</button>
    <button id="pauseEnd">Partie vorzeitig beenden</button>
  </div>`);
  $('#pauseContinue').onclick=()=>modal.close();
  $('#pauseMain').onclick=()=>{modal.close();showMainMenu()};
  $('#pauseEnd').onclick=()=>{modal.close();abortCampaign()};
}
menuButton?.addEventListener('click',pauseMenu);

restart=function(){closeSituations();showMainMenu();showCreateGame()};
endScreen=function(){
  const win=S.over==='win';
  show(win?'Chef der Unterwelt!':'Kampagne beendet',`<div class="item"><h3>${win?'Die Stadt gehört dir.':S.debt>15000?'Überschuldet':'Januar 1934'}</h3><span>${esc(rankName())} · ${S.score} Punkte · ${cash(S.cash)}</span></div><button id="resultMenu">Zum Hauptmenü</button><button id="resultNew">Neues Spiel erstellen</button>`);
  $('#resultMenu').onclick=()=>{
    deactivateCampaign('uw1931-last-finished');
    S=upgrade(fresh());selected=S.loc;modal.close();render();showMainMenu(win?'Kampagne gewonnen und archiviert.':'Kampagne beendet und archiviert.');
  };
  $('#resultNew').onclick=()=>{
    deactivateCampaign('uw1931-last-finished');
    S=upgrade(fresh());selected=S.loc;modal.close();render();showMainMenu();showCreateGame();
  };
};

window.openUnterweltMainMenu=showMainMenu;
window.resumeUnterweltGame=resumeGame;
showMainMenu();
})();