const fs=require('fs'),vm=require('vm'),assert=require('node:assert/strict');
const nodes=new Map();
function element(){return {open:false,innerHTML:'',textContent:'',hidden:false,disabled:false,style:{},dataset:{},classList:{add(){},remove(){},contains(){return false}},append(){},prepend(){},remove(){},setAttribute(){},addEventListener(){},querySelector(){return null},querySelectorAll(){return []},showModal(){this.open=true},close(){this.open=false},click(){},scrollTo(){}}}
const el=k=>{if(!nodes.has(k))nodes.set(k,element());return nodes.get(k)};const data=new Map();
const math=Object.create(Math);math.random=()=>0;
const context={console,Math:math,JSON,Number,String,Array,Object,Set,Map,Date,Boolean,Error,Blob,URL,setTimeout:()=>{},clearTimeout(){},setInterval:()=>0,clearInterval(){},confirm:()=>true,localStorage:{getItem:k=>data.get(k)||null,setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)},navigator:{},location:{reload(){}},crypto:{getRandomValues:a=>a.fill(1)},document:{querySelector:el,querySelectorAll:()=>[],createElement:element,addEventListener(){},body:{append(){}}},addEventListener(){}};
for(const id of ['date','cash','rank','heat','crewCount','steps','location','map','mapFrame','panel','modal','modalTitle','modalBody','battle','battleTitle','battleBody','install','endTurn','newGame','br','fl','yi','toast','online'])context[id]=el('#'+id);
context.window=context;vm.createContext(context);
const run=code=>vm.runInContext(code,context);
run(fs.readFileSync('dist/app.js','utf8'));run(fs.readFileSync('dist/rules.js','utf8'));
function reset(){run('campaignActive=true;S=upgrade(fresh());selected=S.loc;S.over=false;modal.close();battle.close();Math.random=()=>0;save()')}
function test(name,code){reset();run(code);console.log('PASS',name)}

test('1931 campaign migration and new game','if(S.y!==1931||S.m!==1)throw Error("timeline");if(!S.log[0].includes("1931"))throw Error("log")');
test('invalid actions never consume AP','S.score=50;S.ap=6;S.crew[0].intel=40;action("safe");if(S.ap!==6)throw Error("safe AP");S.score=0;action("protect");if(S.ap!==6)throw Error("protect AP");action("breakout");if(S.ap!==6)throw Error("breakout AP")');
test('hotel charges only at month end','S.cash=450;S.ap=12;action("hotel1");if(S.cash!==450||S.hotel!==1||S.ap!==11)throw Error("hotel upfront");nextMonth();if(S.cash!==350)throw Error("rent")');
test('balanced credit and repayment','S.cash=1000;S.ap=12;action("borrow");action("borrow");let before=S.ap;action("borrow");if(S.debt!==2300||S.ap!==before)throw Error("credit limit");action("repay");if(S.debt!==1800)throw Error("repay")');
test('alcohol trade requires geography','S.cash=450;S.ap=12;selected=S.loc="pub";var apAlcohol=S.ap;action("booze");if(S.ap!==apAlcohol||S.booze!==0)throw Error("pub buy");selected=S.loc="station";action("booze");if(S.booze!==1||S.cash!==385)throw Error("station buy");S.market.pub=130;selected=S.loc="pub";action("sell");if(S.booze!==0||S.cash!==515||S.score!==1)throw Error("pub sell")');
test('passport is persistent useful asset and not repurchased','S.cash=1200;S.heat=80;S.ap=12;action("passport");if(!S.passport||S.cash!==700||S.heat!==45)throw Error("passport");var apPassport=S.ap;action("passport");if(S.ap!==apPassport||S.cash!==700)throw Error("duplicate passport")');
test('counterfeit loop is profitable but risky','S.cash=1000;S.ap=12;action("fake");if(S.fake!==180||S.cash!==900)throw Error("fake buy");action("spendfake");if(S.fake!==120||S.cash!==970)throw Error("fake spend")');
test('shop and territories are separate','S.score=50;S.ap=12;action("protect");if(!S.shopOwned||S.territories.reduce((a,n)=>a+n,0)!==0)throw Error("ownership")');
test('family war captures exactly one territory','S.rivals[0][2]=3;S.territories=[0,0,0];S.encounter={name:S.rivals[0][0]};battle.open=true;finishBattle(true);if(S.rivals[0][2]!==2||S.territories[0]!==1)throw Error("territory count")');
test('monthly economy uses 4 percent interest and business income','S.cash=0;S.debt=1000;S.shopOwned=true;S.territories=[1,0,0];S.loan=true;S.invest=1000;S.hotel=0;S.crew=S.crew.slice(0,1);nextMonth();if(S.cash!==410||S.debt!==1040)throw Error("month economy")');
test('ledger records cash movements','S.cash=1000;S.ledger=[];S.ledgerAnchor=1000;S.ap=12;action("borrow");if(!S.ledger.length||S.ledger[0].amount!==1000)throw Error("ledger")');
test('blackjack supports card labels and natural payout','if(cardLabel(1)!=="A"||cardLabel(11)!=="J"||cardLabel(12)!=="Q"||cardLabel(13)!=="K")throw Error("labels");S.cash=0;S.ledgerAnchor=0;S.cards={dealer:[10,7],deck:[],hands:[{cards:[1,13],bet:100,done:true}],active:0,split:false};settleCards();if(S.cash!==250)throw Error("blackjack payout")');
test('tactical enemies have differentiated roles','beginFight("Polizeieskorte",4);let roles=S.encounter.enemies.map(e=>e.role);if(!roles.some(x=>x==="Polizeischütze")||!roles.some(x=>x==="Polizist"))throw Error("roles")');
test('campaign win path','S.score=100;S.mayorDone=true;S.transportDone=true;save();if(S.over!=="win")throw Error("win")');
test('campaign time loss path','S.y=1934;S.over=false;save();if(S.over!=="lose")throw Error("time lose")');
test('campaign debt loss path','S.debt=15001;S.over=false;save();if(S.over!=="lose")throw Error("debt lose")');
test('AP prevents free healing','S.ap=1;action("rest");action("rest");if(S.ap!==0)throw Error("AP")');
test('police persists and blocks movement','S.control={price:200};save();let loc=S.loc;visit("pub");if(S.loc!==loc||!JSON.parse(localStorage.getItem("uw1931")).control)throw Error("control")');
test('jail sentence expires','S.crew[0].jailed=true;S.crew[0].jailMonths=1;nextMonth();if(S.crew[0].jailed)throw Error("jail")');
test('ace scoring','if(total([1,1,9])!==21||total([1,13,5])!==16)throw Error("cards")');

const zlib=require('node:zlib'),nodeCrypto=require('node:crypto');
const empire64=Array.from({length:4},(_,i)=>fs.readFileSync('dist/empire/part'+i+'.gz.b64','utf8').trim()).join('');
const empireSource=zlib.gunzipSync(Buffer.from(empire64,'base64')).toString('utf8');
console.log('EMPIRE_SOURCE_SHA',nodeCrypto.createHash('sha256').update(empireSource).digest('hex'));
new vm.Script(empireSource);new vm.Script(fs.readFileSync('dist/empire-loader.js','utf8'));
run(empireSource);

test('Empire state migrates with 12 districts and active rival AI','if(S.empire.v!==11)throw Error("empire version");if(S.empire.districts.length!==12)throw Error("district count");if(S.empire.rivalAI.length!==3)throw Error("rivals");if(S.empire.districts.filter(d=>d.owner==="player").length<1)throw Error("starting district")');
test('Empire exposes eight businesses, eight vehicles and five black-market goods','if(Object.keys(UNTERWELT_EMPIRE.businesses).length!==8)throw Error("businesses");if(Object.keys(UNTERWELT_EMPIRE.vehicles).length!==8)throw Error("vehicles");if(Object.keys(UNTERWELT_EMPIRE.goods).length!==5)throw Error("goods")');
test('crew receives role, trait, loyalty, XP and injury model','let c=S.crew[0];if(!c.role||!c.trait||typeof c.loyalty!=="number"||typeof c.xp!=="number"||typeof c.level!=="number"||typeof c.injuryMonths!=="number")throw Error("crew depth")');
test('difficulty modes include hardcore permanent-death rules','if(Object.keys(UNTERWELT_DIFFICULTIES).length!==4)throw Error("difficulty count");S.empire.difficulty="hardcore";if(UNTERWELT_DIFFICULTIES.hardcore.death!==1)throw Error("hardcore")');
test('endless mode removes the 1934 time loss','S.empire.campaignWon=true;S.empire.endless=true;S.y=1940;S.over=false;checkEnd();if(S.over)throw Error("endless ended")');
test('tactical Empire combat adds arenas, cover and ammunition','beginFight("Bankwachen",2);if(S.encounter.arena!=="Bankhalle")throw Error("arena");if(!S.encounter.coverType||!Object.keys(S.encounter.coverType).length)throw Error("cover");if(!S.encounter.units.every(u=>typeof u.ammo==="number"))throw Error("ammo")');
test('Empire achievements and statistics are persistent','S.empire.stats.fightsWon=1;UNTERWELT_EMPIRE.checkAchievements();if(!S.empire.ach.includes("firstFight"))throw Error("achievement");if(typeof S.empire.stats.cashPeak!=="number")throw Error("stats")');
test('Empire cargo and smuggling capacity are vehicle-aware','S.car="truck";S.empire.businesses=[];if(UNTERWELT_EMPIRE.cargoCap()!==18)throw Error("truck cargo");if(!S.empire.suppliers.canada)throw Error("supplier")');
console.log('PASS Empire Update systems 1-14');

new vm.Script(fs.readFileSync('dist/online.js','utf8'));new vm.Script(fs.readFileSync('dist/menu.js','utf8'));
const deployedHtml=fs.readFileSync('dist/index.html','utf8');
const deployedSw=fs.readFileSync('dist/sw.js','utf8');
assert.match(deployedHtml,/id="menuScreen"/);
assert.match(deployedHtml,/id="setupScreen"/);
assert.match(deployedHtml,/id="gameScreen"/);
assert.match(deployedHtml,/id="districtGrid"/);
assert.match(deployedHtml,/id="businessList"/);
assert.match(deployedHtml,/id="missionsView"/);
assert.match(deployedHtml,/Vollausbau 2\.0/);
assert.match(deployedHtml,/manifest\.webmanifest/);
assert.match(deployedHtml,/navigator\.serviceWorker\.register/);
assert.match(deployedSw,/unterwelt-syndikat-v20/);
assert.match(deployedSw,/index\.html/);
assert.match(fs.readFileSync('dist/rules.js','utf8'),/Gameplay revision 8/);
assert.match(fs.readFileSync('dist/rules.js','utf8'),/Kreditgeschäft verkaufen/);
assert.match(fs.readFileSync('dist/rules.js','utf8'),/Double Down/);
assert.match(fs.readFileSync('dist/rules.js','utf8'),/Polizeischütze/);
const mapBytes=fs.readFileSync('dist/assets/map/unterwelt-city-map.webp');
assert.equal(mapBytes.subarray(0,4).toString(),'RIFF');assert.equal(mapBytes.subarray(8,12).toString(),'WEBP');
assert.ok(mapBytes.length>350000&&mapBytes.length<450000,'map asset unexpected size');
console.log('PASS purpose-built interactive Unterwelt map');
require('node:child_process').execFileSync(process.execPath,['tests/multiplayer.mjs'],{stdio:'inherit'});
console.log('All revision-11 gameplay, Empire, economy, campaign and multiplayer rule checks passed. DOM mocked; live backend is verified separately.');