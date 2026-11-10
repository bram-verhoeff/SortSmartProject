import type { ImageEmbedder, ImageInput } from "@sorte/core";

/**
 * Runs CLIP in the browser for fast mode (transformers.js + ONNX Runtime Web).
 * Model files and the WASM runtime come from the SORTÉ server, which downloads
 * the model from Hugging Face once and keeps it; nothing else is fetched.
 * Loaded on first use, so the app doesn't carry it until fast mode is chosen.
 */

type Transformers = typeof import("@huggingface/transformers");

let lib: Promise<Transformers> | null = null;
function transformers(): Promise<Transformers> {
  lib ??= import("@huggingface/transformers").then((t) => {
    t.env.allowLocalModels = false;
    t.env.allowRemoteModels = true;
    t.env.remoteHost = `${location.origin}/api/models/`;
    t.env.remotePathTemplate = "{model}/resolve/{revision}/";
    const wasm = (t.env.backends.onnx as { wasm?: { wasmPaths?: string } }).wasm;
    if (wasm) wasm.wasmPaths = `${location.origin}/api/ort/`;
    return t;
  });
  lib.catch(() => (lib = null));
  return lib;
}

interface Loaded {
  tokenizer: Awaited<ReturnType<Transformers["AutoTokenizer"]["from_pretrained"]>>;
  processor: Awaited<ReturnType<Transformers["AutoProcessor"]["from_pretrained"]>>;
  text: Awaited<ReturnType<Transformers["CLIPTextModelWithProjection"]["from_pretrained"]>>;
  vision: Awaited<ReturnType<Transformers["CLIPVisionModelWithProjection"]["from_pretrained"]>>;
  RawImage: Transformers["RawImage"];
}

const loaded = new Map<string, Promise<Loaded>>();

function load(model: string): Promise<Loaded> {
  let p = loaded.get(model);
  if (!p) {
    p = (async () => {
      const t = await transformers();
      // 8-bit weights on the CPU (WASM): small download, fast enough, works on every machine.
      const opts = { dtype: "q8", device: "wasm" } as const;
      const [tokenizer, processor, text, vision] = await Promise.all([
        t.AutoTokenizer.from_pretrained(model),
        t.AutoProcessor.from_pretrained(model),
        t.CLIPTextModelWithProjection.from_pretrained(model, opts),
        t.CLIPVisionModelWithProjection.from_pretrained(model, opts),
      ]);
      return { tokenizer, processor, text, vision, RawImage: t.RawImage };
    })();
    p.catch(() => loaded.delete(model));
    loaded.set(model, p);
  }
  return p;
}

function rows(tensor: { data: ArrayLike<number>; dims: number[] }): number[][] {
  const [n = 0, d = 0] = tensor.dims;
  return Array.from({ length: n }, (_, i) => {
    const v = Array.from(tensor.data).slice(i * d, (i + 1) * d);
    const norm = Math.hypot(...v) || 1;
    return v.map((x) => x / norm);
  });
}

function toBlob(image: ImageInput): Blob {
  const bin = atob(image.base64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new Blob([bytes], { type: image.mimeType });
}

export const browserEmbedder: ImageEmbedder = {
  async load(model, signal) {
    signal?.throwIfAborted();
    await load(model);
  },
  async embedTexts(model, texts, signal) {
    const m = await load(model);
    signal?.throwIfAborted();
    const { text_embeds } = await m.text(m.tokenizer(texts, { padding: true, truncation: true }));
    return rows(text_embeds);
  },
  async embedImage(model, image, signal) {
    const m = await load(model);
    signal?.throwIfAborted();
    const raw = await m.RawImage.fromBlob(toBlob(image));
    const { image_embeds } = await m.vision(await m.processor(raw));
    signal?.throwIfAborted();
    return rows(image_embeds)[0]!;
  },
};
