import { describe, expect, it } from "vitest";
import { CLIP_PROMPTS, ClipProvider, PROVIDER_PRESETS, createProvider, type ImageEmbedder, type ProviderConfig } from "../src";
import { image } from "./helpers";

/**
 * A fake embedder with one axis per stream: a prompt points along its stream's
 * axis, so the test controls exactly how close an "image" is to each stream.
 */
const TARGETS = Object.keys(CLIP_PROMPTS);
const axis = (t: string) => TARGETS.map((x) => (x === t ? 1 : 0));
const promptTarget = new Map(Object.entries(CLIP_PROMPTS).flatMap(([t, ps]) => ps.map((p) => [p, t] as const)));

function fakeEmbedder(imageVec: number[]): ImageEmbedder & { textCalls: number } {
  const e = {
    textCalls: 0,
    async load() {},
    async embedTexts(_m: string, texts: string[]) {
      e.textCalls += 1;
      return texts.map((t) => axis(promptTarget.get(t)!));
    },
    async embedImage() {
      const n = Math.hypot(...imageVec);
      return imageVec.map((x) => x / n);
    },
  };
  return e;
}

const preset = PROVIDER_PRESETS.fast!;
const cfg: ProviderConfig = {
  id: "fast-1",
  kind: preset.kind,
  name: preset.label,
  endpoint: preset.defaultEndpoint,
  location: "local",
  model: "",
  timeoutMs: 60_000,
  declaredImageInput: true,
  declaredStructuredOutput: true,
  keyHint: null,
  ...preset.defaults,
};

/** Image vector leaning towards `t` (cosine ~0.2 more than the rest is decisive at CLIP's temperature). */
const leaning = (weights: Record<string, number>) => TARGETS.map((t) => weights[t] ?? 0.1);

describe("Fast mode (CLIP)", () => {
  it("is created from the preset without an endpoint check", () => {
    expect(createProvider(cfg, { transport: undefined as never, embedder: fakeEmbedder(axis("glass")) })).toBeInstanceOf(ClipProvider);
  });

  it("picks the closest stream and reports confidence among streams", async () => {
    const p = new ClipProvider(cfg, fakeEmbedder(leaning({ "plastic-metal": 0.3 })));
    const { output } = await p.classify(image);
    expect(output.category).toBe("plastic-metal");
    expect(output.confidence).toBeGreaterThan(0.75);
    expect(output.flags).toEqual([]);
  });

  it("declines when the picture is mostly a person or background", async () => {
    const p = new ClipProvider(cfg, fakeEmbedder(leaning({ none: 0.3, paper: 0.15 })));
    const { output } = await p.classify(image);
    expect(output.category).toBe("unknown");
    expect(output.flags).toContain("not-waste");
  });

  it("warns when two streams are close", async () => {
    const p = new ClipProvider(cfg, fakeEmbedder(leaning({ glass: 0.3, "plastic-metal": 0.298 })));
    const { output } = await p.classify(image);
    expect(output.category).toBe("glass");
    expect(output.confidence).toBeLessThan(0.7);
    expect(output.warnings.join(" ")).toMatch(/plastic \/ metal/);
  });

  it("computes the text side once, not per photo", async () => {
    const e = fakeEmbedder(axis("organic"));
    const p = new ClipProvider(cfg, e);
    await p.classify(image);
    await p.classify(image);
    expect(e.textCalls).toBe(1);
  });

  it("explains itself when no in-browser model is available", async () => {
    const p = new ClipProvider(cfg, undefined);
    await expect(p.classify(image)).rejects.toMatchObject({ kind: "config" });
    expect((await p.testConnection()).ok).toBe(false);
  });
});

describe("Fast mode learns from the bins you pick", () => {
  // Stream axes plus three spare dimensions that make photos (dis)similar to each other.
  const pad = (v: number[], extra: number[]) => [...v, ...extra];
  const textEmbedder = (photos: Record<string, number[]>): ImageEmbedder => ({
    async load() {},
    async embedTexts(_m, texts) {
      return texts.map((t) => pad(axis(promptTarget.get(t)!), [0, 0, 0]));
    },
    async embedImage(_m, img) {
      const v = photos[img.base64]!;
      const n = Math.hypot(...v);
      return v.map((x) => x / n);
    },
  });
  // A paper cup-ish item that zero-shot narrowly calls paper; the user says plastic-metal.
  const streams = leaning({ paper: 0.3, "plastic-metal": 0.29 });
  const photos = {
    A: pad(streams, [1, 0, 0]),
    sameItemAgain: pad(streams, [0.9, 0.44, 0]), // ~0.9 alike: same item, slightly different view
    otherItem: pad(streams, [0, 0, 1]), // same zero-shot look, but a different object
  };
  const photo = (base64: string) => ({ ...image, base64, sha256: `sha-${base64}` });
  const memoryStore = () => {
    const saved: { model: string; category: string }[] = [];
    let list: import("../src").LearnedExample[] = [];
    return {
      saved,
      store: {
        async list() {
          return [...list];
        },
        async add(model: string, ex: import("../src").LearnedExample) {
          saved.push({ model, category: ex.category });
          list.push(ex);
        },
        async clear() {
          list = [];
        },
      },
    };
  };

  it("sends the same item to the bin you picked, and leaves other items alone", async () => {
    const { store, saved } = memoryStore();
    const p = new ClipProvider(cfg, textEmbedder(photos), store);
    expect((await p.classify(photo("A"))).output.category).toBe("paper");

    expect(await p.learn("sha-A", "plastic-metal")).toBe(true);
    expect(saved).toEqual([{ model: "Xenova/clip-vit-base-patch32", category: "plastic-metal" }]);

    const again = (await p.classify(photo("sameItemAgain"))).output;
    expect(again.category).toBe("plastic-metal");
    expect(again.confidence).toBeGreaterThan(0.9);
    expect(again.reasoning).toMatch(/sorted here before/);

    expect((await p.classify(photo("otherItem"))).output.category).toBe("paper");
  });

  it("doesn't store the same item twice, and can't learn a photo it never saw", async () => {
    const { store, saved } = memoryStore();
    const p = new ClipProvider(cfg, textEmbedder(photos), store);
    await p.classify(photo("A"));
    expect(await p.learn("sha-A", "plastic-metal")).toBe(true);
    expect(await p.learn("sha-A", "plastic-metal")).toBe(false);
    expect(await p.learn("sha-unknown", "glass")).toBe(false);
    expect(saved).toHaveLength(1);
    expect(await p.learnedCount()).toBe(1);
  });

  it("forgets everything on request", async () => {
    const { store } = memoryStore();
    const p = new ClipProvider(cfg, textEmbedder(photos), store);
    await p.classify(photo("A"));
    await p.learn("sha-A", "plastic-metal");
    await p.forget();
    expect(await p.learnedCount()).toBe(0);
    expect((await p.classify(photo("A"))).output.category).toBe("paper");
  });
});
