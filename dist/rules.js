/* Rules revision 8. All encounter state is serializable; legacy saves are retained. */
const legacy={save,render,action,options,tab,drawMap,drawPanel,reward,hire,weaponShop,equip,carShop};
const clamp=(v,a,b)=>Math.min(b,Math.max(a,Number.isFinite(v)?v:a));
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function upgrade(s){
 const base=fresh();if(!s||typeof s!=='object')throw Error('Kein Spielstand');
 s={...base,...s};for(const k of ['cash','score','heat','steps','max','debt','invest','booze','fake','shops'])s[k]=clamp(s[k],0,10000000);
 s.heat=clamp(s.heat,0,100);s.score=clamp(s.score,0,100);s.m=clamp(s.m,1,12);if(Number(s.y)>=1925&&Number(s.y)<=1928)s.y=Number(s.y)+6;s.y=clamp(s.y,1931,1934);
 s.hotel=clamp(s.hotel,0,3);if(!C[s.car])s.car='none';if(!B[s.loc])s.loc='hideout';
 if(!Array.isArray(s.crew)||!s.crew.length)s.crew=base.crew;
 s.crew=s.crew.slice(0,12).map((c,i)=>({...c,id:Number.isSafeInteger(c.id)?c.id:i+1,name:String(c.name||'Gangster').replace(/[<>"'&]/g,'').slice(0,40),hp:clamp(c.hp,0,100),skill:clamp(c.skill,0,95),power:clamp(c.power,0,95),intel:clamp(c.intel,0,95),weapon:W[c.weapon]?c.weapon:'fist',jailed:!!c.jailed,jailMonths:c.jailed?clamp(c.jailMonths||3,1,6):0}));
 s.stock={...base.stock,...s.stock};for(const k of Object.keys(W))if(k!=='fist')s.stock[k]=clamp(s.stock[k],0,100);
 s.owned=Array.isArray(s.owned)?s.owned.filter(x=>['shop',...base.rivals.map((r,i)=>'rival'+i)].includes(x)):s.shops?['shop']:[];
 s.owned=[...new Set(s.owned)];s.shops=s.owned.length;
 s.rivals=Array.isArray(s.rivals)&&s.rivals.length===3?s.rivals.map((r,i)=>[base.rivals[i][0],clamp(r[1],5,80),clamp(r[2],0,4)]):base.rivals;
 s.log=Array.isArray(s.log)?s.log.slice(0,60).map(x=>String(x).replace(/[<>"'&]/g,'')):[];
 s.ap=clamp(s.ap??12,0,12);s.market=s.market||{price:125,supply:8,demand:8};s.used=s.used||{};s.revision=8;
 return s;
}
try{if(S.revision!==8)localStorage.setItem('uw1931-before-v3',JSON.stringify(S));S=upgrade(S)}catch(e){S=upgrade(fresh())}selected=S.loc;
function persist(){try{localStorage.setItem('uw1931',JSON.stringify(S))}catch(e){toast('Speichern fehlgeschlagen. Bitte Spielstand exportieren.')}}
save=function(){S.heat=clamp(S.heat,0,100);S.cash=Math.max(0,S.cash);S.shops=S.owned.length;checkEnd();persist();render()};
function checkEnd(){if(S.over)return;if(S.score>=100&&S.mayorDone&&S.transportDone)S.over='win';else if(S.y>=1934||S.debt>15000)S.over='lose'}
function blocked(){return S.over||S.encounter||S.control||S.cards}
function useAP(cost=1){if(blocked()){toast('Schließe zuerst die laufende Situation ab.');return false}if(!crew().some(c=>c.hp>0)){toast('Die Bande ist nicht einsatzfähig. Warte einen Monat.');return false}if(S.ap<cost){toast('Keine Aktionspunkte. Beende den Monat.');return false}S.ap-=cost;persist();return true}
render=function(){legacy.render();$('#steps').textContent=S.steps+' Wege · '+S.ap+' AP';$('#endTurn').disabled=!!blocked();$('.close').hidden=!!(S.control||S.cards);document.querySelectorAll('[data-act]').forEach(b=>b.disabled=!!blocked());};
drawPanel=function(){legacy.drawPanel();const hint=$('#panel .hint');if(hint)hint.textContent='Aktionen kosten 1 AP, große Überfälle 2 AP. Kaufen/Anwerben/Ausrüsten kostet beim Bestätigen 1 AP. Reisen kostet entfernungsabhängige Wegepunkte.'};
const quiet=new Set(['career','weapons','equip','cars','hire','train','blackjack','help','backup','cancelhotel','sellcar']);
options=function(k){let html=legacy.options(k);if(k==='hideout')html+=bt('Anleitung','help')+bt('Spielstand & Neustart','backup');if(k==='loan')html+=bt('Kredit zurückzahlen','repay','bis $500');if(k==='hotel')html+=bt('Hotel kündigen','cancelhotel');if(k==='cars')html+=bt('Fahrzeug verkaufen','sellcar');if(k==='fake')html+=bt('Blüten ausgeben','spendfake','$80 pro Versuch');return html};
action=function(a){
 if(blocked())return;
 if(a==='backup')return backups();if(a==='help')return help();if(a==='blackjack')return blackjack();
 if(a==='hire'&&S.crew.length>=(S.hotel?S.hotel*3+1:1))return toast('Keine freien Hotelplätze.');
 if(a==='protect'&&S.owned.includes('shop'))return toast('Dieser Laden zahlt bereits Schutzgeld.');
 if(a==='borrow'&&S.debt>1700)return toast('Kreditlimit erreicht: maximal $3.000 Schulden.');
 if(a==='mayorfight'&&(!S.mayorTip||S.mayorDone)||a==='transportfight'&&(!S.transportTip||S.transportDone))return toast('Auftrag nicht verfügbar.');
 if(a==='booze'&&(S.booze>=({none:2,ford:5,buick:8,cadillac:12}[S.car])||!S.market.supply))return toast('Laderaum voll oder Angebot erschöpft.');
 if(a==='sell'&&(!S.booze||!S.market.demand))return toast('Keine Ware oder Nachfrage.');
 if(!quiet.has(a)&&!useAP(['bank','safe','mayorfight','transportfight','posttrain'].includes(a)?2:1))return;
 if(a==='repay'){let n=Math.min(500,S.cash,S.debt);S.cash-=n;S.debt-=n;note(cash(n)+' Kredit getilgt.');save();return toast('Zurückgezahlt: '+cash(n))}
 if(a==='cancelhotel'){if(S.crew.length>1)return toast('Entlasse erst die zusätzlichen Mitglieder.');S.hotel=0;save();return toast('Hotel gekündigt.')}
 if(a==='sellcar'){if(S.car==='none')return;S.cash+=Math.floor(C[S.car][2]*.55);S.car='none';S.max=8;S.steps=Math.min(8,S.steps);save();return}
 if(a==='spendfake'){if(S.fake<80)return toast('Mindestens $80 Blüten nötig.');S.fake-=80;if(chance(.65)){S.cash+=55;note('Blüten im Warenhandel umgesetzt.')}else{S.heat+=20;note('Falschgeld entdeckt.')}save();return}
 if(a==='sell'){S.booze--;S.market.demand--;return reward(S.market.price,1,2,'Alkohol verkauft')}
 if(a==='booze'){let before=S.booze;legacy.action(a);if(S.booze>before)S.market.supply--;return save()}
 if(a.startsWith('hotel')){let level=+a.at(-1);if(S.crew.length>level*3+1)return toast('Zu wenige Plätze.');if(S.hotel===level)return toast('Bereits gemietet.');if(!pay([0,100,250,500][level]))return;}
 if(a==='collect'){if(S.used.collect)return toast('Diesen Monat bereits eingetrieben.');S.used.collect=true;}
 legacy.action(a);save();
};
protect=function(){if(S.score<45)return toast('Rang Ganove erforderlich.');if(S.owned.includes('shop'))return;if(chance(.55)){S.owned.push('shop');reward(120,4,14,'Krämerladen zahlt Schutzgeld')}else beginFight('Ladenbesitzer',3)};
reward=function(n,p,h,msg){legacy.reward(n,p,h,msg)};
visit=function(k){if(blocked())return;if(k!==S.loc){if(!crew().some(c=>c.hp>0))return toast('Keine einsatzfähigen Mitglieder.');let cost=Math.max(1,Math.ceil((Math.abs(B[k][3]-B[S.loc][3])+Math.abs(B[k][4]-B[S.loc][4]))/35));if(S.steps<cost)return toast('Nicht genug Wegepunkte ('+cost+' nötig).');S.steps-=cost;S.loc=selected=k;policeCheck()}save()};
arrest=function(){let c=crew().at(-1);if(c){c.jailed=true;c.jailMonths=3;S.heat=Math.max(10,S.heat-25);note(c.name+' für drei Monate inhaftiert.');toast(c.name+' verhaftet.')}};
policeCheck=function(){if(Math.random()<S.heat/220){S.control={price:100+S.heat*2};persist();controlUI()}};
function controlUI(){show('Polizeikontrolle',`<p>Du musst dich entscheiden. Diese Kontrolle bleibt auch nach einem Neustart bestehen.</p><div class="grid2"><button id="payCop">Bestechen ${cash(S.control.price)}</button><button id="runCop">Fliehen</button><button id="yieldCop">Stellen</button></div>`);$('.close').hidden=true;$('#payCop').onclick=()=>{if(!pay(S.control.price))return;S.heat-=15;finishControl()};$('#runCop').onclick=()=>{if(!chance(S.car==='none'?.35:.65))arrest();else S.heat+=8;finishControl()};$('#yieldCop').onclick=()=>{arrest();finishControl()}}
function finishControl(){S.control=null;modal.close();save()}
modal.addEventListener('cancel',e=>{if(S.control||S.cards)e.preventDefault()});battle.addEventListener('cancel',e=>e.preventDefault());
$('.close').onclick=()=>{if(!S.control&&!S.cards)modal.close()};
nextMonth=function(){if(blocked())return;let wages=Math.max(0,S.crew.length-1)*60,rent=[0,100,250,500][S.hotel],income=S.owned.length*90+Math.floor(S.invest*.08);if(!confirm(`Monat beenden? Einnahmen ${cash(income)}, Löhne ${cash(wages)}, Miete ${cash(rent)}, Zinsen ${cash(Math.ceil(S.debt*.08))}.`))return;
 S.cash+=income-wages-rent;S.debt+=Math.ceil(S.debt*.08);if(S.cash<0){S.debt-=S.cash;S.cash=0}S.crew.forEach(c=>{c.hp=Math.min(100,c.hp+15);if(c.jailed&&--c.jailMonths<=0){c.jailed=false;note(c.name+' aus Haft entlassen.')}});S.heat=Math.max(0,S.heat-7);if(++S.m===13){S.m=1;S.y++}S.ap=12;S.steps=S.max=C[S.car][1];S.used={};S.market={price:rnd(95,165),supply:rnd(4,10),demand:rnd(3,8)};
 S.rivals.forEach((r,i)=>{if(r[2]<=0)return;r[1]=Math.min(80,r[1]+rnd(1,3));if(S.owned.length&&Math.random()<.2){let key=S.owned.pop();note(r[0]+' verdrängt dich aus '+(key==='shop'?'dem Krämerladen':'einem Revier')+'.');if(key.startsWith('rival'))S.rivals[+key.slice(5)][2]=1;}});
 note(`Abrechnung: ${cash(income)} Einnahmen, ${cash(wages+rent)} Kosten.`);save();
};$('#endTurn').onclick=nextMonth;
endScreen=function(){show(S.over==='win'?'Chef der Unterwelt!':'Kampagne beendet',`<p>${esc(rankName())} · ${S.score} Punkte · ${cash(S.cash)}. ${S.debt>15000?'Überschuldung.':''}</p><button id="restart">Neues Spiel</button>`);$('#restart').onclick=()=>restart()};
function restart(){if(!confirm('Neu beginnen? Der aktuelle Stand wird vorher als Sicherung auf diesem Gerät behalten.'))return;localStorage.setItem('uw1931-backup',JSON.stringify(S));S=upgrade(fresh());selected=S.loc;modal.close();if(battle.open)battle.close();save()}
function help(){show('So spielst du',`<p>Jeder Monat gibt dir 12 Aktionspunkte. Reisen kostet je nach Entfernung Wegepunkte. Kaufe zuerst Ausrüstung und ein Zimmer, dann rekrutiere deine Bande.</p><p>Unterkünfte: 4 / 7 / 10 Plätze. Löhne: $60 je zusätzlichem Mitglied und Monat. Schulden wachsen monatlich um 8 %. Über $15.000 endet die Kampagne.</p><p>Erreiche bis Januar 1928 100 Punkte und erledige beide Spezialaufträge. Hinweise bekommst du in der Kneipe. Geschick verbessert Trefferchancen, Kraft den Nahkampf, Intelligenz ermöglicht Tresoraufträge.</p><p>Im Kampf: eigene Figur wählen; Nachbarfeld zum Bewegen oder roten Gegner zum Angreifen antippen. Jede Figur handelt einmal je Runde. Deckung senkt Trefferchancen.</p><p>Lokal wird weiterhin automatisch gespeichert. Cloud-Synchronisierung und private Mehrspieler-Partien findest du oben unter Online. Export-Sicherungen bleiben zusätzlich verfügbar.</p>`)}
function backups(){show('Spielstand',`<p>Revision 7 · lokaler Spielstand, Cloud und neue Unterwelt-Karte</p><button id="exportSave">Sicherung herunterladen</button><p><label>Sicherung importieren <input id="importSave" type="file" accept="application/json,.json"></label></p><button id="resetSave">Neues Spiel</button>`);$('#exportSave').onclick=()=>{let url=URL.createObjectURL(new Blob([JSON.stringify(S)],{type:'application/json'})),a=document.createElement('a');a.href=url;a.download='unterwelt-spielstand.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)};$('#importSave').onchange=async e=>{try{const file=e.target.files[0];if(!file||file.size>2000000)throw Error();let raw=JSON.parse(await file.text());if(raw.v!==2||!Array.isArray(raw.crew)||raw.encounter||raw.control||raw.cards)throw Error();const next=upgrade(raw);if(!confirm('Aktuellen Stand durch diese Sicherung ersetzen?'))return;if(campaignActive)localStorage.setItem('uw1931-backup',JSON.stringify(S));campaignActive=true;S=next;selected=S.loc;modal.close();save()}catch(err){toast('Sicherung ungültig oder enthält eine laufende Begegnung.')}};$('#resetSave').onclick=restart}
/* Card game: a shuffled finite deck, persisted before each decision. */
const total=hand=>{let n=hand.reduce((s,c)=>s+Math.min(10,c),0),aces=hand.filter(c=>c===1).length;while(aces--&&n+10<=21)n+=10;return n};
blackjack=function(){if(!S.cards){show('Blackjack',`<p>Ein Einsatz kostet 1 AP. Gleichstand gibt den Einsatz zurück.</p>${[25,50,100,250].map(n=>`<button data-bet="${n}">${cash(n)}</button>`).join('')}`);modalBody.querySelectorAll('[data-bet]').forEach(b=>b.onclick=()=>{let bet=+b.dataset.bet;if(S.cash<bet||!useAP())return;S.cash-=bet;let deck=Array.from({length:52},(_,i)=>i%13+1);for(let i=51;i>0;i--){let j=rnd(0,i);[deck[i],deck[j]]=[deck[j],deck[i]]}S.cards={bet,deck,hand:[deck.pop(),deck.pop()],dealer:[deck.pop(),deck.pop()]};persist();cardUI()});}else cardUI()};
function cardUI(){let g=S.cards;show('Blackjack',`<p>Deine Karten: ${g.hand.join(' · ')} → ${total(g.hand)}</p><p>Bank: ${g.dealer[0]} · verdeckt</p><button id="hit">Karte ziehen</button><button id="stand">Stehen bleiben</button>`);$('.close').hidden=true;$('#hit').onclick=()=>{g.hand.push(g.deck.pop());persist();if(total(g.hand)>21)settleCards();else cardUI()};$('#stand').onclick=settleCards}
function settleCards(){let g=S.cards;while(total(g.dealer)<17)g.dealer.push(g.deck.pop());let p=total(g.hand),d=total(g.dealer),factor=p>21?0:d>21||p>d?2:p===d?1:0;S.cash+=g.bet*factor;S.cards=null;modal.close();note('Blackjack: '+(factor===2?'gewonnen':factor===1?'Gleichstand':'verloren'));save();show('Blackjack-Ergebnis',`<p>Du ${p} · Bank ${d}. ${factor===2?'Gewonnen':factor===1?'Gleichstand':'Verloren'}.</p>`)}
training=function(){show('Training',`<p>Eine Einheit kostet $120 und 1 AP.</p>${crew().map(c=>`<div class="item"><b>${esc(c.name)}</b>${['skill','power','intel'].map((k,i)=>`<button data-training="${c.id}|${k}">${['Geschick','Kraft','Intelligenz'][i]} ${c[k]}</button>`).join('')}</div>`).join('')}`);modalBody.querySelectorAll('[data-training]').forEach(b=>b.onclick=()=>{let [id,k]=b.dataset.training.split('|'),c=S.crew.find(c=>c.id==id);if(c[k]>=95||S.cash<120||!useAP())return;S.cash-=120;c[k]=Math.min(95,c[k]+6);note(c.name+' trainiert '+k);save();modal.close()})};
/* Purchase dialogs must charge AP at the transaction, not when opened. */
for(const [key,selector] of [['hire','[data-hire]'],['weaponShop','[data-w]'],['equip','[data-e]'],['carShop','[data-c]']]){window[key]=function(){legacy[key]();modalBody.querySelectorAll(selector).forEach(b=>{const run=b.onclick;b.onclick=()=>{if(!useAP())return;run();save()}})}}
tab=function(t){legacy.tab(t);if(t==='families')modalBody.querySelectorAll('[data-war]').forEach(b=>b.onclick=()=>{let i=+b.dataset.war,r=S.rivals[i];if(r[2]<=0||S.owned.includes('rival'+i))return toast('Dieses Revier gehört bereits dir.');if(!useAP(2))return;modal.close();beginFight(r[0],Math.max(2,Math.ceil(r[1]/12)))});if(t==='crew'){modalBody.querySelectorAll('.item').forEach((el,i)=>{let c=S.crew[i];if(c.id===1)return;let b=document.createElement('button');b.textContent='Entlassen';b.onclick=()=>{if(blocked()||!confirm(c.name+' entlassen?'))return;if(c.weapon!=='fist')S.stock[c.weapon]++;S.crew.splice(i,1);save();tab('crew')};el.append(b)})}if(t==='journal'){let b=document.createElement('button');b.textContent='Anleitung & Sicherung';b.onclick=backups;modalBody.prepend(b)}};
/* Persisted tactical grid. No callbacks are needed to resume an encounter. */
beginFight=function(name,num){if(S.encounter||!crew().some(c=>c.hp>0))return toast('Keine kampffähige Bande.');S.encounter={name,units:crew().filter(c=>c.hp>0).slice(0,10).map((c,i)=>({id:c.id,x:i%2,y:Math.floor(i/2),acted:false})),enemies:Array.from({length:num},(_,i)=>({id:i,x:5-i%2,y:Math.floor(i/2),hp:rnd(35,65),damage:rnd(7,14)})),cover:[[2,1],[3,3]],selected:null,log:['Kampf beginnt.']};persist();drawTactics()};
function distance(a,b){return Math.abs(a.x-b.x)+Math.abs(a.y-b.y)}
function drawTactics(){let g=S.encounter;if(!g)return;$('#battleTitle').textContent=g.name;$('#battleBody').innerHTML=`<p>Figur wählen, dann bewegen oder angreifen. Gold = eigene Bande, Rot = Gegner. ▤ = Deckung.</p><div class="tactical">${Array.from({length:30},(_,i)=>{let x=i%6,y=Math.floor(i/6),u=g.units.find(u=>u.x===x&&u.y===y&&S.crew.find(c=>c.id===u.id)?.hp>0),e=g.enemies.find(e=>e.x===x&&e.y===y&&e.hp>0),c=u&&S.crew.find(c=>c.id===u.id);return `<button data-cell="${x}|${y}" class="${u?'ally':e?'enemy':''} ${u?.id===g.selected?'chosen':''}" aria-label="Feld ${x+1}, ${y+1}">${u?esc(c.name.slice(0,6))+'<small>'+c.hp+' LP'+(u.acted?' ✓':'')+'</small>':e?'Gegner<small>'+e.hp+' LP</small>':g.cover.some(p=>p[0]===x&&p[1]===y)?'▤':''}</button>`}).join('')}</div><p><button id="endRound">Runde beenden</button> <button id="fleeBattle">Flucht versuchen</button></p><div class="log">${g.log.slice(-5).map(esc).join('<br>')}</div>`;if(!battle.open)battle.showModal();battleBody.querySelectorAll('[data-cell]').forEach(b=>b.onclick=()=>cell(...b.dataset.cell.split('|').map(Number)));$('#endRound').onclick=enemyRound;$('#fleeBattle').onclick=()=>{if(chance(.5)){S.encounter=null;S.heat+=8;battle.close();save()}else{g.log.push('Flucht gescheitert.');enemyRound()}}}
function cell(x,y){let g=S.encounter,u=g.units.find(u=>u.x===x&&u.y===y&&S.crew.find(c=>c.id===u.id).hp>0);if(u){g.selected=u.id;persist();return drawTactics()}u=g.units.find(u=>u.id===g.selected);if(!u||u.acted)return;let c=S.crew.find(c=>c.id===u.id),e=g.enemies.find(e=>e.x===x&&e.y===y&&e.hp>0);if(e){let w=W[c.weapon];if(distance(u,e)>w[2])return toast('Außer Reichweite.');let cover=g.cover.some(p=>p[0]===e.x&&p[1]===e.y);if(Math.random()<.55+c.skill/220-(cover?.2:0)){let damage=rnd(Math.ceil(w[1]*.65),w[1])+(w[2]===1?Math.floor(c.power/10):0);e.hp=Math.max(0,e.hp-damage);g.log.push(c.name+' trifft für '+damage)}}else{if(distance(u,{x,y})!==1)return toast('Nur ein Nachbarfeld pro Aktion.');u.x=x;u.y=y}u.acted=true;if(g.enemies.every(e=>e.hp<=0))return finishBattle(true);persist();drawTactics()}
function enemyRound(){let g=S.encounter;for(const e of g.enemies.filter(e=>e.hp>0)){let alive=g.units.filter(u=>S.crew.find(c=>c.id===u.id).hp>0);if(!alive.length)break;let t=alive.sort((a,b)=>distance(e,a)-distance(e,b))[0],c=S.crew.find(c=>c.id===t.id);if(distance(e,t)<=3){let cover=g.cover.some(p=>p[0]===t.x&&p[1]===t.y);if(Math.random()<(cover?.4:.65)){let d=rnd(4,e.damage);c.hp=Math.max(0,c.hp-d);g.log.push(c.name+' verliert '+d+' LP.')}}else{let moves=[[e.x-1,e.y],[e.x+1,e.y],[e.x,e.y-1],[e.x,e.y+1]].filter(([x,y])=>x>=0&&x<6&&y>=0&&y<5&&!g.enemies.some(o=>o.hp>0&&o.x===x&&o.y===y)&&!alive.some(o=>o.x===x&&o.y===y));moves.sort((a,b)=>distance({x:a[0],y:a[1]},t)-distance({x:b[0],y:b[1]},t));if(moves.length)[e.x,e.y]=moves[0]}}
 if(!g.units.some(u=>S.crew.find(c=>c.id===u.id).hp>0))return finishBattle(false);g.units.forEach(u=>u.acted=false);persist();drawTactics()}
function finishBattle(win){let name=S.encounter.name;S.encounter=null;battle.close();if(!win){S.cash=Math.max(0,S.cash-200);crew().filter(c=>c.hp===0).forEach(c=>{c.hp=20;c.jailed=true;c.jailMonths=2});note('Kampf verloren.');return save()}
 let ri=S.rivals.findIndex(r=>r[0]===name);if(ri>=0){S.rivals[ri][2]=0;S.owned.push('rival'+ri);return reward(400,6,20,'Revier erobert')}
 const results={'Leibwächter':[1200,15,25],'Polizeieskorte':[2500,18,35],'Tresorwachen':[1300,9,25],'Bankwachen':[650,6,18],'Postzug-Eskorte':[1000,8,22],'Schuldner':[350,2,6],'Bewaffnete Gegner':[100,2,9],'Ladenbesitzer':[80,3,18],'Polizeiwache':[0,5,28]};
 if(name==='Leibwächter'){S.mayorDone=true;S.mayorTip=false;selected=S.loc='hideout'}if(name==='Polizeieskorte'){S.transportDone=true;S.transportTip=false;selected=S.loc='hideout'}if(name==='Ladenbesitzer'&&!S.owned.includes('shop'))S.owned.push('shop');if(name==='Polizeiwache'){let c=S.crew.find(c=>c.jailed);if(c){c.jailed=false;c.jailMonths=0}}reward(...(results[name]||[100,2,5]),name+' besiegt')}
const MAP_SPOTS={
 station:[51.6,11.7,18.0,5.0],
 fake:[81.2,15.6,18.0,5.0],
 cars:[13.4,21.8,20.0,5.0],
 mayor:[67.4,24.1,21.0,5.0],
 weapons:[34.2,27.6,22.0,5.2],
 bank:[49.0,35.2,15.0,5.0],
 casino:[85.8,42.7,22.0,6.5],
 pub:[12.8,49.8,18.0,5.2],
 hotel:[38.6,63.8,16.0,5.0],
 subway:[64.0,63.7,18.0,5.0],
 police:[85.7,68.9,23.0,5.0],
 shop:[13.5,72.7,21.0,5.0],
 loan:[57.8,78.1,18.0,5.0],
 hideout:[31.0,88.2,22.0,5.0],
 transport:[76.0,87.9,22.0,5.0]
};
drawMap=function(){
  map.querySelectorAll('.place').forEach(e=>e.remove());
  Object.entries(B).forEach(([k,b])=>{
    if(k==='mayor'&&!S.mayorTip||k==='transport'&&!S.transportTip)return;
    const p=MAP_SPOTS[k];
    if(!p)return;
    const x=document.createElement('button');
    x.className='place map-hotspot'+(k===selected?' active':'')+(k===S.loc?' current':'')+(['mayor','transport'].includes(k)?' special':'');
    x.style.cssText=`--x:${p[0]}%;--y:${p[1]}%;--w:${p[2]}%;--h:${p[3]}%`;
    x.dataset.key=k;
    x.onclick=()=>visit(k);
    if(k===S.loc)x.setAttribute('aria-current','location');
    x.setAttribute('aria-label',b[0]+', '+b[2]+(k===S.loc?', aktueller Standort':''));
    x.title=b[0];
    map.append(x);
  });
};
save();if(S.encounter)drawTactics();else if(S.control)controlUI();else if(S.cards)cardUI();

/* Gameplay revision 8: balance, progression, finance views and complete rule cleanup. */
function upgradeGameplay8(s){
  s.passport=!!s.passport;s.loan=!!s.loan;
  const oldOwned=Array.isArray(s.owned)?s.owned:[];
  s.shopOwned=!!s.shopOwned||oldOwned.includes('shop');
  s.territories=Array.isArray(s.territories)&&s.territories.length===3?s.territories.map(n=>clamp(n,0,4)):[0,1,2].map(i=>oldOwned.includes('rival'+i)?1:0);
  s.owned=s.shopOwned?['shop']:[];s.shops=s.shopOwned?1:0;
  s.invest=clamp(s.invest,0,5000);
  s.ledger=Array.isArray(s.ledger)?s.ledger.slice(0,80):[];
  s.ledgerAnchor=Number.isFinite(s.ledgerAnchor)?s.ledgerAnchor:s.cash;
  const oldMarket=s.market||{};
  s.market={pub:clamp(oldMarket.pub??oldMarket.price??130,90,220),casino:clamp(oldMarket.casino??Math.max(120,(oldMarket.price??130)+20),100,240),supply:clamp(oldMarket.supply??8,0,20),demand:clamp(oldMarket.demand??8,0,20)};
  s.used=s.used&&typeof s.used==='object'?s.used:{};
  if(Array.isArray(s.log))s.log=s.log.map(x=>String(x).replace('Januar 1925:','Januar 1931:'));
  s.revision=8;
  return s;
}
const upgradeBefore8=upgrade;
upgrade=function(s){return upgradeGameplay8(upgradeBefore8(s))};
S=upgradeGameplay8(S);selected=S.loc;
let moneyContext='Bargeldbewegung';
function money(reason){moneyContext=reason||'Bargeldbewegung'}
function ledgerRecord(){
  const before=Number.isFinite(S.ledgerAnchor)?S.ledgerAnchor:S.cash,diff=S.cash-before;
  if(diff)S.ledger.unshift({m:S.m,y:S.y,amount:diff,text:moneyContext||'Bargeldbewegung'});
  S.ledger=S.ledger.slice(0,80);S.ledgerAnchor=S.cash;moneyContext='Bargeldbewegung';
}
function statChance(base,stat='skill',extra=0){
  const active=crew().filter(c=>c.hp>0),best=active.length?Math.max(...active.map(c=>c[stat]||0)):0;
  return Math.random()<clamp(base+best/320+extra-S.heat/320,.08,.92);
}
chance=function(n){return statChance(n,'skill')};
checkEnd=function(){if(S.over)return;if(S.score>=100&&S.mayorDone&&S.transportDone)S.over='win';else if(S.y>=1934||S.debt>15000)S.over='lose'};
save=function(){S.heat=clamp(S.heat,0,100);S.cash=Math.max(0,S.cash);S.shops=S.shopOwned?1:0;ledgerRecord();checkEnd();persist();render()};
reward=function(n,p,h,msg){money(msg);S.cash+=n;S.score=Math.min(100,S.score+p);S.heat=Math.min(100,S.heat+h);note(msg);toast(msg+' · '+cash(n));save()};
function debtForecast(){if(!S.debt)return null;if(S.debt>=15000)return 0;return Math.ceil(Math.log(15000/S.debt)/Math.log(1.04))}
function ledgerView(){
  const wages=Math.max(0,S.crew.length-1)*60,rent=[0,100,250,500][S.hotel],territoryIncome=S.territories.reduce((a,n)=>a+n,0)*140,shopIncome=S.shopOwned?90:0,loanIncome=S.loan?100+Math.floor(S.invest*.08):0,interest=Math.ceil(S.debt*.04),forecast=debtForecast();
  show('Kontobuch',`<div class="list"><div class="item"><div class="item-head"><b>Bargeld</b><b>${cash(S.cash)}</b></div><span>Nächster Monat: Einnahmen ${cash(shopIncome+territoryIncome+loanIncome)} · Fixkosten ${cash(wages+rent)} · Kreditzins ${cash(interest)}</span></div><div class="item"><b>Schulden ${cash(S.debt)}</b><span>${forecast===null?'Keine Schulden.':forecast===0?'Überschuldungsgrenze erreicht.':'Ohne Tilgung würde die $15.000-Grenze bei 4 % Monatszins in etwa '+forecast+' Monaten erreicht.'}</span></div>${S.ledger.length?S.ledger.map(e=>`<div class="item"><div class="item-head"><span>${esc(['','Jan','Feb','Mär','Apr','Mai','Jun','Jul','Aug','Sep','Okt','Nov','Dez'][e.m])} ${e.y}</span><b>${e.amount>=0?'+':''}${cash(e.amount)}</b></div><span>${esc(e.text)}</span></div>`).join(''):'<div class="item"><span>Noch keine Buchungen.</span></div>'}</div>`)
}
function policeView(){
  const jailed=S.crew.filter(c=>c.jailed),base=S.heat/220,prob=Math.round(Math.min(.92,base*(S.passport?.7:1))*100);
  show('Polizeiakte',`<div class="list"><div class="item"><div class="item-head"><b>Fahndung</b><b>${S.heat}%</b></div><span>Risiko einer Kontrolle bei der nächsten Reise ungefähr ${prob} %. ${S.passport?'Der falsche Pass reduziert Kontrollen und verbessert Fluchtchancen.':'Kein falscher Pass aktiv.'}</span></div><div class="item"><b>Gefangene</b><span>${jailed.length?jailed.map(c=>esc(c.name)+' ('+c.jailMonths+' Monate)').join(', '):'Niemand in Haft.'}</span></div><div class="item"><b>Bestechung</b><span>Im Präsidium senken $150 die Fahndung um 30 Punkte. Bei Straßenkontrollen richtet sich der Preis nach der Fahndung.</span></div></div>`)
}
document.addEventListener('click',e=>{const x=e.target.closest?.('[data-open]');if(!x)return;if(x.dataset.open==='ledger'||x.dataset.open==='police'){e.preventDefault();e.stopImmediatePropagation();x.dataset.open==='ledger'?ledgerView():policeView()}},true);
const quiet8=new Set(['career','weapons','equip','cars','hire','train','blackjack','help','backup','cancelhotel','sellcar']);
function validateAction(a){
  const maxIntel=crew().length?Math.max(...crew().map(c=>c.intel)):0;
  const need=(ok,msg)=>{if(!ok){toast(msg);return false}return true};
  if(a==='protect')return need(S.score>=45,'Rang Ganove erforderlich.')&&need(!S.shopOwned,'Dieser Laden zahlt bereits Schutzgeld.');
  if(a==='bank')return need(S.score>=25,'Rang Kleiner Fisch erforderlich.');
  if(a==='safe')return need(S.score>=25,'Rang Kleiner Fisch erforderlich.')&&need(maxIntel>=55,'Tresorknacker mit Intelligenz 55 benötigt.');
  if(a==='booze')return need(selected==='station','Alkohol wird am Bahnhof eingekauft.')&&need(S.cash>=65,'Nicht genug Bargeld.')&&need(S.booze<({none:2,ford:5,buick:8,cadillac:12}[S.car]),'Laderaum voll.')&&need(S.market.supply>0,'Angebot erschöpft.');
  if(a==='sell')return need(['pub','casino'].includes(selected),'Verkauf nur in Kneipe oder Casino.')&&need(S.booze>0,'Kein Alkohol vorhanden.')&&need(S.market.demand>0,'Keine Nachfrage.');
  if(a==='fake')return need(S.cash>=100,'Nicht genug Bargeld.');
  if(a==='spendfake')return need(S.fake>=60,'Mindestens $60 Blüten nötig.');
  if(a==='passport')return need(!S.passport,'Du besitzt bereits einen neuen Pass.')&&need(S.cash>=500,'Nicht genug Bargeld.');
  if(a==='bribe')return need(S.heat>0,'Keine Fahndung vorhanden.')&&need(S.cash>=150,'Nicht genug Bargeld.');
  if(a==='breakout')return need(S.crew.some(c=>c.jailed),'Niemand sitzt ein.');
  if(a==='borrow')return need(S.debt<=1850,'Kreditlimit erreicht: maximal etwa $3.000 Schulden.');
  if(a==='repay')return need(S.debt>0&&S.cash>0,'Keine Rückzahlung möglich.');
  if(a==='buyloan')return need(!S.loan,'Geschäft gehört dir bereits.')&&need(S.cash>=3500,'Für das Kreditgeschäft brauchst du $3.500.');
  if(a==='invest')return need(S.loan,'Kreditgeschäft zuerst kaufen.')&&need(S.invest<5000,'Maximal $5.000 investierbar.')&&need(S.cash>0,'Kein Bargeld zum Investieren.');
  if(a==='withdraw'||a==='sellloan'||a==='collect')return need(S.loan,'Kein Kreditgeschäft vorhanden.');
  if(a==='collect')return need(!S.used.collect,'Diesen Monat bereits eingetrieben.');
  if(a==='posttrain')return need(S.train,'Du brauchst zuerst einen Hinweis auf einen Postzug.');
  if(a==='mayorfight')return need(S.mayorTip&&!S.mayorDone,'Auftrag nicht verfügbar.');
  if(a==='transportfight')return need(S.transportTip&&!S.transportDone,'Auftrag nicht verfügbar.');
  if(a.startsWith('hotel')){const level=+a.at(-1);return need(S.crew.length<=level*3+1,'Zu wenige Hotelplätze für deine Bande.')&&need(S.hotel!==level,'Dieses Hotel ist bereits gemietet.');}
  return true;
}
options=function(k){
  let html=legacy.options(k);
  if(k==='pub')html=html.replace(/<button data-act="booze">[\s\S]*?<\/button>/,'');
  if(k==='casino')html+=bt('Alkohol an Gäste verkaufen','sell');
  if(k==='fake')html=html.replace('$100 → $160','$100 → $180').replace('$80 pro Versuch','$60 pro Versuch');
  if(k==='loan')html=html.replace('$5.000','$3.500')+bt('Kapital entnehmen','withdraw','bis $1.000')+bt('Kreditgeschäft verkaufen','sellloan');
  if(k==='hideout')html+=bt('Anleitung','help')+bt('Spielstand & Neustart','backup');
  if(k==='loan')html+=bt('Kredit zurückzahlen','repay','bis $500');
  if(k==='hotel')html+=bt('Hotel kündigen','cancelhotel');
  if(k==='cars')html+=bt('Fahrzeug verkaufen','sellcar');
  if(k==='fake')html+=bt('Blüten ausgeben','spendfake','$60 pro Versuch');
  return html;
};
function crime8(base,a,b,score,heat,msg,stat='skill',battleRisk=false){if(statChance(base,stat)){if(battleRisk&&Math.random()<.35)return beginFight('Bewaffnete Gegner',2);reward(rnd(a,b),score,heat,msg)}else fail()}
function stealCar8(){if(statChance(.38,'skill',S.car==='none'?0:.04)){S.car=Math.random()<.25?'buick':'ford';S.max=C[S.car][1];S.steps=Math.min(S.steps,S.max);reward(0,3,18,C[S.car][0]+' gestohlen')}else fail()}
protect=function(){if(statChance(.34,'power')){S.shopOwned=true;S.owned=['shop'];reward(120,4,14,'Krämerladen zahlt Schutzgeld')}else beginFight('Ladenbesitzer',3)};
function buyLoan8(){money('Kreditgeschäft gekauft');S.cash-=3500;S.loan=true;note('Kreditgeschäft gekauft.');save();toast('Kreditgeschäft gekauft.')}
function invest8(){let n=Math.min(1000,5000-S.invest,S.cash);if(!n)return;money('In Kreditgeschäft investiert');S.cash-=n;S.invest+=n;note(cash(n)+' investiert.');save();toast(cash(n)+' investiert.')}
function withdraw8(){let n=Math.min(1000,S.invest);if(!n)return toast('Kein Kapital zum Entnehmen.');S.invest-=n;let payout=Math.floor(n*.95);money('Kapital aus Kreditgeschäft entnommen');S.cash+=payout;note(cash(n)+' Kapital entnommen, 5 % Kosten.');save();toast(cash(payout)+' ausgezahlt.')}
function sellLoan8(){let payout=2100+Math.floor(S.invest*.9);if(!confirm('Kreditgeschäft für '+cash(payout)+' verkaufen?'))return;money('Kreditgeschäft verkauft');S.cash+=payout;S.loan=false;S.invest=0;note('Kreditgeschäft verkauft.');save()}
action=function(a){
  if(blocked())return;
  if(a==='backup')return backups();if(a==='help')return help();if(a==='career')return career();
  if(a==='blackjack')return blackjack();if(a==='hire')return hire();if(a==='weapons')return weaponShop();if(a==='equip')return equip();if(a==='train')return training();if(a==='cars')return carShop();if(a==='sellloan'){if(!validateAction(a))return;let payout=2100+Math.floor(S.invest*.9);if(!confirm('Kreditgeschäft für '+cash(payout)+' verkaufen?'))return;if(!useAP())return;money('Kreditgeschäft verkauft');S.cash+=payout;S.loan=false;S.invest=0;note('Kreditgeschäft verkauft.');save();return}
  if(a==='cancelhotel'){if(S.crew.length>1)return toast('Entlasse erst die zusätzlichen Mitglieder.');S.hotel=0;save();return toast('Hotel gekündigt.')}
  if(a==='sellcar'){if(S.car==='none')return toast('Kein Fahrzeug vorhanden.');money('Fahrzeug verkauft');S.cash+=Math.floor(C[S.car][2]*.55);S.car='none';S.max=8;S.steps=Math.min(8,S.steps);save();return}
  if(!validateAction(a))return;
  const cost=['bank','safe','mayorfight','transportfight','posttrain'].includes(a)?2:1;
  if(!quiet8.has(a)&&!useAP(cost))return;
  if(a.startsWith('hotel')){S.hotel=+a.at(-1);note('Unterkunft gewählt. Die Miete wird am Monatsende abgerechnet.');save();return toast('Hotel gewählt – keine doppelte Vorauszahlung.')}
  if(a==='borrow'){money('Kredit aufgenommen');S.cash+=1000;S.debt+=1150;note('$1.000 Kredit aufgenommen, $1.150 Schuld.');save();return toast('$1.000 Kredit aufgenommen.')}
  if(a==='repay'){let n=Math.min(500,S.cash,S.debt);money('Kredit getilgt');S.cash-=n;S.debt-=n;note(cash(n)+' Kredit getilgt.');save();return toast('Zurückgezahlt: '+cash(n))}
  if(a==='buyloan')return buyLoan8();if(a==='invest')return invest8();if(a==='withdraw')return withdraw8();if(a==='sellloan')return sellLoan8();
  if(a==='booze'){money('Alkohol am Bahnhof gekauft');S.cash-=65;S.booze++;S.market.supply--;note('Alkoholfass am Bahnhof gekauft.');save();return toast('Alkoholfass geladen.')}
  if(a==='sell'){S.booze--;S.market.demand--;let price=selected==='casino'?S.market.casino:S.market.pub;return reward(price,1,selected==='casino'?3:2,'Alkohol in '+(selected==='casino'?'Casino':'Kneipe')+' verkauft')}
  if(a==='fake'){money('Falschgeld gekauft');S.cash-=100;S.fake+=180;S.heat+=2;note('$180 Blüten gekauft.');save();return toast('$180 Blüten erhalten.')}
  if(a==='spendfake'){S.fake-=60;if(statChance(.48,'intel')){money('Blüten umgesetzt');S.cash+=70;note('$60 Blüten unauffällig umgesetzt.')}else{S.heat+=18;note('Falschgeld entdeckt.')}save();return}
  if(a==='passport'){money('Neuer Pass gekauft');S.cash-=500;S.passport=true;S.heat=Math.max(0,S.heat-35);note('Neue Identität beschafft.');save();return toast('Neuer Pass aktiv.')}
  if(a==='bribe'){money('Polizeichef bestochen');S.cash-=150;S.heat=Math.max(0,S.heat-30);save();return toast('Fahndung gesenkt.')}
  if(a==='breakout'){let j=S.crew.find(c=>c.jailed);if(statChance(.35,'skill')){j.jailed=false;j.jailMonths=0;S.heat+=20;note(j.name+' befreit.');save();return toast(j.name+' befreit.')}return beginFight('Polizeiwache',5)}
  if(a==='collect'){S.used.collect=true;if(Math.random()<.45)return beginFight('Schuldner',2);return reward(rnd(160,380),2,3,'Schulden eingetrieben')}
  if(a==='posttrain'){S.train=false;save();return beginFight('Postzug-Eskorte',5)}
  if(a==='mayorfight')return beginFight('Leibwächter',5);if(a==='transportfight')return beginFight('Polizeieskorte',7);
  if(a==='bank')return beginFight('Bankwachen',4);if(a==='safe')return beginFight('Tresorwachen',5);
  if(a==='steal')return stealCar8();
  if(a==='job')return crime8(.58,80,170,2,5,'Auftrag erledigt','skill');
  if(a==='beg')return crime8(.75,8,28,0,1,'Geld erbettelt','skill');
  if(a==='robshop')return crime8(.42,55,145,2,9,'Kasse ausgeräumt','power',true);
  if(a==='pick')return crime8(.53,25,110,1,4,'Taschendiebstahl gelungen','skill');
  if(a==='protect')return protect();
  if(a==='tip')return tip();
  legacy.action(a);save();
};
hire=function(){
  if(!S.hotel)return toast('Zuerst Hotelzimmer mieten.');if(S.crew.length>=S.hotel*3+1)return toast('Keine freien Hotelplätze.');
  let list=Array.from({length:2+S.hotel},(_,i)=>({name:N[(S.m+i+S.crew.length)%N.length],skill:rnd(30,55+S.hotel*8),power:rnd(25,70),intel:rnd(25,70),price:180+S.hotel*70+i*35}));
  show('Gangster anwerben',`<div class="list">${list.map(c=>`<div class="item"><div class="item-head"><b>${esc(c.name)}</b><b>${cash(c.price)}</b></div><span>Geschick ${c.skill} · Kraft ${c.power} · Intelligenz ${c.intel}</span><button data-hire='${JSON.stringify(c)}'>Anheuern</button></div>`).join('')}</div>`);
  modalBody.querySelectorAll('[data-hire]').forEach(b=>b.onclick=()=>{let c=JSON.parse(b.dataset.hire);if(S.cash<c.price)return toast('Nicht genug Bargeld.');if(S.crew.length>=S.hotel*3+1)return toast('Keine freien Hotelplätze.');if(!useAP())return;money(c.name+' angeheuert');S.cash-=c.price;Object.assign(c,{id:Date.now()+Math.floor(Math.random()*1000),hp:100,weapon:'fist',jailed:false,jailMonths:0});S.crew.push(c);note(c.name+' angeheuert.');save();modal.close()})
};
weaponShop=function(){show('Waffenhändler',`<div class="list">${Object.entries(W).filter(([k])=>k!=='fist').map(([k,w])=>`<div class="item"><b>${w[0]} · ${cash(w[3])}</b><span>Schaden ${w[1]} · Reichweite ${w[2]}</span><button data-w="${k}">Kaufen</button></div>`).join('')}</div>`);modalBody.querySelectorAll('[data-w]').forEach(b=>b.onclick=()=>{let k=b.dataset.w,w=W[k];if(S.cash<w[3])return toast('Nicht genug Bargeld.');if(!useAP())return;money(w[0]+' gekauft');S.cash-=w[3];S.stock[k]++;save();toast(w[0]+' gekauft.')})};
equip=function(){show('Ausrüsten',`<div class="list">${crew().map(c=>`<div class="item"><b>${esc(c.name)}</b><span>${W[c.weapon][0]}</span>${Object.entries(S.stock).filter(([,n])=>n>0).map(([k,n])=>`<button data-e="${c.id}|${k}">${W[k][0]} (${n})</button>`).join('')}</div>`).join('')}</div>`);modalBody.querySelectorAll('[data-e]').forEach(b=>b.onclick=()=>{let [id,w]=b.dataset.e.split('|'),c=S.crew.find(x=>x.id==id);if(!c||!S.stock[w])return toast('Waffe nicht verfügbar.');if(!useAP())return;if(c.weapon!=='fist')S.stock[c.weapon]++;S.stock[w]--;c.weapon=w;note(c.name+' ausgerüstet.');save();modal.close()})};
training=function(){show('Training',`<p>Eine Einheit kostet $120 und 1 AP. Geschick hilft bei Diebstahl/Flucht, Kraft bei Einschüchterung/Nahkampf, Intelligenz bei Tresor und Falschgeld.</p>${crew().map(c=>`<div class="item"><b>${esc(c.name)}</b>${['skill','power','intel'].map((k,i)=>`<button data-training="${c.id}|${k}">${['Geschick','Kraft','Intelligenz'][i]} ${c[k]}</button>`).join('')}</div>`).join('')}`);modalBody.querySelectorAll('[data-training]').forEach(b=>b.onclick=()=>{let [id,k]=b.dataset.training.split('|'),c=S.crew.find(c=>c.id==id);if(!c||c[k]>=95)return toast('Wert bereits am Maximum.');if(S.cash<120)return toast('Nicht genug Bargeld.');if(!useAP())return;money('Training');S.cash-=120;c[k]=Math.min(95,c[k]+6);note(c.name+' trainiert '+k);save();modal.close()})};
carShop=function(){show('Autohändler',`<div class="list">${Object.entries(C).filter(([k])=>k!=='none').map(([k,c])=>`<div class="item"><b>${c[0]} · ${cash(c[2])}</b><span>${c[1]} Wegepunkte pro Monat</span><button data-c="${k}">Kaufen</button></div>`).join('')}</div>`);modalBody.querySelectorAll('[data-c]').forEach(b=>b.onclick=()=>{let k=b.dataset.c,c=C[k];if(S.car===k)return toast('Dieses Fahrzeug besitzt du bereits.');if(S.cash<c[2])return toast('Nicht genug Bargeld.');if(!useAP())return;money(c[0]+' gekauft');S.cash-=c[2];S.car=k;S.max=c[1];S.steps=Math.min(S.steps,S.max);note(c[0]+' gekauft.');save();modal.close()})};
policeCheck=function(){let p=(S.heat/220)*(S.passport?.7:1);if(Math.random()<p){S.control={price:100+S.heat*2};persist();controlUI()}};
arrest=function(){let c=crew().at(-1);if(c){c.jailed=true;c.jailMonths=3;S.heat=Math.max(10,S.heat-25);if(S.passport&&Math.random()<.25){S.passport=false;note('Der falsche Pass wurde beschlagnahmt.')}note(c.name+' für drei Monate inhaftiert.');toast(c.name+' verhaftet.')}};
controlUI=function(){show('Polizeikontrolle',`<p>Du musst dich entscheiden. ${S.passport?'Dein falscher Pass verbessert deine Chancen.':''}</p><div class="grid2"><button id="payCop">Bestechen ${cash(S.control.price)}</button><button id="runCop">Fliehen</button><button id="yieldCop">Stellen</button></div>`);$('.close').hidden=true;$('#payCop').onclick=()=>{if(S.cash<S.control.price)return toast('Nicht genug Bargeld.');money('Straßenkontrolle bestochen');S.cash-=S.control.price;S.heat=Math.max(0,S.heat-15);finishControl()};$('#runCop').onclick=()=>{let bonus=(S.car==='none'?0:.12)+(S.passport?.1:0);if(!statChance(.28,'skill',bonus))arrest();else S.heat+=8;finishControl()};$('#yieldCop').onclick=()=>{arrest();finishControl()}};
visit=function(k){if(blocked())return;if(k!==S.loc){if(!crew().some(c=>c.hp>0))return toast('Keine einsatzfähigen Mitglieder.');let cost=Math.max(1,Math.ceil((Math.abs(B[k][3]-B[S.loc][3])+Math.abs(B[k][4]-B[S.loc][4]))/35));if(S.steps<cost)return toast('Nicht genug Wegepunkte ('+cost+' nötig).');S.steps-=cost;S.loc=selected=k;policeCheck()}save()};
nextMonth=function(){
  if(blocked())return;let wages=Math.max(0,S.crew.length-1)*60,rent=[0,100,250,500][S.hotel],territoryIncome=S.territories.reduce((a,n)=>a+n,0)*140,shopIncome=S.shopOwned?90:0,loanIncome=S.loan?100+Math.floor(S.invest*.08):0,income=territoryIncome+shopIncome+loanIncome,interest=Math.ceil(S.debt*.04);
  if(!confirm(`Monat beenden? Einnahmen ${cash(income)}, Löhne ${cash(wages)}, Miete ${cash(rent)}, Zinsen ${cash(interest)}.`))return;
  money('Monatsabrechnung');S.cash+=income-wages-rent;S.debt+=interest;if(S.cash<0){S.debt-=S.cash;S.cash=0}
  S.crew.forEach(c=>{c.hp=Math.min(100,c.hp+15);if(c.jailed&&--c.jailMonths<=0){c.jailed=false;note(c.name+' aus Haft entlassen.')}});S.heat=Math.max(0,S.heat-7);if(++S.m===13){S.m=1;S.y++}S.ap=12;S.steps=S.max=C[S.car][1];S.used={};S.market={pub:rnd(105,165),casino:rnd(120,190),supply:rnd(4,10),demand:rnd(3,8)};
  S.rivals.forEach((r,i)=>{if(r[2]>0)r[1]=Math.min(80,r[1]+rnd(1,2));if(r[2]>0&&S.territories[i]>0&&Math.random()<.12){S.territories[i]--;r[2]=Math.min(4,r[2]+1);note(r[0]+' erobert ein Revier zurück.')}});
  note(`Abrechnung: ${cash(income)} Einnahmen, ${cash(wages+rent)} Kosten, ${cash(interest)} Zinsen.`);save();toast('Neuer Monat · '+cash(income)+' Einnahmen')
};$('#endTurn').onclick=nextMonth;
function cardLabel(c){return c===1?'A':c===11?'J':c===12?'Q':c===13?'K':String(c)}
function normalizeCards8(){let g=S.cards;if(g&&g.hand&&!g.hands){g.hands=[{cards:g.hand,bet:g.bet,done:false}];delete g.hand;g.active=0;g.split=false}return g}
blackjack=function(){
  if(S.cards){normalizeCards8();return cardUI()}
  show('Blackjack',`<p>Ein Einsatz kostet 1 AP. Blackjack zahlt 3:2. Split und Double Down sind möglich.</p>${[25,50,100,250].map(n=>`<button data-bet="${n}">${cash(n)}</button>`).join('')}`);
  modalBody.querySelectorAll('[data-bet]').forEach(b=>b.onclick=()=>{let bet=+b.dataset.bet;if(S.cash<bet)return toast('Nicht genug Bargeld.');if(!useAP())return;money('Blackjack-Einsatz');S.cash-=bet;let deck=Array.from({length:52},(_,i)=>i%13+1);for(let i=51;i>0;i--){let j=rnd(0,i);[deck[i],deck[j]]=[deck[j],deck[i]]}S.cards={deck,dealer:[deck.pop(),deck.pop()],hands:[{cards:[deck.pop(),deck.pop()],bet,done:false}],active:0,split:false};persist();cardUI()})
};
function cardUI(){
  let g=normalizeCards8();if(!g)return;let h=g.hands[g.active];if(!h)return settleCards();
  if(total(h.cards)>=21)h.done=true;
  if(h.done){let next=g.hands.findIndex((x,i)=>i>g.active&&!x.done);if(next>=0){g.active=next;persist();return cardUI()}return settleCards()}
  show('Blackjack',`<div class="list">${g.hands.map((x,i)=>`<div class="item"><b>${i===g.active?'▶ ':''}Hand ${i+1}: ${x.cards.map(cardLabel).join(' · ')} → ${total(x.cards)}</b><span>Einsatz ${cash(x.bet)}</span></div>`).join('')}</div><p>Bank: ${cardLabel(g.dealer[0])} · verdeckt</p><div class="grid2"><button id="hit">Karte ziehen</button><button id="stand">Stehen bleiben</button><button id="double" ${h.cards.length!==2||S.cash<h.bet?'disabled':''}>Double Down</button><button id="split" ${h.cards.length!==2||g.hands.length>1||h.cards[0]!==h.cards[1]||S.cash<h.bet?'disabled':''}>Split</button></div>`);$('.close').hidden=true;
  $('#hit').onclick=()=>{h.cards.push(g.deck.pop());if(total(h.cards)>=21)h.done=true;persist();cardUI()};$('#stand').onclick=()=>{h.done=true;persist();cardUI()};
  $('#double').onclick=()=>{if(h.cards.length!==2||S.cash<h.bet)return;money('Blackjack Double Down');S.cash-=h.bet;h.bet*=2;h.cards.push(g.deck.pop());h.done=true;persist();cardUI()};
  $('#split').onclick=()=>{if(h.cards.length!==2||g.hands.length>1||h.cards[0]!==h.cards[1]||S.cash<h.bet)return;money('Blackjack Split');S.cash-=h.bet;let a=h.cards[0],b=h.cards[1],bet=h.bet;g.hands=[{cards:[a,g.deck.pop()],bet,done:false},{cards:[b,g.deck.pop()],bet,done:false}];g.active=0;g.split=true;persist();cardUI()}
}
settleCards=function(){let g=normalizeCards8();while(total(g.dealer)<17)g.dealer.push(g.deck.pop());let d=total(g.dealer),payout=0,summary=[];g.hands.forEach((h,i)=>{let p=total(h.cards),natural=!g.split&&h.cards.length===2&&p===21,factor=p>21?0:natural?2.5:d>21||p>d?2:p===d?1:0;payout+=h.bet*factor;summary.push(`Hand ${i+1}: ${p} → ${factor===2.5?'Blackjack':factor===2?'gewonnen':factor===1?'gleich':'verloren'}`)});money('Blackjack-Auszahlung');S.cash+=Math.floor(payout);S.cards=null;modal.close();note('Blackjack: '+summary.join(', '));save();show('Blackjack-Ergebnis',`<p>Bank ${d}. ${summary.map(esc).join('<br>')}</p><p>Auszahlung ${cash(Math.floor(payout))}</p>`)};
function enemyProfile8(name,i,num){
  if(name==='Leibwächter'&&i===num-1)return {role:'Boss',hp:rnd(70,90),damage:rnd(10,18),range:2,accuracy:.72};
  if(name==='Polizeieskorte'||name==='Polizeiwache')return i%3===0?{role:'Polizeischütze',hp:rnd(38,58),damage:rnd(7,13),range:4,accuracy:.64}:{role:'Polizist',hp:rnd(42,62),damage:rnd(6,12),range:3,accuracy:.62};
  if(name.includes('Bank')||name.includes('Tresor'))return {role:'Wachmann',hp:rnd(42,64),damage:rnd(7,14),range:3,accuracy:.63};
  if(S.rivals.some(r=>r[0]===name))return i%4===0?{role:'Capo',hp:rnd(55,72),damage:rnd(8,15),range:3,accuracy:.67}:{role:'Mafioso',hp:rnd(38,60),damage:rnd(6,13),range:3,accuracy:.61};
  return {role:'Gegner',hp:rnd(35,65),damage:rnd(7,14),range:3,accuracy:.62}
}
beginFight=function(name,num){if(S.encounter||!crew().some(c=>c.hp>0))return toast('Keine kampffähige Bande.');S.encounter={name,units:crew().filter(c=>c.hp>0).slice(0,10).map((c,i)=>({id:c.id,x:i%2,y:Math.floor(i/2),acted:false})),enemies:Array.from({length:num},(_,i)=>({id:i,x:5-i%2,y:Math.floor(i/2),...enemyProfile8(name,i,num)})),cover:[[2,1],[3,3]],selected:null,log:['Kampf beginnt.']};persist();drawTactics()};
drawTactics=function(){let g=S.encounter;if(!g)return;$('#battleTitle').textContent=g.name;$('#battleBody').innerHTML=`<p>Figur wählen, dann bewegen oder angreifen. Gold = eigene Bande, Rot = Gegner. ▤ = Deckung.</p><div class="tactical">${Array.from({length:30},(_,i)=>{let x=i%6,y=Math.floor(i/6),u=g.units.find(u=>u.x===x&&u.y===y&&S.crew.find(c=>c.id===u.id)?.hp>0),e=g.enemies.find(e=>e.x===x&&e.y===y&&e.hp>0),c=u&&S.crew.find(c=>c.id===u.id);return `<button data-cell="${x}|${y}" class="${u?'ally':e?'enemy':''} ${u?.id===g.selected?'chosen':''}" aria-label="Feld ${x+1}, ${y+1}">${u?esc(c.name.slice(0,6))+'<small>'+c.hp+' LP'+(u.acted?' ✓':'')+'</small>':e?esc((e.role||'Gegner').slice(0,8))+'<small>'+e.hp+' LP</small>':g.cover.some(p=>p[0]===x&&p[1]===y)?'▤':''}</button>`}).join('')}</div><p><button id="endRound">Runde beenden</button> <button id="fleeBattle">Flucht versuchen</button></p><div class="log">${g.log.slice(-5).map(esc).join('<br>')}</div>`;if(!battle.open)battle.showModal();battleBody.querySelectorAll('[data-cell]').forEach(b=>b.onclick=()=>cell(...b.dataset.cell.split('|').map(Number)));$('#endRound').onclick=enemyRound;$('#fleeBattle').onclick=()=>{if(statChance(.32,'skill',S.car==='none'?0:.1)){S.encounter=null;S.heat+=8;battle.close();save()}else{g.log.push('Flucht gescheitert.');enemyRound()}}};
enemyRound=function(){let g=S.encounter;for(const e of g.enemies.filter(e=>e.hp>0)){let alive=g.units.filter(u=>S.crew.find(c=>c.id===u.id).hp>0);if(!alive.length)break;let t=alive.sort((a,b)=>distance(e,a)-distance(e,b))[0],c=S.crew.find(c=>c.id===t.id),range=e.range||3;if(distance(e,t)<=range){let cover=g.cover.some(p=>p[0]===t.x&&p[1]===t.y),acc=(e.accuracy??.62)-(cover?.22:0);if(Math.random()<acc){let d=rnd(4,e.damage);c.hp=Math.max(0,c.hp-d);g.log.push((e.role||'Gegner')+': '+c.name+' verliert '+d+' LP.')}}else{let moves=[[e.x-1,e.y],[e.x+1,e.y],[e.x,e.y-1],[e.x,e.y+1]].filter(([x,y])=>x>=0&&x<6&&y>=0&&y<5&&!g.enemies.some(o=>o.hp>0&&o.x===x&&o.y===y)&&!alive.some(o=>o.x===x&&o.y===y));moves.sort((a,b)=>distance({x:a[0],y:a[1]},t)-distance({x:b[0],y:b[1]},t));if(moves.length)[e.x,e.y]=moves[0]}}
  if(!g.units.some(u=>S.crew.find(c=>c.id===u.id).hp>0))return finishBattle(false);g.units.forEach(u=>u.acted=false);persist();drawTactics()
};
finishBattle=function(win){let name=S.encounter.name;S.encounter=null;battle.close();if(!win){S.cash=Math.max(0,S.cash-200);crew().filter(c=>c.hp===0).forEach(c=>{c.hp=20;c.jailed=true;c.jailMonths=2});note('Kampf verloren.');money('Kampfniederlage');return save()}
  let ri=S.rivals.findIndex(r=>r[0]===name);if(ri>=0){if(S.rivals[ri][2]>0){S.rivals[ri][2]--;S.rivals[ri][1]=Math.max(5,S.rivals[ri][1]-5);S.territories[ri]++;note('Ein Revier von '+name+' übernommen. '+S.rivals[ri][2]+' verbleiben.')}return reward(400,6,20,'Bandenkrieg gewonnen')}
  const results={'Leibwächter':[1200,15,25],'Polizeieskorte':[2500,18,35],'Tresorwachen':[1300,9,25],'Bankwachen':[650,6,18],'Postzug-Eskorte':[1000,8,22],'Schuldner':[350,2,6],'Bewaffnete Gegner':[110,2,9],'Ladenbesitzer':[80,3,18],'Polizeiwache':[0,5,28]};
  if(name==='Leibwächter'){S.mayorDone=true;S.mayorTip=false;selected=S.loc='hideout'}if(name==='Polizeieskorte'){S.transportDone=true;S.transportTip=false;selected=S.loc='hideout'}if(name==='Ladenbesitzer')S.shopOwned=true;if(name==='Polizeiwache'){let c=S.crew.find(c=>c.jailed);if(c){c.jailed=false;c.jailMonths=0}}reward(...(results[name]||[100,2,5]),name+' besiegt')
};
tab=function(t){
  if(t==='inventory')return show('Besitz & Finanzen',`<div class="list"><div class="item"><b>${C[S.car][0]}</b><span>${S.max} Wegepunkte/Monat · ${S.booze} Fässer geladen</span></div><div class="item"><b>Vorräte</b><span>${cash(S.fake)} Blüten · ${S.passport?'neuer Pass aktiv':'alter Pass'}</span></div><div class="item"><b>Geschäfte & Reviere</b><span>Krämerladen ${S.shopOwned?'unter Schutz':'nicht kontrolliert'} · ${S.territories.reduce((a,n)=>a+n,0)} Reviere · Kreditgeschäft ${S.loan?'gekauft':'nicht gekauft'} · ${cash(S.invest)} Kapital</span></div><div class="item"><b>Schulden ${cash(S.debt)}</b></div></div>`);
  if(t==='families'){show('Rivalisierende Familien',`<div class="list">${S.rivals.map((r,i)=>`<div class="item"><b>${esc(r[0])}</b><span>Stärke ${r[1]} · Gegner-Reviere ${r[2]} · Deine Reviere ${S.territories[i]}</span><button data-war="${i}" ${r[2]<=0?'disabled':''}>${r[2]>0?'Ein Revier angreifen':'Familie verdrängt'}</button></div>`).join('')}</div>`);modalBody.querySelectorAll('[data-war]').forEach(b=>b.onclick=()=>{let i=+b.dataset.war,r=S.rivals[i];if(r[2]<=0)return toast('Keine gegnerischen Reviere mehr.');if(!useAP(2))return;modal.close();beginFight(r[0],Math.max(2,Math.ceil(r[1]/12)))});return}
  legacy.tab(t);if(t==='crew'){modalBody.querySelectorAll('.item').forEach((el,i)=>{let c=S.crew[i];if(!c||c.id===1)return;let b=document.createElement('button');b.textContent='Entlassen';b.onclick=()=>{if(blocked()||!confirm(c.name+' entlassen?'))return;if(c.weapon!=='fist')S.stock[c.weapon]++;S.crew.splice(i,1);save();tab('crew')};el.append(b)})}if(t==='journal'){let b=document.createElement('button');b.textContent='Anleitung & Sicherung';b.onclick=backups;modalBody.prepend(b)}
};
help=function(){show('So spielst du',`<p>Die Kampagne läuft von Januar 1931 bis Dezember 1933. Jeder Monat gibt dir 12 Aktionspunkte. Reisen kostet Wegepunkte; ungültige Aktionen verbrauchen keine AP.</p><p>Alkohol kaufst du am Bahnhof und verkaufst ihn in Kneipe oder Casino. Preise und Nachfrage ändern sich monatlich. Geschick hilft bei Diebstahl und Flucht, Kraft bei Einschüchterung und Nahkampf, Intelligenz beim Tresor und bei Falschgeld.</p><p>Unterkünfte bieten 4 / 7 / 10 Plätze. Die Miete wird nur am Monatsende berechnet. Löhne kosten $60 je zusätzlichem Mitglied. Kredite wachsen um 4 % pro Monat; über $15.000 Schulden endet die Kampagne.</p><p>Reviere werden einzeln erobert. Gegner können einzelne Reviere zurückholen. Das Kreditgeschäft erzeugt $100 Grundgewinn plus 8 % des investierten Kapitals; Kapital kann gegen 5 % Kosten entnommen werden.</p><p>Erreiche vor Januar 1934 100 Punkte und erledige Bürgermeister- sowie Geldtransport-Auftrag. Hinweise bekommst du in der Kneipe. Ein neuer Pass reduziert Kontrollen und verbessert Fluchten.</p><p>Im taktischen Kampf unterscheiden sich Boss, Mafioso, Polizist, Schütze und Wachmann in Reichweite, Trefferchance und Widerstand. Lokal wird automatisch gespeichert; Cloud und private Mehrspieler-Partien findest du unter Online.</p>`)};
backups=function(){show('Spielstand',`<p>Revision 8 · Balancing, Wirtschaft, Reviere, erweiterter Kampf und Cloud/Mehrspieler.</p><button id="exportSave">Sicherung herunterladen</button><p><label>Sicherung importieren <input id="importSave" type="file" accept="application/json,.json"></label></p><button id="resetSave">Neues Spiel</button>`);$('#exportSave').onclick=()=>{let url=URL.createObjectURL(new Blob([JSON.stringify(S)],{type:'application/json'})),a=document.createElement('a');a.href=url;a.download='unterwelt-spielstand.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)};$('#importSave').onchange=async e=>{try{const file=e.target.files[0];if(!file||file.size>2000000)throw Error();let raw=JSON.parse(await file.text());if(raw.v!==2||!Array.isArray(raw.crew)||raw.encounter||raw.control||raw.cards)throw Error();const next=upgrade(raw);if(!confirm('Aktuellen Stand durch diese Sicherung ersetzen?'))return;localStorage.setItem('uw1931-backup',JSON.stringify(S));S=next;selected=S.loc;modal.close();save()}catch(err){toast('Sicherung ungültig oder enthält eine laufende Begegnung.')}};$('#resetSave').onclick=restart};
restart=function(){if(!confirm('Neu beginnen? Der aktuelle Stand wird vorher als Sicherung auf diesem Gerät behalten.'))return;localStorage.setItem('uw1931-backup',JSON.stringify(S));S=upgrade(fresh());selected=S.loc;modal.close();if(battle.open)battle.close();save()};
endScreen=function(){let win=S.over==='win';show(win?'Chef der Unterwelt!':'Kampagne beendet',`<div class="item"><h3>${win?'Die Stadt gehört dir.':S.debt>15000?'Überschuldet':'Januar 1934'}</h3><span>${esc(rankName())} · ${S.score} Punkte · ${cash(S.cash)}</span><button id="restart">Neues Spiel</button></div>`);$('#restart').onclick=restart};
// Main menu decides when a saved campaign is resumed.
render();
