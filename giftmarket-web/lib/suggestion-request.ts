export function scheduleSuggestionRequest<T>(
  query: string,
  request: (query: string, signal: AbortSignal) => Promise<T>,
  onLoading: () => void,
  onSuccess: (value: T) => void,
  onError: () => void,
) {
  const controller = new AbortController();
  const timer = setTimeout(() => {
    onLoading();
    request(query, controller.signal).then((result) => {
      if (!controller.signal.aborted) onSuccess(result);
    }).catch(() => {
      if (!controller.signal.aborted) onError();
    });
  }, 275);
  return () => { clearTimeout(timer); controller.abort(); };
}

export function moveSuggestionIndex(current: number, count: number, direction: 1 | -1): number {
  if (count <= 0) return -1;
  if (direction === 1) return (current + 1) % count;
  return current <= 0 ? count - 1 : current - 1;
}
