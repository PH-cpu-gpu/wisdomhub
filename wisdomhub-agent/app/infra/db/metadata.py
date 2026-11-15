"""ORM 模型汇总入口，供 Alembic 使用。"""

from app.infra.db.base import Base

# 模型模块只在此导入；模型本身不要反向导入本模块。
target_metadata = Base.metadata
