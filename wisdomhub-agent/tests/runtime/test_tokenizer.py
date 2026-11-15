"""token 计数与按预算截断的边界。"""

import pytest

from app.runtime.tokenizer import DEFAULT_MARKER, count_tokens, truncate_text


def test_count_empty() -> None:
    assert count_tokens("") == 0


def test_count_ascii() -> None:
    assert count_tokens("hello world") == 2


def test_count_chinese() -> None:
    assert count_tokens("Spring Bean 生命周期") > 0


def test_special_token_text_does_not_raise() -> None:
    text = "<|endoftext|>"

    assert count_tokens(text) > 0
    assert truncate_text(text, 100) == text


def test_returns_text_when_within_budget() -> None:
    text = "hello world"

    assert truncate_text(text, count_tokens(text)) == text
    assert truncate_text(text, 100) == text


def test_truncates_and_keeps_marker() -> None:
    text = "word " * 100

    result = truncate_text(text, 20)

    assert result.endswith(DEFAULT_MARKER)
    assert count_tokens(result) <= 20
    assert text.startswith(result.removesuffix(DEFAULT_MARKER))


def test_marker_fits_budget_exactly() -> None:
    budget = count_tokens(DEFAULT_MARKER)

    result = truncate_text("word " * 100, budget)

    assert result == DEFAULT_MARKER
    assert count_tokens(result) <= budget


def test_budget_below_marker_returns_empty() -> None:
    budget = count_tokens(DEFAULT_MARKER) - 1

    assert truncate_text("word " * 100, budget) == ""


def test_zero_budget_returns_empty() -> None:
    assert truncate_text("hello", 0) == ""


def test_negative_budget_raises() -> None:
    with pytest.raises(ValueError):
        truncate_text("hello", -1)


def test_empty_text_returns_empty() -> None:
    assert truncate_text("", 10) == ""


def test_custom_marker_counts_toward_budget() -> None:
    marker = "[truncated]"

    result = truncate_text("word " * 100, 10, marker=marker)

    assert result.endswith(marker)
    assert count_tokens(result) <= 10


def test_chinese_truncation_has_no_replacement_char() -> None:
    result = truncate_text("生命周期" * 1000, 50)

    assert "�" not in result
    assert result.endswith(DEFAULT_MARKER)
    assert count_tokens(result) <= 50


def test_one_token_over_budget_truncates() -> None:
    text = "word " * 100

    result = truncate_text(text, count_tokens(text) - 1)

    assert result.endswith(DEFAULT_MARKER)
