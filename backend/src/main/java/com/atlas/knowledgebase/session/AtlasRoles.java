package com.atlas.knowledgebase.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/** Parses the JSON array stored on {@code atlas_user.roles}. */
public final class AtlasRoles {

    public static final String END_USER = "end_user";
    public static final String KB_OWNER = "kb_owner";
    public static final String ATLAS_ADMIN = "atlas_admin";
    public static final String CONNECTOR_OWNER = "connector_owner";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AtlasRoles() {}

    public static boolean has(AtlasUserRecord user, String role) {
        return parse(user.rolesJson()).contains(role);
    }

    public static List<String> parse(String rolesJson) {
        if (rolesJson == null || rolesJson.isBlank()) {
            return List.of(END_USER);
        }
        try {
            List<String> roles = MAPPER.readValue(rolesJson, new TypeReference<List<String>>() {});
            return roles == null ? List.of(END_USER) : List.copyOf(roles);
        } catch (Exception e) {
            return List.of(END_USER);
        }
    }
}
