from app.infra.db.base import NAMING_CONVENTION, Base
from app.infra.db.metadata import target_metadata


def test_naming_convention_keys() -> None:
    assert set(NAMING_CONVENTION) == {"ix", "uq", "ck", "fk", "pk"}
    assert Base.metadata.naming_convention == NAMING_CONVENTION


def test_metadata_points_to_base_metadata() -> None:
    assert target_metadata is Base.metadata
