import { useEffect, useState } from "react";
import { X } from "lucide-react";

import type { Dance } from "../types";
import { Dancer } from "../components/shared";

export function DancandoOverlay({ dance, onClose }: { dance:Dance; onClose:()=>void }) {
  const [count,setCount]=useState(10);
  const [phase,setPhase]=useState<"countdown"|"go"|"playing">("countdown");

  useEffect(()=>{
    if(phase==="countdown"){
      if(count<=0){ setPhase("go"); return; }
      const t=setTimeout(()=>setCount(c=>c-1),1000);
      return ()=>clearTimeout(t);
    }
    if(phase==="go"){
      const t=setTimeout(()=>setPhase("playing"),700);
      return ()=>clearTimeout(t);
    }
  },[count,phase]);

  const radius=52, circ=2*Math.PI*radius;
  const offset=circ*(1-count/10);

  return (
    <div style={{ position:"absolute", inset:0, zIndex:20, background:"rgba(4,0,14,0.97)", display:"flex", flexDirection:"column", alignItems:"center", justifyContent:"center", animation:"fadeIn 0.2s ease-out" }}>
      <button onClick={onClose}
        style={{ position:"absolute", top:"clamp(14px,3.5%,22px)", right:"clamp(14px,4%,22px)", width:30, height:30, borderRadius:"50%", background:"rgba(255,255,255,0.07)", border:"1px solid rgba(255,255,255,0.14)", display:"flex", alignItems:"center", justifyContent:"center", cursor:"pointer", color:"rgba(255,255,255,0.45)", transition:"all 0.15s" }}
        onMouseEnter={e=>{(e.currentTarget as HTMLElement).style.background="rgba(255,255,255,0.15)";(e.currentTarget as HTMLElement).style.color="#fff";}}
        onMouseLeave={e=>{(e.currentTarget as HTMLElement).style.background="rgba(255,255,255,0.07)";(e.currentTarget as HTMLElement).style.color="rgba(255,255,255,0.45)";}}>
        <X size={14} strokeWidth={2}/>
      </button>

      {phase==="countdown"&&(
        <>
          <svg width="148" height="148" style={{ position:"absolute" }}>
            <circle cx="74" cy="74" r={radius} fill="none" stroke="rgba(124,58,237,0.14)" strokeWidth="5"/>
            <circle cx="74" cy="74" r={radius} fill="none" stroke={dance.ring} strokeWidth="5"
              strokeLinecap="round" strokeDasharray={circ} strokeDashoffset={offset}
              transform="rotate(-90 74 74)" style={{ transition:"stroke-dashoffset 0.92s linear" }}/>
          </svg>
          <span key={count} style={{ fontFamily:"'Outfit',sans-serif", fontWeight:900, fontSize:"5.5rem", color:"#fff", lineHeight:1, animation:"countPop 0.35s cubic-bezier(0.34,1.56,0.64,1)", textShadow:`0 0 40px ${dance.ring},0 0 80px ${dance.ring}60`, zIndex:1 }}>
            {count}
          </span>
          <span style={{ marginTop:16, fontFamily:"'Outfit',sans-serif", fontWeight:500, fontSize:"0.7rem", color:"rgba(167,139,250,0.45)", letterSpacing:"0.2em", textTransform:"uppercase" }}>
            Prepare-se
          </span>
        </>
      )}

      {phase==="go"&&(
        <span style={{ fontFamily:"'Outfit',sans-serif", fontWeight:900, fontSize:"4.2rem", color:dance.ring, animation:"countPop 0.4s cubic-bezier(0.34,1.56,0.64,1)", textShadow:`0 0 50px ${dance.ring},0 0 100px ${dance.ring}80`, letterSpacing:"0.1em" }}>
          VAI!
        </span>
      )}

      {phase==="playing"&&(
        <div style={{ display:"flex", flexDirection:"column", alignItems:"center", gap:18, width:"80%" }}>
          <div style={{ width:"60%", aspectRatio:"9/16", borderRadius:16, background:dance.gradient, border:`1.5px solid ${dance.ring}55`, position:"relative", overflow:"hidden", boxShadow:`0 0 40px ${dance.ring}40` }}>
            <div style={{ position:"absolute", bottom:"18%", left:0, right:0, display:"flex", justifyContent:"center" }}>
              <Dancer color={dance.ring} opacity={0.22}/>
            </div>
            <div style={{ position:"absolute", bottom:12, left:0, right:0, display:"flex", justifyContent:"center", gap:3, alignItems:"flex-end", height:22 }}>
              {[0,1,2,3,4,5,6].map(i=><div key={i} style={{ width:3, borderRadius:2, background:dance.ring, animation:`bar 0.6s ease-in-out ${i*0.09}s infinite alternate` }}/>)}
            </div>
          </div>
          <p style={{ fontFamily:"'Outfit',sans-serif", fontSize:"0.7rem", color:"rgba(167,139,250,0.45)", letterSpacing:"0.15em", textTransform:"uppercase" }}>
            Reproduzindo · {dance.name}
          </p>
        </div>
      )}
    </div>
  );
}
