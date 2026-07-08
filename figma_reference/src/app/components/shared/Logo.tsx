import logoSrc from "@/imports/SS_Logo.png";

export function Logo({ scale = 1 }: { scale?: number }) {
  return (
    <div style={{ position:"relative", display:"flex", alignItems:"center", justifyContent:"center", width:"100%" }}>
      <div style={{ position:"absolute", width:"65%", height:"130%", background:"radial-gradient(ellipse 80% 70% at 50% 50%,rgba(124,58,237,0.42) 0%,transparent 72%)", filter:"blur(22px)", pointerEvents:"none" }} />
      <img src={logoSrc} alt="Sintonia & Sincronia" draggable={false}
        style={{ position:"relative", zIndex:1, width:`${82*scale}%`, maxWidth:scale===1?320:240, objectFit:"contain", mixBlendMode:"screen" }} />
    </div>
  );
}
