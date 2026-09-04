import os
from dotenv import load_dotenv

# Project root: ~/BynovaAI/backend
BASE_DIR = os.path.dirname(
    os.path.dirname(
        os.path.dirname(os.path.abspath(__file__))
    )
)

load_dotenv(
    os.path.join(BASE_DIR, ".env")
)

GEMINI_MODEL = os.getenv(
    "GEMINI_MODEL",
    "gemini-3.1-flash-lite"
)

GEMINI_MODELS = [
    "gemini-3.1-flash-lite",
    "gemini-3.5-flash",
    "gemini-3.5-flash-lite",
]

GEMINI_BASE_URL = (
    "https://generativelanguage.googleapis.com/v1beta/models/"
)

MEMORY_FILE = os.path.join(
    BASE_DIR,
    "conversations.json"
)

MAX_HISTORY = 12

BACKEND_HOST = "0.0.0.0"
BACKEND_PORT = 8081
