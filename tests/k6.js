import http from 'k6/http';
import {check} from 'k6';

export const options = {
  scenarios:{catalog:{executor:'constant-vus',vus:6,duration:'15s'}},
  thresholds:{http_req_failed:['rate<0.01'],http_req_duration:['p(95)<750'],checks:['rate==1']}
};
export default function () {
  const base = __ENV.BASE_URL || 'http://127.0.0.1:8080';
  const list = http.get(base + '/api/v1/events?category=CONCERT&size=5');
  const rows = list.status === 200 ? list.json() : [];
  check(list,{'catalog responds with events':r=>r.status===200 && rows.length>0});
  if (rows.length) check(http.get(base + '/api/v1/events/' + rows[0].id + '/seats'),
    {'inventory responds':r=>r.status===200 && r.json().length>0});
}
