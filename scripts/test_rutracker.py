#!/usr/bin/env python3
"""Тест зеркал RuTor — ищем незаблокированные"""
import requests, urllib.parse, json, base64
import urllib.request as ur
from bs4 import BeautifulSoup

GH_TOKEN = __import__("os").environ.get("GITHUB_TOKEN","")
REPO = "Komsomol39/rustream"
UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"

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

# Все известные зеркала
mirrors = [
    "https://rutor.info",
    "https://rutor.is",
    "https://rutor.org",
    "https://rutor.mobi",
    "https://rutorka.club",
    "https://rutorka.net",
    "https://rutorra.com",
    "https://rutor.lat",
    "https://new-rutor.org",
    "https://rutor.su",
]

q = urllib.parse.quote("sting")
log("=== ТЕСТ ЗЕРКАЛ RUTOR ===")
working = []

for mirror in mirrors:
    try:
        r = s.get(f"{mirror}/search/0/0/0/{q}", timeout=8)
        html = r.text
        is_blocked = "Вечная блокировка" in html or "Новый Адрес" in html
        soup = BeautifulSoup(html, "html.parser")
        rows = soup.select("tr.gai")
        magnets = soup.select("a[href^=\'magnet:\']")
        log(f"{mirror}: status={r.status_code} len={len(html)} blocked={is_blocked} tr.gai={len(rows)} magnets={len(magnets)}")
        if not is_blocked and len(rows) > 0:
            working.append(mirror)
            log(f"  ✅ РАБОТАЕТ! Первая строка: {rows[0].text.strip()[:80]}")
    except Exception as e:
        log(f"{mirror}: ERROR {str(e)[:60]}")
    push()

log(f"\nРАБОТАЮЩИЕ: {working}")
push()
