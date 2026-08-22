package com.atlas.knowledgebase.evidence;

import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Audits authenticated open-original attempts that terminate before controller invocation. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class EvidenceRequestAuditFilter extends OncePerRequestFilter {

    static final String AUDITED_ATTRIBUTE =
            EvidenceRequestAuditFilter.class.getName() + ".audited";

    private final EvidenceAuditService audit;

    public EvidenceRequestAuditFilter(EvidenceAuditService audit) {
        this.audit = audit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !path.startsWith("/api/v1/citations/")
                || !path.endsWith("/open-original");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            auditBoundaryFailure(request, response);
        }
    }

    private void auditBoundaryFailure(
            HttpServletRequest request, HttpServletResponse response) {
        if (Boolean.TRUE.equals(request.getAttribute(AUDITED_ATTRIBUTE))) {
            return;
        }
        Object currentUser = request.getAttribute(SessionService.REQUEST_USER_ATTRIBUTE);
        if (!(currentUser instanceof AtlasUserRecord user)) {
            return;
        }
        int statusCode = response.getStatus();
        String category = category(statusCode);
        String authorization = statusCode == HttpServletResponse.SC_FORBIDDEN
                ? "deny"
                : "not_evaluated";
        String status = statusCode == HttpServletResponse.SC_FORBIDDEN
                ? "denied"
                : statusCode >= 500 ? "unknown" : "invalid";
        audit.generic(
                user.userId(),
                "evidence_open",
                authorization,
                status,
                category);
        markAudited(request);
    }

    static void markAudited(HttpServletRequest request) {
        request.setAttribute(AUDITED_ATTRIBUTE, true);
    }

    private static String category(int statusCode) {
        if (statusCode == HttpServletResponse.SC_FORBIDDEN) {
            return "authorization";
        }
        if (statusCode >= 500) {
            return "unknown";
        }
        return "validation";
    }
}
