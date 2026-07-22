import os
from dotenv import load_dotenv

load_dotenv()

# OpenAI / LLM config
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "sk-placeholder")
OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
LLM_MODEL = os.getenv("LLM_MODEL", "gpt-4o-mini")

# Safety thresholds
RED_FLAG_THRESHOLD = int(os.getenv("RED_FLAG_THRESHOLD", "7"))  # severity >= 7
CRITICAL_RISK_SCORE = int(os.getenv("CRITICAL_RISK_SCORE", "8"))

# Version tracking
MODEL_VERSION = "1.0.0"
PROMPT_VERSION = "1.0.0"
KB_VERSION = "1.0.0"
