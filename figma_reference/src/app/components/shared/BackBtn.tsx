import { useState } from "react";
import type { CSSProperties } from "react";
import { ChevronLeft } from "lucide-react";

export function BackBtn({ onClick, style }: { onClick:()=>void; style?: CSSProperties }) {
  const [h,setH]=useState(false);
  return (
    <button onClick={onClick} onMouseEnter={()=>setH(true)} onMouseLeave={()=>setH(false)} aria-label="Voltar"
      style={{ width:32, height:32, borderRadius:8, display:"flex", alignItems:"center", justifyContent:"center", background:h?"rgba(139,92,246,0.22)":"rgba(139,92,246,0.1)", border:"none", color:h?"#c4b5fd":"rgba(167,139,250,0.5)", cursor:"pointer", transition:"all 0.15s", flexShrink:0, ...style }}>
      <ChevronLeft size={18} strokeWidth={1.8} />
    </button>
  );
}
