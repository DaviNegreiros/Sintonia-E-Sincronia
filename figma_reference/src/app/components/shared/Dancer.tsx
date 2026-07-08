export function Dancer({ color, opacity=0.18 }: { color:string; opacity?:number }) {
  return (
    <svg viewBox="0 0 80 160" style={{ width:"55%", opacity }} aria-hidden>
      <circle cx="40" cy="22" r="11" fill={color}/>
      <line x1="40" y1="33" x2="40" y2="90"  stroke={color} strokeWidth="6" strokeLinecap="round"/>
      <line x1="40" y1="55" x2="22" y2="78"  stroke={color} strokeWidth="5" strokeLinecap="round"/>
      <line x1="40" y1="55" x2="58" y2="72"  stroke={color} strokeWidth="5" strokeLinecap="round"/>
      <line x1="40" y1="90" x2="26" y2="130" stroke={color} strokeWidth="5" strokeLinecap="round"/>
      <line x1="40" y1="90" x2="54" y2="130" stroke={color} strokeWidth="5" strokeLinecap="round"/>
    </svg>
  );
}
