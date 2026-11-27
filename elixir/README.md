# land SDK for Elixir

Typed wrappers over the `land` command line. Each call runs `land ... --json` and gives you back the JSON it prints as a map.
The SDK has no network code of its own, so it signs in the same way the CLI does (your `land login`, or `LAND_TOKEN`
in the environment). Install the `land` CLI first. No dependencies, Elixir 1.18 or newer (it uses the built-in `JSON` module).

It is not published to Hex yet. Add it from the public repository.

```elixir
# mix.exs
{:useland, git: "https://github.com/Ataraxy-Labs/land-sdk", tag: "v0.1.0", sparse: "elixir"}
```

```elixir
land = Useland.new(cwd: "/path/to/repo")

case Useland.merge(land, ["sub/api", "sub/ledger"], "main") do
  {:ok, answer} -> IO.puts(answer["push"]) # the push command that moves main
  {:error, %Useland.Error{kind: :refused} = e} -> IO.inspect({e.answer["refused"], e.fix})
end

Useland.local(land, ["spec"])                 # run .land/tests on this machine, unsigned and never a verdict
Useland.add(land, dry_run: true)              # what an add would send, and recipes to start from
Useland.logs(land, "users")                   # the output of a refused test
Useland.logs(land, "users", run: "ls -la")    # a command in that test's view on land's runner
Useland.Secrets.set(land, "STRIPE_KEY", System.fetch_env!("STRIPE_KEY"))
```

Every call returns `{:ok, answer}` or `{:error, %Useland.Error{}}`. The error has `kind`, `code`, `fix` (the command that
fixes it), `docs` and the whole `answer`.

| exit | `kind` | means |
|---|---|---|
| 1 | `:refused` | a branch was refused or a local test failed (the answer still holds what landed) |
| 2 | `:usage` | a usage or local error |
| 3 | `:still_running` | land is still working, so make the same call again |
| 4 | `:could_not_check` | land could not check, so make the same call again |

`Useland.run/3` gives the whole JSON object of any call. Tests run against a fake `land` in `fake` with `mix test`.
The SDK starts the CLI through `sh`, so it needs a POSIX shell.
