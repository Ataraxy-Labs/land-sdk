# land SDKs

land is CI and merging for coding agents. It merges your agents' finished branches, runs your tests on the merged result and lets through only what passes.

These SDKs are thin typed wrappers over the `land` command line. Each call runs `land ... --json` and hands back the JSON it prints, so an SDK has no network code of its own and signs in the same way the CLI does. Install the CLI first.

```sh
curl -fsSL https://useland.dev/install | sh
```

## Install

Python is on PyPI. The others are not on a package registry yet, so each one installs from this repository or from its release.

| Language | Folder | Install |
|---|---|---|
| Python | [`python`](python) | `pip install useland` |
| TypeScript | [`typescript`](typescript) | `npm install https://github.com/Ataraxy-Labs/land-sdk/releases/download/v0.1.0/useland-0.1.0.tgz` |
| Go | [`go`](go) | `go get github.com/Ataraxy-Labs/land-sdk/go@v0.1.0` |
| Rust | [`rust`](rust) | `useland = { git = "https://github.com/Ataraxy-Labs/land-sdk", tag = "v0.1.0" }` in `Cargo.toml` |
| Ruby | [`ruby`](ruby) | `gem "useland", git: "https://github.com/Ataraxy-Labs/land-sdk", tag: "v0.1.0", glob: "ruby/*.gemspec"` in the `Gemfile` |
| Elixir | [`elixir`](elixir) | `{:useland, git: "https://github.com/Ataraxy-Labs/land-sdk", tag: "v0.1.0", sparse: "elixir"}` in `mix.exs` |
| PHP | [`php`](php) | `composer config repositories.land-sdk vcs https://github.com/Ataraxy-Labs/land-sdk` then `composer require ataraxy-labs/useland:^0.1` |
| Java | [`java`](java) | clone this repository, then `cd java && mvn install`, or compile `java/src/main/java` with `javac` |
| .NET | [`dotnet`](dotnet) | clone this repository, then `dotnet add reference land-sdk/dotnet/Useland/Useland.csproj` |

## What every SDK has

- `merge` merges branches onto a target and returns the result, with the push command that moves main
- `local` runs `.land/tests` on this machine
- `add` connects a repository, with a dry run
- `logs` gives the output of a refused test, and runs a command in the view it failed in
- `secrets` sets, lists and removes a repository's secrets, sending values on stdin
- `run` gives the whole JSON object of any call

Every non-zero exit of the CLI becomes a typed error that carries the code, the command that fixes it, a docs link and the whole answer.

| exit | error | means |
|---|---|---|
| 1 | refused | a branch was refused or a local test failed |
| 2 | usage | a usage or local error |
| 3 | still running | land is still working, so make the same call again |
| 4 | could not check | land could not check, so make the same call again |

Each folder's README shows the calls in that language. The full guide is at [docs.useland.dev/agents#sdks](https://docs.useland.dev/agents#sdks).

## Tests

Each SDK's tests run against the small fake `land` in [`fake`](fake), so they need no account. The workflow in `.github/workflows/test.yml` runs all nine on every push.

## Licence

MIT. See [LICENSE](LICENSE).
