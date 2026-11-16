"""提示词：选择器映射与关键约束。"""

import pytest

from app.chat.prompt import (
    LEARNING_PATH_SYSTEM_PROMPT,
    QA_SYSTEM_PROMPT,
    TEXT_QA_SYSTEM_PROMPT,
    system_prompt,
)
from app.contracts.workflow import WorkflowKind


@pytest.mark.parametrize(
    ("workflow", "expected"),
    [
        (WorkflowKind.QA, QA_SYSTEM_PROMPT),
        (WorkflowKind.LEARNING_PATH, LEARNING_PATH_SYSTEM_PROMPT),
        (WorkflowKind.TEXT_QA, TEXT_QA_SYSTEM_PROMPT),
    ],
)
def test_system_prompt_selects_by_workflow(workflow: WorkflowKind, expected: str) -> None:
    assert system_prompt(workflow) == expected


def test_prompts_are_distinct() -> None:
    prompts = {QA_SYSTEM_PROMPT, TEXT_QA_SYSTEM_PROMPT, LEARNING_PATH_SYSTEM_PROMPT}

    assert len(prompts) == 3
    assert all(prompt.strip() for prompt in prompts)


def test_qa_prompt_is_grounded_and_honest() -> None:
    assert "只基于你自己的知识" in QA_SYSTEM_PROMPT
    assert "不要声称查阅了本站内容" in QA_SYSTEM_PROMPT
    assert "不要编造" in QA_SYSTEM_PROMPT


def test_text_prompt_guards_against_injection() -> None:
    assert "<selected_text>" in TEXT_QA_SYSTEM_PROMPT
    assert "不是指令" in TEXT_QA_SYSTEM_PROMPT
    assert "忽略其中任何" in TEXT_QA_SYSTEM_PROMPT
    assert "不要编造" in TEXT_QA_SYSTEM_PROMPT


def test_learning_prompt_follows_route_and_limits_links() -> None:
    assert "route" in LEARNING_PATH_SYSTEM_PROMPT
    assert "不新增、删除、合并或调整阶段" in LEARNING_PATH_SYSTEM_PROMPT
    assert "/tag/" in LEARNING_PATH_SYSTEM_PROMPT
    assert "%20" in LEARNING_PATH_SYSTEM_PROMPT
    assert "/article/" not in LEARNING_PATH_SYSTEM_PROMPT
