#!/usr/bin/env python3
import requests, json, base64, urllib.parse, traceback
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
    except Exception as e: print(f"push err: {e}")

try:
    import xml.etree.ElementTree as ET
    s = requests.Session()
    s.headers.update({"User-Agent":"Mozilla/5.0"})

    q = urllib.parse.quote("паша техник")
    r = s.get(f"https://nnmclub.to/forum/rss.php?nm={q}", timeout=15)
    log(f"HTTP {r.status_code}, len={len(r.content)}")

    # Декодируем windows-1251
    text = r.content.decode("windows-1251", errors="replace")
    log(f"Первые 300 символов: {text[:300]}")
    push()

    # Парсим XmlPullParser-style через ET, но передаём bytes
    root = ET.fromstring(r.content)  # ET сам определит кодировку из XML header
    items = root.findall(".//item")
    log(f"\nItems: {len(items)}")

    has_enc = 0
    no_enc = 0
    for item in items:
        title = item.findtext("title","?")
        enc   = item.find("enclosure")
        cat   = item.findtext("category","")
        if enc is not None:
            has_enc += 1
            size = int(enc.get("length",0))//1024//1024
            log(f"  [TOR] {title[:65]} | {size}MB | cat={cat}")
        else:
            no_enc += 1
            if no_enc <= 3:
                log(f"  [NEWS] {title[:65]} | cat={cat}")

    log(f"\nТоррентов: {has_enc}, Новостей/прочего: {no_enc}")
    log("\nФильтр: оставлять только items с <enclosure> — это торренты")
    push()

except Exception as e:
    log(f"FATAL: {e}"); log(traceback.format_exc()); push()
