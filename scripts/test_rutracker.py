#!/usr/bin/env python3
import requests, urllib.parse, json, base64, traceback
import urllib.request as ur
from bs4 import BeautifulSoup

GH_TOKEN = __import__("os").environ.get("GITHUB_TOKEN","")
REPO = "Komsomol39/rustream"
BASE = "https://nnmclub.to/forum"
UA   = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"

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
    s.headers.update({"User-Agent": UA})

    # 1. Поиск БЕЗ авторизации — что видит незалогиненный
    log("=== ПОИСК БЕЗ АВТОРИЗАЦИИ ===")
    q = urllib.parse.quote("паша техник")
    r = s.get(f"{BASE}/tracker.php?nm={q}", timeout=15)
    log(f"status={r.status_code}")
    soup = BeautifulSoup(r.content.decode("windows-1251","replace"), "html.parser")
    
    # Ищем строки с торрентами
    rows = soup.select("tr.prow1, tr.prow2, tr.hl-tr")
    log(f"Строк prow1/prow2/hl-tr: {len(rows)}")
    
    if rows:
        log(f"\nПервая строка:")
        log(str(rows[0])[:600])
    else:
        # Ищем любые ссылки viewtopic
        links = soup.select("a[href*='viewtopic']")
        log(f"viewtopic links: {len(links)}")
        for a in links[:5]:
            log(f"  {a.text.strip()[:60]}")
    push()

    # 2. Есть ли результаты вообще?
    log("\n=== ПРОВЕРКА ДОСТУПНОСТИ ПОИСКА ===")
    # NNM может требовать авторизацию для поиска
    auth_required = "Для просмотра" in r.text or "войдите" in r.text.lower() or "login" in r.url
    log(f"auth_required={auth_required}")
    
    # Ищем признаки авторизации
    soup_text = r.content.decode("windows-1251","replace")
    log(f"Есть 'Регистрация': {'Регистрация' in soup_text}")
    log(f"Есть 'Выход': {'Выход' in soup_text}")
    log(f"Есть 'tracker': {'tracker' in r.url}")
    
    # Страница выдачи без авторизации
    tbl = soup.find("table", class_="forumline")
    if tbl:
        all_rows = tbl.find_all("tr")
        log(f"table.forumline rows: {len(all_rows)}")
        # Показываем все строки
        for i, row in enumerate(all_rows[:10]):
            tds = row.find_all("td")
            log(f"  row {i}: {len(tds)} tds, classes={row.get('class')}")
    push()

    # 3. Имитируем авторизованный запрос с поддельными куками
    log("\n=== ТЕСТ С ПОДДЕЛЬНЫМИ КУКАМИ ===")
    s2 = requests.Session()
    s2.headers.update({"User-Agent": UA})
    # Устанавливаем типичные NNM куки вручную
    s2.cookies.set("phpbb2mysql_4_sid", "fake_session_id", domain="nnmclub.to")
    s2.cookies.set("phpbb2mysql_4_data", "fake_data", domain="nnmclub.to")
    r2 = s2.get(f"{BASE}/tracker.php?nm={q}", timeout=15)
    soup2 = BeautifulSoup(r2.content.decode("windows-1251","replace"), "html.parser")
    rows2 = soup2.select("tr.prow1, tr.prow2")
    log(f"С куками: rows={len(rows2)}, auth={'Выход' in r2.content.decode('windows-1251','replace')}")
    push()

except Exception as e:
    log(f"FATAL: {e}"); log(traceback.format_exc()); push()
