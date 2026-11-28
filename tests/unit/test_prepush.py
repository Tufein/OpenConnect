"""tools/prepush.sh runs what most often turned CI red after a push; it passes on a clean tree."""

import os
import shutil
import subprocess
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]


def test_the_hook_runs_the_script():
    hook = ROOT / ".githooks" / "pre-push"
    assert os.access(hook, os.X_OK)
    assert "tools/prepush.sh" in hook.read_text()
    assert os.access(ROOT / "tools" / "prepush.sh", os.X_OK)


@pytest.mark.skipif(
    shutil.which("ruff") is None or shutil.which("git") is None, reason="needs ruff and git"
)
def test_prepush_passes_on_this_tree():
    if not (ROOT / ".git").exists():
        pytest.skip("not a git checkout")
    result = subprocess.run(
        ["bash", "tools/prepush.sh"], cwd=ROOT, capture_output=True, text=True, check=False
    )
    assert result.returncode == 0, result.stdout + result.stderr
