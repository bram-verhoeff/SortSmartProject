import type { CategoryId } from "../categories";
import { ProviderError, toProviderError } from "../errors";
import { ModelOutputSchema, type ModelOutput, type ProviderConfig } from "../schemas";
import type { AIProvider, ImageInput, ModelInfo, ProviderCapabilities } from "./types";

/**
 * Fast, free sorting with a CLIP image model that runs on this device (in the
 * browser). CLIP doesn't describe the item; it measures how close the photo is
 * to short descriptions of each stream and picks the closest. That is enough to
 * choose a bin, and takes milliseconds instead of seconds.
 */

export const CLIP_MODEL = "Xenova/clip-vit-base-patch32";
export const CLIP_ENDPOINT = "browser://clip";

/** Runs the actual model. Supplied by the app (the browser), so core stays dependency-free. */
export interface ImageEmbedder {
  /** Downloads (first time) and initialises the model. Safe to call repeatedly. */
  load(model: string, signal?: AbortSignal): Promise<void>;
  /** One L2-normalised embedding per text. */
  embedTexts(model: string, texts: string[], signal?: AbortSignal): Promise<number[][]>;
  /** L2-normalised embedding of the image. */
  embedImage(model: string, image: ImageInput, signal?: AbortSignal): Promise<number[]>;
}

type Target = CategoryId | "none";

/** An item the user sorted themselves: its image embedding (never the photo) and the bin they chose. */
export interface LearnedExample {
  category: CategoryId;
  embedding: number[];
}

/** Where learned examples live (the SORTÉ server keeps them in MySQL). */
export interface ExampleStore {
  list(model: string): Promise<LearnedExample[]>;
  add(model: string, example: LearnedExample): Promise<void>;
  clear(model: string): Promise<void>;
}

/**
 * Learning from corrections. Two photos of the same item in the same place score
 * ~0.9 similarity; the same scene without the item still scores ~0.7-0.75, and other
 * items ~0.45-0.65. So an example only counts above LEARN_FLOOR, and then strongly:
 * a near-identical item (0.92) adds ~7 logits, enough to overrule a wrong first guess.
 */
const LEARN_FLOOR = 0.8;
const LEARN_WEIGHT = 12;
/** A new example this close to an existing one of the same bin adds nothing. */
const DUPLICATE_SIMILARITY = 0.97;
const MAX_EXAMPLES = 2000;

/** Short descriptions per stream. Several per stream make the match robust ("prompt ensembling"). */
export const CLIP_PROMPTS: Record<Target, string[]> = {
  paper: ["a photo of paper", "a photo of a cardboard box", "a photo of a newspaper", "a photo of a paper bag", "a photo of an egg carton"],
  "plastic-metal": [
    "a photo of plastic packaging",
    "a photo of a plastic food container",
    "a photo of a tin can of food",
    "a photo of canned soup or vegetables",
    "a photo of a plastic bag",
    "a photo of a yogurt cup",
    "a photo of a shampoo bottle",
    "a photo of a detergent bottle",
  ],
  // Statiegeld: every plastic drink bottle and drink can.
  deposit: [
    "a photo of a plastic drink bottle",
    "a photo of a plastic water bottle",
    "a photo of a soda bottle",
    "a photo of a drink can",
    "a photo of a soda can",
    "a photo of a beer can",
    "a photo of an energy drink can",
  ],
  glass: ["a photo of a glass bottle", "a photo of a glass jar", "a photo of a wine bottle"],
  organic: ["a photo of fruit", "a photo of vegetables", "a photo of food waste", "a photo of a banana peel", "a photo of an apple core"],
  residual: ["a photo of a crisp packet", "a photo of a used tissue", "a photo of a broken ceramic mug", "a photo of a candy wrapper"],
  // Nothing to sort: keeps faces and empty scenes from being forced into a bin.
  none: ["a photo of a person's face", "a photo of an empty room", "a photo of a wall"],
};

/** Extra frames are only looked at while the whole scan stays this quick (keeps a scan under ~3 s). */
const VIEWS_BUDGET_MS = 1000;

/** CLIP's learned temperature. */
const LOGIT_SCALE = 100;

const dot = (a: number[], b: number[]) => a.reduce((s, x, i) => s + x * (b[i] ?? 0), 0);
const normalise = (v: number[]) => {
  const n = Math.hypot(...v) || 1;
  return v.map((x) => x / n);
};
function softmax(logits: number[]): number[] {
  const m = Math.max(...logits);
  const e = logits.map((x) => Math.exp(x - m));
  const z = e.reduce((a, b) => a + b, 0);
  return e.map((x) => x / z);
}

interface TextIndex {
  targets: Target[];
  /** Mean of each target's prompt embeddings, re-normalised. */
  classEmbeds: number[][];
  prompts: { target: Target; text: string; embed: number[] }[];
}

/** Per stream: the closest learned example's similarity, if any. */
function nearestLearned(image: number[], examples: readonly LearnedExample[]): Map<CategoryId, number> {
  const best = new Map<CategoryId, number>();
  for (const ex of examples) {
    const sim = dot(image, ex.embedding);
    if (sim > (best.get(ex.category) ?? -1)) best.set(ex.category, sim);
  }
  return best;
}

/** Turns similarity scores into the output every provider returns. Exported for tests. */
export function clipDecision(image: number[], index: TextIndex, examples: readonly LearnedExample[] = []): ModelOutput {
  const nearest = nearestLearned(image, examples);
  const boost = (t: Target) => {
    const sim = t === "none" ? undefined : nearest.get(t);
    return sim === undefined ? 0 : (LEARN_WEIGHT * Math.max(0, sim - LEARN_FLOOR)) / (1 - LEARN_FLOOR);
  };
  const probs = softmax(index.classEmbeds.map((c, i) => LOGIT_SCALE * dot(image, c) + boost(index.targets[i]!)));
  const byTarget = new Map(index.targets.map((t, i) => [t, probs[i] ?? 0]));
  const streams = index.targets.filter((t): t is CategoryId => t !== "none").sort((a, b) => (byTarget.get(b) ?? 0) - (byTarget.get(a) ?? 0));
  const best = streams[0]!;
  // Closest single description, as a hint of what it saw.
  const closest = index.prompts
    .filter((p) => p.target === best)
    .map((p) => ({ p, s: dot(image, p.embed) }))
    .sort((a, b) => b.s - a.s)[0]!.p.text.replace(/^a photo of (an? )?/, "");

  // Fast mode always commits to the closest stream and reports it at 100%, so the bin
  // opens straight away: no "not sure", no review, no "could also be" (team decision).
  // The "none" prompts still take part in the softmax, but never win.
  const learned = boost(best) >= 1 ? nearest.get(best)! : null;
  return ModelOutputSchema.parse({
    category: best,
    confidence: 1,
    material: learned ? `${closest} (learned)` : closest,
    reasoning: learned
      ? `Looks like an item you sorted here before (${Math.round(learned * 100)}% alike). Closest description: ${closest}.`
      : `Closest match: ${closest}. Fast mode compares the photo with descriptions of each stream and always picks the closest bin.`,
    flags: [],
    warnings: [],
  });
}

export class ClipProvider implements AIProvider {
  readonly kind = "clip" as const;
  readonly location = "local" as const;
  private index: Promise<TextIndex> | null = null;
  private learned: Promise<LearnedExample[]> | null = null;
  /** Embeddings of recent photos by image hash, so a correction can be learned without keeping photos. */
  private recent = new Map<string, number[]>();

  constructor(
    readonly config: ProviderConfig,
    private embedder: ImageEmbedder | undefined,
    private examples?: ExampleStore,
  ) {}

  /** Learned examples, loaded once. Unavailable storage just means nothing learned yet. */
  private learnedExamples(): Promise<LearnedExample[]> {
    this.learned ??= this.examples
      ? this.examples.list(this.model).catch(() => {
          this.learned = null;
          return [];
        })
      : Promise.resolve([]);
    return this.learned;
  }

  async learnedCount(): Promise<number> {
    return (await this.learnedExamples()).length;
  }

  /**
   * Remembers that the photo with this hash belongs in `category`. Call it when the
   * user picks or corrects a bin. Returns false if there was nothing new to learn.
   */
  async learn(imageSha256: string, category: CategoryId): Promise<boolean> {
    const embedding = this.recent.get(imageSha256);
    if (!embedding || !this.examples) return false;
    const list = await this.learnedExamples();
    if (list.some((ex) => ex.category === category && dot(ex.embedding, embedding) >= DUPLICATE_SIMILARITY)) return false;
    const example = { category, embedding };
    await this.examples.add(this.model, example);
    list.push(example);
    if (list.length > MAX_EXAMPLES) list.splice(0, list.length - MAX_EXAMPLES);
    return true;
  }

  /** Forgets everything learned for this model. */
  async forget(): Promise<void> {
    await this.examples?.clear(this.model);
    this.learned = Promise.resolve([]);
  }

  get id() {
    return this.config.id;
  }
  get name() {
    return this.config.name;
  }
  private get model() {
    return this.config.model || CLIP_MODEL;
  }

  private need(): ImageEmbedder {
    if (!this.embedder) throw new ProviderError("config", "Fast mode needs the SORTÉ web app; it isn't available here.");
    return this.embedder;
  }

  /** Text embeddings never change, so they're computed once per model. */
  private textIndex(signal?: AbortSignal): Promise<TextIndex> {
    this.index ??= (async () => {
      const targets = Object.keys(CLIP_PROMPTS) as Target[];
      const all = targets.flatMap((t) => CLIP_PROMPTS[t].map((text) => ({ target: t, text })));
      const embeds = await this.need().embedTexts(this.model, all.map((p) => p.text), signal);
      const prompts = all.map((p, i) => ({ ...p, embed: embeds[i]! }));
      const classEmbeds = targets.map((t) => {
        const own = prompts.filter((p) => p.target === t);
        return normalise(own[0]!.embed.map((_, d) => own.reduce((s, p) => s + p.embed[d]!, 0) / own.length));
      });
      return { targets, classEmbeds, prompts };
    })().catch((e) => {
      this.index = null;
      throw e;
    });
    return this.index;
  }

  async capabilities(): Promise<ProviderCapabilities> {
    return { imageInput: true, structuredOutput: true, cancellation: false };
  }

  async listModels(): Promise<ModelInfo[]> {
    return [{ id: CLIP_MODEL, name: "CLIP ViT-B/32 · runs in this browser (~150 MB, downloaded once)", imageInput: true }];
  }

  async warmUp(signal?: AbortSignal): Promise<void> {
    await this.need().load(this.model, signal);
    await this.textIndex(signal);
  }

  async testConnection(signal?: AbortSignal) {
    const started = performance.now();
    try {
      await this.warmUp(signal);
      return { ok: true as const, message: "Fast mode is ready. It runs on this computer; nothing is sent anywhere.", modelCount: 1, latencyMs: performance.now() - started };
    } catch (e) {
      const err = toProviderError(e);
      return { ok: false as const, message: err.message, kind: err.kind, latencyMs: performance.now() - started };
    }
  }

  async classify(input: ImageInput, signal?: AbortSignal) {
    try {
      const index = await this.textIndex(signal);
      const started = Date.now();
      const embeds = [await this.need().embedImage(this.model, input, signal)];
      // Extra frames of the same item, while there's time: averaging smooths out blur and glare.
      for (const view of input.views ?? []) {
        if (Date.now() - started > VIEWS_BUDGET_MS) break;
        embeds.push(await this.need().embedImage(this.model, view, signal));
      }
      const image = normalise(embeds[0]!.map((_, d) => embeds.reduce((s, e) => s + (e[d] ?? 0), 0)));
      this.recent.delete(input.sha256);
      this.recent.set(input.sha256, image);
      if (this.recent.size > 64) this.recent.delete(this.recent.keys().next().value!);
      return { output: clipDecision(image, index, await this.learnedExamples()), model: this.model };
    } catch (e) {
      throw e instanceof ProviderError ? e : new ProviderError("config", `Fast mode failed: ${(e as Error)?.message ?? e}`);
    }
  }
}
