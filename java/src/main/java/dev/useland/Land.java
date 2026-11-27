package dev.useland;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed wrappers over the land CLI.
 *
 * <p>Every call runs {@code land ... --json}, reads the one JSON object it prints on stdout and returns it as a map.
 * A non-zero exit throws a {@link LandException} carrying land's fix and docs. No network code: the CLI does all of
 * that, with your login or LAND_TOKEN.
 */
public final class Land {
    private String bin = "land";
    private File cwd;
    private final Map<String, String> env = new LinkedHashMap<>();

    /** Runs {@code land} on PATH in this process's working directory. */
    public Land() {}

    /** The land executable (default: land on PATH). */
    public Land bin(String path) {
        this.bin = path;
        return this;
    }

    /** The repository to run in (default: this process's working directory). */
    public Land cwd(String dir) {
        this.cwd = new File(dir);
        return this;
    }

    /** Extra environment for the CLI, for example LAND_TOKEN. */
    public Land env(String key, String value) {
        this.env.put(key, value);
        return this;
    }

    /** {@code land add}'s switches. A null leaves that switch out. */
    public static final class AddOptions {
        public boolean dryRun;
        public List<String> test = List.of();
        public Boolean allowTestEdits;
        public Boolean allowLowGuard;
        public Integer cpus;
        public Integer timeLimit;
        public String plane;
    }

    /** Run {@code land <args> --json} and return its JSON. A non-zero exit throws the typed exception. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> run(List<String> args, String stdin) {
        List<String> argv = new ArrayList<>();
        argv.add(bin);
        argv.addAll(args);
        argv.add("--json");
        int code;
        String out;
        String err;
        try {
            Path errFile = Files.createTempFile("useland", ".err"); // stderr goes to a file so a full pipe can never stall
            try {
                ProcessBuilder b = new ProcessBuilder(argv).redirectError(errFile.toFile());
                if (cwd != null) b.directory(cwd);
                b.environment().putAll(env);
                Process p = b.start();
                try (OutputStream in = p.getOutputStream()) {
                    in.write(stdin.getBytes(StandardCharsets.UTF_8));
                } catch (IOException closed) {
                    // a CLI that exits without reading its stdin closes the pipe
                }
                out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                code = p.waitFor();
                err = Files.readString(errFile).strip();
            } finally {
                Files.deleteIfExists(errFile);
            }
        } catch (IOException e) {
            throw LandException.of(-1, Map.of("error", "land did not start: " + e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw LandException.of(-1, Map.of("error", "interrupted while land was running"));
        }
        String[] lines = out.strip().split("\n");
        String last = lines[lines.length - 1];
        Map<String, Object> answer;
        try {
            answer = last.isEmpty() ? new LinkedHashMap<>() : (Map<String, Object>) Json.parse(last);
        } catch (IllegalArgumentException | ClassCastException e) {
            String tail = err.length() > 500 ? err.substring(err.length() - 500) : err;
            answer = new LinkedHashMap<>(Map.of("error", "land printed no JSON (exit " + code + "): " + tail));
        }
        if (code != 0) throw LandException.of(code, answer);
        return answer;
    }

    /** Run with nothing on stdin. */
    public Map<String, Object> run(List<String> args) {
        return run(args, "");
    }

    /** Merge branches onto a target on land and prove the result. Exit 1 (some refused) throws LandException.Refused. */
    public Map<String, Object> merge(List<String> branches, String onto) {
        return merge(branches, onto, false);
    }

    /** With resultTree it passes {@code --result tree}. */
    public Map<String, Object> merge(List<String> branches, String onto, boolean resultTree) {
        List<String> a = new ArrayList<>(List.of("merge"));
        a.addAll(branches);
        a.addAll(List.of("--onto", onto));
        if (resultTree) a.addAll(List.of("--result", "tree"));
        return run(a);
    }

    /** Run .land/tests (all, or the named lines or tests) on this machine. Unsigned, never a verdict. */
    public Map<String, Object> local(String... names) {
        List<String> a = new ArrayList<>(List.of("merge", "--local"));
        a.addAll(List.of(names));
        return run(a);
    }

    /** Connect this repository with the defaults. */
    public Map<String, Object> add() {
        return add(new AddOptions());
    }

    /** Connect this repository (or preview with dryRun). */
    public Map<String, Object> add(AddOptions o) {
        List<String> a = new ArrayList<>(List.of("add"));
        if (o.dryRun) a.add("--dry-run");
        for (String t : o.test) a.addAll(List.of("--test", t));
        if (o.allowTestEdits != null) a.add(o.allowTestEdits ? "--allow-test-edits" : "--no-allow-test-edits");
        if (o.allowLowGuard != null) a.add(o.allowLowGuard ? "--allow-low-guard" : "--no-allow-low-guard");
        if (o.cpus != null) a.addAll(List.of("--cpus", o.cpus.toString()));
        if (o.timeLimit != null) a.addAll(List.of("--time-limit", o.timeLimit.toString()));
        if (o.plane != null) a.addAll(List.of("--plane", o.plane));
        return run(a);
    }

    /**
     * The output of the tests the last merge refused (all when name is null, or the named one). With run, a command
     * in that test's view on land's runner.
     */
    public Map<String, Object> logs(String name, String run) {
        List<String> a = new ArrayList<>(List.of("logs"));
        if (name != null) a.add(name);
        if (run != null) a.addAll(List.of("--run", run));
        return run(a);
    }

    public Map<String, Object> logs(String name) {
        return logs(name, null);
    }

    /** This repository's secrets. A value goes on stdin, never in an argument. */
    public Secrets secrets() {
        return new Secrets();
    }

    public final class Secrets {
        /** Store one secret. */
        public Map<String, Object> set(String name, String value) {
            return run(List.of("secret", "set", name), value);
        }

        /** Store every NAME=value line of a .env text. */
        public Map<String, Object> setMany(String dotenv) {
            return run(List.of("secret", "set"), dotenv);
        }

        public Map<String, Object> list() {
            return run(List.of("secret", "list"));
        }

        public Map<String, Object> rm(String name) {
            return run(List.of("secret", "rm", name));
        }
    }
}
