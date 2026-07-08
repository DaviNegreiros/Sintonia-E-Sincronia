import { useState } from "react";
import type { ReactNode } from "react";
import { ChevronRight } from "lucide-react";

export function PillBtn({ label, icon, primary, onClick }: { label:string; icon:ReactNode; primary:boolean; onClick?:()=>void }) {
  const [h,setH]=useState(false);
  return (
    <button onClick={onClick} onMouseEnter={()=>setH(true)} onMouseLeave={()=>setH(false)}
      style={{ width:"100%", display:"flex", alignItems:"center", justifyContent:"space-between", padding:"11px 15px", borderRadius:13, border:primary?"1px solid rgba(167,139,250,0.28)":`1px solid ${h?"rgba(139,92,246,0.55)":"rgba(124,58,237,0.35)"}`, background:primary?(h?"linear-gradient(135deg,#6d28d9,#8b5cf6)":"linear-gradient(135deg,#5b21b6,#7c3aed)"):(h?"rgba(124,58,237,0.12)":"rgba(124,58,237,0.06)"), boxShadow:primary?(h?"0 6px 22px rgba(124,58,237,0.5)":"0 3px 16px rgba(124,58,237,0.28)"):"none", color:primary?"#fff":(h?"#ddd6fe":"#c4b5fd"), fontFamily:"'Outfit',sans-serif", fontWeight:600, fontSize:"0.9rem", letterSpacing:"0.02em", cursor:"pointer", transition:"all 0.15s", transform:h?"scale(0.988)":"scale(1)" }}>
      <span style={{ display:"flex", alignItems:"center", gap:9 }}>
        <span style={{ width:30, height:30, borderRadius:8, background:primary?"rgba(255,255,255,0.15)":"rgba(124,58,237,0.22)", display:"flex", alignItems:"center", justifyContent:"center", flexShrink:0 }}>{icon}</span>
        {label}
      </span>
      <ChevronRight size={15} style={{ opacity:h?0.8:0.35 }}/>
    </button>
  );
}
