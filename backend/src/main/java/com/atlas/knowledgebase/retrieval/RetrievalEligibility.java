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
        return knowledgeBase != null
                && binding != null
                && "active".equals(knowledgeBase.lifecycle())
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
