// @vitest-environment jsdom
import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  cleanup,
  act,
} from "@testing-library/react";
import { BrowserRouter, MemoryRouter } from "react-router-dom";
vi.mock("react-leaflet", () => ({
  MapContainer: ({ children }) => <div>{children}</div>,
  TileLayer: () => null,
  Circle: ({ children }) => <div>{children}</div>,
  Popup: ({ children }) => <div>{children}</div>,
}));
vi.mock("./api", () => ({
  baseURL: "",
  setToken: vi.fn(),
  message: () => "Connection unavailable. Please retry.",
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));
import { api } from "./api";
import {
  App,
  Login,
  Dashboard,
  Device,
  Activity,
  LocationPage,
  Preferences,
} from "./main";
function page(node) {
  return render(<MemoryRouter>{node}</MemoryRouter>);
}
beforeEach(() => {
  vi.resetAllMocks();
  api.get.mockResolvedValue({ data: [] });
});
afterEach(cleanup);
describe("real interface workflows", () => {
  it("returns from child setup to parent sign in without reloading", async () => {
    api.post.mockRejectedValue(new Error("No local session"));
    window.history.replaceState(null, "", "/child-setup");
    await act(async () => {
      render(
        <BrowserRouter>
          <App />
        </BrowserRouter>,
      );
    });
    fireEvent.click(
      screen.getByRole("link", { name: /Return to parent sign in/ }),
    );
    expect(
      await screen.findByRole("button", { name: /Sign in securely/ }),
    ).toBeTruthy();
    fireEvent.click(
      screen.getByRole("link", { name: /Setting up a child device/ }),
    );
    expect(
      screen.getByRole("link", { name: /Return to parent sign in/ }),
    ).toBeTruthy();
    window.history.replaceState(null, "", "/");
  });
  it("does not invent telemetry for an unpaired family", () => {
    page(<Dashboard data={{ device: null }} refresh={() => {}} />);
    expect(screen.getByText("Not paired")).toBeTruthy();
    expect(screen.getByText("Connect a device")).toBeTruthy();
    expect(screen.getByText("Their rhythm, at a glance")).toBeTruthy();
  });
  it("shows an honest empty location state", async () => {
    page(<LocationPage data={{ device: null }} />);
    expect(screen.getByText("Location sharing is not available")).toBeTruthy();
    await waitFor(() =>
      expect(api.get).toHaveBeenCalledWith("/api/events/location"),
    );
  });
  it("marks old coordinates as outdated", async () => {
    const point = {
      id: "test-location",
      occurredAt: "2020-01-01T00:00:00Z",
      data: { latitude: 12, longitude: 77, accuracy: 100 },
    };
    page(<LocationPage data={{ lastLocation: point }} />);
    expect(screen.getByText("Outdated · Last known location")).toBeTruthy();
    await waitFor(() => expect(api.get).toHaveBeenCalled());
  });
  it("submits parent credentials and handles network failure", async () => {
    api.post.mockRejectedValue(new Error("offline"));
    const onLogin = vi.fn();
    page(<Login onLogin={onLogin} />);
    fireEvent.change(screen.getByLabelText("Email address"), {
      target: { value: "parent@example.test" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "test-only-password" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Sign in securely/ }));
    await screen.findByRole("alert");
    expect(api.post).toHaveBeenCalledWith("/api/auth/login", {
      email: "parent@example.test",
      password: "test-only-password",
    });
    expect(onLogin).not.toHaveBeenCalled();
  });
  it("displays only the real one-time pairing response", async () => {
    api.post.mockResolvedValue({
      data: { code: "TESTCODE1234", expiresAt: "2030-01-01T00:00:00Z" },
    });
    page(<Device data={{ device: null }} refresh={() => {}} />);
    fireEvent.click(screen.getByRole("button", { name: /Generate one-time/ }));
    expect(await screen.findByText("TESTCODE1234")).toBeTruthy();
    expect(api.post).toHaveBeenCalledWith("/api/pairing/code");
  });
  it("summarizes notification metadata from the activity endpoint", async () => {
    api.get.mockResolvedValue({
      data: [
        {
          id: "n",
          occurredAt: new Date().toISOString(),
          kind: "NOTIFICATION",
          packageName: "com.whatsapp",
          data: { count: 1 },
        },
      ],
    });
    page(<Activity category="notifications" title="Notification Activity" />);
    expect(await screen.findByText("Notification events")).toBeTruthy();
    expect(screen.getByText("com.whatsapp")).toBeTruthy();
    expect(api.get).toHaveBeenCalledWith("/api/events/notifications", {
      params: { search: "" },
    });
  });
  it("shows privacy controls as reported and saves explicit settings", async () => {
    const prefs = {
      apps: true,
      usage: false,
      notifications: false,
      calls: false,
      location: false,
      alertNotifications: false,
      retentionDays: 30,
      locationHistoryDays: 7,
    };
    api.get.mockResolvedValue({ data: prefs });
    api.put.mockResolvedValue({ data: prefs });
    page(<Preferences data={{ permissions: { usage: false } }} />);
    const save = await screen.findByRole("button", {
      name: /Save preferences/,
    });
    fireEvent.click(save);
    expect(await screen.findByText("Preferences saved.")).toBeTruthy();
    expect(api.put).toHaveBeenCalledWith("/api/preferences", prefs);
  });
});
