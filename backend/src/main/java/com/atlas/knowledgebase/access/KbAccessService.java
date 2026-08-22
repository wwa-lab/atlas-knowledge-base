package com.atlas.knowledgebase.access;

import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.session.AtlasRoles;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import org.springframework.stereotype.Service;

/**
 * Current-user KB authorization used by catalog and Chat.
 *
 * <p>[ASSUMPTION] Without a membership table or provider re-auth, Owner and Atlas Admin are
 * content-authorized. Ordinary users may see Catalog request-path metadata but cannot Browse or
 * Chat. Real delegated ACL is TASK-020 / per-turn adapter authorize.
 */
@Service
public class KbAccessService {

    public boolean authorized(AtlasUserRecord user, LogicalKnowledgeBaseRecord kb) {
        if (user == null || kb == null || kb.ownerUserId() == null) {
            return false;
        }
        return user.userId().equals(kb.ownerUserId()) || AtlasRoles.has(user, AtlasRoles.ATLAS_ADMIN);
    }

    public boolean visible(AtlasUserRecord user, LogicalKnowledgeBaseRecord kb) {
        if (authorized(user, kb)) {
            return true;
        }
        if (!"active".equals(kb.lifecycle())) {
            return false;
        }
        return "catalog".equals(kb.discoverability());
    }

    /**
     * Chat retrieval/generation eligibility (FR-27): Active, Chat-ready, model-eligible, not
     * unavailable, and currently authorized.
     */
    public boolean chatEligible(AtlasUserRecord user, LogicalKnowledgeBaseRecord kb) {
        return authorized(user, kb)
                && "active".equals(kb.lifecycle())
                && "chat_ready".equals(kb.capability())
                && kb.modelEligible()
                && kb.health() != null
                && !"unavailable".equals(kb.health());
    }
}
