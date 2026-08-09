const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API}${path}`, init);
  if (!response.ok) {
    let detail = "";
    try {
      const body = (await response.json()) as { message?: string };
      detail = body.message ? `: ${body.message}` : "";
    } catch {
      // Keep the status-only message when the backend has no JSON body.
    }
    throw new Error(`Aurora API request failed: ${response.status}${detail}`);
  }
  return response.json() as Promise<T>;
}

export function getApi<T>(path: string): Promise<T> {
  return request<T>(path);
}

export function postApi<T>(path: string): Promise<T> {
  return request<T>(path, { method: "POST" });
}
