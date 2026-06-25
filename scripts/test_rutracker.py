#!/usr/bin/env python3
import requests, urllib.parse, traceback, json, base64
import urllib.request as ur
from bs4 import BeautifulSoup

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
    except Exception as e: print(f"push err: {e}")

try:
    MIRRORS = ["https://rutor.info", "https://rutor.is", "https://rutor.im"]
    QUERIES = ["паша техник", "sting", "пилот"]
    UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"

    s = requests.Session()
    s.headers.update({"User-Agent": UA})

    log("=== ДОСТУПНОСТЬ ЗЕРКАЛ ===")
    working = None
    for mirror in MIRRORS:
        try:
            r = s.get(mirror, timeout=10)
            log(f"{mirror}: {r.status_code} (len={len(r.text)})")
            if r.status_code == 200 and working is None:
                working = mirror
        except Exception as e:
            log(f"{mirror}: ERROR {e}")
    push()

    if not working:
        log("Ни одно зеркало не работает"); push(); exit()

    log(f"\n=== ТЕСТ ПОИСКА на {working} ===")
    for query in QUERIES:
        enc = urllib.parse.quote(query)
        # Пробуем разные URL форматы
        urls = [
            f"{working}/search/0/0/0/{enc}",
            f"{working}/search/0/0/100/{enc}",  # сортировка по сидам
            f"{working}/index.php?s={enc}",
        ]
        for search_url in urls:
            try:
                r2 = s.get(search_url, timeout=12)
                soup = BeautifulSoup(r2.text, "html.parser")
                tbl = soup.find("table", id="index")
                rows = tbl.select("tr")[1:] if tbl else []
                log(f"  [{r2.status_code}] {search_url[-40:]}: rows={len(rows)}")
                if rows:
                    for row in rows[:3]:
                        cells = row.select("td")
                        if len(cells) < 4: continue
                        links = cells[1].select("a")
                        title = links[-1].text.strip()[:60] if links else "?"
                        magnet = cells[1].select_one("a[href^='magnet:']")
                        seeds = cells[4].select_one("span") if len(cells) > 4 else None
                        size = cells[3].text.strip() if len(cells) > 3 else "?"
                        log(f"    [{seeds.text.strip() if seeds else '?'}s] {title} | {size} | magnet={'✅' if magnet else '❌'}")
                    break  # нашли рабочий URL
            except Exception as e:
                log(f"  ERROR: {e}")
        push()

    log("\n=== HTML СТРУКТУРА ПЕРВОЙ СТРОКИ ===")
    r3 = s.get(f"{working}/search/0/0/0/{urllib.parse.quote('sting')}", timeout=12)
    soup3 = BeautifulSoup(r3.text, "html.parser")
    tbl3 = soup3.find("table", id="index")
    if tbl3:
        rows3 = tbl3.select("tr")
        if len(rows3) > 1:
            log(str(rows3[1])[:600])
    push()

except Exception as e:
    log(f"FATAL: {e}"); log(traceback.format_exc()); push()
