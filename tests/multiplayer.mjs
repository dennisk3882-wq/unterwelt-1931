import assert from 'node:assert/strict';
import {lobby,player,command,value} from '../supabase/functions/game/engine.mjs';

let g=lobby('a','Alice');
g.players.push(player('b','Bob'));

g=command(g,'a','start',{},()=>0.5,1000);
assert.equal(g.status,'playing');
assert.equal(g.turn,0);
assert.equal(g.players[0].ap,3);
assert.ok(g.deadline>1000);

assert.throws(()=>command(g,'b','job',{},()=>0.5,1100),/nicht am Zug/i);

g=command(g,'a','job',{},()=>0.5,1100);
g=command(g,'a','job',{},()=>0.5,1200);
g=command(g,'a','job',{},()=>0.5,1300);
assert.equal(g.players[0].cash,1060);
assert.equal(g.players[0].score,3);
assert.equal(g.turn,1);

g=command(g,'b','district',{index:0},()=>0.5,1400);
assert.equal(g.districts[0].owner,'b');
assert.equal(g.players[1].cash,350);
assert.equal(g.players[1].score,2);

assert.throws(()=>command(g,'b','timeout',{},()=>0.5,1500),/Zugzeit läuft noch/i);
g=command(g,'b','end',{},()=>0.5,1600);
assert.equal(g.turn,0);

const before=value(g,g.players[0]);
assert.ok(Number.isFinite(before));

g=command(g,'b','resign',{},()=>0.5,1700);
assert.equal(g.status,'finished');
assert.deepEqual(g.winners,['a']);

console.log('PASS authoritative multiplayer engine');
