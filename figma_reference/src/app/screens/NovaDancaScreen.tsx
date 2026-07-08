import { useRef, useState } from "react";
import { Plus, Sparkles } from "lucide-react";

import { BackBtn, Hairline, Logo } from "../components/shared";

export function NovaDancaScreen({ onBack }: { onBack:()=>void }) {
  const [name,setName]=useState("");
  const [videoUrl,setVideoUrl]=useState<string|null>(null);
  const fileRef=useRef<HTMLInputElement>(null);
  function handleFile(e:React.ChangeEvent<HTMLInputElement>){
    const f=e.target.files?.[0]; if(f)setVideoUrl(URL.createObjectURL(f));
  }
  return (
    <div style={{ height:"100%", display:"flex", flexDirection:"column" }}>
      <div style={{ position:"absolute", inset:0, pointerEvents:"none", background:"radial-gradient(ellipse 80% 40% at 50% -5%,rgba(139,92,246,0.1) 0%,transparent 65%)" }}/>
      <BackBtn onClick={onBack} style={{ position:"absolute", top:"clamp(12px,3%,20px)", left:"clamp(12px,4%,20px)", zIndex:10 }}/>
      <div style={{ height:"18%", position:"relative", zIndex:1, display:"flex", alignItems:"center" }}>
        <Logo scale={0.72}/>
      </div>
      <Hairline/>
      <div style={{ flex:1, overflowY:"auto", overflowX:"hidden", padding:"clamp(16px,3.5%,24px) clamp(24px,8%,40px)", display:"flex", flexDirection:"column", alignItems:"center", gap:"clamp(14px,3%,20px)", scrollbarWidth:"none" }}>
        <input value={name} onChange={e=>setName(e.target.value)} placeholder="Nome da dança"
          style={{ width:"100%", padding:"11px 16px", borderRadius:12, background:"rgba(124,58,237,0.07)", border:"1px solid rgba(124,58,237,0.32)", color:"#f0eaff", fontFamily:"'Outfit',sans-serif", fontWeight:500, fontSize:"0.9rem", outline:"none", transition:"border-color 0.15s", flexShrink:0 }}
          onFocus={e=>{e.currentTarget.style.borderColor="rgba(139,92,246,0.7)"}}
          onBlur={e=>{e.currentTarget.style.borderColor="rgba(124,58,237,0.32)"}}/>
        <div onClick={()=>!videoUrl&&fileRef.current?.click()}
          style={{ width:"55%", aspectRatio:"9/16", flexShrink:0, position:"relative", borderRadius:16, overflow:"hidden", border:videoUrl?"2px solid rgba(124,58,237,0.6)":"2px dashed rgba(124,58,237,0.45)", background:videoUrl?"transparent":"rgba(124,58,237,0.06)", cursor:"pointer" }}>
          {videoUrl&&<video src={videoUrl} style={{ position:"absolute", inset:0, width:"100%", height:"100%", objectFit:"cover" }} muted playsInline/>}
          {!videoUrl&&(
            <div style={{ position:"absolute", inset:0, display:"flex", flexDirection:"column", alignItems:"center", justifyContent:"center", gap:8 }}>
              <div style={{ width:44, height:44, borderRadius:"50%", background:"rgba(124,58,237,0.25)", border:"1.5px solid rgba(167,139,250,0.4)", display:"flex", alignItems:"center", justifyContent:"center" }}>
                <Plus size={22} color="#c4b5fd" strokeWidth={2}/>
              </div>
              <span style={{ color:"rgba(167,139,250,0.5)", fontSize:"0.68rem", fontFamily:"'Outfit',sans-serif", letterSpacing:"0.08em" }}>Adicionar vídeo</span>
            </div>
          )}
          {videoUrl&&(
            <button onClick={e=>{e.stopPropagation();fileRef.current?.click();}}
              style={{ position:"absolute", bottom:8, right:8, width:28, height:28, borderRadius:7, background:"rgba(0,0,0,0.55)", border:"none", display:"flex", alignItems:"center", justifyContent:"center", cursor:"pointer", color:"#e9d5ff" }}>
              <Plus size={13} strokeWidth={2.2}/>
            </button>
          )}
        </div>
        <input ref={fileRef} type="file" accept="video/*" style={{ display:"none" }} onChange={handleFile}/>
      </div>
      <div style={{ flexShrink:0, padding:"clamp(12px,3%,20px) clamp(24px,8%,40px)", display:"flex", justifyContent:"center" }}>
        <button style={{ padding:"13px 48px", borderRadius:50, background:name.trim()?"linear-gradient(135deg,#5b21b6,#7c3aed)":"rgba(124,58,237,0.2)", border:name.trim()?"1px solid rgba(167,139,250,0.3)":"1px solid rgba(124,58,237,0.25)", color:name.trim()?"#fff":"rgba(167,139,250,0.4)", fontFamily:"'Outfit',sans-serif", fontWeight:700, fontSize:"0.95rem", letterSpacing:"0.06em", textTransform:"uppercase", cursor:name.trim()?"pointer":"default", transition:"all 0.2s", boxShadow:name.trim()?"0 4px 20px rgba(124,58,237,0.35)":"none", display:"flex", alignItems:"center", gap:8 }}>
          <Sparkles size={15}/> Criar
        </button>
      </div>
    </div>
  );
}
