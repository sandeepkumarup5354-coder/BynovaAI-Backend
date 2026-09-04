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
                        "Mozilla/5.0 "
                        "(Linux; Android 15) "
                        "AppleWebKit/537.36 "
                        "Chrome/140.0 Mobile Safari/537.36"
                    )
                },
                timeout=15,
            )

            response.raise_for_status()

            html = response.text

            result_blocks = re.findall(
                r'<div class="result[^"]*".*?</div>\s*</div>',
                html,
                flags=re.IGNORECASE | re.DOTALL,
            )

            results: List[Dict[str, str]] = []

            for block in result_blocks:
                title_match = re.search(
                    r'class="result__a"[^>]*href="([^"]*)"[^>]*>(.*?)</a>',
                    block,
                    flags=re.IGNORECASE | re.DOTALL,
                )

                if not title_match:
                    continue

                href = self._clean_url(title_match.group(1))
                title = self._clean_text(title_match.group(2))

                snippet_match = re.search(
                    r'class="result__snippet"[^>]*>(.*?)</',
                    block,
                    flags=re.IGNORECASE | re.DOTALL,
                )

                snippet = (
                    self._clean_text(snippet_match.group(1))
                    if snippet_match
                    else ""
                )

                if not href or not title:
                    continue

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
                "message": f"Web search parser error: {exc}",
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
