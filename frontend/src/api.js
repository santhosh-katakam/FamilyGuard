import axios from "axios";
export const baseURL = import.meta.env.VITE_API_URL || "";
export const api = axios.create({
  baseURL,
  withCredentials: true,
  timeout: 45000,
});
let accessToken = null,
  refreshing = null;
export function setToken(value) {
  accessToken = value;
}
api.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});
api.interceptors.response.use(
  (r) => r,
  async (error) => {
    const request = error.config;
    if (
      error.response?.status === 401 &&
      request &&
      !request._retry &&
      !request.url.startsWith("/api/auth/")
    ) {
      request._retry = true;
      try {
        refreshing ||= api
          .post("/api/auth/refresh")
          .then((r) => {
            setToken(r.data.accessToken);
            return r;
          })
          .finally(() => {
            refreshing = null;
          });
        await refreshing;
        return api(request);
      } catch {
        window.dispatchEvent(new Event("session-expired"));
      }
    }
    throw error;
  },
);
export function message(error) {
  return (
    error.response?.data?.message ||
    (error.response?.status === 429
      ? "Too many requests. Wait a minute and try again."
      : error.code === "ECONNABORTED"
        ? "The server is taking longer than expected. Render may be waking up. Please retry."
        : !error.response
          ? "Could not reach FamilyGuard. Check your connection and try again."
          : "The request could not be completed. Please retry.")
  );
}
