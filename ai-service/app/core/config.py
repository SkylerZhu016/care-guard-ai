import os

# DeepSeek LLM config (OpenAI-compatible API)
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
LLM_MODEL = os.getenv("LLM_MODEL", "deepseek-chat")

# Fallback: also support OPENAI_API_KEY for backward compat
API_KEY = DEEPSEEK_API_KEY or os.getenv("OPENAI_API_KEY", "")
BASE_URL = DEEPSEEK_BASE_URL or os.getenv("OPENAI_BASE_URL", "https://api.deepseek.com")

# Version tracking
MODEL_VERSION = "deepseek-chat-v1"
PROMPT_VERSION = "2.0.0"
KB_VERSION = "1.0.0"
