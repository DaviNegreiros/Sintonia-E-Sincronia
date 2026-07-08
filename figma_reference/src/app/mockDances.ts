import type { Dance, Rank } from "./types";

export const RANK_STYLE: Record<Rank, { color: string; bg: string; border: string }> = {
  "S":  { color:"#fde68a", bg:"rgba(251,191,36,0.18)",  border:"rgba(251,191,36,0.5)"  },
  "A+": { color:"#fb923c", bg:"rgba(251,146,60,0.18)",  border:"rgba(251,146,60,0.5)"  },
  "A":  { color:"#86efac", bg:"rgba(74,222,128,0.18)",  border:"rgba(74,222,128,0.5)"  },
  "B":  { color:"#93c5fd", bg:"rgba(96,165,250,0.18)",  border:"rgba(96,165,250,0.5)"  },
  "C":  { color:"#c4b5fd", bg:"rgba(167,139,250,0.18)", border:"rgba(167,139,250,0.5)" },
  "D":  { color:"#fca5a5", bg:"rgba(248,113,113,0.15)", border:"rgba(248,113,113,0.45)"},
  "E":  { color:"#f87171", bg:"rgba(239,68,68,0.15)",   border:"rgba(239,68,68,0.45)"  },
  "?":  { color:"#94a3b8", bg:"rgba(148,163,184,0.12)", border:"rgba(148,163,184,0.35)"},
};

export const DANCES: Dance[] = [
  { id:1, name:"Salsa Cubana",        gradient:"linear-gradient(155deg,#4b0082,#1a0040)", ring:"#9747ff", rank:"S"  },
  { id:2, name:"Tango Argentino",     gradient:"linear-gradient(155deg,#1e0855,#0d0028)", ring:"#7c3aed", rank:"A+" },
  { id:3, name:"Forró Universitário", gradient:"linear-gradient(155deg,#2d1b69,#140030)", ring:"#8b5cf6", rank:"A"  },
  { id:4, name:"Samba de Gafieira",   gradient:"linear-gradient(155deg,#5b0e91,#240040)", ring:"#a855f7", rank:"B"  },
  { id:5, name:"Bachata",             gradient:"linear-gradient(155deg,#180d4f,#09001f)", ring:"#7c3aed", rank:"C"  },
  { id:6, name:"Zouk Brasileiro",     gradient:"linear-gradient(155deg,#3d0f75,#19003a)", ring:"#9333ea", rank:"?"  },
  { id:7, name:"Kizomba",             gradient:"linear-gradient(155deg,#270a58,#0f0026)", ring:"#8b5cf6", rank:"D"  },
];
