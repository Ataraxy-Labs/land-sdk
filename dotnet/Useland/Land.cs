// Typed wrappers over the land CLI.
//
// Every call runs `land ... --json`, reads the one JSON object it prints on stdout and returns it; a non-zero exit
// throws a typed exception carrying land's fix and docs. No network code: the CLI does all of that, with your login
// or LAND_TOKEN.
using System.Diagnostics;
using System.Text.Json.Nodes;

namespace Useland;

/// <summary>A non-zero exit of the CLI. Answer is the whole JSON object it printed.</summary>
public class LandException(string message, int code, string? fix, string? docs, JsonObject answer) : Exception(message)
{
    public int Code { get; } = code;
    /// <summary>The command that fixes it, or the call to make again.</summary>
    public string? Fix { get; } = fix;
    public string? Docs { get; } = docs;
    public JsonObject Answer { get; } = answer;

    /// <summary>Map an exit code and the CLI's JSON to the typed exception (for callers that run the CLI themselves).</summary>
    public static LandException For(int code, JsonObject answer)
    {
        string? S(string k) => answer[k] is JsonValue v && v.TryGetValue<string>(out var s) ? s : null;
        var message = S("error") ?? $"land exited {code}";
        var fix = S("fix") ?? S("again");
        return code switch
        {
            1 => new RefusedException(message, code, fix, S("docs"), answer),
            2 => new UsageException(message, code, fix, S("docs"), answer),
            3 => new StillRunningException(message, code, fix, S("docs"), answer),
            4 => new CouldNotCheckException(message, code, fix, S("docs"), answer),
            _ => new LandException(message, code, fix, S("docs"), answer),
        };
    }
}

/// <summary>exit 1: some branch was refused, a local test failed, or an add was refused. Answer still holds what landed.</summary>
public sealed class RefusedException(string m, int c, string? f, string? d, JsonObject a) : LandException(m, c, f, d, a);
/// <summary>exit 2: a usage or local error; Fix is the command that fixes it.</summary>
public sealed class UsageException(string m, int c, string? f, string? d, JsonObject a) : LandException(m, c, f, d, a);
/// <summary>exit 3: land is still working; make the same call again to wait.</summary>
public sealed class StillRunningException(string m, int c, string? f, string? d, JsonObject a) : LandException(m, c, f, d, a);
/// <summary>exit 4: land could not check (an engine or infrastructure error); make the same call again.</summary>
public sealed class CouldNotCheckException(string m, int c, string? f, string? d, JsonObject a) : LandException(m, c, f, d, a);

/// <summary>`land add`'s switches. A null leaves that switch out.</summary>
public sealed record AddOptions
{
    public bool DryRun { get; init; }
    public IReadOnlyList<string> Test { get; init; } = [];
    public bool? AllowTestEdits { get; init; }
    public bool? AllowLowGuard { get; init; }
    public int? Cpus { get; init; }
    public int? TimeLimit { get; init; }
    public string? Plane { get; init; }
}

/// <summary>Runs the land CLI. With no arguments it runs `land` on PATH in this process's working directory.</summary>
public sealed class Land(string bin = "land", string? cwd = null, IReadOnlyDictionary<string, string>? env = null)
{
    /// <summary>This repository's secrets. A value goes on stdin, never in an argument.</summary>
    public SecretsApi Secrets => new(this);

    /// <summary>Run `land args --json` and return its JSON; a non-zero exit throws the typed exception.</summary>
    public async Task<JsonObject> RunAsync(IEnumerable<string> args, string stdin = "", CancellationToken ct = default)
    {
        var start = new ProcessStartInfo(bin)
        {
            RedirectStandardInput = true, RedirectStandardOutput = true, RedirectStandardError = true, UseShellExecute = false,
        };
        if (cwd is not null) start.WorkingDirectory = cwd;
        foreach (var a in args) start.ArgumentList.Add(a);
        start.ArgumentList.Add("--json");
        foreach (var (k, v) in env ?? new Dictionary<string, string>()) start.Environment[k] = v;
        using var p = Process.Start(start) ?? throw new InvalidOperationException("land did not start");
        var stdout = p.StandardOutput.ReadToEndAsync(ct);
        var stderr = p.StandardError.ReadToEndAsync(ct);
        try
        {
            await p.StandardInput.WriteAsync(stdin.AsMemory(), ct);
            p.StandardInput.Close();
        }
        catch (IOException) { } // a CLI that exits without reading its stdin closes the pipe
        await p.WaitForExitAsync(ct);
        var last = (await stdout).Trim().Split('\n')[^1];
        JsonObject answer;
        try { answer = last == "" ? [] : JsonNode.Parse(last) as JsonObject ?? throw new FormatException(); }
        catch (Exception e) when (e is System.Text.Json.JsonException or FormatException)
        {
            var tail = (await stderr).Trim();
            answer = new JsonObject { ["error"] = $"land printed no JSON (exit {p.ExitCode}): {(tail.Length > 500 ? tail[^500..] : tail)}" };
        }
        return p.ExitCode == 0 ? answer : throw LandException.For(p.ExitCode, answer);
    }

    /// <summary>Merge branches onto a target on land and prove the result. Exit 1 (some refused) throws RefusedException.</summary>
    public Task<JsonObject> MergeAsync(IEnumerable<string> branches, string onto, bool resultTree = false, CancellationToken ct = default) =>
        RunAsync(["merge", .. branches, "--onto", onto, .. resultTree ? new[] { "--result", "tree" } : []], "", ct);

    /// <summary>Run .land/tests (all, or the named lines or tests) on this machine. Unsigned, never a verdict.</summary>
    public Task<JsonObject> LocalAsync(IEnumerable<string>? names = null, CancellationToken ct = default) =>
        RunAsync(["merge", "--local", .. names ?? []], "", ct);

    /// <summary>Connect this repository (or preview with DryRun).</summary>
    public Task<JsonObject> AddAsync(AddOptions? options = null, CancellationToken ct = default)
    {
        var o = options ?? new AddOptions();
        var a = new List<string> { "add" };
        if (o.DryRun) a.Add("--dry-run");
        foreach (var t in o.Test) a.AddRange(["--test", t]);
        if (o.AllowTestEdits is { } edits) a.Add(edits ? "--allow-test-edits" : "--no-allow-test-edits");
        if (o.AllowLowGuard is { } guard) a.Add(guard ? "--allow-low-guard" : "--no-allow-low-guard");
        if (o.Cpus is { } cpus) a.AddRange(["--cpus", cpus.ToString()]);
        if (o.TimeLimit is { } limit) a.AddRange(["--time-limit", limit.ToString()]);
        if (o.Plane is { } plane) a.AddRange(["--plane", plane]);
        return RunAsync(a, "", ct);
    }

    /// <summary>The output of the tests the last merge refused (all, or the named one); with run, a command in that
    /// test's view on land's runner.</summary>
    public Task<JsonObject> LogsAsync(string? name = null, string? run = null, CancellationToken ct = default) =>
        RunAsync(["logs", .. name is null ? [] : new[] { name }, .. run is null ? [] : new[] { "--run", run }], "", ct);

    public sealed class SecretsApi(Land land)
    {
        /// <summary>Store one secret.</summary>
        public Task<JsonObject> SetAsync(string name, string value, CancellationToken ct = default) => land.RunAsync(["secret", "set", name], value, ct);
        /// <summary>Store every NAME=value line of a .env text.</summary>
        public Task<JsonObject> SetManyAsync(string dotenv, CancellationToken ct = default) => land.RunAsync(["secret", "set"], dotenv, ct);
        public Task<JsonObject> ListAsync(CancellationToken ct = default) => land.RunAsync(["secret", "list"], "", ct);
        public Task<JsonObject> RmAsync(string name, CancellationToken ct = default) => land.RunAsync(["secret", "rm", name], "", ct);
    }
}
