from langgraph.graph import MessagesState, StateGraph


class AgentState(MessagesState):
    conversation_id: str
    user_id: int


def new_graph() -> StateGraph:
    """工作流从这里开始加节点/边；B3.14 起使用。"""
    return StateGraph(AgentState)
