from contextvars import ContextVar, Token

_current_user: ContextVar[int] = ContextVar("chat_user_id", default=0)


def set_chat_user(user_id: int) -> Token:
    """请求进入时设置；工具在同一请求的任务上下文里读，模型不参与传参。"""
    return _current_user.set(int(user_id or 0))


def reset_chat_user(token: Token) -> None:
    _current_user.reset(token)


def current_chat_user() -> int:
    return _current_user.get()
