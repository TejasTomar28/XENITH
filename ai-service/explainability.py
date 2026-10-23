"""Small SHAP-specific helper boundary for tests and future model variants."""

from __future__ import annotations

from typing import Any


def top_contributors(contributions: dict[str, float], count: int = 3) -> list[tuple[str, float]]:
    """Return the strongest actual per-feature SHAP contributions."""
    return sorted(contributions.items(), key=lambda item: abs(item[1]), reverse=True)[:count]
