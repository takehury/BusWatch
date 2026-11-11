#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Extracts per-route ordered stop lists from busti.me route pages (parallel)."""
import json, re, subprocess, os, sys, time
from concurrent.futures import ThreadPoolExecutor, as_completed

UA = "Mozilla/5.0 (Linux; Android 8.0) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"

def fetch(slug):
    url = "https://ru.busti.me/krasnoyarsk/%s/" % slug
    try:
        out = subprocess.run(
            ["curl", "-sS", "-L", "--max-time", "18", "-A", UA, url],
            capture_output=True, timeout=22)
        if out.returncode != 0:
            return None
        return out.stdout.decode("utf-8", "ignore")
    except Exception:
        return None

def int_array(html, name):
    m = re.search(r"var\s+" + name + r"\s*=\s*\[", html)
    if not m:
        return []
    end = html.find("]", m.end())
    if end < 0:
        return []
    return [int(x) for x in re.findall(r"\d+", html[m.end():end])]

def parse_busstops(html):
    m = re.search(r"var\s+busstops\s*=\s*\{", html)
    if not m:
        return {}
    start = m.end(); depth = 1; i = start
    while i < len(html) and depth > 0:
        c = html[i]
        if c == "{": depth += 1
        elif c == "}": depth -= 1
        i += 1
    body = html[start:i-1]
    stops = {}
    pat = re.compile(
        r"(\d+)\s*:\s*\{[^{}]*?id\s*:\s*\d+\s*,\s*name\s*:\s*\"((?:[^\"\\]|\\.)*)\""
        r"[^{}]*?x\s*:\s*([-+]?\d+\.\d+)\s*,\s*y\s*:\s*([-+]?\d+\.\d+)\s*\}", re.S)
    for m in pat.finditer(body):
        sid = int(m.group(1))
        name = m.group(2).encode().decode("unicode_escape", "ignore") if "\\" in m.group(2) else m.group(2)
        stops[sid] = {"id": sid, "name": name, "lon": float(m.group(3)), "lat": float(m.group(4))}
    return stops

def process(r):
    slug = r["slug"]
    html = fetch(slug)
    if html is None:
        return r, None, "fetch failed"
    r0 = int_array(html, "route0")
    r1 = int_array(html, "route1")
    stops = parse_busstops(html)
    if not r0 and not r1:
        return r, None, "no route arrays"
    def build(ids):
        out = []
        for sid in ids:
            s = stops.get(sid)
            if s:
                out.append({"id": s["id"], "name": s["name"], "lat": s["lat"], "lon": s["lon"]})
        return out
    return r, {
        "id": r["id"], "name": r["name"], "type": r["type"], "title": r["title"],
        "dir0": build(r0), "dir1": build(r1),
    }, None

def main():
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    routes = json.load(open(os.path.join(base, "data", "routes.json")))
    ground = [r for r in routes if r.get("type", 0) in (0, 1, 2, 3)]
    result = {}
    ok = 0; fail = []
    t0 = time.time()
    with ThreadPoolExecutor(max_workers=5) as ex:
        futs = {ex.submit(process, r): r for r in ground}
        for f in as_completed(futs):
            r = futs[f]
            try:
                r_, data, err = f.result()
            except Exception as e:
                fail.append((r["name"], r["slug"], str(e)))
                continue
            if err:
                fail.append((r["name"], r["slug"], err))
                print("FAIL %-6s %-16s %s" % (r["name"], r["slug"], err), flush=True)
            else:
                result[str(data["id"])] = data
                ok += 1
                print("ok %-6s %-16s d0=%d d1=%d" % (r["name"], r["slug"], len(data["dir0"]), len(data["dir1"])), flush=True)
    out_path = os.path.join(base, "data", "route_stops.json")
    json.dump(result, open(out_path, "w"), ensure_ascii=False, separators=(",", ":"))
    print("done %.1fs ok=%d fail=%d -> %s" % (time.time()-t0, ok, len(fail), out_path), flush=True)
    for f in fail:
        print("  FAILED:", f, flush=True)

if __name__ == "__main__":
    main()
