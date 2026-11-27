// Typed wrappers over the land CLI. Every call spawns `land … --json`, reads the one JSON object it prints on stdout,
// and returns it typed; a non-zero exit becomes a typed error carrying land's `fix` and `docs`. No network code: the
// CLI does all of that, with your login or LAND_TOKEN.
import { spawn } from "node:child_process"

export interface LandOptions {
  /** the land executable (default: `land` on PATH) */
  bin?: string
  /** the repository to run in (default: this process's working directory) */
  cwd?: string
  /** extra environment for the CLI, e.g. { LAND_TOKEN: "lk_…" } */
  env?: Record<string, string>
}

/** One test row, as a merge answer's `tests.ran_units` lists it (and `merge --local` too). */
export interface RanUnit {
  name: string
  state: "ran" | "failed" | "rerun" | "not_run" | string
  seconds?: number
  why?: string
  exit?: number
  output?: string
  [k: string]: unknown
}

export interface Tests {
  total?: number
  ran?: number
  carried?: number
  failed?: number
  ran_units?: RanUnit[]
  [k: string]: unknown
}

/** One file a landing changes on the target. A binary file has no line counts. */
export interface Change {
  path: string
  status: 'added' | 'deleted' | 'modified' | 'renamed'
  /** the old path of a rename */
  from?: string
  added?: number
  removed?: number
  binary?: boolean
}

/** What a landing changes on the target: git's own counts of the target against the result. */
export interface Changes {
  files: number
  added: number
  removed: number
  /** at most 500 files; `more` says how many are left out */
  entries: Change[]
  more?: number
}

export interface MergeAnswer {
  /** what the landing changes on the target; absent when nothing landed */
  changes?: Changes
  landed: string[]
  refused: Array<{ branch?: string; [k: string]: unknown }>
  /** the commit to push */
  result?: string
  /** the exact push command */
  push?: string
  tests?: Tests
  [k: string]: unknown
}

/** `land merge --local`: never a verdict. `signed`, `recorded` and `landed` are always false. */
export interface LocalAnswer {
  local: true
  signed: false
  recorded: false
  landed: false
  verdict: null
  said: string
  tests: Tests
  [k: string]: unknown
}

export interface AddAnswer {
  added?: boolean
  dry_run?: boolean
  repo?: string
  /** recipes to start from, when the tree has no .land/tests and no workflow */
  suggest?: Array<{ recipe: string; because: string[]; setup: string; tests: string; units: number | string; docs: string }>
  [k: string]: unknown
}

export interface LogsAnswer {
  failing?: Array<{ branch?: string; test: string; output: string }>
  probe?: { tree?: string; unit?: string; exit?: number; output?: string; [k: string]: unknown }
  [k: string]: unknown
}

/** A non-zero exit of the CLI. `answer` is the whole JSON object it printed. */
export class LandError extends Error {
  readonly code: number
  readonly fix: string | undefined
  readonly docs: string | undefined
  readonly answer: Record<string, unknown>
  constructor(message: string, code: number, fix: string | undefined, docs: string | undefined, answer: Record<string, unknown>) {
    super(message)
    this.name = new.target.name
    this.code = code
    this.fix = fix
    this.docs = docs
    this.answer = answer
  }
}
/** exit 1: some branch was refused, a local test failed, or an add was refused. `answer` still holds what landed. */
export class RefusedError extends LandError {}
/** exit 2: a usage or local error; `fix` is the command that fixes it */
export class UsageError extends LandError {}
/** exit 3: land is still working; run the same call again to wait (`answer.again`, `answer.job`) */
export class StillRunningError extends LandError {}
/** exit 4: land could not check (an engine or infrastructure error): run the same call again */
export class CouldNotCheckError extends LandError {}

const BY_CODE: Record<number, typeof LandError> = { 1: RefusedError, 2: UsageError, 3: StillRunningError, 4: CouldNotCheckError }

/** Map an exit code and the CLI's JSON to the typed error (exported for callers that run the CLI themselves). */
export function errorFor(code: number, answer: Record<string, unknown>): LandError {
  const str = (k: string) => (typeof answer[k] === "string" ? (answer[k] as string) : undefined)
  const Cls = BY_CODE[code] ?? LandError
  return new Cls(str("error") ?? `land exited ${code}`, code, str("fix") ?? str("again"), str("docs"), answer)
}

export class Land {
  private readonly opts: LandOptions
  constructor(opts: LandOptions = {}) {
    this.opts = opts
  }

  /** Run `land <args> --json` and return its JSON; a non-zero exit throws the typed error. */
  async run<T = Record<string, unknown>>(args: string[], stdin?: string): Promise<T> {
    const child = spawn(this.opts.bin ?? "land", [...args, "--json"], {
      cwd: this.opts.cwd,
      env: { ...process.env, ...this.opts.env },
      stdio: ["pipe", "pipe", "pipe"],
    })
    let out = ""
    let err = ""
    child.stdout.on("data", (d) => (out += d))
    child.stderr.on("data", (d) => (err += d))
    child.stdin.end(stdin ?? "")
    const code: number = await new Promise((resolve, reject) => {
      child.on("error", reject)
      child.on("close", (c) => resolve(c ?? -1))
    })
    let answer: Record<string, unknown>
    try {
      answer = JSON.parse(out.trim().split("\n").pop() || "{}")
    } catch {
      answer = { error: `land printed no JSON (exit ${code}): ${err.trim().slice(-500)}` }
    }
    if (code !== 0) throw errorFor(code, answer)
    return answer as T
  }

  /** Merge branches onto a target on land and prove the result. Exit 1 (some refused) throws RefusedError. */
  merge(branches: string[], onto: string, opts: { resultTree?: boolean } = {}): Promise<MergeAnswer> {
    return this.run<MergeAnswer>(["merge", ...branches, "--onto", onto, ...(opts.resultTree ? ["--result", "tree"] : [])])
  }

  /** Run .land/tests (all, or the named lines or tests) on this machine. Unsigned, never a verdict. */
  local(names: string[] = []): Promise<LocalAnswer> {
    return this.run<LocalAnswer>(["merge", "--local", ...names])
  }

  /** Connect this repository (or preview with dryRun). */
  add(opts: { dryRun?: boolean; test?: string[]; allowTestEdits?: boolean; allowLowGuard?: boolean; cpus?: number; timeLimit?: number; plane?: string } = {}): Promise<AddAnswer> {
    const a = ["add"]
    if (opts.dryRun) a.push("--dry-run")
    for (const t of opts.test ?? []) a.push("--test", t)
    if (opts.allowTestEdits !== undefined) a.push(opts.allowTestEdits ? "--allow-test-edits" : "--no-allow-test-edits")
    if (opts.allowLowGuard !== undefined) a.push(opts.allowLowGuard ? "--allow-low-guard" : "--no-allow-low-guard")
    if (opts.cpus !== undefined) a.push("--cpus", String(opts.cpus))
    if (opts.timeLimit !== undefined) a.push("--time-limit", String(opts.timeLimit))
    if (opts.plane) a.push("--plane", opts.plane)
    return this.run<AddAnswer>(a)
  }

  /** The output of the tests the last merge refused; with `run`, a command in that test's view on land's runner. */
  logs(name?: string, run?: string): Promise<LogsAnswer> {
    return this.run<LogsAnswer>(["logs", ...(name ? [name] : []), ...(run !== undefined ? ["--run", run] : [])])
  }

  /** This repository's secrets. A value goes on stdin, never in an argument. */
  readonly secrets = {
    set: (name: string, value: string) => this.run<{ set: string; repo: string }>(["secret", "set", name], value),
    /** every NAME=value line of a .env text */
    setMany: (dotenv: string) => this.run<{ set: string[]; repo: string }>(["secret", "set"], dotenv),
    list: () => this.run<{ secrets: string[]; repo: string }>(["secret", "list"]),
    rm: (name: string) => this.run<{ removed: string; repo: string }>(["secret", "rm", name]),
  }
}
