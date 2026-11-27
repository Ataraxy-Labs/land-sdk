# frozen_string_literal: true

# Typed wrappers over the land CLI.
#
# Every call runs `land ... --json`, reads the one JSON object it prints on stdout and returns it as a Hash; a non-zero
# exit raises a typed error carrying land's fix and docs. No network code: the CLI does all of that, with your login
# or LAND_TOKEN.
require "json"
require "open3"

module Useland
  # A non-zero exit of the CLI. `answer` is the whole JSON object it printed.
  class LandError < StandardError
    attr_reader :code, :fix, :docs, :answer

    def initialize(message, code, fix, docs, answer)
      super(message)
      @code, @fix, @docs, @answer = code, fix, docs, answer
    end
  end

  # exit 1: some branch was refused, a local test failed, or an add was refused. `answer` still holds what landed.
  class RefusedError < LandError; end
  # exit 2: a usage or local error; `fix` is the command that fixes it.
  class UsageError < LandError; end
  # exit 3: land is still working; make the same call again to wait.
  class StillRunningError < LandError; end
  # exit 4: land could not check (an engine or infrastructure error); make the same call again.
  class CouldNotCheckError < LandError; end

  BY_CODE = { 1 => RefusedError, 2 => UsageError, 3 => StillRunningError, 4 => CouldNotCheckError }.freeze

  # Map an exit code and the CLI's JSON to the typed error (for callers that run the CLI themselves).
  def self.error_for(code, answer)
    s = ->(k) { answer[k].is_a?(String) ? answer[k] : nil }
    BY_CODE.fetch(code, LandError).new(s["error"] || "land exited #{code}", code, s["fix"] || s["again"], s["docs"], answer)
  end

  # This repository's secrets. A value goes on stdin, never in an argument.
  class Secrets
    def initialize(land) = @land = land

    # Store one secret.
    def set(name, value) = @land.run(["secret", "set", name], stdin: value)
    # Store every NAME=value line of a .env text.
    def set_many(dotenv) = @land.run(%w[secret set], stdin: dotenv)
    def list = @land.run(%w[secret list])
    def rm(name) = @land.run(["secret", "rm", name])
  end

  # Runs the land CLI. With no arguments it runs `land` on PATH in this process's working directory.
  class Land
    attr_reader :secrets

    def initialize(bin: "land", cwd: nil, env: {})
      @bin, @cwd, @env = bin, cwd, env
      @secrets = Secrets.new(self)
    end

    # Run `land <args> --json` and return its JSON; a non-zero exit raises the typed error.
    def run(args, stdin: "")
      opts = { stdin_data: stdin }
      opts[:chdir] = @cwd if @cwd
      out, err, status = Open3.capture3(@env, @bin, *args, "--json", **opts)
      last = out.strip.lines.last
      answer =
        begin
          last ? JSON.parse(last) : {}
        rescue JSON::ParserError
          { "error" => "land printed no JSON (exit #{status.exitstatus}): #{err.strip[-500..] || err.strip}" }
        end
      raise Useland.error_for(status.exitstatus, answer) unless status.success?

      answer
    end

    # Merge branches onto a target on land and prove the result. Exit 1 (some refused) raises RefusedError.
    def merge(branches, onto:, result_tree: false)
      run(["merge", *branches, "--onto", onto, *(result_tree ? %w[--result tree] : [])])
    end

    # Run .land/tests (all, or the named lines or tests) on this machine. Unsigned, never a verdict.
    def local(*names) = run(["merge", "--local", *names])

    # Connect this repository (or preview with dry_run). A nil leaves that switch out.
    def add(dry_run: false, test: [], allow_test_edits: nil, allow_low_guard: nil, cpus: nil, time_limit: nil, plane: nil)
      a = ["add"]
      a << "--dry-run" if dry_run
      test.each { |t| a.push("--test", t) }
      a << (allow_test_edits ? "--allow-test-edits" : "--no-allow-test-edits") unless allow_test_edits.nil?
      a << (allow_low_guard ? "--allow-low-guard" : "--no-allow-low-guard") unless allow_low_guard.nil?
      a.push("--cpus", cpus.to_s) if cpus
      a.push("--time-limit", time_limit.to_s) if time_limit
      a.push("--plane", plane) if plane
      run(a)
    end

    # The output of the tests the last merge refused (all, or the named one); with `run`, a command in that test's
    # view on land's runner.
    def logs(name = nil, run: nil)
      self.run(["logs", *(name ? [name] : []), *(run.nil? ? [] : ["--run", run])])
    end
  end
end
