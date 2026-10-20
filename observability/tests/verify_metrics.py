#!/usr/bin/env python3
"""Verify real metrics through Grafana/Prometheus using the isolated HTTP fixture.

Starts/stops no containers and never writes to PostgreSQL. Python standard library only.
"""
import argparse
import base64
import json
import math
import re
import urllib.parse
import urllib.request
from pathlib import Path

from verify_stack import strict_object, wait_for

CANARY = "FINFAST_METRICS_SECRET_MUST_NOT_APPEAR_4D4"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--environment-file", default=".env.observability")
    parser.add_argument("--grafana-url", default="http://localhost:3300")
    parser.add_argument("--backend-url", default="http://localhost:8080")
    parser.add_argument("--evidence", type=Path)
    args = parser.parse_args()
    settings = {}
    for line in Path(args.environment_file).read_text().splitlines():
        key, separator, value = line.partition("=")
        if separator and not key.lstrip().startswith("#"):
            settings[key.strip()] = value.strip().strip("\"'")
    credentials = settings["GRAFANA_ADMIN_USER"] + ":" + settings["GRAFANA_ADMIN_PASSWORD"]
    authorization = "Basic " + base64.b64encode(credentials.encode()).decode()

    def request(url, authenticated=False, canary=False):
        headers = {"Accept": "*/*"}
        if authenticated:
            headers["Authorization"] = authorization
        if canary:
            headers.update({"Authorization": "Bearer " + CANARY, "X-Request-ID": CANARY,
                            "Cookie": "session=" + CANARY, "Idempotency-Key": CANARY})
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=10) as response:
            return response.read().decode()

    def grafana(path):
        return json.loads(request(args.grafana_url + path, True), object_pairs_hook=strict_object)

    def query(expression):
        result = grafana("/api/datasources/proxy/uid/finfast-prometheus/api/v1/query?" +
                         urllib.parse.urlencode({"query": expression}))
        assert result["status"] == "success", result
        return result["data"]["result"]

    def present(expression):
        values = query(expression)
        assert values, expression + " has no series yet"
        return values

    def counter():
        return float(present('sum(http_server_requests_seconds_count{job="finfast-backend",uri="/test-only/tracing/{value}"})')[0]["value"][1])

    def healthy():
        assert json.loads(request(args.backend_url + "/actuator/health"))["status"] == "UP"
        return True

    wait_for(healthy, "backend health")
    assert grafana("/api/datasources/uid/finfast-prometheus/health")["status"] == "OK"
    wait_for(lambda: present('up{job="finfast-backend"} == 1'), "successful real backend scrape")
    targets = grafana("/api/datasources/proxy/uid/finfast-prometheus/api/v1/targets")["data"]["activeTargets"]
    target = next(t for t in targets if t["labels"]["job"] == "finfast-backend")
    assert target["scrapeUrl"] == "http://backend:8080/actuator/prometheus" and target["health"] == "up"
    request(args.backend_url + "/test-only/tracing/metrics-warmup")
    before = wait_for(counter, "first real HTTP counter sample")
    for number in range(12):
        request(args.backend_url + "/test-only/tracing/metrics-" + str(number) + "?secret=" + CANARY, canary=True)

    def incremented():
        value = counter()
        assert value >= before + 12
        return value

    after = wait_for(incremented, "real HTTP counter increment")
    metrics = request(args.backend_url + "/actuator/prometheus")
    assert CANARY not in metrics
    assert not re.search(r'(?:trace_id|span_id|request_id|account_number|idempotency_key)=', metrics)
    required = ("http_server_requests_seconds_count", "http_server_requests_seconds_sum",
                "http_server_requests_seconds_bucket", "jvm_memory_used_bytes", "jvm_memory_max_bytes",
                "jvm_threads_live_threads", "process_cpu_usage", "process_uptime_seconds",
                "hikaricp_connections_active", "hikaricp_connections_idle", "hikaricp_connections_max",
                "hikaricp_connections_pending", "hikaricp_connections_acquire_seconds_count",
                "hikaricp_connections_timeout_total", "jdbc_connections_max")
    for name in required:
        assert name in metrics
        wait_for(lambda name=name: present(name + '{job="finfast-backend"}'), name + " ingestion")
    bucket_lines = [line for line in metrics.splitlines() if line.startswith("http_server_requests_seconds_bucket{")
                    and 'uri="/test-only/tracing/{value}"' in line and 'status="200"' in line]
    assert len(bucket_lines) == 12
    assert any('le="+Inf"' in line for line in bucket_lines)

    def percentile(name):
        value = float(present("job_instance:http_latency_seconds:" + name)[0]["value"][1])
        assert math.isfinite(value) and value > 0
        return value

    quantiles = {name: wait_for(lambda name=name: percentile(name), name + " histogram estimate")
                 for name in ("p95_5m", "p99_5m")}
    board = grafana("/api/dashboards/uid/finfast-backend-overview")["dashboard"]
    panels = [p for p in board["panels"] if p["type"] != "row"]
    assert len(panels) >= 15
    panel_series = {}
    for panel in panels:
        panel_series[panel["title"]] = sum(len(query(target["expr"].replace("$environment", ".*")))
                                           for target in panel["targets"])
    assert panel_series["Hikari pool connections"] >= 4
    assert panel_series["Heap and non-heap memory used"] >= 2
    assert panel_series["HTTP responses by status class"] >= 1
    rules = grafana("/api/datasources/proxy/uid/finfast-prometheus/api/v1/rules")["data"]["groups"]
    loaded = [rule for group in rules for rule in group["rules"]]
    assert len(loaded) == 11 and all(rule["health"] == "ok" for rule in loaded)
    managers = grafana("/api/datasources/proxy/uid/finfast-prometheus/api/v1/alertmanagers")["data"]
    assert any("alertmanager:9093" in manager["url"] for manager in managers["activeAlertmanagers"])
    evidence = {"scrape_url": target["scrapeUrl"], "counter_before": before, "counter_after": after,
                "required_metrics": required, "percentiles_seconds": quantiles,
                "dashboard_panels": panel_series, "loaded_rules": len(loaded), "canary_absent": True}
    if args.evidence:
        args.evidence.write_text(json.dumps(evidence, indent=2) + "\n")
    print(json.dumps(evidence, indent=2))
    print("PASS: real HTTP/JVM/Hikari metrics, histograms, dashboard queries and alert delivery target.")


if __name__ == "__main__":
    main()
