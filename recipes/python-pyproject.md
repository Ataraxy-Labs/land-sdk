# python-pyproject

A venv with pyproject.toml's dependencies and every extra, one pytest unit per test file. For a project with no lock
file.

when: pyproject.toml
group: python

## setup.sh

```sh
#!/bin/sh
set -e
apt-get update -qq && apt-get install -y -qq curl ca-certificates
curl -LsSf https://astral.sh/uv/install.sh | env UV_UNMANAGED_INSTALL=/usr/local/bin sh
uv venv /opt/venv --python /usr/bin/python3
uv pip install --python /opt/venv/bin/python -r pyproject.toml --all-extras
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
