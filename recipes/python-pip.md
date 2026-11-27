# python-pip

A venv with the first of requirements-dev.txt, requirements-test.txt or requirements.txt installed, one pytest unit per
test file.

when: requirements-dev.txt|requirements-test.txt|requirements.txt
group: python

## setup.sh

```sh
#!/bin/sh
set -e
apt-get update -qq && apt-get install -y -qq curl ca-certificates
curl -LsSf https://astral.sh/uv/install.sh | env UV_UNMANAGED_INSTALL=/usr/local/bin sh
uv venv /opt/venv --python /usr/bin/python3
for r in requirements-dev.txt requirements-test.txt requirements.txt; do
  if [ -f "$r" ]; then uv pip install --python /opt/venv/bin/python -r "$r"; break; fi
done
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
