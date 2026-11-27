defmodule Useland do
  @moduledoc """
  Typed wrappers over the land CLI.

  Every call runs `land ... --json`, reads the one JSON object it prints on stdout and returns it as a map. A non-zero
  exit comes back as `{:error, %Useland.Error{}}` carrying land's fix and docs. No network code: the CLI does all of
  that, with your login or LAND_TOKEN.
  """

  defmodule Error do
    @moduledoc """
    A non-zero exit of the CLI. `answer` is the whole JSON object it printed, and `kind` names the exit:

      * `:refused` (exit 1) some branch was refused, a local test failed, or an add was refused
      * `:usage` (exit 2) a usage or local error, and `fix` is the command that fixes it
      * `:still_running` (exit 3) land is still working, so make the same call again
      * `:could_not_check` (exit 4) land could not check, so make the same call again
    """
    defexception [:kind, :message, :code, :fix, :docs, :answer]
  end

  defstruct bin: "land", cwd: nil, env: %{}

  @type t :: %__MODULE__{bin: String.t(), cwd: String.t() | nil, env: %{optional(String.t()) => String.t()}}
  @type answer :: {:ok, map()} | {:error, Error.t()}

  @kinds %{1 => :refused, 2 => :usage, 3 => :still_running, 4 => :could_not_check}

  @doc "A client. Options are `:bin` (default `land` on PATH), `:cwd` (the repository) and `:env` (a map)."
  def new(opts \\ []), do: struct!(__MODULE__, opts)

  @doc "Map an exit code and the CLI's JSON to the error (for callers that run the CLI themselves)."
  def error_for(code, answer) do
    s = fn k -> if is_binary(answer[k]), do: answer[k] end

    %Error{
      kind: Map.get(@kinds, code, :other),
      message: s.("error") || "land exited #{code}",
      code: code,
      fix: s.("fix") || s.("again"),
      docs: s.("docs"),
      answer: answer
    }
  end

  @doc "Run `land <args> --json` and return its JSON. A non-zero exit returns `{:error, %Useland.Error{}}`."
  @spec run(t(), [String.t()], String.t()) :: answer()
  def run(%__MODULE__{} = land, args, stdin \\ "") do
    err = Path.join(System.tmp_dir!(), "useland-#{System.unique_integer([:positive])}.err")
    # A port cannot close stdin on its own, so head passes exactly the bytes of stdin on and then the CLI sees the end.
    script =
      ~s(n=$0; err=$1; shift; if [ "$n" = 0 ]; then "$@" </dev/null 2>"$err"; else head -c "$n" | "$@" 2>"$err"; fi)

    argv = ["-c", script, Integer.to_string(byte_size(stdin)), err, land.bin | args] ++ ["--json"]
    env = for {k, v} <- land.env, do: {String.to_charlist(k), String.to_charlist(v)}
    opts = [:binary, :exit_status, :hide, args: argv, env: env] ++ if(land.cwd, do: [cd: land.cwd], else: [])
    port = Port.open({:spawn_executable, System.find_executable("sh")}, opts)
    if stdin != "", do: Port.command(port, stdin)
    {out, code} = collect(port, "")
    stderr = File.read(err) |> elem(1) |> to_string()
    File.rm(err)

    answer =
      case out |> String.trim() |> String.split("\n") |> List.last() do
        "" ->
          %{}

        last ->
          case JSON.decode(last) do
            {:ok, map} when is_map(map) ->
              map

            _ ->
              %{
                "error" =>
                  "land printed no JSON (exit #{code}): #{stderr |> String.trim() |> String.slice(-500..-1//1)}"
              }
          end
      end

    if code == 0, do: {:ok, answer}, else: {:error, error_for(code, answer)}
  end

  defp collect(port, acc) do
    receive do
      {^port, {:data, data}} -> collect(port, acc <> data)
      {^port, {:exit_status, code}} -> {acc, code}
    end
  end

  @doc """
  Merge branches onto a target on land and prove the result. Exit 1 (some refused) is an error of kind `:refused`.
  With `result_tree: true` it passes `--result tree`.
  """
  def merge(land, branches, onto, opts \\ []) do
    run(land, ["merge" | branches] ++ ["--onto", onto] ++ if(opts[:result_tree], do: ["--result", "tree"], else: []))
  end

  @doc "Run .land/tests (all, or the named lines or tests) on this machine. Unsigned, never a verdict."
  def local(land, names \\ []), do: run(land, ["merge", "--local" | names])

  @doc """
  Connect this repository (or preview with `dry_run: true`). Options are `:dry_run`, `:test` (a list),
  `:allow_test_edits`, `:allow_low_guard`, `:cpus`, `:time_limit` and `:plane`. A missing option leaves its switch out.
  """
  def add(land, opts \\ []) do
    flag = fn name, key ->
      case opts[key] do
        nil -> []
        true -> ["--" <> name]
        false -> ["--no-" <> name]
      end
    end

    num = fn name, key -> if opts[key], do: ["--" <> name, to_string(opts[key])], else: [] end

    run(
      land,
      ["add"] ++
        if(opts[:dry_run], do: ["--dry-run"], else: []) ++
        Enum.flat_map(opts[:test] || [], &["--test", &1]) ++
        flag.("allow-test-edits", :allow_test_edits) ++
        flag.("allow-low-guard", :allow_low_guard) ++
        num.("cpus", :cpus) ++ num.("time-limit", :time_limit) ++ num.("plane", :plane)
    )
  end

  @doc """
  The output of the tests the last merge refused (all, or the named one). With `run: "cmd"` it runs a command in that
  test's view on land's runner.
  """
  def logs(land, name \\ nil, opts \\ []) do
    run(land, ["logs"] ++ if(name, do: [name], else: []) ++ if(opts[:run], do: ["--run", opts[:run]], else: []))
  end

  defmodule Secrets do
    @moduledoc "This repository's secrets. A value goes on stdin, never in an argument."

    @doc "Store one secret."
    def set(land, name, value), do: Useland.run(land, ["secret", "set", name], value)
    @doc "Store every NAME=value line of a .env text."
    def set_many(land, dotenv), do: Useland.run(land, ["secret", "set"], dotenv)
    def list(land), do: Useland.run(land, ["secret", "list"])
    def rm(land, name), do: Useland.run(land, ["secret", "rm", name])
  end
end
