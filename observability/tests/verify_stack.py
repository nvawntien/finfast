#!/usr/bin/env python3
"""Read-only storage assertions plus safe requests to the separate test-only probe.

Starts/stops no containers and never accesses PostgreSQL. Requires Python 3 only.
"""
import argparse
import base64
import datetime
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

CANARY = "FINFAST_TRACE_SECRET_DO_NOT_EXPORT_4D2"


def strict_object(pairs):
    result = {}
    for key, value in pairs:
        assert key not in result, "Duplicate JSON key: " + key
        result[key] = value
    return result


def wait_for(check, description, timeout=90):
    deadline = time.monotonic() + timeout
    while True:
        try:
            return check()
        except (AssertionError, urllib.error.URLError, KeyError, IndexError):
            if time.monotonic() >= deadline:
                raise AssertionError("Timed out waiting for " + description) from None
            time.sleep(0.5)


def spans(tree):
    if isinstance(tree, dict):
        for key, value in tree.items():
            if key == "spans" and isinstance(value, list):
                yield from value
            else:
                yield from spans(value)
    elif isinstance(tree, list):
        for value in tree:
            yield from spans(value)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--grafana-url", default="http://localhost:3300")
    parser.add_argument("--probe-url", default="http://localhost:18080")
    parser.add_argument("--environment-file", default=".env.observability")
    parser.add_argument("--evidence", type=Path)
    args = parser.parse_args()
    settings = {}
    for line in Path(args.environment_file).read_text().splitlines():
        key, separator, value = line.partition("=")
        if separator and not key.lstrip().startswith("#"):
            settings[key.strip()] = value.strip().strip("\"'")
    credentials = settings["GRAFANA_ADMIN_USER"] + ":" + settings["GRAFANA_ADMIN_PASSWORD"]
    authorization = "Basic " + base64.b64encode(credentials.encode()).decode()

    def request(url, authenticated=False, headers=None):
        headers = dict(headers or {})
        headers["Accept"] = "application/json"
        if authenticated:
            headers["Authorization"] = authorization
        try:
            response = urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            body = response.read().decode()
            # Tempo can return plain text while a trace is not queryable yet.
            try:
                parsed = json.loads(body, object_pairs_hook=strict_object)
            except json.JSONDecodeError:
                assert response.status >= 400
                parsed = body
            return response.status, dict(response.headers), parsed

    def grafana(path):
        status, _, body = request(args.grafana_url + path, True)
        assert status == 200
        return body

    def probe_ready():
        status, _, body = request(args.probe_url + "/actuator/health")
        assert status == 200 and body["status"] == "UP"
        return body

    wait_for(probe_ready, "test-only HTTP probe readiness", timeout=60)
    for uid in ("finfast-loki", "finfast-tempo"):
        assert grafana("/api/datasources/uid/" + uid + "/health")["status"] == "OK"
    loki = grafana("/api/datasources/uid/finfast-loki")
    derived = loki["jsonData"]["derivedFields"][0]
    assert derived["datasourceUid"] == "finfast-tempo"
    assert derived["url"] == "${__value.raw}"
    pattern = re.compile(derived["matcherRegex"])
    start = time.time_ns() - 60_000_000_000

    def logs_for(request_id):
        query = '{service_name="finfast-backend"} |= ' + json.dumps(request_id)
        parameters = urllib.parse.urlencode({"query": query, "start": start, "end": time.time_ns(), "limit": 100})
        result = grafana("/api/datasources/proxy/uid/finfast-loki/loki/api/v1/query_range?" + parameters)
        entries = [(stream["stream"], stamp, line) for stream in result["data"]["result"] for stamp, line in stream["values"]]
        assert len(entries) == 1, "Expected one log event per boundary"
        return entries[0]

    evidence = []
    for path, expected in (("/test-only/tracing/e2e", 200), ("/test-only/tracing-failure", 500),
                           ("/test-only/tracing-sdk-failure", 500)):
        status, headers, body = request(args.probe_url + path + "?secret=" + CANARY,
                                        headers={"X-Request-ID": CANARY, "Authorization": "Bearer " + CANARY})
        assert status == expected
        assert CANARY not in json.dumps(body)
        request_id = next(value for key, value in headers.items() if key.lower() == "x-request-id")
        assert request_id != CANARY
        labels, timestamp, line = wait_for(lambda: logs_for(request_id), "correlated Loki log")
        event = json.loads(line, object_pairs_hook=strict_object)
        trace_id = event["trace.id"]
        assert re.fullmatch("[0-9a-f]{32}", trace_id)
        assert pattern.search(line).group(1) == trace_id
        assert set(labels) == {"service_name", "environment", "level", "module"}
        assert labels["environment"] == event["service"]["environment"]
        assert labels["level"] == ("INFO" if status == 200 else "ERROR")
        assert event["request.id"] == request_id
        assert CANARY not in line
        seconds, _, fraction = event["@timestamp"].rstrip("Z").partition(".")
        epoch = int(datetime.datetime.fromisoformat(seconds).replace(tzinfo=datetime.timezone.utc).timestamp())
        assert int(timestamp) == epoch * 1_000_000_000 + int(fraction.ljust(9, "0"))
        if status == 200:
            assert trace_id == body["traceId"] == body["otelTraceId"] == body["mdcTraceId"]
            assert event["span.id"] == body["spanId"]

        def trace():
            result = grafana("/api/datasources/proxy/uid/finfast-tempo/api/traces/" + trace_id)
            assert list(spans(result))
            return result

        stored = wait_for(trace, "stored Tempo trace")
        assert CANARY not in json.dumps(stored)
        server_spans = list(spans(stored))
        assert len(server_spans) == 1, "Expected one server span"
        span = server_spans[0]
        assert base64.b64decode(span["traceId"]).hex() == trace_id
        assert base64.b64decode(span["spanId"]).hex() == event["span.id"]
        assert span["kind"] == "SPAN_KIND_SERVER"
        resources = {v["key"]: v["value"]["stringValue"] for v in stored["batches"][0]["resource"]["attributes"]}
        assert resources["service.name"] == "finfast-backend"
        assert resources["service.version"] == "step4d3-verification"
        assert resources["deployment.environment.name"] == "verification"
        attributes = {v["key"]: v["value"] for v in span.get("attributes", [])}
        assert attributes["status"]["stringValue"] == str(status)
        assert "http.url" not in attributes
        serialized = json.dumps(stored)
        assert "finfast-backend" in serialized and "step4d3-verification" in serialized
        # Legacy Spring/Micrometer tags differ from native OTel HTTP conventions.
        assert "http.response.status_code" not in attributes
        if status == 500:
            def searchable():
                parameters = urllib.parse.urlencode({"q": '{span.status = "500"}', "limit": 100,
                                                     "start": start // 1_000_000_000, "end": int(time.time())})
                search = grafana("/api/datasources/proxy/uid/finfast-tempo/api/search?" + parameters)
                assert any(v["traceID"] == trace_id for v in search.get("traces", []))
                return search
            wait_for(searchable, "HTTP 500 TraceQL search index")
        item = {"trace_id": trace_id, "span_id": event["span.id"], "request_id": request_id,
                "http_status": status, "otel_status": span.get("status", {}), "labels": labels}
        evidence.append(item)
        print(json.dumps(item), flush=True)
    assert request(args.probe_url + "/actuator/health")[2]["status"] == "UP"
    if args.evidence:
        args.evidence.write_text(json.dumps(evidence, indent=2) + "\n")
    print("PASS: real Loki/Tempo correlation, exact timestamps, no duplicate events, safe exported traces and HTTP 5xx queries.")


if __name__ == "__main__":
    main()
