// Runs the SDK against the fake `land` in fake, put first on PATH.
package useland

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

var ctx = context.Background()

func fake(t *testing.T) (*Land, func() string) {
	dir, _ := filepath.Abs("../fake")
	t.Setenv("PATH", dir+string(os.PathListSeparator)+os.Getenv("PATH"))
	log := filepath.Join(t.TempDir(), "argv")
	t.Setenv("FAKE_LAND_LOG", log)
	return &Land{}, func() string { b, _ := os.ReadFile(log); return strings.TrimSpace(string(b)) }
}

func TestMergeReturnsTheAnswerAndPassesJSON(t *testing.T) {
	land, argv := fake(t)
	a, err := land.Merge(ctx, []string{"sub/ok"}, "main", false)
	if err != nil || len(a.Landed) != 1 || a.Landed[0] != "sub/ok" {
		t.Fatal(a, err)
	}
	if argv() != "merge sub/ok --onto main --json" {
		t.Fatal(argv())
	}
}

func TestExit1IsRefusedAndKeepsTheAnswer(t *testing.T) {
	land, _ := fake(t)
	_, err := land.Merge(ctx, []string{"sub/bad"}, "main", false)
	var r *RefusedError
	if !errors.As(err, &r) || r.Code != 1 || len(r.Answer["refused"].([]any)) != 1 {
		t.Fatal(err)
	}
}

func TestExit2CarriesFixAndDocs(t *testing.T) {
	land, _ := fake(t)
	_, err := land.Logs(ctx, "users", nil)
	var u *UsageError
	if !errors.As(err, &u) || u.Fix != "land merge <branch>... --onto main" || u.Docs != "https://docs.useland.dev/errors#exit-2" {
		t.Fatal(err)
	}
	var le *LandError
	if !errors.As(err, &le) || le.Code != 2 {
		t.Fatal("a UsageError is also a *LandError")
	}
}

func TestExit3IsStillRunning(t *testing.T) {
	land, _ := fake(t)
	_, err := land.Add(ctx, AddOptions{})
	var s *StillRunningError
	if !errors.As(err, &s) || s.Fix != "land add" {
		t.Fatal(err)
	}
}

func TestALocalRunIsNeverSigned(t *testing.T) {
	land, _ := fake(t)
	a, err := land.Local(ctx)
	if err != nil || a.Signed || a.Tests.RanUnits[0].Name != "spec" {
		t.Fatal(a, err)
	}
}

func TestASecretsValueGoesOnStdin(t *testing.T) {
	land, argv := fake(t)
	if _, err := land.Secrets().Set(ctx, "STRIPE_KEY", "sk_test_1"); err != nil {
		t.Fatal(err)
	}
	stdin, _ := os.ReadFile(os.Getenv("FAKE_LAND_LOG") + ".stdin")
	if argv() != "secret set STRIPE_KEY --json" || string(stdin) != "sk_test_1" {
		t.Fatal(argv(), string(stdin))
	}
}
