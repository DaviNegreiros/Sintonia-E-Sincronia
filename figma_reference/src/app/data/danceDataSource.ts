import { DANCES, RANK_STYLE } from "../mockDances";
import type { Dance, Rank } from "../types";

export function getInitialDances(): Dance[] {
  return DANCES;
}

export function getRankStyle(rank: Rank) {
  return RANK_STYLE[rank];
}
