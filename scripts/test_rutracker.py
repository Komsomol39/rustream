#!/usr/bin/env python3
import requests, urllib.parse, json, base64, traceback
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
    except: pass

try:
    s = requests.Session()
    s.headers.update({"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"})
    
    url = "https://rutor.info/search/0/0/0/sting"
    r = s.get(url, timeout=12)
    soup = BeautifulSoup(r.text, "html.parser")
    
    log(f"URL: {r.url} status={r.status_code}")
    
    # Все таблицы
    tables = soup.find_all("table")
    log(f"Таблиц на странице: {len(tables)}")
    for t in tables:
        rows = t.find_all("tr")
        log(f"  table id={t.get('id')} class={t.get('class')} rows={len(rows)}")
    push()

    # Попробуем найти результаты по-другому
    log("\n=== ПОИСК РЕЗУЛЬТАТОВ ===")
    # Строки с ссылками на детали
    detail_links = soup.select("a[href*='/torrent/']")
    log(f"Ссылки /torrent/: {len(detail_links)}")
    for a in detail_links[:3]:
        log(f"  {a.text.strip()[:60]} → {a['href']}")
    push()

    # Magnet ссылки
    magnets = soup.select("a[href^='magnet:']")
    log(f"Magnet ссылок: {len(magnets)}")
    for m in magnets[:2]:
        log(f"  {m['href'][:80]}")
    push()

    # Полный HTML фрагмент где есть результаты (ищем по тексту sting)
    log("\n=== HTML вокруг первого результата ===")
    # Найдём первый элемент содержащий Sting
    for tag in soup.find_all(string=lambda t: t and "sting" in t.lower() and len(t) > 10):
        parent = tag.parent
        log(f"tag={parent.name} class={parent.get('class')} text={str(tag)[:80]}")
        # Покажем строку таблицы
        tr = parent.find_parent("tr")
        if tr:
            log(f"TR: {str(tr)[:400]}")
            break
    push()

except Exception as e:
    log(f"FATAL: {e}"); log(traceback.format_exc()); push()
