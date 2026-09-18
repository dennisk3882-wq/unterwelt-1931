(async()=>{
  try{
    const parts=await Promise.all(Array.from({length:4},(_,i)=>fetch('empire/part'+i+'.gz.b64',{cache:'no-cache'}).then(r=>{
      if(!r.ok)throw new Error('Empire part '+i+': '+r.status);
      return r.text();
    })));
    const b64=parts.join('').replace(/\s+/g,'');
    const bin=atob(b64);
    const bytes=Uint8Array.from(bin,c=>c.charCodeAt(0));
    if(typeof DecompressionStream!=='function')throw new Error('GZIP-Dekomprimierung wird von diesem Browser nicht unterstützt.');
    const stream=new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip'));
    const code=await new Response(stream).text();
    (0,eval)(code);
    document.documentElement.classList.add('empire-ready');
    window.dispatchEvent(new CustomEvent('unterwelt-empire-ready',{detail:{version:window.UNTERWELT_EMPIRE?.version||0}}));
  }catch(err){
    console.error('Empire Update konnte nicht geladen werden',err);
    document.documentElement.classList.add('empire-load-error');
  }
})();