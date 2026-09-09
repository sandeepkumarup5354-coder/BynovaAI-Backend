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
]

GEMINI_BASE_URL = (
    "https://generativelanguage.googleapis.com/v1beta/models/"
)

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-5.6-luna")
OPENAI_BASE_URL = "https://api.openai.com/v1/responses"

MEMORY_FILE = os.path.join(
    BASE_DIR,
    "conversations.json"
)

MAX_HISTORY = 12

BACKEND_HOST = "0.0.0.0"
BACKEND_PORT = 8081
