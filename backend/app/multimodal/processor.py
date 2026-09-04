import base64
import os
from typing import Any, Dict


TEXT_EXTENSIONS = {
    ".txt", ".csv", ".json", ".md", ".xml", ".html",
    ".css", ".js", ".java", ".py", ".kt", ".log"
}


class MultimodalProcessor:

    def read_text_file(self, file_path: str) -> Dict[str, Any]:
        if not file_path:
            return {
                "success": False,
                "type": "file",
                "message": "File path is empty.",
            }

        extension = os.path.splitext(file_path)[1].lower()

        if extension not in TEXT_EXTENSIONS:
            return {
                "success": False,
                "type": "file",
                "message": f"Unsupported text file type: {extension}",
            }

        try:
            with open(file_path, "r", encoding="utf-8") as file:
                content = file.read()

            return {
                "success": True,
                "type": "file",
                "filename": os.path.basename(file_path),
                "extension": extension,
                "content": content,
            }

        except Exception as exc:
            return {
                "success": False,
                "type": "file",
                "message": str(exc),
            }

    def read_text_bytes(
        self,
        file_bytes: bytes,
        filename: str,
        max_chars: int = 120000
    ) -> Dict[str, Any]:
        if not file_bytes:
            return {
                "success": False,
                "type": "file",
                "message": "File is empty.",
            }

        filename = filename or "file"
        extension = os.path.splitext(filename)[1].lower()

        if extension not in TEXT_EXTENSIONS:
            return {
                "success": False,
                "type": "file",
                "message": f"Unsupported text file type: {extension}",
            }

        try:
            content = file_bytes.decode(
                "utf-8",
                errors="replace"
            )

            return {
                "success": True,
                "type": "file",
                "filename": filename,
                "extension": extension,
                "content": content[:max_chars],
            }

        except Exception as exc:
            return {
                "success": False,
                "type": "file",
                "message": str(exc),
            }

    def encode_image(self, file_path: str) -> Dict[str, Any]:
        if not file_path:
            return {
                "success": False,
                "type": "image",
                "message": "Image path is empty.",
            }

        try:
            with open(file_path, "rb") as image_file:
                encoded = base64.b64encode(
                    image_file.read()
                ).decode("utf-8")

            return {
                "success": True,
                "type": "image",
                "filename": os.path.basename(file_path),
                "data": encoded,
            }

        except Exception as exc:
            return {
                "success": False,
                "type": "image",
                "message": str(exc),
            }


    def encode_image_bytes(
        self,
        image_bytes: bytes,
        filename: str = "image",
        mime_type: str = "image/jpeg"
    ) -> Dict[str, Any]:
        if not image_bytes:
            return {
                "success": False,
                "type": "image",
                "message": "Image is empty.",
            }

        try:
            encoded = base64.b64encode(
                image_bytes
            ).decode("ascii")

            return {
                "success": True,
                "type": "image",
                "filename": filename or "image",
                "mime_type": mime_type or "image/jpeg",
                "data": encoded,
            }

        except Exception as exc:
            return {
                "success": False,
                "type": "image",
                "message": str(exc),
            }


multimodal = MultimodalProcessor()
