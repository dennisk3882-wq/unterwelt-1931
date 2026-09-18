import {lobby,player,command} from './engine.mjs';

// Custom account authentication: 256-bit device/recovery key, stored only as a
// SHA-256 digest server-side. No service key is ever sent to the web client.
const url=Deno.env.get('SUPABASE_URL')!;
const service=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
const origins=new Set(['https://unterwelt-1931.onrender.com','http://localhost:4173','http://127.0.0.1:4173']);
class ApiError extends Error { constructor(message:string,public status=400){super(message);} }
async function db(path:string,method='GET',body?:unknown){
 const r=await fetch(url+'/rest/v1/'+path,{method,headers:{apikey:service,Authorization:'Bearer '+service,'Content-Type':'application/json',Prefer:'return=representation'},body:body===undefined?undefined:JSON.stringify(body)});
 if(!r.ok){console.error('Database request failed',r.status,path.split('?')[0]);throw new ApiError('Speicherdienst vorübergehend nicht verfügbar.',503);}
 return r.status===204?null:await r.json();
}
async function limit(bucket:string,max:number,expires:string){
 if(!await db('rpc/uw_take_limit','POST',{p_bucket:bucket,p_max:max,p_expires:expires}))throw new ApiError('Kostenloses Nutzungslimit erreicht. Bitte später erneut versuchen.',429);
}
const hash=async(s:string)=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s))),b=>b.toString(16).padStart(2,'0')).join('');
const hex=(n:number)=>Array.from(crypto.getRandomValues(new Uint8Array(n)),b=>b.toString(16).padStart(2,'0')).join('');
const clean=(s:unknown)=>String(s||'').replace(/[<>"'&\x00-\x1f]/g,'').trim().slice(0,24);
function assert(ok:unknown,message:string,status=400):asserts ok {if(!ok)throw new ApiError(message,status);}
function roomPublic(r:any){return {id:r.id,code:r.code,host:r.host,version:r.version,state:r.state,updated_at:r.updated_at};}
async function commitRoom(r:any,state:any,members=r.members){
 const out=await db('uw_rooms?id=eq.'+r.id+'&version=eq.'+r.version,'PATCH',{state,members,version:r.version+1,updated_at:new Date().toISOString()});
 assert(out.length,'Die Runde wurde inzwischen geändert. Bitte aktualisieren.',409);return roomPublic(out[0]);
}
Deno.serve(async(req:Request)=>{
 const origin=req.headers.get('origin');
 const headers:Record<string,string>={'Content-Type':'application/json','Cache-Control':'no-store','Vary':'Origin','Access-Control-Allow-Headers':'content-type,apikey,x-player-key,authorization','Access-Control-Allow-Methods':'POST, OPTIONS'};
 if(origin&&origins.has(origin))headers['Access-Control-Allow-Origin']=origin;
 const reply=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers});
 if(origin&&!origins.has(origin))return reply({error:'Unzulässige Herkunft.'},403);
 if(req.method==='OPTIONS')return new Response(null,{status:204,headers});
 if(req.method!=='POST')return reply({error:'POST erforderlich.'},405);
 try{
  const token=req.headers.get('x-player-key')||'';
  assert(/^uw1_[a-f0-9]{64}$/.test(token),'Bitte mit deinem privaten Spielercode anmelden.',401);
  const declared=Number(req.headers.get('content-length')||0);assert(declared<=270000,'Anfrage zu groß.',413);
  // Bound streamed payloads too, including chunked requests without Content-Length.
  const reader=req.body?.getReader();let bytes=0,chunks:Uint8Array[]=[];
  if(reader)while(true){const {value,done}=await reader.read();if(done)break;bytes+=value.length;if(bytes>270000){await reader.cancel();throw new ApiError('Anfrage zu groß.',413);}chunks.push(value);}
  const data=new Uint8Array(bytes);let offset=0;for(const chunk of chunks){data.set(chunk,offset);offset+=chunk.length;}
  let b:any;try{b=JSON.parse(new TextDecoder().decode(data));}catch{throw new ApiError('Ungültige Anfrage.');}
  assert(b&&typeof b==='object'&&!Array.isArray(b),'Ungültige Anfrage.');
  const digest=await hash(token),now=new Date(),hour=now.toISOString().slice(0,13),month=now.toISOString().slice(0,7);
  const hourEnd=new Date(now.getTime()+3600000).toISOString(),monthEnd=new Date(Date.UTC(now.getUTCFullYear(),now.getUTCMonth()+1,1)).toISOString();
  // Shared ceiling stays below Free Function invocation quota during normal use.
  await limit('requests:'+month,300000,monthEnd);
  await limit('player:'+digest+':'+hour,720,hourEnd);
  let account=(await db('uw_accounts?key_hash=eq.'+digest+'&select=id,name'))[0];
  if(b.op==='register'){
   if(!account){
    const name=clean(b.name);assert(name,'Bitte einen Spielernamen eingeben.');
    await limit('accounts:all',500,'2100-01-01T00:00:00Z');
    account=(await db('uw_accounts','POST',{id:crypto.randomUUID(),key_hash:digest,name}))[0];
   }
   return reply({account:{id:account.id,name:account.name}});
  }
  assert(account,'Spielercode nicht gefunden. Bitte prüfen.',401);
  const id=account.id;
  if(b.op==='profile'){
   const save=(await db('uw_saves?account_id=eq.'+id+'&select=version,updated_at'))[0]||null;
   return reply({account,save});
  }
  if(b.op==='cloud.get'){return reply({save:(await db('uw_saves?account_id=eq.'+id+'&select=version,state,updated_at'))[0]||null});}
  if(b.op==='cloud.put'){
   const s=b.state;
   assert(s&&s.v===2&&Array.isArray(s.crew)&&s.crew.length>=1&&s.crew.length<=12&&Number.isFinite(s.cash)&&Number.isFinite(s.m)&&Number.isFinite(s.y),'Kein gültiger Einzelspieler-Spielstand.');
   assert(JSON.stringify(s).length<240000,'Spielstand ist zu groß.');
   assert(Number.isSafeInteger(b.version)&&b.version>=0,'Ungültige Speicherversion.');
   const existing=(await db('uw_saves?account_id=eq.'+id+'&select=version'))[0];
   assert((existing?.version||0)===b.version,'Auf einem anderen Gerät wurde gespeichert. Bitte Cloud-Stand prüfen.',409);
   const values={state:s,version:b.version+1,updated_at:now.toISOString()};
   let saved;
   if(existing){saved=await db('uw_saves?account_id=eq.'+id+'&version=eq.'+b.version,'PATCH',values);assert(saved.length,'Ein anderes Gerät war schneller. Bitte Cloud-Stand prüfen.',409);}
   else saved=await db('uw_saves','POST',{account_id:id,...values});
   return reply({version:saved[0].version,updated_at:saved[0].updated_at});
  }
  if(b.op==='rooms.list'){
   const rows=await db('uw_rooms?members=cs.%7B'+id+'%7D&select=id,code,host,version,state,updated_at&order=updated_at.desc&limit=20');
   return reply({rooms:rows.map(roomPublic)});
  }
  if(b.op==='rooms.create'){
   const own=await db('uw_rooms?host=eq.'+id+'&select=id,state');
   assert(own.filter((r:any)=>r.state.status!=='finished').length<3,'Maximal drei offene eigene Runden.');
   await limit('rooms:all',1000,'2100-01-01T00:00:00Z');
   const r=(await db('uw_rooms','POST',{id:crypto.randomUUID(),code:hex(8).toUpperCase(),host:id,members:[id],state:lobby(id,account.name)}))[0];return reply({room:roomPublic(r)});
  }
  if(b.op==='rooms.join'){
   const code=String(b.code||'').replace(/[\s-]/g,'').toUpperCase();assert(/^[A-F0-9]{16}$/.test(code),'Einladungscode muss 16 Zeichen haben.');
   const r=(await db('uw_rooms?code=eq.'+code))[0];assert(r,'Runde nicht gefunden.',404);
   if(r.members.includes(id))return reply({room:roomPublic(r)});
   assert(r.state.status==='lobby','Die Runde hat bereits begonnen.');assert(r.members.length<4,'Runde ist voll.');
   const state=structuredClone(r.state);state.players.push(player(id,account.name));return reply({room:await commitRoom(r,state,[...r.members,id])});
  }
  if(['rooms.get','rooms.action','rooms.leave'].includes(b.op)){
   assert(typeof b.room==='string'&&/^[0-9a-f-]{36}$/.test(b.room),'Ungültige Runde.');
   const r=(await db('uw_rooms?id=eq.'+b.room))[0];assert(r&&r.members.includes(id),'Kein Zugriff auf diese Runde.',403);
   if(b.op==='rooms.get')return reply({room:roomPublic(r)});
   assert(b.version===r.version,'Runde wurde geändert. Bitte aktualisieren.',409);
   if(b.op==='rooms.leave'){
    assert(r.state.status==='lobby','Während einer Partie bitte Aufgeben wählen.');
    const state=structuredClone(r.state);
    if(r.host===id){state.status='finished';state.log.unshift('Gastgeber hat die Lobby geschlossen.');return reply({room:await commitRoom(r,state)});}
    state.players=state.players.filter((p:any)=>p.id!==id);await commitRoom(r,state,r.members.filter((x:string)=>x!==id));return reply({left:true});
   }
   const random=()=>crypto.getRandomValues(new Uint32Array(1))[0]/4294967296;
   return reply({room:await commitRoom(r,command(r.state,id,b.action,b.arg||{},random))});
  }
  throw new ApiError('Unbekannte Anfrage.');
 }catch(e){return reply({error:e instanceof ApiError||e instanceof Error?e.message:'Anfrage fehlgeschlagen.'},e instanceof ApiError?e.status:400);}
});
