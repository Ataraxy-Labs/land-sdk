# land SDK for Java

Typed wrappers over the `land` command line. Each call runs `land ... --json` and gives you back the JSON it prints as a `Map<String, Object>`.
The SDK has no network code of its own, so it signs in the same way the CLI does (your `land login`, or `LAND_TOKEN`
in the environment). Install the `land` CLI first. No dependencies, Java 17 or newer.

It is not published to Maven Central yet. Build it from the public repository.

```sh
git clone --branch v0.1.0 https://github.com/Ataraxy-Labs/land-sdk
cd land-sdk/java && mvn install    # then depend on dev.useland:useland:0.1.0, or copy src/main/java/dev/useland
```

```java
import dev.useland.Land;
import dev.useland.LandException;

Land land = new Land().cwd("/path/to/repo");

try {
    Map<String, Object> answer = land.merge(List.of("sub/api", "sub/ledger"), "main");
    System.out.println(answer.get("push")); // the push command that moves main
} catch (LandException.Refused e) {
    System.out.println(e.answer.get("refused") + " " + e.fix);
}

land.local("spec");                            // run .land/tests on this machine, unsigned and never a verdict
Land.AddOptions preview = new Land.AddOptions();
preview.dryRun = true;
land.add(preview);                             // what an add would send, and recipes to start from
land.logs("users");                            // the output of a refused test
land.logs("users", "ls -la");                  // a command in that test's view on land's runner
land.secrets().set("STRIPE_KEY", System.getenv("STRIPE_KEY"));
```

Every non-zero exit throws an unchecked exception with `code`, `fix` (the command that fixes it), `docs` and the whole
`answer`. Each one extends `LandException`, so catching that catches them all.

| exit | exception | means |
|---|---|---|
| 1 | `LandException.Refused` | a branch was refused or a local test failed (the answer still holds what landed) |
| 2 | `LandException.Usage` | a usage or local error |
| 3 | `LandException.StillRunning` | land is still working, so make the same call again |
| 4 | `LandException.CouldNotCheck` | land could not check, so make the same call again |

`run` gives the whole JSON object of any call. A JSON object is a `Map`, an array a `List`, and a number a `Long` or a
`Double`. Tests run against a fake `land` in `fake` with `sh test.sh`, which needs only `javac`.
