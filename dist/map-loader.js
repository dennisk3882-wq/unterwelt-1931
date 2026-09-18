(()=>{'use strict';
const PARTS=Array.from({length:7},(_,i)=>'assets/map/map.part'+i+'.b64');

async function loadCityMap(){
  try{
    const chunks=await Promise.all(PARTS.map(async path=>{
      const response=await fetch(path,{cache:'force-cache'});
      if(!response.ok)throw new Error('Kartenasset fehlt: '+path);
      return (await response.text()).trim();
    }));
    const raw=atob(chunks.join(''));
    const bytes=new Uint8Array(raw.length);
    for(let i=0;i<raw.length;i++)bytes[i]=raw.charCodeAt(i);
    const objectUrl=URL.createObjectURL(new Blob([bytes],{type:'image/webp'}));
    document.documentElement.style.setProperty('--city-map','url("'+objectUrl+'")');
    document.documentElement.classList.add('city-map-ready');
    addEventListener('pagehide',()=>URL.revokeObjectURL(objectUrl),{once:true});
  }catch(error){
    console.error('Stadtkarte konnte nicht geladen werden.',error);
    document.documentElement.classList.add('city-map-fallback');
  }
}

loadCityMap();
})();