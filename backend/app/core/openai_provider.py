import requests

from app.core.config import (
    OPENAI_API_KEY,
    OPENAI_MODEL,
    OPENAI_BASE_URL,
)


def call_openai(
    message,
    history=None,
    timeout=45,
):
    if not OPENAI_API_KEY:
        return None

    history = history or []

    input_items = []

    for item in history:
        if not isinstance(item, dict):
            continue

        role = str(item.get("role", "")).strip().lower()

        if role in ("assistant", "model"):
            role = "assistant"
        elif role != "user":
            continue

        parts = item.get("parts")

        if isinstance(parts, list):
            text_parts = []

            for part in parts:
                if isinstance(part, dict):
                    text = str(part.get("text", ""))
                    if text:
                        text_parts.append(text)

            if text_parts:
                input_items.append({
                    "role": role,
                    "content": "\n".join(text_parts),
                })
                continue

        content = item.get("content")

        if content:
            input_items.append({
                "role": role,
                "content": str(content),
            })

    input_items.append({
        "role": "user",
        "content": str(message),
    })

    payload = {
        "model": OPENAI_MODEL,
        "input": input_items,
    }

    try:
        response = requests.post(
            OPENAI_BASE_URL,
            headers={
                "Authorization": f"Bearer {OPENAI_API_KEY}",
                "Content-Type": "application/json",
            },
            json=payload,
            timeout=timeout,
        )

        return response

    except requests.RequestException as exc:
        print(
            f"[OPENAI ERROR] {type(exc).__name__}: {exc}",
            flush=True,
        )
        return None


def extract_openai_text(response):
    if response is None or not response.ok:
        return ""

    try:
        data = response.json()
    except ValueError:
        return ""

    output = data.get("output", [])

    texts = []

    for item in output:
        if not isinstance(item, dict):
            continue

        if item.get("type") != "message":
            continue

        for content in item.get("content", []):
            if not isinstance(content, dict):
                continue

            if content.get("type") == "output_text":
                text = content.get("text", "")

                if text:
                    texts.append(str(text))

    return "".join(texts).strip()
