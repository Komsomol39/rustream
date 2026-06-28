#!/usr/bin/env python3
"""Тест YTS API"""
import requests, json, base64
import urllib.request as ur

GH_TOKEN = __import__("os").environ.get("GITHUB_TOKEN","")
REPO = "Komsomol39/rustream"

results = []
def log(msg): print(msg); results.append(str(msg))
def push():
    content = "\n".join(results)
    with open("test-results.txt","w",encoding="utf-8") as f: f.write(content)
    api = f"https://api.github.com/repos/{REPO}/contents/test-results.txt"
    req = ur.Request(api, headers={"Authorization":f"token {GH_TOKEN}"})
    sha = None
    try:
        with ur.urlopen(req) as r: sha = json.loads(r.read())["sha"]
    except: pass
    body = {"message":"test results [skip ci]","content":base64.b64encode(content.encode()).decode(),"branch":"main"}
    if sha: body["sha"] = sha
    req2 = ur.Request(api, data=json.dumps(body).encode(), method="PUT",
        headers={"Authorization":f"token {GH_TOKEN}","Content-Type":"application/json"})
    try:
        with ur.urlopen(req2): pass
    except: pass

s = requests.Session()
s.headers.update({"User-Agent": "Mozilla/5.0"})

# Тест разных YTS доменов
for base in ["https://yts.mx", "https://yts.lt", "https://yts.rs"]:
    log(f"\n=== {base} ===")
    try:
        url = f"{base}/api/v2/list_movies.json?query_term=sting&limit=5&sort_by=seeds"
        r = s.get(url, timeout=10)
        log(f"status={r.status_code} len={len(r.text)}")
        data = r.json()
        status = data.get("status","?")
        log(f"API status: {status}")
        movies = data.get("data", {}).get("movies", [])
        log(f"Movies: {len(movies)}")
        for m in movies[:3]:
            log(f"  {m.get('title')} ({m.get('year')}) rating={m.get('rating')}")
            for t in m.get("torrents", [])[:2]:
                log(f"    [{t.get('seeds')}s] {t.get('quality')} {t.get('type')} size={t.get('size')} magnet hash={t.get('hash','')[:20]}")
    except Exception as e:
        log(f"ERROR: {e}")
    push()
