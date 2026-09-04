from typing import Any, Callable, Dict


class ToolManager:
    """
    Central registry and executor for BynovaAI tools.
    """

    def __init__(self):
        self._tools: Dict[str, Callable[..., Any]] = {}

    def register(self, name: str, function: Callable[..., Any]) -> None:
        name = (name or "").strip()

        if not name:
            raise ValueError("Tool name cannot be empty.")

        if not callable(function):
            raise TypeError("Tool must be callable.")

        self._tools[name] = function

    def has_tool(self, name: str) -> bool:
        return name in self._tools

    def list_tools(self):
        return sorted(self._tools.keys())

    def execute(self, name: str, **kwargs) -> Any:
        if name not in self._tools:
            raise KeyError(f"Tool not found: {name}")

        return self._tools[name](**kwargs)


tool_manager = ToolManager()
