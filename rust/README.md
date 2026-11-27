# land SDK for Rust

Typed wrappers over the `land` command line. Each call runs `land ... --json` and gives you back the JSON it prints as a `serde_json::Value`.
The SDK has no network code of its own, so it signs in the same way the CLI does (your `land login`, or `LAND_TOKEN`
in the environment). Install the `land` CLI first. One dependency, `serde_json`.

It is not published to crates.io yet. Add it from the public repository.

```toml
# Cargo.toml
[dependencies]
useland = { git = "https://github.com/Ataraxy-Labs/land-sdk", tag = "v0.1.0" }
```

```rust
use useland::{AddOptions, Kind, Land};

let land = Land::new().cwd("/path/to/repo");

match land.merge(&["sub/api", "sub/ledger"], "main", false) {
    Ok(answer) => println!("{}", answer["push"]), // the push command that moves main
    Err(e) if e.kind == Kind::Refused => println!("{} {:?}", e.answer["refused"], e.fix),
    Err(e) => return Err(e.into()),
}

land.local(&["spec"])?;                                          // run .land/tests on this machine, unsigned and never a verdict
land.add(&AddOptions { dry_run: true, ..Default::default() })?;  // what an add would send, and recipes to start from
land.logs(Some("users"), None)?;                                 // the output of a refused test
land.logs(Some("users"), Some("ls -la"))?;                       // a command in that test's view on land's runner
land.secrets().set("STRIPE_KEY", &std::env::var("STRIPE_KEY")?)?;
```

Every non-zero exit returns a `LandError` with `kind`, `code`, `fix` (the command that fixes it), `docs` and the whole
`answer`.

| exit | `kind` | means |
|---|---|---|
| 1 | `Kind::Refused` | a branch was refused or a local test failed (the answer still holds what landed) |
| 2 | `Kind::Usage` | a usage or local error |
| 3 | `Kind::StillRunning` | land is still working, so make the same call again |
| 4 | `Kind::CouldNotCheck` | land could not check, so make the same call again |

`run` gives the whole JSON object of any call. Tests run against a fake `land` in `fake` with
`cargo test --tests`.
