import json
import threading

from app.core.config import (
    MEMORY_FILE,
    MAX_HISTORY,
)


class MemoryManager:

    def __init__(self):
        self.conversations = {}
        self.lock = threading.Lock()
        self.load()

    def load(self):
        try:
            with open(
                MEMORY_FILE,
                "r",
                encoding="utf-8"
            ) as f:
                data = json.load(f)

            if isinstance(data, dict):
                self.conversations = data
            else:
                self.conversations = {}

        except (
            FileNotFoundError,
            json.JSONDecodeError,
            OSError
        ):
            self.conversations = {}

    def save(self):
        try:
            with open(
                MEMORY_FILE,
                "w",
                encoding="utf-8"
            ) as f:
                json.dump(
                    self.conversations,
                    f,
                    ensure_ascii=False,
                    indent=2
                )
        except OSError:
            pass

    def get_history(
        self,
        client_id,
        chat_id
    ):
        with self.lock:
            user_chats = self.conversations.get(
                client_id,
                {}
            )

            if not isinstance(
                user_chats,
                dict
            ):
                return []

            saved_chat = user_chats.get(
                chat_id,
                []
            )

            if isinstance(
                saved_chat,
                dict
            ):
                history = saved_chat.get(
                    "messages",
                    []
                )
            elif isinstance(
                saved_chat,
                list
            ):
                history = saved_chat
            else:
                history = []

            if not isinstance(
                history,
                list
            ):
                return []

            return list(
                history[-MAX_HISTORY:]
            )

    def save_chat(
        self,
        client_id,
        chat_id,
        history,
        created_at,
        updated_at
    ):
        history = list(
            history[-MAX_HISTORY:]
        )

        with self.lock:

            if client_id not in self.conversations:
                self.conversations[client_id] = {}

            if not isinstance(
                self.conversations[client_id],
                dict
            ):
                self.conversations[client_id] = {}

            existing = self.conversations[
                client_id
            ].get(
                chat_id,
                {}
            )

            if isinstance(existing, dict):
                original_created_at = existing.get(
                    "created_at",
                    created_at
                )
            else:
                original_created_at = created_at

            self.conversations[
                client_id
            ][chat_id] = {
                "messages": history,
                "created_at": original_created_at,
                "updated_at": updated_at
            }

            self.save()

    def list_chats(
        self,
        client_id
    ):
        with self.lock:

            user_chats = self.conversations.get(
                client_id,
                {}
            )

            if not isinstance(
                user_chats,
                dict
            ):
                return []

            result = []

            for chat_id, chat_data in user_chats.items():

                if isinstance(
                    chat_data,
                    dict
                ):
                    history = chat_data.get(
                        "messages",
                        []
                    )
                    created_at = chat_data.get(
                        "created_at",
                        ""
                    )
                    updated_at = chat_data.get(
                        "updated_at",
                        ""
                    )
                else:
                    history = chat_data
                    created_at = ""
                    updated_at = ""

                if not isinstance(
                    history,
                    list
                ):
                    history = []

                title = "New Chat"

                for item in history:

                    if not isinstance(
                        item,
                        dict
                    ):
                        continue

                    if item.get("role") != "user":
                        continue

                    parts = item.get(
                        "parts",
                        []
                    )

                    if (
                        parts
                        and isinstance(
                            parts[0],
                            dict
                        )
                    ):
                        text = str(
                            parts[0].get(
                                "text",
                                ""
                            )
                        ).strip()

                        if text:
                            title = text[:40]

                            if len(text) > 40:
                                title += "..."

                            break

                result.append({
                    "chat_id": chat_id,
                    "title": title,
                    "messages": len(history),
                    "created_at": created_at,
                    "updated_at": updated_at
                })

            return result

    def get_chat(
        self,
        client_id,
        chat_id
    ):
        with self.lock:

            user_chats = self.conversations.get(
                client_id,
                {}
            )

            if not isinstance(
                user_chats,
                dict
            ):
                return None

            chat_data = user_chats.get(
                chat_id
            )

            if isinstance(
                chat_data,
                dict
            ):
                history = list(
                    chat_data.get(
                        "messages",
                        []
                    )
                )

                created_at = chat_data.get(
                    "created_at",
                    ""
                )

                updated_at = chat_data.get(
                    "updated_at",
                    ""
                )

            elif isinstance(
                chat_data,
                list
            ):
                history = list(chat_data)
                created_at = ""
                updated_at = ""

            else:
                return None

            if not history:
                return None

            return {
                "chat_id": chat_id,
                "messages": history,
                "created_at": created_at,
                "updated_at": updated_at
            }

    def delete_chat(
        self,
        client_id,
        chat_id
    ):
        with self.lock:

            user_chats = self.conversations.get(
                client_id,
                {}
            )

            if not isinstance(
                user_chats,
                dict
            ):
                return False

            if chat_id not in user_chats:
                return False

            user_chats.pop(
                chat_id,
                None
            )

            if user_chats:
                self.conversations[client_id] = user_chats
            else:
                self.conversations.pop(
                    client_id,
                    None
                )

            self.save()

            return True

    def clear_chat(
        self,
        client_id,
        chat_id
    ):
        return self.delete_chat(
            client_id,
            chat_id
        )


memory = MemoryManager()
