import { useEffect, useRef, useState } from "react";
import { Info } from "lucide-react";

export function InfoPopover() {
  const [open,setOpen]=useState(false);
  const ref=useRef<HTMLDivElement>(null);
  useEffect(()=>{
    const fn=(e:MouseEvent)=>{ if(ref.current&&!ref.current.contains(e.target as Node))setOpen(false); };
    document.addEventListener("mousedown",fn);
    return ()=>document.removeEventListener("mousedown",fn);
  },[]);
  return (
    <div style={{ position:"relative", flexShrink:0 }} ref={ref}>
      <button onClick={()=>setOpen(v=>!v)}
        style={{ width:30, height:30, borderRadius:"50%", display:"flex", alignItems:"center", justifyContent:"center", background:open?"rgba(139,92,246,0.35)":"rgba(139,92,246,0.14)", border:`1.5px solid ${open?"rgba(167,139,250,0.7)":"rgba(139,92,246,0.4)"}`, color:open?"#e9d5ff":"#a78bfa", cursor:"pointer", transition:"all 0.15s" }}>
        <Info size={13} strokeWidth={2.3}/>
      </button>
      {open&&(
        <div style={{ position:"absolute", top:"calc(100% + 8px)", left:0, zIndex:50, width:238, padding:"13px 15px", borderRadius:16, background:"linear-gradient(150deg,#180840,#220d58)", border:"1px solid rgba(124,58,237,0.5)", boxShadow:"0 12px 40px rgba(0,0,0,0.55)" }}>
          <p style={{ color:"#c4b5fd", fontSize:"0.68rem", fontFamily:"'Outfit',sans-serif", fontWeight:600, letterSpacing:"0.12em", textTransform:"uppercase", marginBottom:9 }}>Para melhor resultado:</p>
          <ul style={{ display:"flex", flexDirection:"column", gap:7 }}>
            {["A câmera está parada","O corpo do dançarino aparece por inteiro","Boa iluminação","Boa qualidade do vídeo"].map((item,i)=>(
              <li key={i} style={{ display:"flex", alignItems:"flex-start", gap:8 }}>
                <span style={{ flexShrink:0, width:15, height:15, borderRadius:"50%", background:"rgba(124,58,237,0.4)", color:"#e9d5ff", fontSize:"0.58rem", fontWeight:700, display:"flex", alignItems:"center", justifyContent:"center", marginTop:1, fontFamily:"'Outfit',sans-serif" }}>{i+1}</span>
                <span style={{ color:"#ddd6fe", fontSize:"0.78rem", lineHeight:1.45, fontFamily:"'DM Sans',sans-serif" }}>{item}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
