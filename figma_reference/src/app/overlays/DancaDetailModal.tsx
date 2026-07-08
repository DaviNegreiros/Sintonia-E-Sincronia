import { useEffect, useState } from "react";
import { Pause, Play, Sparkles, Trash2 } from "lucide-react";

import type { Dance } from "../types";
import { BackBtn, Dancer, Hairline } from "../components/shared";

export function DancaDetailModal({ dance, onBack, onDancar, onDelete }: { dance:Dance; onBack:()=>void; onDancar:()=>void; onDelete:()=>void }) {
  const [mounted,setMounted]=useState(false);
  const [playing,setPlaying]=useState(false);
  const [confirmDelete,setConfirmDelete]=useState(false);
  useEffect(()=>{ const id=requestAnimationFrame(()=>setMounted(true)); return ()=>cancelAnimationFrame(id); },[]);

  return (
    <div style={{ position:"absolute", inset:0, zIndex:10, background:"linear-gradient(180deg,#160730 0%,#0c051e 60%,#080014 100%)", transform:mounted?"translateY(0)":"translateY(100%)", transition:"transform 0.3s cubic-bezier(0.32,0.72,0,1)", display:"flex", flexDirection:"column" }}>
      <div style={{ flexShrink:0, display:"flex", alignItems:"center", gap:12, padding:"clamp(14px,3.5%,22px) clamp(16px,5%,24px)" }}>
        <BackBtn onClick={onBack}/>
        <span style={{ flex:1, fontFamily:"'Outfit',sans-serif", fontWeight:700, fontSize:"0.95rem", color:"#e9d5ff" }}>{dance.name}</span>
        <button onClick={()=>setConfirmDelete(true)}
          style={{ width:32, height:32, borderRadius:8, display:"flex", alignItems:"center", justifyContent:"center", background:"rgba(255,30,80,0.1)", border:"1px solid rgba(255,50,90,0.3)", color:"#ff2d55", cursor:"pointer", transition:"all 0.15s", flexShrink:0 }}
          onMouseEnter={e=>{(e.currentTarget as HTMLElement).style.background="rgba(255,30,80,0.22)";(e.currentTarget as HTMLElement).style.boxShadow="0 0 12px rgba(255,45,85,0.5)";}}
          onMouseLeave={e=>{(e.currentTarget as HTMLElement).style.background="rgba(255,30,80,0.1)";(e.currentTarget as HTMLElement).style.boxShadow="none";}}
          aria-label="Deletar dança">
          <Trash2 size={15} strokeWidth={2}/>
        </button>
      </div>
      <Hairline/>

      {confirmDelete&&(
        <div style={{ position:"absolute", inset:0, zIndex:20, background:"rgba(4,0,14,0.82)", backdropFilter:"blur(6px)", display:"flex", alignItems:"center", justifyContent:"center", padding:"0 clamp(20px,7%,36px)", animation:"fadeIn 0.18s ease-out" }}>
          <div style={{ width:"100%", borderRadius:20, background:"linear-gradient(150deg,#1a0830 0%,#230d50 100%)", border:"1px solid rgba(255,45,85,0.35)", boxShadow:"0 16px 48px rgba(0,0,0,0.7),0 0 0 1px rgba(255,45,85,0.1)", padding:"clamp(20px,5%,28px)" }}>
            <div style={{ width:48, height:48, borderRadius:"50%", background:"rgba(255,30,80,0.14)", border:"1.5px solid rgba(255,45,85,0.4)", display:"flex", alignItems:"center", justifyContent:"center", margin:"0 auto 16px", boxShadow:"0 0 20px rgba(255,45,85,0.3)" }}>
              <Trash2 size={22} color="#ff2d55" strokeWidth={1.8}/>
            </div>
            <p style={{ fontFamily:"'Outfit',sans-serif", fontWeight:700, fontSize:"1rem", color:"#f0eaff", textAlign:"center", marginBottom:8 }}>Apagar dança?</p>
            <p style={{ fontFamily:"'DM Sans',sans-serif", fontSize:"0.8rem", color:"rgba(196,181,253,0.65)", textAlign:"center", lineHeight:1.5, marginBottom:22 }}>
              Tem certeza que deseja apagar <strong style={{ color:"#e9d5ff" }}>{dance.name}</strong>? Esta ação não pode ser desfeita.
            </p>
            <div style={{ display:"flex", gap:10 }}>
              <button onClick={()=>setConfirmDelete(false)}
                style={{ flex:1, padding:"11px 0", borderRadius:12, background:"rgba(124,58,237,0.12)", border:"1px solid rgba(124,58,237,0.35)", color:"#c4b5fd", fontFamily:"'Outfit',sans-serif", fontWeight:600, fontSize:"0.85rem", cursor:"pointer", transition:"all 0.15s" }}
                onMouseEnter={e=>{(e.currentTarget as HTMLElement).style.background="rgba(124,58,237,0.22)";}}
                onMouseLeave={e=>{(e.currentTarget as HTMLElement).style.background="rgba(124,58,237,0.12)";}}>
                Cancelar
              </button>
              <button onClick={onDelete}
                style={{ flex:1, padding:"11px 0", borderRadius:12, background:"linear-gradient(135deg,#b91c1c,#ff2d55)", border:"1px solid rgba(255,45,85,0.4)", color:"#fff", fontFamily:"'Outfit',sans-serif", fontWeight:700, fontSize:"0.85rem", cursor:"pointer", boxShadow:"0 4px 16px rgba(255,45,85,0.35)", transition:"all 0.15s" }}
                onMouseEnter={e=>{(e.currentTarget as HTMLElement).style.boxShadow="0 4px 22px rgba(255,45,85,0.55)";}}
                onMouseLeave={e=>{(e.currentTarget as HTMLElement).style.boxShadow="0 4px 16px rgba(255,45,85,0.35)";}}>
                Apagar
              </button>
            </div>
          </div>
        </div>
      )}

      <div style={{ flex:1, display:"flex", flexDirection:"column", alignItems:"center", padding:"clamp(16px,4%,24px) clamp(24px,8%,40px) 0", gap:14, overflow:"hidden" }}>
        <div style={{ width:"58%", aspectRatio:"9/16", flexShrink:0, position:"relative", borderRadius:16, overflow:"hidden", background:dance.gradient, border:"1.5px solid rgba(124,58,237,0.35)", boxShadow:"0 8px 32px rgba(0,0,0,0.5)" }}>
          <div style={{ position:"absolute", bottom:"18%", left:0, right:0, display:"flex", justifyContent:"center" }}>
            <Dancer color={dance.ring} opacity={0.2}/>
          </div>
          {playing&&(
            <div style={{ position:"absolute", bottom:12, left:0, right:0, display:"flex", justifyContent:"center", gap:3, alignItems:"flex-end", height:22 }}>
              {[0,1,2,3,4].map(i=><div key={i} style={{ width:3, borderRadius:2, background:dance.ring, animation:`bar 0.65s ease-in-out ${i*0.12}s infinite alternate` }}/>)}
            </div>
          )}
          <button onClick={()=>setPlaying(v=>!v)}
            style={{ position:"absolute", top:"50%", left:"50%", transform:"translate(-50%,-50%)", width:48, height:48, borderRadius:"50%", background:"rgba(0,0,0,0.55)", border:"2px solid rgba(255,255,255,0.28)", display:"flex", alignItems:"center", justifyContent:"center", cursor:"pointer", backdropFilter:"blur(4px)", transition:"all 0.15s" }}>
            {playing
              ? <Pause size={20} color="white" fill="white"/>
              : <Play  size={20} color="white" fill="white" style={{ marginLeft:2 }}/>}
          </button>
        </div>
      </div>

      <div style={{ flexShrink:0, padding:"clamp(14px,3.5%,22px) clamp(24px,8%,40px)", display:"flex", justifyContent:"center" }}>
        <button onClick={onDancar}
          style={{ padding:"13px 52px", borderRadius:50, background:"linear-gradient(135deg,#5b21b6,#7c3aed)", border:"1px solid rgba(167,139,250,0.3)", color:"#fff", fontFamily:"'Outfit',sans-serif", fontWeight:700, fontSize:"0.95rem", letterSpacing:"0.06em", textTransform:"uppercase", cursor:"pointer", boxShadow:"0 4px 24px rgba(124,58,237,0.4)", display:"flex", alignItems:"center", gap:8 }}>
          <Sparkles size={15}/> Dançar
        </button>
      </div>
    </div>
  );
}
