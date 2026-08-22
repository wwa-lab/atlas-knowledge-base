package com.atlas.knowledgebase.adapters;

import com.atlas.knowledgebase.registry.BindingRecord;
import java.util.Map;

/**
 * Provider-neutral Connection Test / Content Audit probe. Real adapters implement this in
 * TASK-019–021; MVP uses {@link StubSourceProbe}.
 */
public interface SourceProbe {

    Map<String, String> connectionChecks(BindingRecord binding);

    boolean connectionPassed(Map<String, String> checks);

    boolean hasOriginalVersionMapping(BindingRecord binding);

    boolean gitKbValidated(BindingRecord binding);

    AuditCounts auditCounts(BindingRecord binding);

    record AuditCounts(
            int total, int chatEligible, int excluded, Map<String, Integer> exclusionReasons) {}
}
