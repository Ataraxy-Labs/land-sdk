package dev.useland;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the SDK against the fake {@code land} in fake, named as the executable. No test framework, so there is
 * nothing to download: {@code sh test.sh} compiles and runs this, and it exits 0 when every check passes.
 */
public final class LandTest {
    interface Body {
        void run(Land land, Path log) throws Exception;
    }

    static int failed = 0;

    static void test(String name, Body body) {
        Path log = Path.of(System.getProperty("java.io.tmpdir"), "useland-java-" + UUID.randomUUID());
        Land land = new Land().bin(Path.of("..", "fake", "land").toAbsolutePath().normalize().toString())
                .env("FAKE_LAND_LOG", log.toString());
        try {
            body.run(land, log);
            System.out.println("ok   " + name);
        } catch (Throwable e) {
            failed++;
            System.out.println("FAIL " + name + ": " + e);
        }
    }

    static void eq(Object want, Object got) {
        if (!java.util.Objects.equals(want, got)) throw new AssertionError("wanted " + want + ", got " + got);
    }

    static <T extends LandException> T thrown(Class<T> type, Runnable f) {
        try {
            f.run();
        } catch (LandException e) {
            return type.cast(e);
        }
        throw new AssertionError("no " + type.getSimpleName());
    }

    static String argv(Path log) throws Exception {
        return Files.readString(log).strip();
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        test("merge returns the answer and passes --json", (land, log) -> {
            eq(List.of("sub/ok"), land.merge(List.of("sub/ok"), "main").get("landed"));
            eq("merge sub/ok --onto main --json", argv(log));
        });
        test("exit 1 is refused and keeps the answer", (land, log) -> {
            LandException.Refused e = thrown(LandException.Refused.class, () -> land.merge(List.of("sub/bad"), "main"));
            eq(1, e.code);
            eq(1, ((List<Object>) e.answer.get("refused")).size());
        });
        test("exit 2 carries fix and docs", (land, log) -> {
            LandException e = thrown(LandException.Usage.class, () -> land.logs("users"));
            eq("land merge <branch>... --onto main", e.fix);
            eq("https://docs.useland.dev/errors#exit-2", e.docs);
        });
        test("exit 3 is still running", (land, log) ->
                eq("land add", thrown(LandException.StillRunning.class, land::add).fix));
        test("a local run is never signed", (land, log) -> {
            Map<String, Object> a = land.local();
            eq(false, a.get("signed"));
            Map<String, Object> tests = (Map<String, Object>) a.get("tests");
            eq("spec", ((Map<String, Object>) ((List<Object>) tests.get("ran_units")).get(0)).get("name"));
            eq(0.4, ((Map<String, Object>) ((List<Object>) tests.get("ran_units")).get(0)).get("seconds"));
            eq(1L, tests.get("total"));
        });
        test("a secret's value goes on stdin", (land, log) -> {
            land.secrets().set("STRIPE_KEY", "sk_test_1");
            eq("secret set STRIPE_KEY --json", argv(log));
            eq("sk_test_1", Files.readString(Path.of(log + ".stdin")));
        });
        test("add and logs switches", (land, log) -> {
            Land.AddOptions o = new Land.AddOptions();
            o.dryRun = true;
            o.test = List.of("pytest -q");
            o.allowTestEdits = false;
            o.cpus = 4;
            thrown(LandException.StillRunning.class, () -> land.add(o));
            eq("add --dry-run --test pytest -q --no-allow-test-edits --cpus 4 --json", argv(log));
            thrown(LandException.Usage.class, () -> land.logs("users", "ls"));
            eq("logs users --run ls --json", argv(log));
        });
        test("the JSON reader handles escapes, nesting and numbers", (land, log) -> {
            Map<String, Object> m = (Map<String, Object>) Json.parse(
                    "{\"a\":\"x\\n\\u00e9\\\"\",\"b\":[1,-2.5,1e3,true,null,{}],\"c\":{\"d\":[]}}");
            eq("x\né\"", m.get("a"));
            eq(List.of(1L, -2.5, 1000.0, true), ((List<Object>) m.get("b")).subList(0, 4));
            eq(null, ((List<Object>) m.get("b")).get(4));
        });
        System.out.println(failed == 0 ? "8 passed" : failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
