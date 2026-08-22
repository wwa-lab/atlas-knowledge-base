package com.atlas.knowledgebase.adapters;

import java.util.List;

/**
 * Provider-neutral Git Browse port. Real GitHub adapters implement this in TASK-020; MVP uses
 * {@link StubGitBrowse}. The request is registry-free so Browse does not depend on persistence
 * rows.
 */
public interface GitBrowse {

    Tree tree(Request request);

    Preview preview(Request request, String path);

    record Request(String bindingId, String sourceIdentityJson) {}

    record Entry(String path, String type) {}

    record Tree(String bindingId, List<Entry> entries, String originalUrl) {}

    record Preview(String bindingId, String path, String markdown, String originalUrl) {}
}
