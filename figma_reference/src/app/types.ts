export type Page = "home" | "nova-danca" | "dancas-salvas";

export type Rank = "S" | "A+" | "A" | "B" | "C" | "D" | "E" | "?";

export interface Dance {
  id: number;
  name: string;
  gradient: string;
  ring: string;
  rank: Rank;
}
