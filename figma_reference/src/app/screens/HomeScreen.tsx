import { BookOpen, Music2, Play } from "lucide-react";

import type { Page } from "../types";
import { Hairline, InfoPopover, Logo, PillBtn } from "../components/shared";

export function HomeScreen({ onNavigate }: { onNavigate:(p:Page)=>void }) {
  return (
    <div style={{ height:"100%", display:"flex", flexDirection:"column" }}>
      <div style={{ position:"absolute", inset:0, pointerEvents:"none", background:"radial-gradient(ellipse 80% 40% at 50% -5%,rgba(139,92,246,0.12) 0%,transparent 65%)" }}/>
      {([{top:"8%",side:"left",val:"7%",size:15,delay:"0s"},{top:"12%",side:"right",val:"8%",size:11,delay:"1.4s"},{top:"34%",side:"left",val:"5%",size:10,delay:"0.8s"},{top:"31%",side:"right",val:"6%",size:14,delay:"2.1s"},{top:"60%",side:"left",val:"9%",size:9,delay:"0.3s"},{top:"65%",side:"right",val:"7%",size:12,delay:"1.9s"}] as const).map((n,i)=>(
        <div key={i} style={{ position:"absolute", top:n.top, [n.side]:n.val, pointerEvents:"none", animation:`floatNote 3.4s ease-in-out ${n.delay} infinite alternate` }}>
          <Music2 size={n.size} color="rgba(139,92,246,0.2)"/>
        </div>
      ))}
      <div style={{ height:"25%", position:"relative", zIndex:1, display:"flex", alignItems:"center" }}>
        <Logo/>
      </div>
      <Hairline/>
      <div style={{ flex:1, position:"relative", zIndex:1, display:"flex", flexDirection:"column", justifyContent:"flex-start", gap:11, padding:"clamp(22px,5%,36px) clamp(36px,13%,60px) 0" }}>
        <div style={{ position:"relative", paddingLeft:38 }}>
          <div style={{ position:"absolute", left:0, top:0, bottom:0, width:30, display:"flex", alignItems:"center", justifyContent:"center" }}>
            <InfoPopover/>
          </div>
          <PillBtn label="Nova Dança" icon={<Play size={15} fill="white" color="white"/>} primary onClick={()=>onNavigate("nova-danca")}/>
        </div>
        <div style={{ paddingLeft:38 }}>
          <PillBtn label="Danças Salvas" icon={<BookOpen size={15}/>} primary={false} onClick={()=>onNavigate("dancas-salvas")}/>
        </div>
      </div>
      <div style={{ position:"absolute", bottom:0, left:0, right:0, height:"30%", pointerEvents:"none", background:"radial-gradient(ellipse 100% 80% at 50% 120%,rgba(91,33,182,0.14) 0%,transparent 70%)" }}/>
    </div>
  );
}
