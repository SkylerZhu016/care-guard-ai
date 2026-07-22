"""pytest 配置：将 ai-service 加入 sys.path，Mock 模式"""
import sys
import os

# 将 app 包加入路径
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# 强制 Mock 模式
os.environ["MOCK_LLM"] = "true"
