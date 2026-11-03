const BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '');

/**
 * 공통 fetch 래퍼
 * - 토큰은 URL에 넣지 않고 Authorization: Bearer 헤더로만 전달한다.
 * - 이번 뼈대 단계에서는 실제 서버 호출을 하지 않으며, 추후 API 연동 시 사용한다.
 */
export async function apiRequest(path, { method = 'GET', body, token, headers = {} } = {}) {
  const url = `${BASE_URL}${path.startsWith('/') ? path : `/${path}`}`;
  const requestHeaders = {
    ...headers,
  };

  if (token) {
    requestHeaders.Authorization = `Bearer ${token}`;
  }

  const options = {
    method,
    headers: requestHeaders,
  };

  if (body !== undefined) {
    if (body instanceof FormData) {
      options.body = body;
    } else {
      requestHeaders['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body);
    }
  }

  const response = await fetch(url, options);
  const contentType = response.headers.get('content-type') || '';
  const data = contentType.includes('application/json') ? await response.json() : null;

  if (!response.ok) {
    const error = new Error(data?.message || '요청을 처리하지 못했어요.');
    error.status = response.status;
    throw error;
  }

  return data;
}

