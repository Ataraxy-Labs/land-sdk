# land SDK for Python

Typed wrappers over the `land` command line. Each call runs `land ... --json` and gives you back the JSON it prints.
The SDK has no network code of its own, so it signs in the same way the CLI does (your `land login`, or `LAND_TOKEN`
in the environment). Install the `land` CLI first. No dependencies, Python 3.9 or newer.

```python
import os
from useland import Land, RefusedError

land = Land(cwd="/path/to/repo")

try:
    answer = land.merge(["sub/api", "sub/ledger"], onto="main")
    print(answer["push"])  # the push command that moves main
except RefusedError as e:
    print(e.answer["refused"], e.fix)

land.local(["spec"])        # run .land/tests on this machine, unsigned and never a verdict
land.add(dry_run=True)      # what an add would send, and recipes to start from
land.logs("users")          # the output of a refused test
land.secrets.set("STRIPE_KEY", os.environ["STRIPE_KEY"])
```

Every non-zero exit raises an error with `code`, `fix` (the command that fixes it), `docs` and the whole `answer`.

| exit | error | means |
|---|---|---|
| 1 | `RefusedError` | a branch was refused or a local test failed (`answer` still holds what landed) |
| 2 | `UsageError` | a usage or local error |
| 3 | `StillRunningError` | land is still working, so make the same call again |
| 4 | `CouldNotCheckError` | land could not check, so make the same call again |

Tests run against a fake `land` in `fake` with `python3 -m unittest discover -s tests`. It is not published to PyPI yet. Install it from the public repository.

```sh
pip install "git+https://github.com/Ataraxy-Labs/land-sdk@v0.1.0#subdirectory=python"
```
