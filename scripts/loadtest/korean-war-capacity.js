import http from 'k6/http';
import { check } from 'k6';

// 용량 산정: 사람 수(VU)가 아니라 "초당 첫 화면 열기 횟수"를 정해 보내고, 계단처럼 올린다.
// 반복 한 번 = 첫 화면 열기(요청 5개). 초당 요청 수 = 단계 값 × 5
// 서버가 못 버티면(p95 2초 넘음 또는 실패 5% 넘음) 30초 뒤 저절로 멈춘다
const BASE = __ENV.BASE_URL || 'https://localhost:8443';
const TARGETS = [
  ['home', '/'],
  ['snapshots', '/api/snapshots'],
  ['events', '/api/events'],
  ['stories', '/api/stories'],
  ['countries', '/api/countries'],
];
const HEADERS = { 'Accept-Encoding': 'gzip' };
// 계단: 초당 첫 화면 열기 횟수. 각 단계 30초 동안 올리고 90초 유지
const STEPS = (__ENV.STEPS || "5,10,20,30,40,60,80,100,130,160,200").split(",");

const stages = [];
for (const s of STEPS) {
  stages.push({ target: Number(s), duration: '30s' });
  stages.push({ target: Number(s), duration: '90s' });
}

export const options = {
  // 로컬 자체 인증서라 인증서 검사를 끈다 (운영 주소에는 쓰지 않는다)
  insecureSkipTLSVerify: true,
  scenarios: {
    capacity: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 600,
      stages: stages,
    },
  },
  thresholds: {
    http_req_duration: [{ threshold: 'p(95)<2000', abortOnFail: true, delayAbortEval: '30s' }],
    http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '30s' }],
  },
};

export default function () {
  const reqs = TARGETS.map(([name, path]) => ['GET', BASE + path, null, { tags: { name: name }, headers: HEADERS }]);
  const res = http.batch(reqs);
  for (const r of res) {
    check(r, { 'status 200': (x) => x.status === 200 });
  }
}
