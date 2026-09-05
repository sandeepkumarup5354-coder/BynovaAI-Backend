from dataclasses import dataclass
import re


@dataclass
class RouteDecision:
    intent: str
    confidence: float
    reason: str


class SmartRouter:

    def route(self, message: str, has_image=False, has_file=False):
        text = (message or "").strip().lower()

        if has_image:
            return RouteDecision("IMAGE", 1.0, "Image attached")

        if has_file:
            return RouteDecision("FILE", 1.0, "File attached")

        if not text:
            return RouteDecision("CHAT", 1.0, "Empty message")

        # Image generation
        image_words = (
            "generate image",
            "generate an image",
            "create an image",
            "make an image",
            "draw an image",
            "generate a picture",
            "create a picture",
            "make a picture",
            "draw a picture",
        )

        if any(x in text for x in image_words):
            return RouteDecision(
                "IMAGE_GENERATE",
                0.98,
                "Image generation request"
            )

        # YouTube
        youtube_words = (
            "youtube",
            "youtube video",
            "youtube videos",
            "watch video",
            "video link",
            "video tutorial",
        )

        if any(x in text for x in youtube_words):
            return RouteDecision(
                "YOUTUBE",
                0.98,
                "YouTube/video request"
            )

        # Web / current information
        web_words = (
            "search",
            "google",
            "web search",
            "internet",
            "latest",
            "today",
            "current",
            "news",
            "recent",
            "live",
            "find",
            "lookup",
            "website",
            "source",
            "link",
        )

        if any(x in text for x in web_words):
            return RouteDecision(
                "WEB",
                0.95,
                "Current/search request"
            )

        # Calculations
        if (
            re.search(r"\d+\s*[\+\-\*\/%]\s*\d+", text)
            or any(x in text for x in (
                "calculate",
                "calculator",
                "solve",
                "percentage",
                "percent",
                "multiply",
                "divide",
                "add",
                "subtract",
            ))
        ):
            return RouteDecision(
                "CALCULATOR",
                0.96,
                "Calculation request"
            )

        # Normal AI
        return RouteDecision(
            "CHAT",
            0.90,
            "General conversation/question"
        )


smart_router = SmartRouter()
