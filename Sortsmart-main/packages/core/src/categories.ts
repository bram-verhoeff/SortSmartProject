/**
 * The six default sorting streams. This is a configurable default schema,
 * not a universal recycling standard — see rules.ts for local overrides.
 */
export const CATEGORY_IDS = ["paper", "plastic-metal", "deposit", "glass", "organic", "residual"] as const;
export type CategoryId = (typeof CATEGORY_IDS)[number];

export interface CategoryDefinition {
  id: CategoryId;
  index: number;
  label: string;
  short: string;
  description: string;
  examples: string[];
  notAccepted: string[];
  /** CSS custom property name defined in @sorte/ui tokens. */
  token: string;
}

export const CATEGORIES: readonly CategoryDefinition[] = [
  {
    id: "paper",
    index: 1,
    label: "Paper & Cardboard",
    short: "Paper",
    description: "Clean, dry paper and fibre-based packaging.",
    examples: ["Newspaper", "Corrugated boxes", "Paper bags", "Office paper"],
    notAccepted: ["Grease-soaked pizza boxes", "Waxed or plastic-coated paper", "Used tissues"],
    token: "--cat-paper",
  },
  {
    id: "plastic-metal",
    index: 2,
    label: "Plastic & Metals",
    short: "Plastic · Metal",
    description: "Plastic packaging and metal containers without a deposit.",
    examples: ["Food tubs and trays", "Shampoo bottles", "Food tins", "Plastic bags"],
    notAccepted: ["Drink bottles and cans with a deposit", "Pressurised containers with contents", "Hard toys", "Electronics"],
    token: "--cat-plastic",
  },
  {
    id: "deposit",
    index: 3,
    label: "Deposit (statiegeld)",
    short: "Deposit",
    description: "Drink bottles and cans you get money back for. Return them instead of throwing them away.",
    examples: ["Plastic drink bottles", "Drink cans", "Water and soda bottles"],
    notAccepted: ["Bottles of cleaning products or shampoo", "Food tins", "Crushed cans (often refused by return machines)"],
    token: "--cat-deposit",
  },
  {
    id: "glass",
    index: 4,
    label: "Glass",
    short: "Glass",
    description: "Glass packaging, subject to local collection rules.",
    examples: ["Clear jars", "Green bottles", "Brown bottles"],
    notAccepted: ["Window glass", "Ceramics", "Light bulbs", "Mirrors"],
    token: "--cat-glass",
  },
  {
    id: "organic",
    index: 5,
    label: "Organic Waste",
    short: "Organic",
    description: "Food scraps and other locally accepted organic material.",
    examples: ["Fruit and vegetable peel", "Coffee grounds", "Eggshells"],
    notAccepted: ["Compostable plastics (varies locally)", "Pet waste", "Liquids"],
    token: "--cat-organic",
  },
  {
    id: "residual",
    index: 6,
    label: "Residual Waste",
    short: "Residual",
    description: "Items that do not fit the supported recycling streams.",
    examples: ["Multi-layer pouches", "Soiled packaging", "Ceramic fragments"],
    notAccepted: ["Batteries", "Electronics", "Chemicals — these need dedicated collection"],
    token: "--cat-residual",
  },
];

export function getCategory(id: CategoryId): CategoryDefinition {
  const found = CATEGORIES.find((c) => c.id === id);
  if (!found) throw new Error(`Unknown category ${id}`);
  return found;
}
