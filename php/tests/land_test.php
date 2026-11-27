<?php
// Runs the SDK against the fake `land` in fake, named as the executable. No test framework: `php tests/land_test.php`
// exits 0 when every check passes.
declare(strict_types=1);

require __DIR__ . '/../src/Land.php';

use Useland\{Land, LandException, RefusedException, StillRunningException, UsageException};

$failed = 0;
function eq(mixed $want, mixed $got): void
{
    if ($want !== $got) {
        throw new Exception('wanted ' . var_export($want, true) . ', got ' . var_export($got, true));
    }
}
function throws(string $class, callable $f): LandException
{
    try {
        $f();
    } catch (LandException $e) {
        eq($class, $e::class);
        return $e;
    }
    throw new Exception("no $class");
}
function test(string $name, callable $body): void
{
    global $failed;
    $log = sys_get_temp_dir() . '/useland-php-' . bin2hex(random_bytes(6));
    try {
        $body(new Land(realpath(__DIR__ . '/../../fake/land'), env: ['FAKE_LAND_LOG' => $log]), $log);
        echo "ok   $name\n";
    } catch (Throwable $e) {
        $failed++;
        echo "FAIL $name: {$e->getMessage()}\n";
    }
}
$argv_of = fn (string $log): string => trim(file_get_contents($log));

test('merge returns the answer and passes --json', function (Land $land, string $log) use ($argv_of) {
    eq(['sub/ok'], $land->merge(['sub/ok'], onto: 'main')['landed']);
    eq('merge sub/ok --onto main --json', $argv_of($log));
});
test('exit 1 is refused and keeps the answer', function (Land $land) {
    $e = throws(RefusedException::class, fn () => $land->merge(['sub/bad'], onto: 'main'));
    eq(1, $e->exit);
    eq(1, count($e->answer['refused']));
});
test('exit 2 carries fix and docs', function (Land $land) {
    $e = throws(UsageException::class, fn () => $land->logs('users'));
    eq('land merge <branch>... --onto main', $e->fix);
    eq('https://docs.useland.dev/errors#exit-2', $e->docs);
});
test('exit 3 is still running', function (Land $land) {
    eq('land add', throws(StillRunningException::class, fn () => $land->add())->fix);
});
test('a local run is never signed', function (Land $land) {
    $a = $land->local();
    eq(false, $a['signed']);
    eq('spec', $a['tests']['ran_units'][0]['name']);
});
test("a secret's value goes on stdin", function (Land $land, string $log) use ($argv_of) {
    $land->secrets->set('STRIPE_KEY', 'sk_test_1');
    eq('secret set STRIPE_KEY --json', $argv_of($log));
    eq('sk_test_1', file_get_contents("$log.stdin"));
});
test('add and logs switches', function (Land $land, string $log) use ($argv_of) {
    throws(StillRunningException::class, fn () => $land->add(dryRun: true, test: ['pytest -q'], allowTestEdits: false, cpus: 4));
    eq('add --dry-run --test pytest -q --no-allow-test-edits --cpus 4 --json', $argv_of($log));
    throws(UsageException::class, fn () => $land->logs('users', run: 'ls'));
    eq('logs users --run ls --json', $argv_of($log));
});

echo $failed === 0 ? "7 passed\n" : "$failed failed\n";
exit($failed === 0 ? 0 : 1);
