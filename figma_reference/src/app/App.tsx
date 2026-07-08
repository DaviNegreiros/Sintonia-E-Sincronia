import { useState } from "react";

import { DANCES } from "./mockDances";
import type { Dance, Page } from "./types";
import { DancaDetailModal, DancandoOverlay } from "./overlays";
import { DancasSalvasScreen, HomeScreen, NovaDancaScreen } from "./screens";
import { APP_KEYFRAMES } from "./styles/keyframes";

export default function App() {
  const [page,setPage]=useState<Page>("home");
  const [dances,setDances]=useState<Dance[]>(DANCES);
  const [selected,setSelected]=useState<Dance|null>(null);
  const [dancing,setDancing]=useState(false);

  function selectDance(d:Dance){ setSelected(d); setDancing(false); }
  function closeAll(){ setSelected(null); setDancing(false); }
  function deleteDance(){
    if(!selected) return;
    setDances(ds=>ds.filter(d=>d.id!==selected.id));
    closeAll();
  }

  return (
    <div className="w-screen h-screen flex items-center justify-center" style={{ background:"#080014" }}>
      <div style={{ position:"relative", aspectRatio:"9/16", height:"min(100vh, calc(100vw * 16 / 9))", width:"min(100vw, calc(100vh * 9 / 16))", background:"linear-gradient(180deg,#160730 0%,#0c051e 55%,#080014 100%)", borderRadius:"clamp(0px,1.5vw,20px)", boxShadow:"0 0 0 1px rgba(124,58,237,0.12),0 32px 80px rgba(0,0,0,0.75)", overflow:"hidden" }}>

        {page==="home"          && <HomeScreen onNavigate={setPage}/>}
        {page==="nova-danca"    && <NovaDancaScreen onBack={()=>setPage("home")}/>}
        {page==="dancas-salvas" && <DancasSalvasScreen onBack={()=>setPage("home")} onSelect={selectDance} dances={dances}/>}

        {selected&&!dancing && <DancaDetailModal dance={selected} onBack={closeAll} onDancar={()=>setDancing(true)} onDelete={deleteDance}/>}
        {selected&&dancing  && <DancandoOverlay  dance={selected} onClose={closeAll}/>}
      </div>

      <style>{APP_KEYFRAMES}</style>
    </div>
  );
}
