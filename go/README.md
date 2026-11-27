# land SDK for Go

Typed wrappers over the `land` command line. Each call runs `land ... --json` and gives you back the JSON it prints,
typed. The SDK has no network code of its own, so it signs in the same way the CLI does (your `land login`, or
`LAND_TOKEN` in the environment). Install the `land` CLI first. No dependencies, Go 1.21 or newer.

```sh
go get github.com/Ataraxy-Labs/land-sdk/go
```

```go
import useland "github.com/Ataraxy-Labs/land-sdk/go"

land := &useland.Land{Cwd: "/path/to/repo"}

answer, err := land.Merge(ctx, []string{"sub/api", "sub/ledger"}, "main", false)
var refused *useland.RefusedError
if errors.As(err, &refused) {
	fmt.Println(refused.Answer["refused"], refused.Fix)
} else if err == nil {
	fmt.Println(answer.Push) // the push command that moves main
}

land.Local(ctx, "spec")                           // run .land/tests on this machine, unsigned and never a verdict
land.Add(ctx, useland.AddOptions{DryRun: true})   // what an add would send, and recipes to start from
land.Logs(ctx, "users", nil)                      // the output of a refused test
land.Secrets().Set(ctx, "STRIPE_KEY", os.Getenv("STRIPE_KEY"))
```

Every non-zero exit returns an error with `Code`, `Fix` (the command that fixes it), `Docs` and the whole `Answer`.
Each typed error wraps a `*LandError`, so `errors.As(err, &le)` with `var le *useland.LandError` catches them all.

| exit | error | means |
|---|---|---|
| 1 | `*RefusedError` | a branch was refused or a local test failed (`Answer` still holds what landed) |
| 2 | `*UsageError` | a usage or local error |
| 3 | `*StillRunningError` | land is still working, so make the same call again |
| 4 | `*CouldNotCheckError` | land could not check, so make the same call again |

`Run` gives the whole JSON object of any call, fields the typed answers do not name included. Tests run against a fake
`land` in `fake` with `go test ./...`.
