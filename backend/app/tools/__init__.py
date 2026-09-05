from app.tools.calculator import calculate
from app.tools.tool_manager import tool_manager

tool_manager.register("calculator", calculate)

__all__ = ["calculate", "tool_manager"]
