"""Sample catalog response times and print percentiles using only the Python standard library."""
import argparse
import concurrent.futures
import statistics
import time
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("--base", default="http://127.0.0.1:8080")
parser.add_argument("--requests", type=int, default=60)
parser.add_argument("--workers", type=int, default=6)
args = parser.parse_args()
if min(args.requests, args.workers) < 1:
    parser.error("requests and workers must be positive")


def sample(_):
    start = time.perf_counter()
    request = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with request.open(args.base.rstrip("/") + "/api/v1/events?size=6", timeout=10) as response:
        if response.status != 200:
            raise RuntimeError(f"Unexpected HTTP status {response.status}")
        response.read()
    return 1000 * (time.perf_counter() - start)


with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
    observations = sorted(pool.map(sample,range(args.requests)))
p95 = observations[(95*len(observations)+99)//100-1]
print(f"requests={len(observations)} workers={args.workers} median_ms={statistics.median(observations):.2f} p95_ms={p95:.2f}")
