export function constantTimeEqual(a: string, b: string): boolean {
  if (!a || a.length !== b.length) return false;
  let difference = 0;
  for (let i = 0; i < a.length; i++) difference |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return difference === 0;
}
export function json(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' } });
}
export function errorCode(error: unknown): string {
  // Do not persist tokens, document paths, phone numbers or provider response bodies.
  const e = error as { code?: string; status?: number; name?: string };
  return String(e?.code ?? e?.status ?? e?.name ?? 'worker_error').slice(0,80);
}
