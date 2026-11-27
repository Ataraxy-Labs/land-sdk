// Package useland is a set of typed wrappers over the land CLI.
//
// Every call runs `land ... --json`, reads the one JSON object it prints on stdout and returns it typed; a non-zero
// exit becomes a typed error carrying land's fix and docs. No network code: the CLI does all of that, with your login
// or LAND_TOKEN.
package useland

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"strconv"
	"strings"
)

// RanUnit is one test row, as a merge answer's tests.ran_units lists it (and a local run too).
type RanUnit struct {
	Name    string  `json:"name"`
	State   string  `json:"state"` // "ran", "failed", "rerun", "not_run"
	Seconds float64 `json:"seconds,omitempty"`
	Why     string  `json:"why,omitempty"`
	Exit    *int    `json:"exit,omitempty"`
	Output  string  `json:"output,omitempty"`
}

type Tests struct {
	Total    int       `json:"total"`
	Ran      int       `json:"ran"`
	Carried  int       `json:"carried"`
	Failed   int       `json:"failed"`
	RanUnits []RanUnit `json:"ran_units"`
}

// Change is one file a landing changes on the target. Status is added, deleted, modified or renamed (From is the
// old path of a rename). A binary file has no line counts.
type Change struct {
	Path    string `json:"path"`
	Status  string `json:"status"`
	From    string `json:"from,omitempty"`
	Added   int    `json:"added"`
	Removed int    `json:"removed"`
	Binary  bool   `json:"binary,omitempty"`
}

// Changes is what a landing changes on the target: git's own counts of the target against the result. Entries lists
// at most 500 files; More says how many it leaves out.
type Changes struct {
	Files   int      `json:"files"`
	Added   int      `json:"added"`
	Removed int      `json:"removed"`
	Entries []Change `json:"entries"`
	More    int      `json:"more,omitempty"`
}

type MergeAnswer struct {
	Landed  []string         `json:"landed"`
	Refused []map[string]any `json:"refused"`
	Result  string           `json:"result"` // the commit to push
	Push    string           `json:"push"`   // the exact push command
	Tests   *Tests           `json:"tests"`
	Changes *Changes         `json:"changes"` // nil when nothing landed
}

// LocalAnswer is `land merge --local`: never a verdict. Signed, Recorded and Landed are always false.
type LocalAnswer struct {
	Local    bool   `json:"local"`
	Signed   bool   `json:"signed"`
	Recorded bool   `json:"recorded"`
	Landed   bool   `json:"landed"`
	Verdict  any    `json:"verdict"`
	Said     string `json:"said"`
	Tests    Tests  `json:"tests"`
}

// Recipe is one item of an add's suggest list: a .land/setup.sh and .land/tests to start from.
type Recipe struct {
	Recipe  string   `json:"recipe"`
	Because []string `json:"because"`
	Setup   string   `json:"setup"`
	Tests   string   `json:"tests"`
	Units   any      `json:"units"` // a count, or why the lines do not expand here
	Docs    string   `json:"docs"`
}

type AddAnswer struct {
	Added   bool     `json:"added"`
	DryRun  bool     `json:"dry_run"`
	Repo    string   `json:"repo"`
	Suggest []Recipe `json:"suggest"` // recipes to start from, when the tree has no .land/tests and no workflow
}

type LogsAnswer struct {
	Failing []struct {
		Branch string `json:"branch"`
		Test   string `json:"test"`
		Output string `json:"output"`
	} `json:"failing"`
	Probe map[string]any `json:"probe"`
}

// LandError is a non-zero exit of the CLI. Answer is the whole JSON object it printed.
type LandError struct {
	Message string
	Code    int
	Fix     string // the command that fixes it, or the call to make again
	Docs    string
	Answer  map[string]any
}

func (e *LandError) Error() string { return e.Message }

// RefusedError is exit 1: some branch was refused, a local test failed, or an add was refused. Answer still holds
// what landed.
type RefusedError struct{ *LandError }

// UsageError is exit 2: a usage or local error; Fix is the command that fixes it.
type UsageError struct{ *LandError }

// StillRunningError is exit 3: land is still working; make the same call again to wait.
type StillRunningError struct{ *LandError }

// CouldNotCheckError is exit 4: land could not check (an engine or infrastructure error); make the same call again.
type CouldNotCheckError struct{ *LandError }

func (e *RefusedError) Unwrap() error       { return e.LandError }
func (e *UsageError) Unwrap() error         { return e.LandError }
func (e *StillRunningError) Unwrap() error  { return e.LandError }
func (e *CouldNotCheckError) Unwrap() error { return e.LandError }

// ErrorFor maps an exit code and the CLI's JSON to the typed error (for callers that run the CLI themselves).
func ErrorFor(code int, answer map[string]any) error {
	str := func(k string) string { s, _ := answer[k].(string); return s }
	e := &LandError{Message: str("error"), Code: code, Fix: str("fix"), Docs: str("docs"), Answer: answer}
	if e.Message == "" {
		e.Message = fmt.Sprintf("land exited %d", code)
	}
	if e.Fix == "" {
		e.Fix = str("again")
	}
	switch code {
	case 1:
		return &RefusedError{e}
	case 2:
		return &UsageError{e}
	case 3:
		return &StillRunningError{e}
	case 4:
		return &CouldNotCheckError{e}
	}
	return e
}

// Land runs the land CLI. The zero value runs `land` on PATH in this process's working directory.
type Land struct {
	Bin string            // the land executable (default: land on PATH)
	Cwd string            // the repository to run in (default: this process's working directory)
	Env map[string]string // extra environment for the CLI, e.g. {"LAND_TOKEN": "lk_..."}
}

// Run runs `land <args> --json` and returns its JSON; a non-zero exit returns the typed error.
func (l *Land) Run(ctx context.Context, args []string, stdin string) (map[string]any, error) {
	bin := l.Bin
	if bin == "" {
		bin = "land"
	}
	cmd := exec.CommandContext(ctx, bin, append(append([]string{}, args...), "--json")...)
	cmd.Dir = l.Cwd
	cmd.Env = os.Environ()
	for k, v := range l.Env {
		cmd.Env = append(cmd.Env, k+"="+v)
	}
	cmd.Stdin = strings.NewReader(stdin)
	var out, errOut bytes.Buffer
	cmd.Stdout, cmd.Stderr = &out, &errOut
	code := 0
	if err := cmd.Run(); err != nil {
		var exit *exec.ExitError
		if !errors.As(err, &exit) {
			return nil, err // the CLI did not start
		}
		code = exit.ExitCode()
	}
	answer := map[string]any{}
	lines := strings.Split(strings.TrimSpace(out.String()), "\n")
	if last := lines[len(lines)-1]; last != "" && json.Unmarshal([]byte(last), &answer) != nil {
		tail := strings.TrimSpace(errOut.String())
		if len(tail) > 500 {
			tail = tail[len(tail)-500:]
		}
		answer = map[string]any{"error": fmt.Sprintf("land printed no JSON (exit %d): %s", code, tail)}
	}
	if code != 0 {
		return nil, ErrorFor(code, answer)
	}
	return answer, nil
}

// call runs the CLI and decodes its answer into T. Run gives the whole object, fields T does not name included.
func call[T any](ctx context.Context, l *Land, args []string) (*T, error) {
	answer, err := l.Run(ctx, args, "")
	if err != nil {
		return nil, err
	}
	b, _ := json.Marshal(answer)
	var t T
	if err := json.Unmarshal(b, &t); err != nil {
		return nil, err
	}
	return &t, nil
}

// Merge merges branches onto a target on land and proves the result. Exit 1 (some refused) is a *RefusedError.
// With resultTree it passes --result tree.
func (l *Land) Merge(ctx context.Context, branches []string, onto string, resultTree bool) (*MergeAnswer, error) {
	a := append(append([]string{"merge"}, branches...), "--onto", onto)
	if resultTree {
		a = append(a, "--result", "tree")
	}
	return call[MergeAnswer](ctx, l, a)
}

// Local runs .land/tests (all, or the named lines or tests) on this machine. Unsigned, never a verdict.
func (l *Land) Local(ctx context.Context, names ...string) (*LocalAnswer, error) {
	return call[LocalAnswer](ctx, l, append([]string{"merge", "--local"}, names...))
}

// AddOptions are `land add`'s switches. A nil pointer or a zero leaves that switch out.
type AddOptions struct {
	DryRun         bool
	Test           []string
	AllowTestEdits *bool
	AllowLowGuard  *bool
	CPUs           int
	TimeLimit      int
	Plane          string
}

// Add connects this repository (or previews with DryRun).
func (l *Land) Add(ctx context.Context, o AddOptions) (*AddAnswer, error) {
	a := []string{"add"}
	if o.DryRun {
		a = append(a, "--dry-run")
	}
	for _, t := range o.Test {
		a = append(a, "--test", t)
	}
	flag := func(name string, v *bool) {
		if v != nil && *v {
			a = append(a, "--"+name)
		} else if v != nil {
			a = append(a, "--no-"+name)
		}
	}
	flag("allow-test-edits", o.AllowTestEdits)
	flag("allow-low-guard", o.AllowLowGuard)
	if o.CPUs != 0 {
		a = append(a, "--cpus", strconv.Itoa(o.CPUs))
	}
	if o.TimeLimit != 0 {
		a = append(a, "--time-limit", strconv.Itoa(o.TimeLimit))
	}
	if o.Plane != "" {
		a = append(a, "--plane", o.Plane)
	}
	return call[AddAnswer](ctx, l, a)
}

// Logs is the output of the tests the last merge refused (all, or the named one); with run, a command in that test's
// view on land's runner.
func (l *Land) Logs(ctx context.Context, name string, run *string) (*LogsAnswer, error) {
	a := []string{"logs"}
	if name != "" {
		a = append(a, name)
	}
	if run != nil {
		a = append(a, "--run", *run)
	}
	return call[LogsAnswer](ctx, l, a)
}

// Secrets are this repository's secrets. A value goes on stdin, never in an argument.
func (l *Land) Secrets() Secrets { return Secrets{l} }

type Secrets struct{ land *Land }

// Set stores one secret.
func (s Secrets) Set(ctx context.Context, name, value string) (map[string]any, error) {
	return s.land.Run(ctx, []string{"secret", "set", name}, value)
}

// SetMany stores every NAME=value line of a .env text.
func (s Secrets) SetMany(ctx context.Context, dotenv string) (map[string]any, error) {
	return s.land.Run(ctx, []string{"secret", "set"}, dotenv)
}

func (s Secrets) List(ctx context.Context) (map[string]any, error) {
	return s.land.Run(ctx, []string{"secret", "list"}, "")
}

func (s Secrets) Rm(ctx context.Context, name string) (map[string]any, error) {
	return s.land.Run(ctx, []string{"secret", "rm", name}, "")
}
