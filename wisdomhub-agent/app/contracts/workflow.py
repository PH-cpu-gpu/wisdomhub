"""会话工作流类型，wire 上使用整数。"""

from enum import IntEnum


class WorkflowKind(IntEnum):
    QA = 1
    LEARNING_PATH = 2
    TEXT_QA = 3
