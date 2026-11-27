# python-uv

A venv with the dependencies uv.lock pins (every group), one pytest unit per test file.

when: pyproject.toml uv.lock
group: python

## setup.sh

```sh
#!/bin/sh
set -e
apt-get update -qq && apt-get install -y -qq curl ca-certificates
curl -LsSf https://astral.sh/uv/install.sh | env UV_UNMANAGED_INSTALL=/usr/local/bin sh
uv venv /opt/venv --python /usr/bin/python3
uv export --frozen --all-groups --no-hashes --no-emit-project -o /tmp/land-requirements.txt
uv pip install --python /opt/venv/bin/python -r /tmp/land-requirements.txt
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
