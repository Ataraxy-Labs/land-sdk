// Runs the SDK against the fake `land` in fake, named as the executable. No test framework, so there is nothing
// to restore: `dotnet run --project Useland.Tests` exits 0 when every check passes. Run it from dotnet.
using Useland;

var fake = Path.GetFullPath(Path.Combine(Directory.GetCurrentDirectory(), "..", "fake", "land"));
if (!File.Exists(fake)) throw new FileNotFoundException("run from dotnet", fake);
var failed = 0;

async Task Test(string name, Func<Land, string, Task> body)
{
    var log = Path.Combine(Path.GetTempPath(), $"useland-cs-{Guid.NewGuid():N}");
    try { await body(new Land(fake, env: new Dictionary<string, string> { ["FAKE_LAND_LOG"] = log }), log); Console.WriteLine($"ok   {name}"); }
    catch (Exception e) { failed++; Console.WriteLine($"FAIL {name}: {e.Message}"); }
}
void Eq<T>(T want, T got) { if (!Equals(want, got)) throw new Exception($"wanted {want}, got {got}"); }
string Argv(string log) => File.ReadAllText(log).Trim();
async Task<T> Throws<T>(Func<Task> f) where T : Exception
{
    try { await f(); } catch (T e) { return e; }
    throw new Exception($"no {typeof(T).Name}");
}

await Test("merge returns the answer and passes --json", async (land, log) =>
{
    var a = await land.MergeAsync(["sub/ok"], "main");
    Eq("sub/ok", (string?)a["landed"]![0]);
    Eq("merge sub/ok --onto main --json", Argv(log));
});
await Test("exit 1 is refused and keeps the answer", async (land, _) =>
{
    var e = await Throws<RefusedException>(() => land.MergeAsync(["sub/bad"], "main"));
    Eq(1, e.Code);
    Eq(1, e.Answer["refused"]!.AsArray().Count);
});
await Test("exit 2 carries fix and docs", async (land, _) =>
{
    LandException e = await Throws<UsageException>(() => land.LogsAsync("users"));
    Eq("land merge <branch>... --onto main", e.Fix);
    Eq("https://docs.useland.dev/errors#exit-2", e.Docs);
});
await Test("exit 3 is still running", async (land, _) =>
{
    var e = await Throws<StillRunningException>(() => land.AddAsync());
    Eq("land add", e.Fix);
});
await Test("a local run is never signed", async (land, _) =>
{
    var a = await land.LocalAsync();
    Eq(false, (bool)a["signed"]!);
    Eq("spec", (string?)a["tests"]!["ran_units"]![0]!["name"]);
});
await Test("a secret's value goes on stdin", async (land, log) =>
{
    await land.Secrets.SetAsync("STRIPE_KEY", "sk_test_1");
    Eq("secret set STRIPE_KEY --json", Argv(log));
    Eq("sk_test_1", File.ReadAllText(log + ".stdin"));
});
await Test("add and logs switches", async (land, log) =>
{
    await Throws<StillRunningException>(() => land.AddAsync(new AddOptions { DryRun = true, Test = ["pytest -q"], AllowTestEdits = false, Cpus = 4 }));
    Eq("add --dry-run --test pytest -q --no-allow-test-edits --cpus 4 --json", Argv(log));
    await Throws<UsageException>(() => land.LogsAsync("users", run: "ls"));
    Eq("logs users --run ls --json", Argv(log));
});

Console.WriteLine(failed == 0 ? "7 passed" : $"{failed} failed");
return failed == 0 ? 0 : 1;
