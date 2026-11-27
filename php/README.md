# land SDK for PHP

Typed wrappers over the `land` command line. Each call runs `land ... --json` and gives you back the JSON it prints as an array.
The SDK has no network code of its own, so it signs in the same way the CLI does (your `land login`, or `LAND_TOKEN`
in the environment). Install the `land` CLI first. No dependencies beyond `ext-json`, PHP 8.1 or newer.

It is not published to Packagist yet. Add the public repository to Composer.

```sh
composer config repositories.land-sdk vcs https://github.com/Ataraxy-Labs/land-sdk
composer require ataraxy-labs/useland:^0.1
```

```php
use Useland\Land;
use Useland\RefusedException;

$land = new Land(cwd: "/path/to/repo");

try {
    $answer = $land->merge(["sub/api", "sub/ledger"], onto: "main");
    echo $answer["push"]; // the push command that moves main
} catch (RefusedException $e) {
    print_r($e->answer["refused"]);
    echo $e->fix;
}

$land->local("spec");                          // run .land/tests on this machine, unsigned and never a verdict
$land->add(dryRun: true);                      // what an add would send, and recipes to start from
$land->logs("users");                          // the output of a refused test
$land->logs("users", run: "ls -la");           // a command in that test's view on land's runner
$land->secrets->set("STRIPE_KEY", getenv("STRIPE_KEY"));
```

Every non-zero exit throws an exception with `exit`, `fix` (the command that fixes it), `docs` and the whole `answer`.
Each one extends `Useland\LandException`, so catching that catches them all.

| exit | exception | means |
|---|---|---|
| 1 | `RefusedException` | a branch was refused or a local test failed (the answer still holds what landed) |
| 2 | `UsageException` | a usage or local error |
| 3 | `StillRunningException` | land is still working, so make the same call again |
| 4 | `CouldNotCheckException` | land could not check, so make the same call again |

`run` gives the whole JSON object of any call. Tests run against a fake `land` in `fake` with
`php tests/land_test.php`.
