from app.contracts.workflow import WorkflowKind


def test_workflow_kind_values() -> None:
    assert int(WorkflowKind.QA) == 1
    assert int(WorkflowKind.LEARNING_PATH) == 2
    assert int(WorkflowKind.TEXT_QA) == 3
    assert [kind.name for kind in WorkflowKind] == ["QA", "LEARNING_PATH", "TEXT_QA"]
