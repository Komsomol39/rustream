#!/usr/bin/env python3
import requests, json, base64, urllib.parse
import urllib.request as ur
from bs4 import BeautifulSoup

GH_TOKEN = __import__("os").environ.get("GITHUB_TOKEN","")
REPO = "Komsomol39/rustream"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"

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
s.headers.update({"User-Agent": UA})

# Расширенный список возможных доменов
domains = [
    "https://torrentby.club",
    "https://torrentby.cc",
    "https://torrentby.pw",
    "https://torrentby.me",
    "https://torrentby.io",
    "https://torrent.by",
    "https://tby.tv",
    "https://t-by.net",
    # Другие белорусские трекеры
    "https://tfilm.club",
    "https://toloka.to",
    "https://baibako.tv",
]

for domain in domains:
    try:
        r = s.get(domain, timeout=6)
        log(f"✅ {domain}: {r.status_code} len={len(r.text)}")
    except Exception as e:
        log(f"❌ {domain}: {str(e)[:50]}")
push()
