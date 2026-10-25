from functools import lru_cache
from typing import Protocol

import tiktoken

MESSAGE_OVERHEAD_TOKENS = 4


class TokenCounter(Protocol):
    def count(self, text: str) -> int: ...

    def count_message(self, text: str) -> int: ...


@lru_cache
def _encoding():
    return tiktoken.get_encoding("cl100k_base")


class TiktokenCounter:
    """cl100k_base 只是 Qwen 的保守近似；换精确 tokenizer 时替换实现即可。"""

    def count(self, text: str) -> int:
        return len(_encoding().encode(text))

    def count_message(self, text: str) -> int:
        return self.count(text) + MESSAGE_OVERHEAD_TOKENS
