package com.atlas.knowledgebase.security;

import com.atlas.knowledgebase.adapters.Retriever;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Read-only boundary for provider-returned content. Retrieved text is data, never an instruction
 * source: suspicious embedded instructions and active markup are classified before citation or
 * model dispatch and callers can drop the affected evidence without retaining its body.
 */
public final class UntrustedContentContainment {

    private static final int MAX_FIELD_CHARS = 16_384;
    private static final List<Rule> RULES =
            List.of(
                    new Rule(
                            "active_markup",
                            Pattern.compile(
                                    "(?is)<\\s*(?:script|iframe|object|embed|style)\\b|"
                                            + "(?:\\b(?:href|src)\\s*=\\s*[\\\"']?\\s*javascript\\s*:|"
                                            + "(?:^|[\\s\\\"'(<])javascript\\s*:\\s*(?:/{1,2}|"
                                            + "[a-z_$][\\w$]*(?:\\.[a-z_$][\\w$]*)*\\s*(?:\\(|=|;)))|"
                                            + "<[^>]{0,2048}\\bon[a-z]+\\s*=|"
                                            + "\\{\\{\\s*/?\\s*(?:exec|execute|run|shell|command|eval|include|import|system|tool|function)\\b[^}]*\\}\\}")),
                    new Rule(
                            "embedded_instruction",
                            Pattern.compile(
                                    "(?is)\\b(?:ignore|disregard|forget|override)\\b.{0,120}"
                                            + "\\b(?:previous|prior|system|developer|user|all)\\b.{0,120}"
                                            + "\\b(?:instructions?|policy|rules?)\\b")),
                    new Rule(
                            "embedded_instruction",
                            Pattern.compile(
                                    "(?is)\\b(?:assistant|agent|model|system)\\s*[,!:]\\s*"
                                            + "(?:please\\s+)?(?:reveal|disclose|exfiltrate|print|dump)\\b.{0,100}"
                                            + "\\b(?:system\\s+prompt|secret|token|credential|password)\\b")),
                    new Rule(
                            "embedded_instruction",
                            Pattern.compile(
                                    "(?is)\\b(?:assistant|agent|model|system)\\s*[,!:]\\s*"
                                            + "(?:please\\s+)?(?:execute|run|invoke|call)\\b.{0,100}"
                                            + "\\b(?:tool|command|shell|script|function)\\b")),
                    new Rule(
                            "embedded_instruction",
                            Pattern.compile(
                                    "(?is)\\b(?:do not|don't|never)\\s+(?:follow|obey)\\b.{0,100}"
                                            + "\\b(?:policy|rules?|instructions?)\\b")));

    /** Inspects metadata and content without returning or logging the inspected source text. */
    public Decision inspect(Retriever.Hit hit) {
        if (hit == null) {
            return Decision.safe();
        }
        for (String field : fields(hit)) {
            if (field.length() > MAX_FIELD_CHARS) {
                return Decision.contained("field_too_large");
            }
            String normalized = normalize(field);
            if (normalized.length() > MAX_FIELD_CHARS) {
                return Decision.contained("field_too_large");
            }
            for (Rule rule : RULES) {
                if (rule.pattern().matcher(normalized).find()) {
                    return Decision.contained(rule.reason());
                }
            }
        }
        return Decision.safe();
    }

    private static List<String> fields(Retriever.Hit hit) {
        return List.of(
                safe(hit.canonicalSourceIdentity()),
                safe(hit.sourceUrl()),
                safe(hit.documentId()),
                safe(hit.title()),
                safe(hit.excerpt()),
                safe(hit.version()),
                safe(hit.locatorJson()),
                safe(hit.fingerprint()));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return normalized.replaceAll("\\p{Cf}", "");
    }

    private record Rule(String reason, Pattern pattern) {}

    public record Decision(boolean contained, String reason) {
        private static Decision safe() {
            return new Decision(false, null);
        }

        private static Decision contained(String reason) {
            return new Decision(true, reason);
        }
    }
}
