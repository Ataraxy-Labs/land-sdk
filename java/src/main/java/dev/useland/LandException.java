package dev.useland;

import java.util.Map;

/** A non-zero exit of the CLI. {@link #answer} is the whole JSON object it printed. */
public class LandException extends RuntimeException {
    /** The CLI's exit code. */
    public final int code;
    /** The command that fixes it, or the call to make again. May be null. */
    public final String fix;
    /** The docs page for this error. May be null. */
    public final String docs;
    public final Map<String, Object> answer;

    LandException(String message, int code, String fix, String docs, Map<String, Object> answer) {
        super(message);
        this.code = code;
        this.fix = fix;
        this.docs = docs;
        this.answer = answer;
    }

    /** exit 1: some branch was refused, a local test failed, or an add was refused. The answer still holds what landed. */
    public static final class Refused extends LandException {
        Refused(String m, int c, String f, String d, Map<String, Object> a) {
            super(m, c, f, d, a);
        }
    }

    /** exit 2: a usage or local error, and fix is the command that fixes it. */
    public static final class Usage extends LandException {
        Usage(String m, int c, String f, String d, Map<String, Object> a) {
            super(m, c, f, d, a);
        }
    }

    /** exit 3: land is still working, so make the same call again to wait. */
    public static final class StillRunning extends LandException {
        StillRunning(String m, int c, String f, String d, Map<String, Object> a) {
            super(m, c, f, d, a);
        }
    }

    /** exit 4: land could not check (an engine or infrastructure error), so make the same call again. */
    public static final class CouldNotCheck extends LandException {
        CouldNotCheck(String m, int c, String f, String d, Map<String, Object> a) {
            super(m, c, f, d, a);
        }
    }

    /** Map an exit code and the CLI's JSON to the typed exception (for callers that run the CLI themselves). */
    public static LandException of(int code, Map<String, Object> answer) {
        String message = str(answer, "error");
        if (message == null) message = "land exited " + code;
        String fix = str(answer, "fix") != null ? str(answer, "fix") : str(answer, "again");
        String docs = str(answer, "docs");
        return switch (code) {
            case 1 -> new Refused(message, code, fix, docs, answer);
            case 2 -> new Usage(message, code, fix, docs, answer);
            case 3 -> new StillRunning(message, code, fix, docs, answer);
            case 4 -> new CouldNotCheck(message, code, fix, docs, answer);
            default -> new LandException(message, code, fix, docs, answer);
        };
    }

    private static String str(Map<String, Object> answer, String key) {
        return answer.get(key) instanceof String s ? s : null;
    }
}
