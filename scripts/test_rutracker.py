#!/usr/bin/env python3
import requests, urllib.parse, json, base64, traceback
import urllib.request as ur
from bs4 import BeautifulSoup

NNM_LOGIN    = __import__("os").environ.get("NNM_LOGIN","")
NNM_PASSWORD = __import__("os").environ.get("NNM_PASSWORD","")
GH_TOKEN     = __import__("os").environ.get("GITHUB_TOKEN","")
REPO         = "Komsomol39/rustream"
BASE         = "https://nnmclub.to/forum"
UA           = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"

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
    s.headers.update({"User-Agent": UA, "Accept-Language": "ru-RU,ru;q=0.9"})

    # 1. Получаем CSRF code со страницы логина
    log("=== ЛОГИН С CSRF ===")
    r0 = s.get(f"{BASE}/login.php", timeout=15)
    soup0 = BeautifulSoup(r0.content.decode("windows-1251","replace"), "html.parser")
    form = soup0.find("form", id="loginFrm")
    csrf = form.find("input", {"name":"code"})["value"] if form else ""
    redirect = form.find("input", {"name":"redirect"})["value"] if form else ""
    log(f"CSRF code: {csrf[:20]}...")
    log(f"Cookies after GET: {dict(s.cookies)}")
    push()

    # POST с CSRF
    r1 = s.post(f"{BASE}/login.php", data={
        "username": NNM_LOGIN,
        "password": NNM_PASSWORD,
        "autologin": "on",
        "redirect": redirect,
        "code": csrf,
        "login": "Вход",
    }, timeout=20, headers={"Referer": f"{BASE}/login.php"})
    log(f"POST: {r1.status_code} → {r1.url}")
    log(f"Cookies: {dict(s.cookies)}")
    
    logged = "phpbb2mysql" in str(s.cookies) or NNM_LOGIN.lower() in r1.text.lower()
    log(f"logged={logged}")
    push()

    if not logged:
        # Фрагмент ответа для диагностики
        log(f"Фрагмент ответа: {r1.content.decode('windows-1251','replace')[500:1200]}")
        push()
    else:
        # 2. Поиск
        log("\n=== ПОИСК ===")
        q = urllib.parse.quote("паша техник")
        r2 = s.get(f"{BASE}/tracker.php?nm={q}", timeout=20)
        log(f"Search: {r2.status_code}")
        soup2 = BeautifulSoup(r2.content.decode("windows-1251","replace"), "html.parser")

        # Разные варианты таблицы
        for sel in ["table.forumline", "table#tor-tbl", "table.tracker", "#tor-tbl"]:
            tbl = soup2.select_one(sel)
            if tbl:
                rows = tbl.find_all("tr")
                data = [r for r in rows if len(r.find_all("td")) > 3]
                log(f"  {sel}: {len(rows)} rows, {len(data)} data rows")
                if data:
                    log(f"  Первая строка: {str(data[0])[:600]}")
                break

        # Ссылки на торренты
        links = soup2.select("a[href*='viewtopic']")
        log(f"viewtopic links: {len(links)}")
        for a in links[:3]:
            log(f"  {a.text.strip()[:60]}")
        push()

except Exception as e:
    log(f"FATAL: {e}"); log(traceback.format_exc()); push()
