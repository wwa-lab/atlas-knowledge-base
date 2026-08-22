package com.atlas.knowledgebase.chat;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Environment-owned allow-list of classifications approved to cross the Chat boundary. */
@ConfigurationProperties(prefix = "atlas.chat.classification")
public class ChatClassificationProperties {

    private Set<String> approvedValues = new LinkedHashSet<>();

    public Set<String> getApprovedValues() {
        return Set.copyOf(approvedValues);
    }

    public void setApprovedValues(Set<String> approvedValues) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (approvedValues != null) {
            approvedValues.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .forEach(normalized::add);
        }
        this.approvedValues = normalized;
    }

    public boolean approved(String classification) {
        return classification != null && approvedValues.contains(classification.trim());
    }

    public void validate() {
        if (approvedValues.isEmpty()) {
            throw new IllegalStateException(
                    "At least one approved classification is required for the Chat boundary");
        }
    }
}
