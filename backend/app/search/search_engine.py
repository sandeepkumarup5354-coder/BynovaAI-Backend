import re
from html import unescape
from typing import Any, Dict, List
from urllib.parse import parse_qs, unquote, urlparse

import requests


class SearchEngine:
    """
    Central search layer for BynovaAI.

    Provides:
    - Real web search
    - YouTube search provider hook
    """

    SEARCH_URL = "https://html.duckduckgo.com/html/"

    def _clean_text(self, text: str) -> str:
        text = unescape(text or "")
        text = re.sub(r"<[^>]+>", "", text)
        text = re.sub(r"\s+", " ", text)
        return text.strip()

    def _clean_url(self, href: str) -> str:
        href = unescape(href or "").strip()

        if href.startswith("//"):
            href = "https:" + href

        if href.startswith("/"):
            return ""

        try:
            parsed = urlparse(href)

            if "duckduckgo.com" in parsed.netloc:
                params = parse_qs(parsed.query)
                if "uddg" in params:
                    return unquote(params["uddg"][0])

            return href
        except Exception:
            return href

    def web_search(
        self,
        query: str,
        max_results: int = 8
    ) -> Dict[str, Any]:
        query = (query or "").strip()

        if not query:
            return {
                "success": False,
                "type": "web",
                "query": "",
                "results": [],
                "message": "Search query is empty.",
            }

        try:
            response = requests.get(
                self.SEARCH_URL,
                params={"q": query},
                headers={
                    "User-Agent": (
                        "Mozilla/5.0 (Linux; Android 15) "
                        "AppleWebKit/537.36 "
                        "Chrome/140.0 Mobile Safari/537.36"
                    ),
                    "Accept": "text/html,application/xhtml+xml",
                    "Accept-Language": "en-US,en;q=0.9",
                },
                timeout=15,
            )

            response.raise_for_status()
            html = response.text

            results: List[Dict[str, str]] = []

            # DuckDuckGo result links can appear in different HTML forms.
            link_patterns = [
                r'<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>',
                r'<a[^>]*href="([^"]+)"[^>]*class="[^"]*result__a[^"]*"[^>]*>(.*?)</a>',
            ]

            matches = []
            for pattern in link_patterns:
                matches.extend(
                    re.findall(
                        pattern,
                        html,
                        flags=re.IGNORECASE | re.DOTALL,
                    )
                )

            seen_urls = set()

            for href, raw_title in matches:
                href = self._clean_url(href)
                title = self._clean_text(raw_title)

                if not href or not title:
                    continue

                if href in seen_urls:
                    continue

                seen_urls.add(href)

                results.append({
                    "title": title,
                    "url": href,
                    "snippet": "",
                })

                if len(results) >= max_results:
                    break

            # Fallback parser: inspect result containers individually.
            if not results:
                blocks = re.findall(
                    r'<div[^>]+class="[^"]*result[^"]*"[^>]*>(.*?)'
                    r'(?=<div[^>]+class="[^"]*result[^"]*"|</main>|</body>)',
                    html,
                    flags=re.IGNORECASE | re.DOTALL,
                )

                for block in blocks:
                    title_match = re.search(
                        r'<a[^>]+href="([^"]+)"[^>]*class="[^"]*result__a[^"]*"[^>]*>(.*?)</a>',
                        block,
                        flags=re.IGNORECASE | re.DOTALL,
                    )

                    if not title_match:
                        title_match = re.search(
                            r'<a[^>]+class="[^"]*result__a[^"]*"[^>]+href="([^"]+)"[^>]*>(.*?)</a>',
                            block,
                            flags=re.IGNORECASE | re.DOTALL,
                        )

                    if not title_match:
                        continue

                    href = self._clean_url(title_match.group(1))
                    title = self._clean_text(title_match.group(2))

                    if not href or not title or href in seen_urls:
                        continue

                    snippet_match = re.search(
                        r'class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</',
                        block,
                        flags=re.IGNORECASE | re.DOTALL,
                    )

                    snippet = (
                        self._clean_text(snippet_match.group(1))
                        if snippet_match
                        else ""
                    )

                    seen_urls.add(href)

                    results.append({
                        "title": title,
                        "url": href,
                        "snippet": snippet,
                    })

                    if len(results) >= max_results:
                        break

            return {
                "success": True,
                "type": "web",
                "query": query,
                "results": results,
                "count": len(results),
            }

        except requests.RequestException as exc:
            return {
                "success": False,
                "type": "web",
                "query": query,
                "results": [],
                "message": f"Web search failed: {exc}",
            }

        except Exception as exc:
            return {
                "success": False,
                "type": "web",
                "query": query,
                "results": [],
                "message": f"Web search parser failed: {exc}",
            }


    def _resolve_google_news_url(
        self,
        url: str,
        title: str = "",
        source: str = "",
    ) -> str:
        """Resolve Google News article URL to the original publisher URL."""

        try:
            import re
            import base64
            import requests
            from urllib.parse import urlparse, parse_qs, unquote

            if not url or "news.google.com" not in url:
                return url

            # Try Google's encoded article payload first.
            m = re.search(r"/rss/articles/([^?]+)", url)

            if m:
                token = m.group(1)

                # Google News article tokens are often URL-safe base64.
                try:
                    padded = token + "=" * (-len(token) % 4)
                    raw = base64.urlsafe_b64decode(padded)

                    text = raw.decode("utf-8", errors="ignore")

                    urls = re.findall(
                        r'https?://[A-Za-z0-9./?%&_=:#@+\-~]+',
                        text,
                    )

                    for candidate in urls:
                        candidate = unquote(candidate)
                        host = urlparse(candidate).netloc.lower()

                        if (
                            host
                            and "google.com" not in host
                            and "googleusercontent.com" not in host
                            and "gstatic.com" not in host
                        ):
                            return candidate.rstrip(".,;\"'")

                except Exception:
                    pass

            # Try Google's redirect endpoint.
            try:
                r = requests.get(
                    url,
                    headers={
                        "User-Agent": (
                            "Mozilla/5.0 (Linux; Android 15) "
                            "AppleWebKit/537.36 "
                            "Chrome/140.0 Mobile Safari/537.36"
                        )
                    },
                    timeout=10,
                    allow_redirects=True,
                )

                final_url = r.url
                host = urlparse(final_url).netloc.lower()

                if (
                    host
                    and "news.google.com" not in host
                    and "google.com" not in host
                ):
                    return final_url

            except Exception:
                pass

            # Last safe fallback: search publisher site by exact title.
            try:
                publisher_domains = {
                    "ndtv": "ndtv.com",
                    "the times of india": "timesofindia.indiatimes.com",
                    "times of india": "timesofindia.indiatimes.com",
                    "the hindu": "thehindu.com",
                    "hindustan times": "hindustantimes.com",
                    "india today": "indiatoday.in",
                    "the indian express": "indianexpress.com",
                    "indian express": "indianexpress.com",
                    "deccan herald": "deccanherald.com",
                    "economic times": "economictimes.indiatimes.com",
                    "business standard": "business-standard.com",
                    "news18": "news18.com",
                    "firstpost": "firstpost.com",
                    "times now": "timesnownews.com",
                    "moneycontrol": "moneycontrol.com",
                    "livemint": "livemint.com",
                }

                source_lower = (source or "").lower()
                domain = None

                for name, d in publisher_domains.items():
                    if name in source_lower:
                        domain = d
                        break

                if domain and title:
                    from urllib.parse import quote

                    q = quote(f'site:{domain} "{title}"')
                    search_url = f"https://www.google.com/search?q={q}"

                    rr = requests.get(
                        search_url,
                        headers={
                            "User-Agent": (
                                "Mozilla/5.0 (Linux; Android 15) "
                                "AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36"
                            )
                        },
                        timeout=10,
                    )

                    matches = re.findall(
                        r'https?://[^"<> ]+',
                        rr.text,
                    )

                    for candidate in matches:
                        candidate = unquote(candidate)
                        host = urlparse(candidate).netloc.lower()

                        if domain in host and "google.com" not in host:
                            return candidate

            except Exception:
                pass

            return url

        except Exception:
            return url

    def google_news_search(
        self,
        query: str,
        max_results: int = 10,
    ) -> dict:
        """Find direct article URLs from known news publishers."""

        try:
            import re
            import requests
            from urllib.parse import urljoin, urlparse

            publisher_domains = [
                "ndtv.com",
                "timesofindia.indiatimes.com",
                "thehindu.com",
                "hindustantimes.com",
                "indiatoday.in",
                "indianexpress.com",
                "deccanherald.com",
                "economictimes.indiatimes.com",
                "business-standard.com",
                "news18.com",
                "firstpost.com",
                "timesnownews.com",
                "moneycontrol.com",
                "livemint.com",
            ]

            if not query.strip():
                return {
                    "success": False,
                    "results": [],
                    "count": 0,
                    "error": "Search query is empty",
                }

            headers = {
                "User-Agent": (
                    "Mozilla/5.0 (Linux; Android 15) "
                    "AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36"
                ),
                "Accept-Language": "en-IN,en;q=0.9",
            }

            base = self.web_search(
                query.strip(),
                max(max_results * 4, 20),
            )

            if not base.get("success"):
                return {
                    "success": False,
                    "results": [],
                    "count": 0,
                    "error": base.get("error"),
                }

            def words(text):
                return set(re.findall(r"[a-z0-9]+", text.lower()))

            query_words = words(query)
            candidates = []
            seen = set()

            for item in base.get("results", []):
                page_url = (item.get("url") or "").strip()

                if not page_url:
                    continue

                host = urlparse(page_url).netloc.lower()

                if any(x in host for x in (
                    "google.com",
                    "news.google.com",
                    "duckduckgo.com",
                    "bing.com",
                )):
                    continue

                domain = next(
                    (d for d in publisher_domains if d in host),
                    None,
                )

                if not domain:
                    continue

                try:
                    response = requests.get(
                        page_url,
                        headers=headers,
                        timeout=10,
                    )

                    if response.status_code != 200:
                        continue

                    html = response.text

                    # Extract links without complicated quote nesting.
                    links = re.findall(
                        r'href=["\']([^"\']+)["\']',
                        html,
                        re.I,
                    )

                    for href in links:
                        article_url = urljoin(page_url, href)
                        parsed = urlparse(article_url)

                        if parsed.scheme not in ("http", "https"):
                            continue

                        if domain not in parsed.netloc.lower():
                            continue

                        # Skip images, videos and static assets.
                        if parsed.path.lower().endswith((
                            ".jpg", ".jpeg", ".png", ".webp",
                            ".gif", ".svg", ".mp4", ".webm",
                            ".css", ".js", ".xml", ".json",
                            ".ico", ".woff", ".woff2", ".ttf",
                            ".eot", ".map",
                        )):
                            continue

                        path = parsed.path.strip("/").lower()

                        if not path:
                            continue

                        blocked = {
                            "india",
                            "india-news",
                            "news",
                            "latest",
                            "latest-news",
                            "world",
                            "sports",
                            "business",
                            "entertainment",
                        }

                        if path in blocked:
                            continue

                        if any(x in path for x in (
                            "/category/",
                            "/tag/",
                            "/author/",
                        )):
                            continue

                        clean_url = (
                            parsed.scheme
                            + "://"
                            + parsed.netloc
                            + parsed.path
                        )

                        if clean_url in seen:
                            continue

                        # Look for text associated with this URL.
                        pos = html.find(href)
                        nearby = html[max(0, pos - 500):pos + 1000]

                        text = re.sub(
                            r"<[^>]+>",
                            " ",
                            nearby,
                        )
                        text = re.sub(r"\s+", " ", text).strip()

                        candidate_words = words(text)

                        if query_words:
                            score = (
                                len(query_words & candidate_words)
                                / len(query_words)
                            )
                        else:
                            score = 0.0

                        # Article-looking URLs get a small preference.
                        path_words = len(
                            re.findall(r"[a-z0-9]+", path)
                        )

                        if path_words >= 5:
                            score += 0.10

                        if score >= 0.30:
                            seen.add(clean_url)

                            candidates.append({
                                "title": (
                                    item.get("title")
                                    or query
                                ),
                                "url": clean_url,
                                "source": domain,
                                "description": "",
                                "published": "",
                                "_score": score,
                            })

                except Exception:
                    continue

            candidates.sort(
                key=lambda x: x.get("_score", 0),
                reverse=True,
            )

            results = candidates[:max_results]

            for item in results:
                item.pop("_score", None)

            return {
                "success": True,
                "results": results,
                "count": len(results),
                "error": None,
            }

        except Exception as e:
            return {
                "success": False,
                "results": [],
                "count": 0,
                "error": str(e),
            }

    def youtube_search(
        self,
        query: str,
        max_results: int = 8
    ) -> Dict[str, Any]:
        query = (query or "").strip()

        if not query:
            return {
                "success": False,
                "type": "youtube",
                "query": "",
                "results": [],
                "message": "YouTube search query is empty.",
            }

        try:
            import yt_dlp

            max_results = max(1, min(int(max_results), 20))

            options = {
                "extract_flat": True,
                "quiet": True,
                "no_warnings": True,
                "skip_download": True,
            }

            search_query = f"ytsearch{max_results}:{query}"

            with yt_dlp.YoutubeDL(options) as ydl:
                info = ydl.extract_info(
                    search_query,
                    download=False
                )

            results: List[Dict[str, Any]] = []

            for entry in info.get("entries", []):
                if not entry:
                    continue

                video_id = entry.get("id")
                title = entry.get("title") or ""

                if not video_id or not title:
                    continue

                results.append({
                    "title": title,
                    "video_id": video_id,
                    "url": (
                        "https://www.youtube.com/watch?v="
                        + video_id
                    ),
                    "channel": (
                        entry.get("uploader")
                        or entry.get("channel")
                        or ""
                    ),
                    "duration": entry.get("duration"),
                    "thumbnail": entry.get("thumbnail") or "",
                    "webpage_url": (
                        entry.get("webpage_url")
                        or (
                            "https://www.youtube.com/watch?v="
                            + video_id
                        )
                    ),
                })

                if len(results) >= max_results:
                    break

            return {
                "success": True,
                "type": "youtube",
                "query": query,
                "results": results,
                "count": len(results),
            }

        except Exception as exc:
            return {
                "success": False,
                "type": "youtube",
                "query": query,
                "results": [],
                "message": f"YouTube search failed: {exc}",
            }


search_engine = SearchEngine()
