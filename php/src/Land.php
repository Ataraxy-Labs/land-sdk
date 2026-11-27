<?php
// Typed wrappers over the land CLI.
//
// Every call runs `land ... --json`, reads the one JSON object it prints on stdout and returns it as an array; a
// non-zero exit throws a typed exception carrying land's fix and docs. No network code: the CLI does all of that,
// with your login or LAND_TOKEN.
declare(strict_types=1);

namespace Useland;

/** A non-zero exit of the CLI. $answer is the whole JSON object it printed. */
class LandException extends \RuntimeException
{
    public function __construct(
        string $message,
        public readonly int $exit,
        /** the command that fixes it, or the call to make again */
        public readonly ?string $fix,
        public readonly ?string $docs,
        public readonly array $answer,
    ) {
        parent::__construct($message, $exit);
    }

    /** Map an exit code and the CLI's JSON to the typed exception (for callers that run the CLI themselves). */
    public static function for(int $code, array $answer): self
    {
        $s = fn (string $k): ?string => is_string($answer[$k] ?? null) ? $answer[$k] : null;
        $class = [1 => RefusedException::class, 2 => UsageException::class, 3 => StillRunningException::class,
            4 => CouldNotCheckException::class][$code] ?? self::class;
        return new $class($s('error') ?? "land exited $code", $code, $s('fix') ?? $s('again'), $s('docs'), $answer);
    }
}

/** exit 1: some branch was refused, a local test failed, or an add was refused. $answer still holds what landed. */
final class RefusedException extends LandException {}
/** exit 2: a usage or local error; $fix is the command that fixes it. */
final class UsageException extends LandException {}
/** exit 3: land is still working; make the same call again to wait. */
final class StillRunningException extends LandException {}
/** exit 4: land could not check (an engine or infrastructure error); make the same call again. */
final class CouldNotCheckException extends LandException {}

/** This repository's secrets. A value goes on stdin, never in an argument. */
final class Secrets
{
    public function __construct(private readonly Land $land) {}

    /** Store one secret. */
    public function set(string $name, string $value): array { return $this->land->run(['secret', 'set', $name], $value); }
    /** Store every NAME=value line of a .env text. */
    public function setMany(string $dotenv): array { return $this->land->run(['secret', 'set'], $dotenv); }
    public function list(): array { return $this->land->run(['secret', 'list']); }
    public function rm(string $name): array { return $this->land->run(['secret', 'rm', $name]); }
}

/** Runs the land CLI. With no arguments it runs `land` on PATH in this process's working directory. */
final class Land
{
    public readonly Secrets $secrets;

    /** @param array<string,string> $env extra environment for the CLI, e.g. LAND_TOKEN */
    public function __construct(
        private readonly string $bin = 'land',
        private readonly ?string $cwd = null,
        private readonly array $env = [],
    ) {
        $this->secrets = new Secrets($this);
    }

    /** Run `land <args> --json` and return its JSON; a non-zero exit throws the typed exception. */
    public function run(array $args, string $stdin = ''): array
    {
        $err = tmpfile();   // stderr goes to a file so a full pipe can never stall the call
        $p = proc_open([$this->bin, ...$args, '--json'], [0 => ['pipe', 'r'], 1 => ['pipe', 'w'], 2 => $err], $pipes,
            $this->cwd, $this->env ? array_merge(getenv(), $this->env) : null);
        if (!is_resource($p)) {
            throw LandException::for(-1, ['error' => 'land did not start']);
        }
        @fwrite($pipes[0], $stdin);   // a CLI that exits without reading its stdin closes the pipe
        fclose($pipes[0]);
        $out = stream_get_contents($pipes[1]);
        fclose($pipes[1]);
        $code = proc_close($p);
        rewind($err);
        $stderr = trim((string) stream_get_contents($err));
        $lines = preg_split('/\R/', trim((string) $out));
        $last = end($lines) ?: '';
        $answer = $last === '' ? [] : json_decode($last, true);
        if (!is_array($answer)) {
            $answer = ['error' => "land printed no JSON (exit $code): " . substr($stderr, -500)];
        }
        if ($code !== 0) {
            throw LandException::for($code, $answer);
        }
        return $answer;
    }

    /** Merge branches onto a target on land and prove the result. Exit 1 (some refused) throws RefusedException. */
    public function merge(array $branches, string $onto, bool $resultTree = false): array
    {
        return $this->run(['merge', ...$branches, '--onto', $onto, ...($resultTree ? ['--result', 'tree'] : [])]);
    }

    /** Run .land/tests (all, or the named lines or tests) on this machine. Unsigned, never a verdict. */
    public function local(string ...$names): array
    {
        return $this->run(['merge', '--local', ...$names]);
    }

    /** Connect this repository (or preview with dryRun). A null leaves that switch out. */
    public function add(bool $dryRun = false, array $test = [], ?bool $allowTestEdits = null, ?bool $allowLowGuard = null,
        ?int $cpus = null, ?int $timeLimit = null, ?string $plane = null): array
    {
        $a = ['add', ...($dryRun ? ['--dry-run'] : [])];
        foreach ($test as $t) {
            array_push($a, '--test', $t);
        }
        if ($allowTestEdits !== null) {
            $a[] = $allowTestEdits ? '--allow-test-edits' : '--no-allow-test-edits';
        }
        if ($allowLowGuard !== null) {
            $a[] = $allowLowGuard ? '--allow-low-guard' : '--no-allow-low-guard';
        }
        if ($cpus !== null) {
            array_push($a, '--cpus', (string) $cpus);
        }
        if ($timeLimit !== null) {
            array_push($a, '--time-limit', (string) $timeLimit);
        }
        if ($plane !== null) {
            array_push($a, '--plane', $plane);
        }
        return $this->run($a);
    }

    /**
     * The output of the tests the last merge refused (all, or the named one); with $run, a command in that test's
     * view on land's runner.
     */
    public function logs(?string $name = null, ?string $run = null): array
    {
        return $this->run(['logs', ...($name !== null ? [$name] : []), ...($run !== null ? ['--run', $run] : [])]);
    }
}
