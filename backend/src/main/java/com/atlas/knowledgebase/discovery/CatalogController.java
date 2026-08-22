package com.atlas.knowledgebase.discovery;

import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.CurrentRequestAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/knowledge-bases")
    public Map<String, Object> list(
            HttpServletRequest request,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String capability,
            @RequestParam(required = false) String lifecycle,
            @RequestParam(required = false) String health,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String freshness,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        return catalog.list(
                user,
                new CatalogService.CatalogQuery(
                        q, provider, capability, lifecycle, health, owner, freshness, cursor, limit));
    }

    @GetMapping("/knowledge-bases/{logicalKbId}")
    public Map<String, Object> detail(HttpServletRequest request, @PathVariable String logicalKbId) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        return catalog.detail(user, logicalKbId);
    }

    @GetMapping("/knowledge-bases/{logicalKbId}/browse/tree")
    public Map<String, Object> tree(HttpServletRequest request, @PathVariable String logicalKbId) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        return catalog.tree(user, logicalKbId);
    }

    @GetMapping("/knowledge-bases/{logicalKbId}/browse/preview")
    public Map<String, Object> preview(
            HttpServletRequest request,
            @PathVariable String logicalKbId,
            @RequestParam(required = false) String path) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        return catalog.preview(user, logicalKbId, path);
    }
}
