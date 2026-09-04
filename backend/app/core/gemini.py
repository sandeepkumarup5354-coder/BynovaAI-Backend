import time
import requests

from app.core.config import (
    GEMINI_BASE_URL,
    GEMINI_MODELS,
)


def call_gemini_with_retry(
    payload,
    api_key,
    timeout=45
):
    """
    Call Gemini with automatic model fallback
    and retry handling.

    Retries temporary provider errors:
    429, 500, 502, 503, 504
    """

    last_response = None

    for model in GEMINI_MODELS:
        url = (
            GEMINI_BASE_URL
            + model
            + ":generateContent"
        )

        for attempt in range(3):
            try:
                response = requests.post(
                    url,
                    params={"key": api_key},
                    json=payload,
                    timeout=timeout
                )

                last_response = response

                if response.ok:
                    return response

                if response.status_code in (
                    429,
                    500,
                    502,
                    503,
                    504
                ):
                    if attempt < 2:
                        time.sleep(
                            1.5 * (attempt + 1)
                        )
                        continue

                    break

                return response

            except requests.RequestException:
                if attempt < 2:
                    time.sleep(
                        1.5 * (attempt + 1)
                    )
                    continue

                last_response = None
                break

    return last_response


def build_generate_url(
    model,
    streaming=False
):
    """
    Build a Gemini API endpoint URL.
    """

    action = (
        "streamGenerateContent"
        if streaming
        else "generateContent"
    )

    return (
        GEMINI_BASE_URL
        + model
        + ":"
        + action
    )


def stream_gemini_with_fallback(
    payload,
    api_key,
    timeout=60
):
    """
    Start Gemini streaming with automatic model fallback.

    Returns:
        (response, selected_model)
        or (None, None) if all configured models fail.
    """

    for model in GEMINI_MODELS:
        url = build_generate_url(
            model,
            streaming=True
        )

        try:
            response = requests.post(
                url,
                params={
                    "key": api_key,
                    "alt": "sse"
                },
                json=payload,
                timeout=timeout,
                stream=True
            )

            if response.ok:
                return response, model

            status = response.status_code
            response.close()

            if status in (
                429,
                500,
                502,
                503,
                504
            ):
                continue

            return response, model

        except requests.RequestException:
            continue

    return None, None
