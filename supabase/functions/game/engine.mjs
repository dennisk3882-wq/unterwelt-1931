// Authoritative multiplayer rules, shared by the Edge Function and Node tests.
export const DISTRICTS = ['South Side','Little Italy','West Loop','River North','The Loop','Rail Yard','North Side','Back Yards'];
export function player(id,name) { return {id,name,cash:700,score:0,heat:0,crew:1,weapon:0,car:false,booze:0,debt:0,ap:3,soldRound:0,resigned:false}; }
export function lobby(id,name) { return {status:'lobby',players:[player(id,name)],districts:DISTRICTS.map(name=>({name,owner:null})),round:1,turn:0,log:['Private Runde eröffnet.'],deadline:null}; }
function requireRule(ok,message) { if(!ok) throw new Error(message); }
function pay(p,n) { requireRule(p.cash>=n,'Nicht genug Bargeld.'); p.cash-=n; }
function log(g,s) { g.log.unshift(s);g.log=g.log.slice(0,40); }
export function standings(g) { return [...g.players].sort((a,b)=>value(g,b)-value(g,a)); }
export function value(g,p) { return p.resigned?-1:p.score*100+p.cash-p.debt+g.districts.filter(d=>d.owner===p.id).length*500; }
function finish(g) { g.status='finished';g.deadline=null;g.winners=standings(g).filter(p=>!p.resigned&&value(g,p)===value(g,standings(g)[0])).map(p=>p.id);log(g,'Runde beendet. Wertung: Punkte × 100 + Bargeld − Schulden + $500 je Revier.'); }
function advance(g,now) {
 if(g.players.filter(p=>!p.resigned).length<2)return finish(g);
 for(let i=0;i<g.players.length;i++) {
  g.turn++;
  if(g.turn===g.players.length){g.turn=0;g.round++;if(g.round>12)return finish(g);
   for(const p of g.players.filter(p=>!p.resigned)){
    let net=g.districts.filter(d=>d.owner===p.id).length*140-(p.crew-1)*35;
    p.cash+=net;p.debt+=Math.ceil(p.debt*.08);if(p.cash<0){p.debt-=p.cash;p.cash=0;}p.heat=Math.max(0,p.heat-12);
   }
   log(g,'Monat '+g.round+': Schutzgeld, Löhne und Kreditzinsen abgerechnet.');
  }
  if(!g.players[g.turn].resigned)break;
 }
 g.players[g.turn].ap=3;g.deadline=now+180000;
}
export function command(original,id,action,arg={},random=Math.random,now=Date.now()) {
 const g=structuredClone(original),p=g.players.find(p=>p.id===id);
 requireRule(p&&!p.resigned,'Du bist kein aktives Mitglied dieser Runde.');
 if(action==='start') { requireRule(g.status==='lobby'&&g.players[0].id===id,'Nur der Gastgeber kann starten.');requireRule(g.players.length>=2,'Mindestens zwei Spieler nötig.');g.status='playing';g.deadline=now+180000;log(g,'Die Stadt ist eröffnet. Jeder Zug hat 3 Aktionen und maximal 3 Minuten.');return g; }
 requireRule(g.status==='playing','Diese Runde läuft nicht.');
 if(action==='resign'){p.resigned=true;g.districts.filter(d=>d.owner===id).forEach(d=>d.owner=null);log(g,p.name+' hat aufgegeben.');if(g.players.filter(p=>!p.resigned).length<2)finish(g);else if(g.players[g.turn].id===id)advance(g,now);return g;}
 if(action==='timeout'){requireRule(now>=g.deadline,'Die Zugzeit läuft noch.');log(g,g.players[g.turn].name+': Zugzeit abgelaufen.');advance(g,now);return g;}
 requireRule(g.players[g.turn].id===id,'Du bist noch nicht am Zug.');
 requireRule(now<g.deadline,'Zugzeit abgelaufen. Bitte Zug weitergeben.');
 if(action==='end'){advance(g,now);return g;}
 requireRule(p.ap>0,'Keine Aktionen übrig.');
 let description='';
 switch(action){
  case 'job':p.cash+=120;p.score++;description='Auftrag erledigt (+$120, +1 Punkt)';break;
  case 'hire':requireRule(p.crew<5,'Maximal fünf Bandenmitglieder.');pay(p,240);p.crew++;description='Verstärkung angeworben';break;
  case 'weapon':requireRule(p.weapon<3,'Beste Bewaffnung vorhanden.');pay(p,[220,390,650][p.weapon]);p.weapon++;description='Bewaffnung verbessert';break;
  case 'car':requireRule(!p.car,'Du besitzt bereits ein Auto.');pay(p,480);p.car=true;description='Ford gekauft';break;
  case 'buy':requireRule(p.booze<(p.car?8:3),'Laderaum voll.');pay(p,65);p.booze++;description='Alkohol eingekauft';break;
  case 'sell':requireRule(p.booze>0,'Kein Alkohol vorhanden.');p.booze--;p.cash+=145;p.score++;p.heat+=3;description='Alkohol verkauft (+$145)';break;
  case 'bribe':pay(p,120);p.heat=Math.max(0,p.heat-35);description='Polizei bestochen';break;
  case 'loan':requireRule(p.debt<=1200,'Kreditlimit erreicht.');p.cash+=600;p.debt+=780;description='Kredit aufgenommen (+$600, $780 Schulden)';break;
  case 'repay':{let n=Math.min(500,p.debt,p.cash);requireRule(n>0,'Keine Rückzahlung möglich.');p.cash-=n;p.debt-=n;description='$'+n+' zurückgezahlt';break;}
  case 'bank':{
   requireRule(p.weapon>=1,'Für den Banküberfall brauchst du eine Waffe.');
   const win=random()<Math.max(.15,Math.min(.85,.25+p.crew*.08+p.weapon*.1-p.heat*.004));
   p.heat+=25;if(win){p.cash+=550;p.score+=4;description='Bank überfallen (+$550, +4 Punkte)';}else{p.cash=Math.max(0,p.cash-150);description='Banküberfall gescheitert (bis zu $150 verloren)';}break;}
  case 'district':{
   requireRule(Number.isInteger(arg.index)&&arg.index>=0&&arg.index<g.districts.length,'Ungültiges Revier.');const d=g.districts[arg.index];
   requireRule(d.owner!==id,'Dieses Revier gehört dir bereits.');
   if(!d.owner){pay(p,350);d.owner=id;p.score+=2;description=d.name+' übernommen (+2 Punkte)';}
   else{const defender=g.players.find(x=>x.id===d.owner);pay(p,150);const attack=p.crew+p.weapon*2+(p.car?1:0),defence=defender.crew+defender.weapon*2+2;
    const win=random()<Math.max(.2,Math.min(.8,attack/(attack+defence)));p.heat+=20;
    if(win){d.owner=id;p.score+=3;description=d.name+' von '+defender.name+' erobert (+3 Punkte)';}else description='Angriff auf '+d.name+' abgewehrt';
   }break;}
  default:throw new Error('Unbekannte Aktion.');
 }
 p.ap--;p.heat=Math.min(100,p.heat);log(g,p.name+': '+description+'.');
 if(p.heat>=80&&random()<.35){const fine=Math.min(p.cash,200);p.cash-=fine;p.heat=Math.max(0,p.heat-30);log(g,p.name+': Polizeirazzia, $'+fine+' beschlagnahmt.');}
 if(p.ap===0)advance(g,now);return g;
}
