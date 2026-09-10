#!/usr/bin/env python3
"""Summarize observed EDT stacks and queue latency; this is not a frame benchmark."""
import argparse
from collections import Counter
import json
import math
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("recording", type=Path)
parser.add_argument("destination", type=Path)
args = parser.parse_args()
assert (args.recording / "complete.txt").exists(), "Stop the observer before summarizing"
samples = [json.loads(line) for line in (args.recording / "edt-samples.jsonl").read_text().splitlines()]
probes = [json.loads(line) for line in (args.recording / "event-queue-probes.jsonl").read_text().splitlines()]


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * fraction) - 1)] if ordered else None


suspect_terms = (
    "java.io.FileInputStream.read", "java.io.FileOutputStream.write", "java.io.RandomAccessFile.",
    "java.io.UnixFileSystem.", "sun.nio.fs.UnixNativeDispatcher.", "java.lang.ProcessImpl.",
    "java.lang.ProcessBuilder.start", "uniffi.toolkit.", "java.security.KeyStore.load",
)
result = {
    "method": "Java agent in actual ProGuard app and packaged runtime, EDT stack every 50 ms and one pending EventQueue probe at a time.",
    "limitations": [
        "Observer changes process timing and starts EventQueue early; startup/first-frame timings are not measured.",
        "Queue latency is synthetic event dispatch latency, not rendered-frame time, input-to-photon latency or a baseline comparison.",
        "Sampling can miss short calls; zero observed I/O/FFI frames cannot prove zero executions.",
        "Heap samples have no forced GC and these short runs do not establish leak freedom.",
        "FileKit/native desktop actions can intentionally run on EDT; classify suspicious stacks using their complete call chain.",
    ],
    "samples": len(samples), "probes": len(probes), "scenarios": {}, "suspiciousStacks": [],
}
for label in sorted({x["scenario"] for x in samples + probes}):
    rows = [x for x in samples if x["scenario"] == label]
    times = [x["latencyMs"] for x in probes if x["scenario"] == label]
    stacks = Counter(tuple(x["stack"]) for x in rows)
    result["scenarios"][label] = {
        "sampleCount": len(rows), "probeCount": len(times),
        "queueLatencyMs": {"median": percentile(times, .5), "p95": percentile(times, .95), "max": max(times) if times else None},
        "heapUsedRange": [min(x["heapUsed"] for x in rows), max(x["heapUsed"] for x in rows)] if rows else None,
        "threadCountRange": [min(x["threadCount"] for x in rows), max(x["threadCount"] for x in rows)] if rows else None,
        "topStacks": [{"count": count, "frames": list(stack)} for stack, count in stacks.most_common(6)],
    }
    for stack, count in stacks.items():
        if any(term in frame for frame in stack for term in suspect_terms):
            result["suspiciousStacks"].append({"scenario": label, "count": count, "frames": list(stack)})
args.destination.parent.mkdir(parents=True, exist_ok=True)
args.destination.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
print(json.dumps({"samples": len(samples), "probes": len(probes), "scenarios": list(result["scenarios"]),
                  "suspiciousUniqueStacks": len(result["suspiciousStacks"])}, ensure_ascii=False))
