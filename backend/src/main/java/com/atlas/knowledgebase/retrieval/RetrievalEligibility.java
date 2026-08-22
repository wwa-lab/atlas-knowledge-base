package com.atlas.knowledgebase.retrieval;

import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;

/** Shared runtime eligibility predicate used by retrieval and governance previews. */
public final class RetrievalEligibility {

    private RetrievalEligibility() {}

    public static boolean isEligible(
            LogicalKnowledgeBaseRecord knowledgeBase,
            BindingRecord binding,
            RetrievalProperties properties) {
        return isEligible(knowledgeBase, binding, properties, true);
    }

    /**
     * Evaluates binding/runtime gates while optionally ignoring the current lifecycle. Governance
     * uses the latter form for a Suspended KB to decide whether another safe binding would keep the
     * KB available for remediation and re-activation rather than terminal retirement.
     */
    public static boolean isEligible(
            LogicalKnowledgeBaseRecord knowledgeBase,
            BindingRecord binding,
            RetrievalProperties properties,
            boolean requireActiveLifecycle) {
        return knowledgeBase != null
                && binding != null
                && (!requireActiveLifecycle || "active".equals(knowledgeBase.lifecycle()))
                && "chat_ready".equals(knowledgeBase.capability())
                && knowledgeBase.modelEligible()
                && knowledgeBase.health() != null
                && !"unavailable".equals(knowledgeBase.health())
                && !knowledgeBase.freshnessRequired()
                && binding.enabled()
                && !binding.killSwitch()
                && binding.featureFlag()
                && properties.enabled(binding.providerProfile())
                && binding.health() != null
                && !"unavailable".equals(binding.health());
    }
}
