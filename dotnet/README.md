# land SDK for .NET

Typed wrappers over the `land` command line. Each call runs `land ... --json` and gives you back the JSON it prints as a `JsonObject`.
The SDK has no network code of its own, so it signs in the same way the CLI does (your `land login`, or `LAND_TOKEN`
in the environment). Install the `land` CLI first. No dependencies beyond `System.Text.Json`, .NET 8 or newer.

It is not published to NuGet yet. Reference the project from a clone of the public repository.

```sh
git clone --branch v0.1.0 https://github.com/Ataraxy-Labs/land-sdk
dotnet add reference land-sdk/dotnet/Useland/Useland.csproj
```

```csharp
using Useland;

var land = new Land(cwd: "/path/to/repo");

try
{
    var answer = await land.MergeAsync(["sub/api", "sub/ledger"], onto: "main");
    Console.WriteLine(answer["push"]); // the push command that moves main
}
catch (RefusedException e)
{
    Console.WriteLine($"{e.Answer["refused"]} {e.Fix}");
}

await land.LocalAsync(["spec"]);                         // run .land/tests on this machine, unsigned and never a verdict
await land.AddAsync(new AddOptions { DryRun = true });   // what an add would send, and recipes to start from
await land.LogsAsync("users");                           // the output of a refused test
await land.LogsAsync("users", run: "ls -la");            // a command in that test's view on land's runner
await land.Secrets.SetAsync("STRIPE_KEY", Environment.GetEnvironmentVariable("STRIPE_KEY")!);
```

Every non-zero exit throws an exception with `Code`, `Fix` (the command that fixes it), `Docs` and the whole `Answer`.
Each one extends `LandException`, so catching that catches them all.

| exit | exception | means |
|---|---|---|
| 1 | `RefusedException` | a branch was refused or a local test failed (the answer still holds what landed) |
| 2 | `UsageException` | a usage or local error |
| 3 | `StillRunningException` | land is still working, so make the same call again |
| 4 | `CouldNotCheckException` | land could not check, so make the same call again |

`RunAsync` gives the whole JSON object of any call. Tests run against a fake `land` in `fake` with
`dotnet run --project Useland.Tests` from `dotnet`.
