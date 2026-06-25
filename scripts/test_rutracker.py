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

s = requests.Session()
s.headers.update({"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"})

# Kinozal детальный разбор строки
log("=== KINOZAL детальный парсинг ===")
try:
    r = s.get("https://kinozal.tv/browse.php?s=sting&g=0&c=0&v=0&d=0&w=0&t=0&f=0", timeout=12)
    soup = BeautifulSoup(r.text, "html.parser")
    rows = soup.select("tr.first.bg, tr.bg")[:5]
    log(f"Строк: {len(rows)}")
    for row in rows[:3]:
        tds = row.find_all("td")
        log(f"  TD классы: {[td.get('class') for td in tds]}")
        # Ищем ссылку на детали
        detail_a = row.select_one("td.nam a") or row.select_one("a[href*=details]")
        size_td  = row.select_one("td.s")
        seeds_td = row.select_one("td.sl_s")
        leech_td = row.select_one("td.sl_p")
        topic_id = detail_a["href"].split("id=")[-1] if detail_a and "id=" in detail_a.get("href","") else "?"
        log(f"  title: {detail_a.text.strip()[:65] if detail_a else '?'}")
        log(f"  size={size_td.text.strip() if size_td else '?'} seeds={seeds_td.text.strip() if seeds_td else '?'} id={topic_id}")
    push()
except Exception as e:
    log(f"Kinozal ERROR: {e}"); log(traceback.format_exc()); push()

# NNM-Club RSS детальный разбор
log("\n=== NNM-CLUB RSS парсинг ===")
try:
    r2 = s.get("https://nnmclub.to/forum/rss.php?nm=sting", timeout=12)
    # RSS в windows-1251
    content_bytes = r2.content
    xml_text = content_bytes.decode("windows-1251", errors="replace")
    
    from xml.etree import ElementTree as ET
    root = ET.fromstring(xml_text.encode("utf-8"))
    ns = {"dc": "http://purl.org/dc/elements/1.1/"}
    
    items = root.findall(".//item")
    log(f"Items в RSS: {len(items)}")
    for item in items[:5]:
        title   = item.findtext("title","?")
        link    = item.findtext("link","?")
        size_el = item.find("enclosure")
        size    = int(size_el.get("length",0))//1024//1024 if size_el is not None else 0
        log(f"  {title[:65]} | {size}MB | {link[-30:]}")
    push()
except Exception as e:
    log(f"NNM ERROR: {e}"); log(traceback.format_exc()); push()
