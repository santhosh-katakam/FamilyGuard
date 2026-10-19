// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { ChildPortal } from "./ChildPortal";

function portal(path = "/child") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <ChildPortal />
    </MemoryRouter>,
  );
}
beforeEach(() => window.localStorage.clear());
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("child browser interface", () => {
  it("opens every screen without an Android installation or parent sign-in", () => {
    portal();
    expect(screen.getAllByText("Browser interface test").length).toBeGreaterThan(0);
    expect(
      screen.getByRole("heading", { name: "Your space. Your choices." }),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole("link", { name: "Pairing" }));
    expect(
      screen.getByRole("button", { name: /Check pairing form/ }),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole("link", { name: "Permissions" }));
    expect(
      screen.getByRole("checkbox", { name: "Application usage" }),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole("link", { name: "Monitoring" }));
    expect(screen.getByText("Unavailable in browser")).toBeTruthy();
    expect(
      screen.getByText("Pending device events").nextElementSibling.textContent,
    ).toBe("Not available");
  });

  it("requires a device name, valid code format and notice acknowledgement while keeping codes local", () => {
    portal("/child/pairing");
    fireEvent.click(screen.getByRole("button", { name: /Check pairing form/ }));
    const errors = screen.getByRole("alert").textContent;
    expect(errors).toContain("Enter a device name");
    expect(errors).toContain("12-character pairing code");
    expect(errors).toContain("Read and acknowledge");
    fireEvent.change(screen.getByLabelText("Device name"), {
      target: { value: "My phone" },
    });
    fireEvent.change(screen.getByLabelText("Parent pairing code"), {
      target: { value: "abcd-efg_123" },
    });
    expect(screen.getByLabelText("Parent pairing code").value).toBe(
      "ABCD-EFG_123",
    );
    fireEvent.click(
      screen.getByRole("checkbox", { name: /read the supervision notice/ }),
    );
    fireEvent.click(screen.getByRole("button", { name: /Check pairing form/ }));
    expect(
      screen.getByText(/Setup form checked. No device was paired/),
    ).toBeTruthy();
    expect(window.localStorage.length).toBe(0);
    fireEvent.change(screen.getByLabelText("Parent pairing code"), {
      target: { value: "bad-code" },
    });
    expect(screen.queryByText(/Setup form checked/)).toBeNull();
  });

  it("saves sharing choices and selected applications without claiming Android access", () => {
    portal("/child/permissions");
    fireEvent.click(
      screen.getByRole("checkbox", { name: "Application usage" }),
    );
    fireEvent.click(
      screen.getByRole("checkbox", { name: "Notification events" }),
    );
    fireEvent.click(screen.getByRole("checkbox", { name: "WhatsApp" }));
    fireEvent.click(
      screen.getByRole("button", { name: /Save browser choices/ }),
    );
    expect(screen.getByRole("status").textContent).toContain(
      "Android permissions are unchanged",
    );
    cleanup();
    portal("/child/permissions");
    expect(
      screen.getByRole("checkbox", { name: "Application usage" }).checked,
    ).toBe(true);
    expect(screen.getByRole("checkbox", { name: "WhatsApp" }).checked).toBe(
      true,
    );
    expect(
      screen.getByRole("checkbox", { name: "Location sharing" }).checked,
    ).toBe(false);
    expect(
      screen.getAllByText("Android access: unavailable in browser"),
    ).toHaveLength(5);
  });

  it("allows cancellation and confirmation of the pause test without changing device status", () => {
    portal("/child/status");
    fireEvent.click(
      screen.getByRole("button", { name: "Test pause confirmation" }),
    );
    expect(
      screen.getByRole("dialog", { name: "Pause monitoring?" }),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(screen.getByRole("status").textContent).toBe("");
    fireEvent.click(
      screen.getByRole("button", { name: "Test pause confirmation" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Confirm pause test" }));
    expect(screen.getByRole("status").textContent).toContain(
      "no phone command was sent",
    );
    expect(
      screen.getByText("Monitoring", { selector: "dt" }).nextElementSibling
        .textContent,
    ).toBe("Unavailable in browser");
  });

  it("resets only the browser test choices", () => {
    window.localStorage.setItem("unrelated-app-value", "keep");
    portal("/child/permissions");
    fireEvent.click(screen.getByRole("checkbox", { name: "Location sharing" }));
    fireEvent.click(
      screen.getByRole("button", { name: /Save browser choices/ }),
    );
    fireEvent.click(screen.getByRole("link", { name: "Monitoring" }));
    fireEvent.click(
      screen.getByRole("button", { name: "Reset browser choices" }),
    );
    fireEvent.click(screen.getByRole("dialog").querySelector("button.danger"));
    expect(screen.getByRole("status").textContent).toContain(
      "device pairing are unchanged",
    );
    expect(window.localStorage.getItem("unrelated-app-value")).toBe("keep");
    fireEvent.click(screen.getByRole("link", { name: "Permissions" }));
    expect(
      screen.getByRole("checkbox", { name: "Location sharing" }).checked,
    ).toBe(false);
    cleanup();
    portal("/child/permissions");
    expect(
      screen.getByRole("checkbox", { name: "Location sharing" }).checked,
    ).toBe(false);
  });

  it("shows actual browser connectivity changes", () => {
    vi.spyOn(window.navigator, "onLine", "get").mockReturnValue(true);
    portal("/child/status");
    expect(screen.getByText("Browser network: Online")).toBeTruthy();
    vi.spyOn(window.navigator, "onLine", "get").mockReturnValue(false);
    act(() => window.dispatchEvent(new Event("offline")));
    expect(screen.getByText("Browser network: Offline")).toBeTruthy();
    vi.spyOn(window.navigator, "onLine", "get").mockReturnValue(true);
    act(() => window.dispatchEvent(new Event("online")));
    expect(screen.getByText("Browser network: Online")).toBeTruthy();
  });

  it("keeps the interface usable when browser storage is blocked", () => {
    vi.spyOn(window.Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("Blocked");
    });
    vi.spyOn(window.Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("Blocked");
    });
    portal("/child/permissions");
    fireEvent.click(
      screen.getByRole("checkbox", { name: "Application usage" }),
    );
    fireEvent.click(
      screen.getByRole("button", { name: /Save browser choices/ }),
    );
    expect(screen.getByRole("status").textContent).toContain(
      "Browser storage is unavailable",
    );
    expect(
      screen.getByRole("checkbox", { name: "Application usage" }).checked,
    ).toBe(true);
  });
});
