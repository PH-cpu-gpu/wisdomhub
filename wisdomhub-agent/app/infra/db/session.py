"""异步数据库引擎与会话工厂。"""

from dataclasses import dataclass
from typing import Self

from sqlalchemy import text
from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from app.core.config import DbSettings


@dataclass(frozen=True, slots=True)
class Database:
    engine: AsyncEngine
    session_factory: async_sessionmaker[AsyncSession]

    @classmethod
    def create(cls, settings: DbSettings) -> Self:
        engine = create_async_engine(
            settings.url,
            pool_size=settings.pool_size,
            pool_timeout=settings.pool_timeout,
            pool_pre_ping=True,
            echo=settings.echo,
        )
        return cls(
            engine=engine,
            session_factory=async_sessionmaker(engine, expire_on_commit=False, autoflush=False),
        )

    async def ping(self) -> None:
        async with self.engine.connect() as connection:
            await connection.execute(text("SELECT 1"))

    async def close(self) -> None:
        await self.engine.dispose()
