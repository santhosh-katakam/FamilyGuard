import { describe, it, expect } from "vitest";
import { usageRows, installed, stale } from "./data";
describe("activity calculations", () => {
  it("uses latest cumulative snapshot without counting retries twice", () => {
    const rows = [
      {
        packageName: "app",
        occurredAt: "2026-01-01T02:00:00Z",
        data: { day: "2026-01-01", durationMs: 200 },
      },
      {
        packageName: "app",
        occurredAt: "2026-01-01T01:00:00Z",
        data: { day: "2026-01-01", durationMs: 100 },
      },
    ];
    expect(usageRows(rows)).toHaveLength(1);
    expect(usageRows(rows)[0].data.durationMs).toBe(200);
  });
  it("removes uninstalled apps", () =>
    expect(
      installed([
        { packageName: "a", kind: "INSTALLED", occurredAt: "2026-01-01" },
        { packageName: "a", kind: "REMOVED", occurredAt: "2026-01-02" },
      ]),
    ).toHaveLength(0));
  it("never describes an old coordinate as live", () => {
    expect(stale("2026-01-01", new Date("2026-01-02").getTime())).toBe(true);
    expect(stale(null)).toBe(true);
  });
});
