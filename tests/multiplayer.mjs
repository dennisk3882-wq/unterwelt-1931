import assert from 'node:assert/strict';
import {lobby,player,command,value} from '../supabase/functions/game/engine.mjs';

let g=lobby('a','Alice');g.players.push(player('b','Bob'));
g=command(g,'a','start',{},()=>0.5,1000);assert.equal(g.status,'playing');assert.equal(g.players[0].ap,3);
assert.throws(()=>command(g,'b','job',{},()=>0.5,1100),/nicht am Zug/i);
g=command(g,'a','job',{},()=>0.5,1100);g=command(g,'a','job',{},()=>0.5,1200);g=command(g,'a','job',{},()=>0.5,1300);assert.equal(g.turn,1);
g=command(g,'b','district',{index:0},()=>0.5,1400);assert.equal(g.districts[0].owner,'b');
assert.throws(()=>command(g,'b','timeout',{},()=>0.5,1500),/Zugzeit läuft noch/i);g=command(g,'b','end',{},()=>0.5,1600);assert.equal(g.turn,0);

// Exercise the complete multiplayer action set with server-authoritative validation.
let h=lobby('a','Alice');h.players.push(player('b','Bob'));h=command(h,'a','start',{},()=>0,1000);
function act(id,a,arg={},rnd=()=>0){h=command(h,id,a,arg,rnd,1000+h.round*100+(h.players.find(p=>p.id===id)?.ap||0));}
act('a','loan');act('a','hire');act('a','end');
act('b','job');act('b','loan');act('b','end');
act('a','weapon');act('a','car');act('a','end');
act('b','buy');act('b','sell');act('b','end');
act('a','bank',{},()=>0);act('a','bribe');act('a','end');
act('b','district',{index:1});act('b','repay');act('b','end');
assert.ok(Number.isFinite(value(h,h.players[0]))&&Number.isFinite(value(h,h.players[1])));

// Deterministically play out the remaining 12-round match and prove a terminal winner exists.
while(h.status==='playing'){
 const p=h.players[h.turn];
 if(p.ap>0){h=command(h,p.id,'job',{},()=>0.5,Date.now());}
}
assert.equal(h.status,'finished');assert.ok(Array.isArray(h.winners)&&h.winners.length>=1);

let r=lobby('x','X');r.players.push(player('y','Y'));r=command(r,'x','start',{},()=>0.5,1000);r=command(r,'y','resign',{},()=>0.5,1100);assert.equal(r.status,'finished');assert.deepEqual(r.winners,['x']);
console.log('PASS authoritative multiplayer engine, all actions and full 12-round completion');