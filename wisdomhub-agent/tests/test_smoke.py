"""工程冒烟：包可导入、版本可读。"""

import app


def test_package_imports() -> None:
    assert app.__version__ == "0.1.0"
