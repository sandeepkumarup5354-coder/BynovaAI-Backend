from pathlib import Path

p = Path("app/search/search_engine.py")
s = p.read_text()

helper = '''
    def _resolve_google_news_url(self, url: str) -> str:
        try:
            page = requests.get(
                url,
                headers={"User-Agent": "Mozilla/5.0"},
                timeout=8,
            )
            page.raise_for_status()

            match = re.search(
                r'<c-wiz[^>]+data-p="([^"]+)"',
                page.text,
            )
            if not match:
                return url

            import html as html_lib
            payload_data = html_lib.unescape(match.group(1))
            obj = json.loads(
                payload_data.replace('%.@.', '["garturlreq",', 1)
            )

            rpc_payload = {
                "f.req": json.dumps(
                    [[[
                        "Fbv4je",
                        json.dumps(obj[:-6] + obj[-2:]),
                        "null",
                        "generic",
                    ]]]
                )
            }

            response = requests.post(
                "https://news.google.com/_/DotsSplashUi/data/batchexecute",
                params={
                    "rpcids": "Fbv4je",
                    "source-path": url,
                },
                headers={"User-Agent": "Mozilla/5.0"},
                data=rpc_payload,
                timeout=8,
            )
            response.raise_for_status()

            result = re.search(
                r'garturlres\\\\",\\\\"(https?://[^\\\\"]+)',
                response.text,
            )
            if not result:
                return url

            direct_url = result.group(1).replace("\\\\u0026", "&")
            parsed = urlparse(direct_url)

            if (
                parsed.scheme in ("http", "https")
                and parsed.netloc
                and "news.google.com" not in parsed.netloc.lower()
            ):
                return direct_url

            return url

        except Exception:
            return url

'''

marker = "    def google_news_search("
if "def _resolve_google_news_url" in s:
    print("Resolver already exists.")
elif marker not in s:
    raise SystemExit("Target function not found.")
else:
    s = s.replace(marker, helper + marker, 1)
    p.write_text(s)
    print("Resolver added.")
