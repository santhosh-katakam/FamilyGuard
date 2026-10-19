// @vitest-environment jsdom
import React from "react";
import { afterEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
vi.mock("./api", () => ({
  api: { get: vi.fn(), delete: vi.fn() },
  message: () => "Could not reach FamilyGuard.",
}));
import { api } from "./api";
import { ParentReceiverSettings } from "./ParentReceiverSettings";
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
it("shows the real receiver registration and requires confirmation before revocation", async () => {
  api.get
    .mockResolvedValueOnce({
      data: {
        registered: true,
        deviceName: "Parent Pixel",
        lastSeen: "2026-10-19T10:00:00Z",
        expiresAt: "2026-11-18T10:00:00Z",
      },
    })
    .mockResolvedValue({ data: { registered: false } });
  api.delete.mockResolvedValue({});
  render(<ParentReceiverSettings />);
  expect(await screen.findByText("Parent Pixel")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: /Revoke parent phone/ }));
  expect(api.delete).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
  expect(api.delete).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: /Revoke parent phone/ }));
  fireEvent.click(screen.getByRole("button", { name: "Confirm revocation" }));
  expect(await screen.findByText("Not registered")).toBeTruthy();
  expect(api.delete).toHaveBeenCalledWith("/api/parent-receiver");
});
it("exposes a retry when the server is unavailable", async () => {
  api.get
    .mockRejectedValueOnce(new Error("offline"))
    .mockResolvedValue({ data: { registered: false } });
  render(<ParentReceiverSettings />);
  expect(await screen.findByRole("alert")).toBeTruthy();
  fireEvent.click(
    screen.getByRole("button", { name: "Refresh parent receiver" }),
  );
  await waitFor(() => expect(screen.queryByRole("alert")).toBeNull());
  expect(screen.getByText("Not registered")).toBeTruthy();
});
