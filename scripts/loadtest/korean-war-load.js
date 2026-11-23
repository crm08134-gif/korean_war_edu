import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'https://koreanwar-edu.tech';
// 브라우저처럼 압축(gzip)을 요청한다. GZIP=0이면 압축 없이(2026-11-16 이전 측정과 같은 조건)
const GZIP = (__ENV.GZIP || '1') !== '0';
const HEADERS = GZIP ? { 'Accept-Encoding': 'gzip' } : {};
const TARGETS = [
  ['home', '/'],
  ['snapshots', '/api/snapshots'],
  ['events', '/api/events'],
  ['stories', '/api/stories'],
  ['countries', '/api/countries'],
];

const thresholds = {
  http_req_duration: ['p(95)<1000'],
  http_req_failed: ['rate<0.01'],
};
for (const [name] of TARGETS) {
  thresholds['http_req_duration{name:' + name + '}'] = ['p(95)<1000'];
}

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '3m',
  thresholds: thresholds,
};

export default function () {
  const reqs = TARGETS.map(([name, path]) => ['GET', BASE + path, null, { tags: { name: name }, headers: HEADERS }]);
  const res = http.batch(reqs);
  for (const r of res) {
    check(r, { 'status 200': (x) => x.status === 200 });
  }
  sleep(3);
}
