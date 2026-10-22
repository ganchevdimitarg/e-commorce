import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8084';
const TOKEN_URL = __ENV.TOKEN_URL || 'http://localhost:8090/default/token';
const HOT_PRODUCT_ID = '00000000-0000-0000-0000-000000000001';

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '45s',
  summaryTrendStats: ['avg', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

const byIdDuration = new Trend('by_id_duration', true);
const byIdSize = new Trend('by_id_size_bytes', false);
const searchDuration = new Trend('search_duration', true);
const searchSize = new Trend('search_size_bytes', false);
const cursorDuration = new Trend('cursor_duration', true);
const cursorSize = new Trend('cursor_size_bytes', false);

export function setup() {
  const res = http.post(
    TOKEN_URL,
    'grant_type=client_credentials&client_id=perf&client_secret=perf',
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } },
  );
  const token = res.json('access_token');
  check(res, {
    'token endpoint returned 200': (r) => r.status === 200,
    'token present': () => Boolean(token),
  });
  if (!token) {
    throw new Error(`token request failed: ${res.status} ${res.body}`);
  }
  return { token };
}

function get(url, headers, durationTrend, sizeTrend) {
  const res = http.get(url, { headers });
  durationTrend.add(res.timings.duration);
  sizeTrend.add(res.body ? res.body.length : 0);
  check(res, { [`${url} returned 200`]: (r) => r.status === 200 });
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };

  get(`${BASE_URL}/api/v1/catalog/products/${HOT_PRODUCT_ID}`,
      headers, byIdDuration, byIdSize);
  get(`${BASE_URL}/api/v1/catalog/products?q=Phone&size=20`,
      headers, searchDuration, searchSize);
  get(`${BASE_URL}/api/v1/catalog/products/:cursor?limit=20&sort=createdAt,desc`,
      headers, cursorDuration, cursorSize);

  sleep(1);
}
