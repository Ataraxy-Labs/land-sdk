# rust-cargo

Rust from rustup with crates vendored so tests run offline. Every test binary is built once, then the library's tests
and each integration test file run after it.

when: Cargo.toml

## setup.sh

```sh
#!/bin/sh
set -e
apt-get update -qq && apt-get install -y -qq curl ca-certificates gcc libc6-dev
export HOME=/root RUSTUP_HOME=/opt/rustup CARGO_HOME=/opt/cargo
curl -fsSL https://sh.rustup.rs | sh -s -- -y --no-modify-path --profile minimal --default-toolchain 1.100.0
ln -sfn /opt/rustup/toolchains/1.100.0-x86_64-unknown-linux-gnu /opt/rust
mkdir -p .cargo && /opt/rust/bin/cargo vendor vendor > .cargo/config.toml
chmod -R a+rX /opt/rustup vendor .cargo Cargo.lock
```

## tests

```
# name  folder  files  command
build   .       -  PATH=/opt/rust/bin:$PATH cargo test --offline --no-run ;; keep
lib     .       -  PATH=/opt/rust/bin:$PATH cargo test --offline --lib ;; after=build
tests   .       tests/*.rs  PATH=/opt/rust/bin:$PATH cargo test --offline --test $(basename {file} .rs) ;; after=build
```

## tests

```
# name  folder  files  command
build   .       -  PATH=/opt/rust/bin:$PATH cargo test --offline --no-run ;; keep
lib     .       -  PATH=/opt/rust/bin:$PATH cargo test --offline --lib ;; after=build
```
