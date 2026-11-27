# Recipes

A `.land/setup.sh` and a `.land/tests` to start from, one file per recipe. `land add --dry-run --json` offers the ones
that fit a repository as `suggest` when the repository has no `.land/tests` and no CI workflow. A recipe is only a
suggestion: the owner edits it, commits it, and the committed files are what land checks. The list with every recipe
is at https://docs.useland.dev/recipes.

## Where it lives, and its versions

The catalog is public, MIT licensed, at https://github.com/Ataraxy-Labs/land-sdk/tree/main/recipes. Each release of
the land client carries the catalog as it stood then, tagged `recipes/v<release>` there, and every suggestion says
which one it came from (`version`, and `source`, the recipe's file at that tag). To get an older catalog, install that
release (`curl -fsSL https://useland.dev/install | LAND_VERSION=0.4.1 sh`). A recipe you committed is yours: a later
catalog never changes it.

## Adding a recipe

Open a pull request at https://github.com/Ataraxy-Labs/land-sdk that adds one file, `recipes/<name>.md`. Before
sending it, run the recipe on a real repository with `land add` until it is accepted, and say which repository in the
pull request. Once merged, the next client release offers it.

## The file

```
# <name>                      the file's name without .md: letters, digits and -

A sentence or two on what it installs and how it splits the tests.

when: <file> <file>           every one of these is at the repository's root; a|b is either
group: <group>                at most one recipe of a group is offered: the one whose when names the most files

## setup.sh
(a fenced block: the whole .land/setup.sh)

## tests [<variant>] [if <file> has <text>]
(a fenced block: the whole .land/tests)
```

A recipe may have several `## tests` sections. The first one whose condition holds and whose every pattern matches a
file of the repository is offered, named `<name>-<variant>` (or `<name>` with no variant); with none, the recipe is
not offered. So a last section with no pattern (`-` in the files column) is the fallback. `group` defaults to the
name.

Rules a recipe follows, because the check does: setup.sh runs as root with the network on and installs everything
system-wide (`/usr/local`, `/opt`); the tests run offline, as an unprivileged user, with a fresh HOME. Pin the versions
you install. The format of `.land/tests` is at https://docs.useland.dev/tests-file.
