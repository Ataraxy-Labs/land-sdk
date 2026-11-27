# python-poetry

A venv with the dependencies poetry.lock pins, installed by Poetry from its own venv, one pytest unit per test file.

when: pyproject.toml poetry.lock
group: python

## setup.sh

```sh
#!/bin/sh
set -e
apt-get update -qq && apt-get install -y -qq curl ca-certificates
curl -LsSf https://astral.sh/uv/install.sh | env UV_UNMANAGED_INSTALL=/usr/local/bin sh
uv venv /opt/venv --python /usr/bin/python3
uv venv /opt/poetry && uv pip install --python /opt/poetry/bin/python poetry
VIRTUAL_ENV=/opt/venv /opt/poetry/bin/poetry install --no-root
```

## tests pytest

```
# name  folder  files  command
tests   .       **/{test_*.py,*_test.py}  /opt/venv/bin/python -m pytest -q {file}
```

## tests pytest

```
# name  folder  files  command
tests   .       -  /opt/venv/bin/python -m pytest -q
```
