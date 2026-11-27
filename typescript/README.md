# land SDK for TypeScript

Typed wrappers over the `land` command line. Each call runs `land ... --json` and gives you back the JSON it prints,
typed. The SDK has no network code of its own, so it signs in the same way the CLI does (your `land login`, or
`LAND_TOKEN` in the environment). Install the `land` CLI first.

```ts
import { Land, RefusedError } from "useland"

const land = new Land({ cwd: "/path/to/repo" })

try {
  const answer = await land.merge(["sub/api", "sub/ledger"], "main")
  console.log(answer.push) // the push command that moves main
} catch (e) {
  if (e instanceof RefusedError) console.log(e.answer.refused, e.fix)
  else throw e
}

await land.local(["spec"])          // run .land/tests on this machine, unsigned and never a verdict
await land.add({ dryRun: true })    // what an add would send, and recipes to start from
await land.logs("users")            // the output of a refused test
await land.secrets.set("STRIPE_KEY", process.env.STRIPE_KEY!)
```

Every non-zero exit becomes an error with `code`, `fix` (the command that fixes it), `docs` and the whole `answer`.

| exit | error | means |
|---|---|---|
| 1 | `RefusedError` | a branch was refused or a local test failed (`answer` still holds what landed) |
| 2 | `UsageError` | a usage or local error |
| 3 | `StillRunningError` | land is still working, so make the same call again |
| 4 | `CouldNotCheckError` | land could not check, so make the same call again |

Tests run against a fake `land` in `fake` with `npm test` (Node 22.6 or newer). It is not published to npm yet.
Install the tarball from the release.

```sh
npm install https://github.com/Ataraxy-Labs/land-sdk/releases/download/v0.1.0/useland-0.1.0.tgz
```
