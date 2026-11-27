"""Typed wrappers over the land CLI.

Every call spawns ``land ... --json``, reads the one JSON object it prints on stdout and returns it; a non-zero exit
raises a typed error carrying land's ``fix`` and ``docs``. No network code: the CLI does all of that, with your login
or LAND_TOKEN.
"""
from __future__ import annotations

import json
import os
import subprocess
from typing import Any, Dict, List, Optional, Sequence, TypedDict

__all__ = ["Land", "LandError", "RefusedError", "UsageError", "StillRunningError", "CouldNotCheckError", "error_for"]


class RanUnit(TypedDict, total=False):
    name: str
    state: str  # "ran", "failed", "rerun", "not_run"
    seconds: float
    why: str
    exit: int
    output: str


class Tests(TypedDict, total=False):
    total: int
    ran: int
    carried: int
    failed: int
    ran_units: List[RanUnit]


class MergeAnswer(TypedDict, total=False):
    landed: List[str]
    refused: List[Dict[str, Any]]
    result: str  # the commit to push
    push: str  # the exact push command
    tests: Tests


class LocalAnswer(TypedDict, total=False):
    """``land merge --local``: never a verdict. ``signed``, ``recorded`` and ``landed`` are always False."""

    local: bool
    signed: bool
    recorded: bool
    landed: bool
    verdict: None
    said: str
    tests: Tests


class AddAnswer(TypedDict, total=False):
    added: bool
    dry_run: bool
    repo: str
    suggest: List[Dict[str, Any]]  # recipes to start from, when the tree has no .land/tests and no workflow


class LogsAnswer(TypedDict, total=False):
    failing: List[Dict[str, Any]]
    probe: Dict[str, Any]


class LandError(Exception):
    """A non-zero exit of the CLI. ``answer`` is the whole JSON object it printed."""

    def __init__(self, message: str, code: int, fix: Optional[str], docs: Optional[str], answer: Dict[str, Any]):
        super().__init__(message)
        self.code, self.fix, self.docs, self.answer = code, fix, docs, answer


class RefusedError(LandError):
    """exit 1: some branch was refused, a local test failed, or an add was refused. ``answer`` still holds what landed."""


class UsageError(LandError):
    """exit 2: a usage or local error; ``fix`` is the command that fixes it."""


class StillRunningError(LandError):
    """exit 3: land is still working; run the same call again to wait (``answer['again']``, ``answer['job']``)."""


class CouldNotCheckError(LandError):
    """exit 4: land could not check (an engine or infrastructure error): run the same call again."""


_BY_CODE = {1: RefusedError, 2: UsageError, 3: StillRunningError, 4: CouldNotCheckError}


def error_for(code: int, answer: Dict[str, Any]) -> LandError:
    """Map an exit code and the CLI's JSON to the typed error."""
    s = lambda k: answer[k] if isinstance(answer.get(k), str) else None  # noqa: E731
    cls = _BY_CODE.get(code, LandError)
    return cls(s("error") or f"land exited {code}", code, s("fix") or s("again"), s("docs"), answer)


class _Secrets:
    def __init__(self, land: "Land"):
        self._land = land

    def set(self, name: str, value: str) -> Dict[str, Any]:
        """Store one secret. The value goes on stdin, never in an argument."""
        return self._land.run(["secret", "set", name], stdin=value)

    def set_many(self, dotenv: str) -> Dict[str, Any]:
        """Store every NAME=value line of a .env text."""
        return self._land.run(["secret", "set"], stdin=dotenv)

    def list(self) -> Dict[str, Any]:
        return self._land.run(["secret", "list"])

    def rm(self, name: str) -> Dict[str, Any]:
        return self._land.run(["secret", "rm", name])


class Land:
    def __init__(self, bin: str = "land", cwd: Optional[str] = None, env: Optional[Dict[str, str]] = None):
        self.bin, self.cwd, self.env = bin, cwd, env or {}
        self.secrets = _Secrets(self)

    def run(self, args: Sequence[str], stdin: str = "") -> Dict[str, Any]:
        """Run ``land <args> --json`` and return its JSON; a non-zero exit raises the typed error."""
        p = subprocess.run([self.bin, *args, "--json"], cwd=self.cwd, env={**os.environ, **self.env},
                           input=stdin, capture_output=True, text=True)
        lines = p.stdout.strip().splitlines()
        try:
            answer = json.loads(lines[-1]) if lines else {}
        except json.JSONDecodeError:
            answer = {"error": f"land printed no JSON (exit {p.returncode}): {p.stderr.strip()[-500:]}"}
        if p.returncode != 0:
            raise error_for(p.returncode, answer)
        return answer

    def merge(self, branches: Sequence[str], onto: str, result_tree: bool = False) -> MergeAnswer:
        """Merge branches onto a target on land and prove the result. Exit 1 (some refused) raises RefusedError."""
        return self.run(["merge", *branches, "--onto", onto, *(["--result", "tree"] if result_tree else [])])  # type: ignore[return-value]

    def local(self, names: Sequence[str] = ()) -> LocalAnswer:
        """Run .land/tests (all, or the named lines or tests) on this machine. Unsigned, never a verdict."""
        return self.run(["merge", "--local", *names])  # type: ignore[return-value]

    def add(self, dry_run: bool = False, test: Sequence[str] = (), allow_test_edits: Optional[bool] = None,
            allow_low_guard: Optional[bool] = None, cpus: Optional[int] = None, time_limit: Optional[int] = None,
            plane: Optional[str] = None) -> AddAnswer:
        """Connect this repository (or preview with dry_run)."""
        a = ["add"] + (["--dry-run"] if dry_run else [])
        for t in test:
            a += ["--test", t]
        if allow_test_edits is not None:
            a.append("--allow-test-edits" if allow_test_edits else "--no-allow-test-edits")
        if allow_low_guard is not None:
            a.append("--allow-low-guard" if allow_low_guard else "--no-allow-low-guard")
        if cpus is not None:
            a += ["--cpus", str(cpus)]
        if time_limit is not None:
            a += ["--time-limit", str(time_limit)]
        if plane:
            a += ["--plane", plane]
        return self.run(a)  # type: ignore[return-value]

    def logs(self, name: Optional[str] = None, run: Optional[str] = None) -> LogsAnswer:
        """The output of the tests the last merge refused; with ``run``, a command in that test's view on land's runner."""
        return self.run(["logs", *([name] if name else []), *(["--run", run] if run is not None else [])])  # type: ignore[return-value]
