"""token 计数与按预算截断。"""

from functools import lru_cache

import tiktoken
from tiktoken import Encoding

ENCODING_NAME = "cl100k_base"
DEFAULT_MARKER = "……（已截断）"


@lru_cache(maxsize=1)
def _encoding() -> Encoding:
    return tiktoken.get_encoding(ENCODING_NAME)


def count_tokens(text: str) -> int:
    """返回文本的 token 数；空串为 0。"""
    if not text:
        return 0
    return len(_encoding().encode(text, disallowed_special=()))


def truncate_text(text: str, max_tokens: int, *, marker: str = DEFAULT_MARKER) -> str:
    """超预算时保留开头并追加 marker，结果含 marker 不超过 max_tokens。"""
    if max_tokens < 0:
        raise ValueError("max_tokens 不能为负数")
    if max_tokens == 0 or not text:
        return ""

    encoding = _encoding()
    tokens = encoding.encode(text, disallowed_special=())
    if len(tokens) <= max_tokens:
        return text

    marker_tokens = len(encoding.encode(marker, disallowed_special=())) if marker else 0
    if marker_tokens > max_tokens:
        return ""

    keep = max_tokens - marker_tokens
    while keep >= 0:
        prefix = encoding.decode(tokens[:keep], errors="ignore")
        result = prefix + marker
        if len(encoding.encode(result, disallowed_special=())) <= max_tokens:
            return result
        keep -= 1
    return ""
