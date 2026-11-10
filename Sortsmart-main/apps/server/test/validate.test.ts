import { describe, expect, it } from "vitest";
import { allowedHeader, apiKey, endpoint, originKey, providerId } from "../src/validate";

describe("server validation", () => {
  it("accepts only safe provider ids", () => {
    expect(providerId("openai-1a2b3c")).toBe("openai-1a2b3c");
    for (const bad of ["", "../etc", "UPPER", "a".repeat(65), 42]) expect(() => providerId(bad)).toThrow();
  });

  it("allows HTTPS anywhere and plain HTTP only on loopback", () => {
    expect(() => endpoint("https://api.openai.com/v1")).not.toThrow();
    expect(() => endpoint("http://127.0.0.1:11434")).not.toThrow();
    expect(() => endpoint("http://localhost:1234/v1")).not.toThrow();
    expect(() => endpoint("http://[::1]:11434")).not.toThrow();
    expect(() => endpoint("http://api.openai.com/v1")).toThrow();
    expect(() => endpoint("http://192.168.1.10:11434")).toThrow();
    expect(() => endpoint("https://user:pw@api.openai.com")).toThrow();
    expect(() => endpoint("file:///etc/passwd")).toThrow();
    expect(() => endpoint("not a url")).toThrow();
  });

  it("binds keys to scheme, host and port", () => {
    const a = originKey(endpoint("https://api.openai.com/v1"));
    expect(a).toBe("https://api.openai.com:443");
    expect(originKey(endpoint("https://api.openai.com/v1/chat/completions"))).toBe(a);
    expect(originKey(endpoint("https://evil.example.com/v1"))).not.toBe(a);
    expect(originKey(endpoint("https://api.openai.com:8443/v1"))).not.toBe(a);
  });

  it("never accepts auth headers from the browser", () => {
    expect(allowedHeader("Content-Type")).toBe(true);
    expect(allowedHeader("anthropic-version")).toBe(true);
    for (const h of ["Authorization", "x-api-key", "x-goog-api-key", "cookie"]) expect(allowedHeader(h)).toBe(false);
  });

  it("rejects empty, huge or control-character keys", () => {
    expect(apiKey("  sk-test-1234  ")).toBe("sk-test-1234");
    for (const bad of ["", "   ", "a".repeat(4097), "sk\nx", "sk\u0085x", null]) expect(() => apiKey(bad)).toThrow();
  });
});
