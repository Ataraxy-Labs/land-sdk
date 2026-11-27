# go

Go from go.dev, modules downloaded once so tests run offline, one unit per package folder.

when: go.mod

## setup.sh

```sh
#!/bin/sh
set -e
apt-get update -qq && apt-get install -y -qq curl ca-certificates
curl -fsSL https://go.dev/dl/go1.27.4.linux-amd64.tar.gz | tar -xz -C /usr/local
ln -sf /usr/local/go/bin/go /usr/local/bin/
export HOME=/root GOMODCACHE=/opt/gomod GOTOOLCHAIN=local GOFLAGS=-modcacherw
go mod download && chmod -R a+rX /opt/gomod
```

## tests

```
# name  folder  files  command
pkg     .       **/*_test.go  GOMODCACHE=/opt/gomod GOPROXY=off GOTOOLCHAIN=local go test ./{dir}
```

## tests

```
# name  folder  files  command
pkg     .       -  GOMODCACHE=/opt/gomod GOPROXY=off GOTOOLCHAIN=local go test ./...
```
