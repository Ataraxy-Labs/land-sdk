// Runs the SDK against the fake `land` in fake, put first on PATH.
import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync, mkdtempSync } from "node:fs"
import { tmpdir } from "node:os"
import { join, resolve } from "node:path"
import { Land, RefusedError, UsageError, StillRunningError } from "../src/index.ts"

const log = join(mkdtempSync(join(tmpdir(), "land-sdk-")), "argv")
process.env.PATH = `${resolve(import.meta.dirname, "../../fake")}:${process.env.PATH}`
process.env.FAKE_LAND_LOG = log
const land = new Land()

test("merge returns the typed answer and passes --json", async () => {
  const a = await land.merge(["sub/ok"], "main")
  assert.deepEqual(a.landed, ["sub/ok"])
  assert.equal(readFileSync(log, "utf8").trim(), "merge sub/ok --onto main --json")
})

test("exit 1 is RefusedError and keeps the answer", async () => {
  await assert.rejects(land.merge(["sub/bad"], "main"), (e: unknown) => e instanceof RefusedError && e.code === 1 && (e.answer.refused as unknown[]).length === 1)
})

test("exit 2 carries fix and docs", async () => {
  await assert.rejects(land.logs("users"), (e: unknown) =>
    e instanceof UsageError && e.fix === "land merge <branch>... --onto main" && e.docs === "https://docs.useland.dev/errors#exit-2")
})

test("exit 3 is StillRunningError with the command to run again", async () => {
  await assert.rejects(land.add(), (e: unknown) => e instanceof StillRunningError && e.fix === "land add")
})

test("a local run is never signed", async () => {
  const a = await land.local()
  assert.equal(a.signed, false)
  assert.equal(a.tests.ran_units?.[0].name, "spec")
})

test("a secret's value goes on stdin, not in the arguments", async () => {
  await land.secrets.set("STRIPE_KEY", "sk_test_1")
  assert.equal(readFileSync(log, "utf8").trim(), "secret set STRIPE_KEY --json")
  assert.equal(readFileSync(`${log}.stdin`, "utf8"), "sk_test_1")
})
