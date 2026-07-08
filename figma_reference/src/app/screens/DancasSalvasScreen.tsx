import { useState } from "react";
import { Play } from "lucide-react";

import { getRankStyle } from "../data";
import type { Dance } from "../types";
import { BackBtn, Dancer, Hairline } from "../components/shared";

export function DancasSalvasScreen({ onBack, onSelect, dances }: { onBack:()=>void; onSelect:(d:Dance)=>void; dances:Dance[] }) {
  return (
    <div style={{ height:"100%", display:"flex", flexDirection:"column" }}>
      <div style={{ flexShrink:0, display:"flex", alignItems:"center", gap:12, padding:"clamp(14px,3.5%,22px) clamp(16px,5%,24px)" }}>
        <BackBtn onClick={onBack}/>
        <span style={{ fontFamily:"'Outfit',sans-serif", fontWeight:700, fontSize:"1rem", color:"#e9d5ff", letterSpacing:"0.04em" }}>Danças Salvas</span>
      </div>
      <Hairline/>
      <div style={{ flex:1, overflowY:"auto", overflowX:"hidden", padding:"clamp(12px,3%,18px) clamp(14px,4%,20px)", scrollbarWidth:"none" }}>
        {dances.length===0
          ? <div style={{ height:"100%", display:"flex", alignItems:"center", justifyContent:"center" }}>
              <p style={{ fontFamily:"'Outfit',sans-serif", fontSize:"0.8rem", color:"rgba(167,139,250,0.35)", letterSpacing:"0.1em" }}>Nenhuma dança salva</p>
            </div>
          : <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:"clamp(10px,2.5%,14px)" }}>
              {dances.map(d=><DanceCard key={d.id} dance={d} onClick={()=>onSelect(d)}/>)}
            </div>
        }
      </div>
    </div>
  );
}

function DanceCard({ dance, onClick }: { dance:Dance; onClick:()=>void }) {
  const [h,setH]=useState(false);
  const rankStyle = getRankStyle(dance.rank);
  return (
    <div onClick={onClick} onMouseEnter={()=>setH(true)} onMouseLeave={()=>setH(false)}
      style={{ aspectRatio:"9/16", borderRadius:14, overflow:"hidden", position:"relative", cursor:"pointer", background:dance.gradient, border:`1px solid ${h?"rgba(167,139,250,0.35)":"rgba(124,58,237,0.2)"}`, boxShadow:h?"0 8px 24px rgba(0,0,0,0.5)":"0 2px 10px rgba(0,0,0,0.35)", transform:h?"scale(1.025)":"scale(1)", transition:"all 0.18s" }}>
      <div style={{ position:"absolute", bottom:"20%", left:0, right:0, display:"flex", justifyContent:"center" }}>
        <Dancer color={dance.ring} opacity={h?0.22:0.14}/>
      </div>
      <div style={{ position:"absolute", bottom:0, left:0, right:0, padding:"22px 8px 9px", background:"linear-gradient(to top,rgba(0,0,0,0.78) 0%,transparent 100%)" }}>
        <p style={{ fontFamily:"'Outfit',sans-serif", fontWeight:600, fontSize:"0.68rem", color:"#f0eaff", lineHeight:1.3, textAlign:"center", marginBottom:5 }}>{dance.name}</p>
        <div style={{ display:"flex", alignItems:"center", justifyContent:"center", gap:5 }}>
          <span style={{ fontFamily:"'Outfit',sans-serif", fontSize:"0.56rem", color:"rgba(167,139,250,0.6)", letterSpacing:"0.06em", fontWeight:500 }}>Melhor Ranque</span>
          <span style={{ fontFamily:"'Outfit',sans-serif", fontWeight:800, fontSize:"0.65rem", color:rankStyle.color, background:rankStyle.bg, border:`1px solid ${rankStyle.border}`, borderRadius:5, padding:"1px 5px", letterSpacing:"0.04em", lineHeight:1.5 }}>
            {dance.rank}
          </span>
        </div>
      </div>
      <div style={{ position:"absolute", top:"38%", left:"50%", transform:"translate(-50%,-50%)", width:32, height:32, borderRadius:"50%", background:"rgba(124,58,237,0.4)", border:"1.5px solid rgba(167,139,250,0.4)", display:"flex", alignItems:"center", justifyContent:"center", opacity:h?1:0, transition:"opacity 0.18s" }}>
        <Play size={12} fill="white" color="white"/>
      </div>
    </div>
  );
}
