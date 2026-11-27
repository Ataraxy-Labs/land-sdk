# land SDK for Ruby

Typed wrappers over the `land` command line. Each call runs `land ... --json` and gives you back the JSON it prints as a Hash.
The SDK has no network code of its own, so it signs in the same way the CLI does (your `land login`, or `LAND_TOKEN`
in the environment). Install the `land` CLI first. No dependencies beyond the standard library, Ruby 3.0 or newer.

It is not published to RubyGems yet. Add it from the public repository.

```ruby
# Gemfile
gem "useland", git: "https://github.com/Ataraxy-Labs/land-sdk", tag: "v0.1.0", glob: "ruby/*.gemspec"
```

```ruby
require "useland"

land = Useland::Land.new(cwd: "/path/to/repo")

begin
  answer = land.merge(["sub/api", "sub/ledger"], onto: "main")
  puts answer["push"] # the push command that moves main
rescue Useland::RefusedError => e
  puts e.answer["refused"], e.fix
end

land.local("spec")                            # run .land/tests on this machine, unsigned and never a verdict
land.add(dry_run: true)                       # what an add would send, and recipes to start from
land.logs("users")                            # the output of a refused test
land.logs("users", run: "ls -la")             # a command in that test's view on land's runner
land.secrets.set("STRIPE_KEY", ENV["STRIPE_KEY"])
```

Every non-zero exit raises an error with `code`, `fix` (the command that fixes it), `docs` and the whole `answer`.
Each one is a `Useland::LandError`, so rescuing that catches them all.

| exit | error | means |
|---|---|---|
| 1 | `Useland::RefusedError` | a branch was refused or a local test failed (the answer still holds what landed) |
| 2 | `Useland::UsageError` | a usage or local error |
| 3 | `Useland::StillRunningError` | land is still working, so make the same call again |
| 4 | `Useland::CouldNotCheckError` | land could not check, so make the same call again |

`run` gives the whole JSON object of any call. Tests run against a fake `land` in `fake` with
`ruby test/land_test.rb`.
