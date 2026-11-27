//! Typed wrappers over the land CLI.
//!
//! Every call runs `land ... --json`, reads the one JSON object it prints on stdout and returns it; a non-zero exit
//! becomes a typed error carrying land's fix and docs. No network code: the CLI does all of that, with your login or
//! LAND_TOKEN.
use serde_json::{json, Map, Value};
use std::collections::BTreeMap;
use std::fmt;
use std::io::Write;
use std::path::PathBuf;
use std::process::{Command, Stdio};

/// Which exit the CLI made.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Kind {
    /// exit 1: some branch was refused, a local test failed, or an add was refused. `answer` still holds what landed.
    Refused,
    /// exit 2: a usage or local error; `fix` is the command that fixes it.
    Usage,
    /// exit 3: land is still working; make the same call again to wait.
    StillRunning,
    /// exit 4: land could not check (an engine or infrastructure error); make the same call again.
    CouldNotCheck,
    /// any other exit, or the CLI did not start (then `code` is -1).
    Other,
}

/// A non-zero exit of the CLI. `answer` is the whole JSON object it printed.
#[derive(Debug, Clone)]
pub struct LandError {
    pub kind: Kind,
    pub message: String,
    pub code: i32,
    /// the command that fixes it, or the call to make again
    pub fix: Option<String>,
    pub docs: Option<String>,
    pub answer: Value,
}

impl fmt::Display for LandError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}
impl std::error::Error for LandError {}

/// Map an exit code and the CLI's JSON to the typed error (for callers that run the CLI themselves).
pub fn error_for(code: i32, answer: Value) -> LandError {
    let s = |k: &str| answer.get(k).and_then(Value::as_str).map(str::to_owned);
    LandError {
        kind: match code {
            1 => Kind::Refused,
            2 => Kind::Usage,
            3 => Kind::StillRunning,
            4 => Kind::CouldNotCheck,
            _ => Kind::Other,
        },
        message: s("error").unwrap_or_else(|| format!("land exited {code}")),
        code,
        fix: s("fix").or_else(|| s("again")),
        docs: s("docs"),
        answer,
    }
}

pub type Result<T = Value> = std::result::Result<T, LandError>;

/// `land add`'s switches. A `None` leaves that switch out.
#[derive(Debug, Clone, Default)]
pub struct AddOptions {
    pub dry_run: bool,
    pub test: Vec<String>,
    pub allow_test_edits: Option<bool>,
    pub allow_low_guard: Option<bool>,
    pub cpus: Option<u32>,
    pub time_limit: Option<u32>,
    pub plane: Option<String>,
}

/// Runs the land CLI. `Land::default()` runs `land` on PATH in this process's working directory.
#[derive(Debug, Clone, Default)]
pub struct Land {
    /// the land executable (default: land on PATH)
    pub bin: Option<PathBuf>,
    /// the repository to run in (default: this process's working directory)
    pub cwd: Option<PathBuf>,
    /// extra environment for the CLI, e.g. LAND_TOKEN
    pub env: BTreeMap<String, String>,
}

impl Land {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn cwd(mut self, dir: impl Into<PathBuf>) -> Self {
        self.cwd = Some(dir.into());
        self
    }

    pub fn env(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.env.insert(key.into(), value.into());
        self
    }

    /// Run `land <args> --json` and return its JSON; a non-zero exit returns the typed error.
    pub fn run<S: AsRef<str>>(&self, args: &[S], stdin: &str) -> Result {
        let failed = |e: std::io::Error| error_for(-1, json!({ "error": format!("land did not start: {e}") }));
        let mut cmd = Command::new(self.bin.clone().unwrap_or_else(|| "land".into()));
        cmd.args(args.iter().map(AsRef::as_ref)).arg("--json").envs(&self.env);
        if let Some(dir) = &self.cwd {
            cmd.current_dir(dir);
        }
        let mut child = cmd.stdin(Stdio::piped()).stdout(Stdio::piped()).stderr(Stdio::piped()).spawn().map_err(failed)?;
        // a CLI that exits without reading its stdin closes the pipe; that is not an error of the call
        let _ = child.stdin.take().map(|mut pipe| pipe.write_all(stdin.as_bytes()));
        let out = child.wait_with_output().map_err(failed)?;
        let code = out.status.code().unwrap_or(-1);
        let stdout = String::from_utf8_lossy(&out.stdout);
        let answer = match stdout.trim().lines().last() {
            None => Value::Object(Map::new()),
            Some(last) => serde_json::from_str(last).unwrap_or_else(|_| {
                let err = String::from_utf8_lossy(&out.stderr);
                let tail: String = err.trim().chars().rev().take(500).collect::<Vec<_>>().into_iter().rev().collect();
                json!({ "error": format!("land printed no JSON (exit {code}): {tail}") })
            }),
        };
        if code != 0 {
            return Err(error_for(code, answer));
        }
        Ok(answer)
    }

    /// Merge branches onto a target on land and prove the result. Exit 1 (some refused) is `Kind::Refused`.
    /// With `result_tree` it passes `--result tree`.
    pub fn merge<S: AsRef<str>>(&self, branches: &[S], onto: &str, result_tree: bool) -> Result {
        let mut a = vec!["merge"];
        a.extend(branches.iter().map(AsRef::as_ref));
        a.extend(["--onto", onto]);
        if result_tree {
            a.extend(["--result", "tree"]);
        }
        self.run(&a, "")
    }

    /// Run .land/tests (all, or the named lines or tests) on this machine. Unsigned, never a verdict.
    pub fn local<S: AsRef<str>>(&self, names: &[S]) -> Result {
        let mut a = vec!["merge", "--local"];
        a.extend(names.iter().map(AsRef::as_ref));
        self.run(&a, "")
    }

    /// Connect this repository (or preview with `dry_run`).
    pub fn add(&self, o: &AddOptions) -> Result {
        let mut a: Vec<String> = vec!["add".into()];
        if o.dry_run {
            a.push("--dry-run".into());
        }
        for t in &o.test {
            a.extend(["--test".into(), t.clone()]);
        }
        for (name, v) in [("allow-test-edits", o.allow_test_edits), ("allow-low-guard", o.allow_low_guard)] {
            if let Some(on) = v {
                a.push(format!("--{}{name}", if on { "" } else { "no-" }));
            }
        }
        if let Some(n) = o.cpus {
            a.extend(["--cpus".into(), n.to_string()]);
        }
        if let Some(n) = o.time_limit {
            a.extend(["--time-limit".into(), n.to_string()]);
        }
        if let Some(p) = &o.plane {
            a.extend(["--plane".into(), p.clone()]);
        }
        self.run(&a, "")
    }

    /// The output of the tests the last merge refused (all, or the named one); with `run`, a command in that test's
    /// view on land's runner.
    pub fn logs(&self, name: Option<&str>, run: Option<&str>) -> Result {
        let mut a = vec!["logs"];
        a.extend(name);
        if let Some(cmd) = run {
            a.extend(["--run", cmd]);
        }
        self.run(&a, "")
    }

    /// This repository's secrets. A value goes on stdin, never in an argument.
    pub fn secrets(&self) -> Secrets<'_> {
        Secrets { land: self }
    }
}

pub struct Secrets<'a> {
    land: &'a Land,
}

impl Secrets<'_> {
    /// Store one secret.
    pub fn set(&self, name: &str, value: &str) -> Result {
        self.land.run(&["secret", "set", name], value)
    }
    /// Store every NAME=value line of a .env text.
    pub fn set_many(&self, dotenv: &str) -> Result {
        self.land.run(&["secret", "set"], dotenv)
    }
    pub fn list(&self) -> Result {
        self.land.run(&["secret", "list"], "")
    }
    pub fn rm(&self, name: &str) -> Result {
        self.land.run(&["secret", "rm", name], "")
    }
}
