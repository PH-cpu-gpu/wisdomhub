from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder


def build_chat_prompt(system: str) -> ChatPromptTemplate:
    """system + 历史消息 + 当前问题；具体 system 文案由各工作流传入。"""
    return ChatPromptTemplate.from_messages(
        [
            ("system", system),
            MessagesPlaceholder("history"),
            ("human", "{question}"),
        ]
    )
