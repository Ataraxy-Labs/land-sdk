# frozen_string_literal: true

# Runs the SDK against the fake `land` in fake, put first on PATH.
require "minitest/autorun"
require "tmpdir"
require_relative "../lib/useland"

class LandTest < Minitest::Test
  def setup
    @log = File.join(Dir.mktmpdir, "argv")
    ENV["FAKE_LAND_LOG"] = @log
    fake = File.expand_path("../../fake", __dir__)
    ENV["PATH"] = "#{fake}#{File::PATH_SEPARATOR}#{ENV['PATH']}" unless ENV["PATH"].start_with?(fake)
    @land = Useland::Land.new
  end

  def argv = File.read(@log).strip

  def test_merge_returns_the_answer_and_passes_json
    a = @land.merge(["sub/ok"], onto: "main")
    assert_equal ["sub/ok"], a["landed"]
    assert_equal "merge sub/ok --onto main --json", argv
  end

  def test_exit_1_is_refused_and_keeps_the_answer
    e = assert_raises(Useland::RefusedError) { @land.merge(["sub/bad"], onto: "main") }
    assert_equal 1, e.code
    assert_equal 1, e.answer["refused"].length
  end

  def test_exit_2_carries_fix_and_docs
    e = assert_raises(Useland::UsageError) { @land.logs("users") }
    assert_equal "land merge <branch>... --onto main", e.fix
    assert_equal "https://docs.useland.dev/errors#exit-2", e.docs
    assert_kind_of Useland::LandError, e
  end

  def test_exit_3_is_still_running
    e = assert_raises(Useland::StillRunningError) { @land.add }
    assert_equal "land add", e.fix
  end

  def test_a_local_run_is_never_signed
    a = @land.local
    refute a["signed"]
    assert_equal "spec", a["tests"]["ran_units"][0]["name"]
  end

  def test_a_secrets_value_goes_on_stdin
    @land.secrets.set("STRIPE_KEY", "sk_test_1")
    assert_equal "secret set STRIPE_KEY --json", argv
    assert_equal "sk_test_1", File.read("#{@log}.stdin")
  end

  def test_add_and_logs_switches
    assert_raises(Useland::StillRunningError) { @land.add(dry_run: true, test: ["pytest -q"], allow_test_edits: false, cpus: 4) }
    assert_equal "add --dry-run --test pytest -q --no-allow-test-edits --cpus 4 --json", argv
    assert_raises(Useland::UsageError) { @land.logs("users", run: "ls") }
    assert_equal "logs users --run ls --json", argv
  end
end
