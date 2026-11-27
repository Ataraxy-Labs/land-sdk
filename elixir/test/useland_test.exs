# Runs the SDK against the fake `land` in fake, named as the executable.
defmodule UselandTest do
  use ExUnit.Case, async: true

  setup do
    log = Path.join(System.tmp_dir!(), "useland-ex-#{System.unique_integer([:positive])}")
    land = Useland.new(bin: Path.expand("../../fake/land", __DIR__), env: %{"FAKE_LAND_LOG" => log})
    {:ok, land: land, log: log}
  end

  defp argv(log), do: log |> File.read!() |> String.trim()

  test "merge returns the answer and passes --json", %{land: land, log: log} do
    assert {:ok, %{"landed" => ["sub/ok"]}} = Useland.merge(land, ["sub/ok"], "main")
    assert argv(log) == "merge sub/ok --onto main --json"
  end

  test "exit 1 is refused and keeps the answer", %{land: land} do
    assert {:error, %Useland.Error{kind: :refused, code: 1, answer: %{"refused" => [_]}}} =
             Useland.merge(land, ["sub/bad"], "main")
  end

  test "exit 2 carries fix and docs", %{land: land} do
    assert {:error, %Useland.Error{kind: :usage} = e} = Useland.logs(land, "users")
    assert e.fix == "land merge <branch>... --onto main"
    assert e.docs == "https://docs.useland.dev/errors#exit-2"
  end

  test "exit 3 is still running", %{land: land} do
    assert {:error, %Useland.Error{kind: :still_running, fix: "land add"}} = Useland.add(land)
  end

  test "a local run is never signed", %{land: land} do
    assert {:ok, %{"signed" => false, "tests" => %{"ran_units" => [%{"name" => "spec"}]}}} = Useland.local(land)
  end

  test "a secret's value goes on stdin", %{land: land, log: log} do
    assert {:ok, _} = Useland.Secrets.set(land, "STRIPE_KEY", "sk_test_1")
    assert argv(log) == "secret set STRIPE_KEY --json"
    assert File.read!(log <> ".stdin") == "sk_test_1"
  end

  test "add and logs switches", %{land: land, log: log} do
    assert {:error, _} = Useland.add(land, dry_run: true, test: ["pytest -q"], allow_test_edits: false, cpus: 4)
    assert argv(log) == "add --dry-run --test pytest -q --no-allow-test-edits --cpus 4 --json"
    assert {:error, _} = Useland.logs(land, "users", run: "ls")
    assert argv(log) == "logs users --run ls --json"
  end
end
