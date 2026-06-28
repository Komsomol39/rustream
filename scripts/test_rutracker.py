#!/usr/bin/env python3
"""Тест TorrentBy"""
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

# Пробуем разные домены TorrentBy
domains = [
    "https://torrentby.tv",
    "https://torrentby.org", 
    "https://torrentby.net",
    "https://torrentby.ru",
    "https://by.com.ru",
]

for domain in domains:
    log(f"\n=== {domain} ===")
    try:
        r = s.get(domain, timeout=8)
        log(f"main: status={r.status_code} len={len(r.text)}")
        if r.status_code == 200:
            # Ищем форму поиска
            soup = BeautifulSoup(r.text, "html.parser")
            forms = soup.find_all("form")
            log(f"Forms: {[f.get('action','') for f in forms[:3]]}")
            # Пробуем поиск
            q = urllib.parse.quote("sting")
            for search_url in [
                f"{domain}/search/{q}",
                f"{domain}/search.php?q={q}",
                f"{domain}/?s={q}",
                f"{domain}/index.php?nm={q}",
            ]:
                try:
                    r2 = s.get(search_url, timeout=8)
                    soup2 = BeautifulSoup(r2.text, "html.parser")
                    links = soup2.select("a[href*='torrent'], a[href*='download']")
                    log(f"  {search_url[-40:]}: {r2.status_code} torrent_links={len(links)}")
                    if links:
                        log(f"    First: {links[0].text.strip()[:60]} -> {links[0].get('href','')[:60]}")
                        break
                except Exception as e:
                    log(f"  {search_url[-40:]}: {e}")
    except Exception as e:
        log(f"ERROR: {e}")
    push()
