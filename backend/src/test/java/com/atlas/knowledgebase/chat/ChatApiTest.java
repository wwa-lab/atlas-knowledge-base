package com.atlas.knowledgebase.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlas.knowledgebase.session.SessionProperties;
import com.atlas.knowledgebase.session.SessionService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ChatApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SessionProperties sessionProperties;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void resetModelEntitlement() {
        jdbcTemplate.update("UPDATE atlas_user SET model_entitled = 0");
    }

    @Test
    void unauthenticatedChatsAre401() throws Exception {
        mockMvc.perform(get("/api/v1/chats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REQUIRED"));
    }

    @Test
    void createAskCancelAndRetryStayIncompleteWhenCancelled() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId = activateDify(owner, "Chat Ready Dify");
        MvcResult created =
                mockMvc.perform(
                                post("/api/v1/chats")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.thread_id").exists())
                        .andReturn();
        String threadId = jsonString(created.getResponse().getContentAsString(), "thread_id");

        mockMvc.perform(get("/api/v1/chats").cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.last_valid_logical_kb_ids[0]").value(kbId));

        MvcResult ask =
                mockMvc.perform(
                                post("/api/v1/chats/" + threadId + "/messages")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.TEXT_EVENT_STREAM)
                                        .content("{\"question\":\"How do we rotate the gateway cert?\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        String assistantId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT message_id FROM chat_message
                        WHERE thread_id = ? AND message_role = 'assistant'
                        ORDER BY created_at DESC
                        """,
                        String.class,
                        threadId);
        mockMvc.perform(
                        post("/api/v1/chats/" + threadId + "/messages/" + assistantId + "/cancel")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("incomplete_cancelled"));
        mockMvc.perform(asyncDispatch(ask)).andExpect(status().isOk());
        String status =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM chat_message WHERE message_id = ?", String.class, assistantId);
        String answer =
                jdbcTemplate.queryForObject(
                        "SELECT answer_text FROM chat_message WHERE message_id = ?", String.class, assistantId);
        assertThat(status).isEqualTo("incomplete_cancelled");
        assertThat(answer).isNull();
        jdbcTemplate.update(
                "UPDATE logical_knowledge_base SET config_version = 2, classification = 'restricted' WHERE logical_kb_id = ?",
                kbId);
        String retriedBindingId =
                jdbcTemplate.queryForObject(
                        "SELECT binding_id FROM binding WHERE logical_kb_id = ?", String.class, kbId);
        jdbcTemplate.update(
                "UPDATE binding SET config_version = 2 WHERE binding_id = ?", retriedBindingId);

        MvcResult retry =
                mockMvc.perform(
                                post("/api/v1/chats/" + threadId + "/messages/" + assistantId + "/retry")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .accept(MediaType.TEXT_EVENT_STREAM))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        String retryBody =
                mockMvc.perform(asyncDispatch(retry)).andExpect(status().isOk()).andReturn().getResponse()
                        .getContentAsString();
        assertThat(retryBody).contains("insufficient");
        assertThat(retryBody.toLowerCase()).doesNotContain("secret");
        Integer completed =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM chat_message
                        WHERE thread_id = ? AND message_role = 'assistant' AND status = 'completed'
                        """,
                        Integer.class,
                        threadId);
        assertThat(completed).isEqualTo(1);
        String retriedVersions =
                jdbcTemplate.queryForObject(
                        "SELECT config_versions FROM chat_message WHERE message_id = ?",
                        String.class,
                        assistantId);
        assertThat(retriedVersions).contains("\"" + kbId + "\":2");
        assertThat(retriedVersions).contains("\"" + retriedBindingId + "\":2");
        String retriedClassification =
                jdbcTemplate.queryForObject(
                        "SELECT classification FROM chat_message WHERE message_id = ?",
                        String.class,
                        assistantId);
        assertThat(retriedClassification).isEqualTo("restricted");
        assertThat(retryBody).contains("restricted");
        mockMvc.perform(
                        post("/api/v1/chats/" + threadId + "/scope")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MODE_REQUIRED"));
        mockMvc.perform(
                        post("/api/v1/chats/" + threadId + "/scope")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"],\"mode\":\"branch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branched_from_thread_id").value(threadId));
    }

    @Test
    void browseOnlyGitCannotEnterChat() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId = activateGitBrowseOnly(owner, "Browse Git");
        mockMvc.perform(
                        post("/api/v1/chats")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("NOT_CHAT_READY"));
    }

    @Test
    void moreThanFiveKnowledgeBasesIs422() throws Exception {
        LoggedIn owner = loginOwner();
        mockMvc.perform(
                        post("/api/v1/chats")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"logical_kb_ids\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SCOPE_LIMIT"));
    }

    @Test
    void mixedClassificationsFailClosedUntilAnOrderingPolicyIsApproved() throws Exception {
        LoggedIn owner = loginOwner();
        String internalKb = activateDify(owner, "Internal Chat");
        String restrictedKb = activateDify(owner, "Restricted Chat");
        jdbcTemplate.update(
                "UPDATE logical_knowledge_base SET classification = 'restricted' WHERE logical_kb_id = ?",
                restrictedKb);

        mockMvc.perform(
                        post("/api/v1/chats")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"logical_kb_ids\":[\""
                                                + internalKb
                                                + "\",\""
                                                + restrictedKb
                                                + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CLASSIFICATION_MISMATCH"));
    }

    @Test
    void unapprovedClassificationFailsClosedAtChatCreation() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId = activateDify(owner, "Unapproved Create Classification");
        jdbcTemplate.update(
                "UPDATE logical_knowledge_base SET classification = 'unknown' WHERE logical_kb_id = ?",
                kbId);

        mockMvc.perform(
                        post("/api/v1/chats")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CLASSIFICATION_UNAPPROVED"));
    }

    @Test
    void unapprovedClassificationFailsClosedWhenAskRevalidatesScope() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId = activateDify(owner, "Unapproved Ask Classification");
        String threadId =
                jsonString(
                        mockMvc.perform(
                                        post("/api/v1/chats")
                                                .cookie(owner.session())
                                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "thread_id");
        jdbcTemplate.update(
                "UPDATE logical_knowledge_base SET classification = 'unknown' WHERE logical_kb_id = ?",
                kbId);

        mockMvc.perform(
                        post("/api/v1/chats/" + threadId + "/messages")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.TEXT_EVENT_STREAM)
                                .content("{\"question\":\"What changed?\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CLASSIFICATION_UNAPPROVED"));
    }

    @Test
    void retryFailsClosedWhenASelectedClassificationBecomesUnapproved() throws Exception {
        LoggedIn owner = loginOwner();
        String firstKb = activateDify(owner, "Retry Classification One");
        String secondKb = activateDify(owner, "Retry Classification Two");
        String threadId =
                jsonString(
                        mockMvc.perform(
                                        post("/api/v1/chats")
                                                .cookie(owner.session())
                                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"logical_kb_ids\":[\""
                                                                + firstKb
                                                                + "\",\""
                                                                + secondKb
                                                                + "\"]}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "thread_id");
        MvcResult ask =
                mockMvc.perform(
                                post("/api/v1/chats/" + threadId + "/messages")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.TEXT_EVENT_STREAM)
                                        .content("{\"question\":\"What changed?\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        String assistantId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT message_id FROM chat_message
                        WHERE thread_id = ? AND message_role = 'assistant'
                        """,
                        String.class,
                        threadId);
        mockMvc.perform(
                        post("/api/v1/chats/" + threadId + "/messages/" + assistantId + "/cancel")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(asyncDispatch(ask)).andExpect(status().isOk());
        jdbcTemplate.update(
                "UPDATE logical_knowledge_base SET classification = 'unknown' WHERE logical_kb_id = ?",
                secondKb);

        mockMvc.perform(
                        post("/api/v1/chats/" + threadId + "/messages/" + assistantId + "/retry")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CLASSIFICATION_UNAPPROVED"));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM chat_message WHERE message_id = ?",
                                String.class,
                                assistantId))
                .isEqualTo("incomplete_cancelled");
    }

    @Test
    void restoreLastValidScopeWhenCreateOmitsIds() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId = activateDify(owner, "Restore Chat KB");
        mockMvc.perform(
                        post("/api/v1/chats")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(
                        post("/api/v1/chats")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.logical_kb_ids[0]").value(kbId));
    }

    @Test
    void completedRetryIsIdempotent() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId = activateDify(owner, "Idempotent Chat");
        String threadId =
                jsonString(
                        mockMvc.perform(
                                        post("/api/v1/chats")
                                                .cookie(owner.session())
                                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "thread_id");
        MvcResult ask =
                mockMvc.perform(
                                post("/api/v1/chats/" + threadId + "/messages")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.TEXT_EVENT_STREAM)
                                        .content("{\"question\":\"What is the runbook?\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        String completedBody =
                mockMvc.perform(asyncDispatch(ask))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String assistantId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT message_id FROM chat_message
                        WHERE thread_id = ? AND message_role = 'assistant' AND status = 'completed'
                        """,
                        String.class,
                        threadId);
        Integer citationCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM citation WHERE message_id = ?",
                        Integer.class,
                        assistantId);
        assertThat(citationCount).isEqualTo(2);
        String firstCitationId =
                jdbcTemplate.queryForObject(
                        "SELECT MIN(citation_id) FROM citation WHERE message_id = ?",
                        String.class,
                        assistantId);
        assertThat(completedBody).contains(firstCitationId).contains("\"title\"");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT binding_set FROM chat_message WHERE message_id = ?",
                                String.class,
                                assistantId))
                .contains("\"binding_role\":\"canonical\"");
        MvcResult retry =
                mockMvc.perform(
                                post("/api/v1/chats/" + threadId + "/messages/" + assistantId + "/retry")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .accept(MediaType.TEXT_EVENT_STREAM))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        String replayBody =
                mockMvc.perform(asyncDispatch(retry))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(replayBody).contains(firstCitationId).contains("\"citations\"");
        Integer completed =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM chat_message
                        WHERE thread_id = ? AND message_role = 'assistant' AND status = 'completed'
                        """,
                        Integer.class,
                        threadId);
        assertThat(completed).isEqualTo(1);
    }

    @Test
    void evidenceDrawerReauthorizesAndOpensOnlyThePersistedFixtureVersion() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId =
                activateDify(
                        owner,
                        "Evidence Drawer",
                        """
                        {
                          "dataset_id": "ds_1",
                          "original_version_mapping": {"doc_1": "v1"},
                          "atlas_fixture": true,
                          "evidence_authorization_fixture": "authorized",
                          "evidence_resolution_fixture": "ok"
                        }
                        """);
        String threadId =
                jsonString(
                        mockMvc.perform(
                                        post("/api/v1/chats")
                                                .cookie(owner.session())
                                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "thread_id");
        MvcResult ask =
                mockMvc.perform(
                                post("/api/v1/chats/" + threadId + "/messages")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.TEXT_EVENT_STREAM)
                                        .content("{\"question\":\"Show the exact source.\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        mockMvc.perform(asyncDispatch(ask)).andExpect(status().isOk());
        String citationId =
                jdbcTemplate.queryForObject(
                        "SELECT MIN(citation_id) FROM citation c JOIN chat_message m ON m.message_id = c.message_id WHERE m.thread_id = ?",
                        String.class,
                        threadId);
        String bindingId =
                jdbcTemplate.queryForObject(
                        "SELECT binding_id FROM citation WHERE citation_id = ?",
                        String.class,
                        citationId);

        mockMvc.perform(get("/api/v1/citations/" + citationId).cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citation_id").value(citationId))
                .andExpect(jsonPath("$.logical_kb_name").value("Evidence Drawer"))
                .andExpect(jsonPath("$.binding_role").value("canonical"))
                .andExpect(jsonPath("$.verification_mode").value("fixture"))
                .andExpect(jsonPath("$.provider_verified").value(false))
                .andExpect(jsonPath("$.open_original_action.path")
                        .value("/api/v1/citations/" + citationId + "/open-original"));

        mockMvc.perform(
                        post("/api/v1/citations/" + citationId + "/open-original")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolve_status").value("ok"))
                .andExpect(jsonPath("$.verification_mode").value("fixture"))
                .andExpect(jsonPath("$.provider_verified").value(false))
                .andExpect(jsonPath("$.navigation_url")
                        .value(org.hamcrest.Matchers.startsWith("https://evidence-fixture.invalid/")));

        mockMvc.perform(
                        post("/api/v1/citations/" + citationId + "/open-original")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"navigation_url\":\"https://attacker.invalid\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("EVIDENCE_OPEN_BODY_INVALID"));

        mockMvc.perform(
                        post("/api/v1/citations/" + citationId + "/open-original")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, "invalid-csrf"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_MISMATCH"));
        mockMvc.perform(
                        post("/api/v1/citations/" + citationId + "/open-original")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{"))
                .andExpect(status().isBadRequest());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM audit_event WHERE user_id = ? AND action = 'evidence_open'",
                                Integer.class,
                                owner.userId()))
                .isEqualTo(4);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM audit_event WHERE user_id = ? AND action = 'evidence_open' AND error_category = 'authorization' AND evidence_locator_ids IS NULL",
                                Integer.class,
                                owner.userId()))
                .isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM audit_event WHERE user_id = ? AND action = 'evidence_open' AND error_category = 'validation' AND evidence_locator_ids IS NULL",
                                Integer.class,
                                owner.userId()))
                .isEqualTo(2);

        jdbcTemplate.update(
                "INSERT INTO atlas_user (user_id, sso_subject, display_name, email, roles, model_entitled, created_at, updated_at) VALUES ('usr_other_evidence', 'other-evidence', 'Other', 'other@localhost', '[\"end_user\"]', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbcTemplate.update(
                "UPDATE chat_thread SET user_id = 'usr_other_evidence' WHERE thread_id = ?", threadId);
        mockMvc.perform(get("/api/v1/citations/" + citationId).cookie(owner.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EVIDENCE_NOT_FOUND"));
        jdbcTemplate.update(
                "UPDATE chat_thread SET user_id = ? WHERE thread_id = ?", owner.userId(), threadId);

        jdbcTemplate.update(
                "UPDATE binding SET source_identity = ? WHERE binding_id = ?",
                """
                {"dataset_id":"ds_1","atlas_fixture":true,
                 "evidence_authorization_fixture":"authorized",
                 "evidence_resolution_fixture":"unavailable"}
                """,
                bindingId);
        mockMvc.perform(
                        post("/api/v1/citations/" + citationId + "/open-original")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("EVIDENCE_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.details.verification_mode").value("fixture"));

        jdbcTemplate.update(
                "UPDATE binding SET source_identity = ? WHERE binding_id = ?",
                """
                {"dataset_id":"ds_1","atlas_fixture":true,
                 "evidence_authorization_fixture":"denied",
                 "evidence_resolution_fixture":"ok"}
                """,
                bindingId);
        mockMvc.perform(
                        post("/api/v1/citations/" + citationId + "/open-original")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EVIDENCE_ACCESS_DENIED"));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM audit_event WHERE user_id = ? AND action = 'authorization_denied' AND details IS NULL",
                                Integer.class,
                                owner.userId()))
                .isEqualTo(1);
    }

    @Test
    void ordinaryTimeoutIsDisclosedPartialCoverage() throws Exception {
        LoggedIn owner = loginOwner();
        String okKb = activateDify(owner, "Partial Ok");
        String slowKb =
                activateDify(
                        owner,
                        "Partial Slow",
                        """
                        {"dataset_id":"ds_slow","original_version_mapping":{"doc_1":"v1"},"retrieval_fixture":"timeout"}
                        """);
        String threadId =
                jsonString(
                        mockMvc.perform(
                                        post("/api/v1/chats")
                                                .cookie(owner.session())
                                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"logical_kb_ids\":[\""
                                                                + okKb
                                                                + "\",\""
                                                                + slowKb
                                                                + "\"]}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "thread_id");
        MvcResult ask =
                mockMvc.perform(
                                post("/api/v1/chats/" + threadId + "/messages")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.TEXT_EVENT_STREAM)
                                        .content("{\"question\":\"How do we rotate the gateway cert?\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        String body =
                mockMvc.perform(asyncDispatch(ask)).andExpect(status().isOk()).andReturn().getResponse()
                        .getContentAsString();
        assertThat(body).contains("timed_out");
        assertThat(body).contains("successful");
        assertThat(body).doesNotContain("Local retrieval fixture");
        assertThat(body.toLowerCase()).doesNotContain("secret");
        String slowBinding =
                jdbcTemplate.queryForObject(
                        "SELECT binding_id FROM binding WHERE logical_kb_id = ?", String.class, slowKb);
        assertThat(body).contains(slowBinding);
    }

    @Test
    void cancelInterruptsProviderRetrievalForANewAsk() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId =
                activateDify(
                        owner,
                        "Cancellable Retrieval",
                        "{\"dataset_id\":\"ds_cancel\",\"original_version_mapping\":{\"doc_1\":\"v1\"},\"retrieval_fixture\":\"cancel_wait\"}");
        String threadId =
                jsonString(
                        mockMvc.perform(
                                        post("/api/v1/chats")
                                                .cookie(owner.session())
                                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "thread_id");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<MvcResult> pendingAsk =
                    executor.submit(
                            () ->
                                    mockMvc.perform(
                                                    post("/api/v1/chats/" + threadId + "/messages")
                                                            .cookie(owner.session())
                                                            .header(
                                                                    SessionService.CSRF_HEADER,
                                                                    owner.csrf())
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .accept(MediaType.TEXT_EVENT_STREAM)
                                                            .content(
                                                                    "{\"question\":\"Cancel this retrieval\"}"))
                                            .andReturn());
            String assistantId = awaitAssistantId(threadId);

            mockMvc.perform(
                            post(
                                            "/api/v1/chats/"
                                                    + threadId
                                                    + "/messages/"
                                                    + assistantId
                                                    + "/cancel")
                                    .cookie(owner.session())
                                    .header(SessionService.CSRF_HEADER, owner.csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("incomplete_cancelled"));

            MvcResult ask = pendingAsk.get(2, TimeUnit.SECONDS);
            assertThat(ask.getRequest().isAsyncStarted()).isTrue();
            mockMvc.perform(asyncDispatch(ask)).andExpect(status().isOk());
        }

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM chat_message WHERE thread_id = ? AND message_role = 'assistant'",
                                String.class,
                                threadId))
                .isEqualTo("incomplete_cancelled");
    }

    @Test
    void allTimeoutsReturnRetrievalErrorWithoutGeneratingAnAnswer() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId =
                activateDify(
                        owner,
                        "All Timeout",
                        "{\"dataset_id\":\"ds_timeout\",\"original_version_mapping\":{\"doc_1\":\"v1\"},\"retrieval_fixture\":\"timeout\"}");
        String bindingId =
                jdbcTemplate.queryForObject(
                        "SELECT binding_id FROM binding WHERE logical_kb_id = ?", String.class, kbId);
        String threadId =
                jsonString(
                        mockMvc.perform(
                                        post("/api/v1/chats")
                                                .cookie(owner.session())
                                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "thread_id");

        String stream = askAndAwaitStreamError(owner, threadId, "What is the runbook?");
        assertThat(stream)
                .contains("event:error")
                .contains("\"category\":\"retrieval\"")
                .contains("\"code\":\"NO_GROUNDED_EVIDENCE\"")
                .contains("\"next_step\":\"retry_or_change_scope\"")
                .contains(bindingId);

        Integer messages =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM chat_message WHERE thread_id = ?", Integer.class, threadId);
        assertThat(messages).isEqualTo(2);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM chat_message WHERE thread_id = ? AND message_role = 'assistant'",
                                String.class,
                                threadId))
                .isEqualTo("failed");
    }

    @Test
    void streamedRetrievalErrorUsesProcessingRequestId() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId =
                activateDify(
                        owner,
                        "Correlated Timeout",
                        "{\"dataset_id\":\"ds_correlated_timeout\",\"original_version_mapping\":{\"doc_1\":\"v1\"},\"retrieval_fixture\":\"timeout\"}");
        String threadId =
                jsonString(
                        mockMvc.perform(
                                        post("/api/v1/chats")
                                                .cookie(owner.session())
                                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "thread_id");

        String stream = askAndAwaitStreamError(owner, threadId, "What is the runbook?");

        assertThat(requestIdForEvent(stream, "error"))
                .isEqualTo(requestIdForEvent(stream, "processing"));
    }

    @Test
    void securityRetrievalSuspendsTheKnowledgeBase() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId =
                activateDify(
                        owner,
                        "Security Fail KB",
                        "{\"dataset_id\":\"ds_sec\",\"original_version_mapping\":{\"doc_1\":\"v1\"},\"retrieval_fixture\":\"security\"}");
        String threadId =
                jsonString(
                        mockMvc.perform(
                                        post("/api/v1/chats")
                                                .cookie(owner.session())
                                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "thread_id");
        String stream =
                askAndAwaitStreamError(owner, threadId, "How do we rotate the gateway cert?");
        assertThat(stream)
                .contains("event:error")
                .contains("\"category\":\"authorization\"")
                .contains("\"code\":\"KB_SECURITY_FAILURE\"");
        String lifecycle =
                jdbcTemplate.queryForObject(
                        "SELECT lifecycle FROM logical_knowledge_base WHERE logical_kb_id = ?",
                        String.class,
                        kbId);
        assertThat(lifecycle).isEqualTo("suspended");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void quotaFailureStreamsActionableRetryAfterWithoutCallingTheModel() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId =
                activateDify(
                        owner,
                        "Quota Limited KB",
                        "{\"dataset_id\":\"ds_quota\",\"original_version_mapping\":{\"doc_1\":\"v1\"},\"retrieval_fixture\":\"quota\"}");
        String bindingId =
                jdbcTemplate.queryForObject(
                        "SELECT binding_id FROM binding WHERE logical_kb_id = ?", String.class, kbId);
        String threadId =
                jsonString(
                        mockMvc.perform(
                                        post("/api/v1/chats")
                                                .cookie(owner.session())
                                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "thread_id");

        String stream = askAndAwaitStreamError(owner, threadId, "What is the runbook?");

        assertThat(stream)
                .contains("event:error")
                .contains("\"category\":\"quota\"")
                .contains("\"code\":\"PROVIDER_QUOTA_EXHAUSTED\"")
                .contains("\"retry_after\":{\"" + bindingId + "\":\"PT1S\"}");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM chat_message WHERE thread_id = ? AND message_role = 'assistant'",
                                String.class,
                                threadId))
                .isEqualTo("failed");
    }

    @Test
    void featureDisabledBindingFailsClosedWithTruthfulCoverage() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId = activateDify(owner, "Feature Disabled KB");
        String bindingId =
                jdbcTemplate.queryForObject(
                        "SELECT binding_id FROM binding WHERE logical_kb_id = ?", String.class, kbId);
        String threadId =
                jsonString(
                        mockMvc.perform(
                                        post("/api/v1/chats")
                                                .cookie(owner.session())
                                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "thread_id");
        jdbcTemplate.update("UPDATE binding SET feature_flag = 0 WHERE binding_id = ?", bindingId);

        String stream = askAndAwaitStreamError(owner, threadId, "What is the runbook?");
        assertThat(stream)
                .contains("event:error")
                .contains("\"category\":\"retrieval\"")
                .contains("\"code\":\"KB_BINDING_UNAVAILABLE\"")
                .contains(bindingId);

        Integer messages =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM chat_message WHERE thread_id = ?", Integer.class, threadId);
        assertThat(messages).isEqualTo(2);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM chat_message WHERE thread_id = ? AND message_role = 'assistant'",
                                String.class,
                                threadId))
                .isEqualTo("failed");
    }

    @Test
    void bindingAccessFailureNamesTheActionableKnowledgeBaseAndBinding() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId =
                activateDify(
                        owner,
                        "Binding Denied KB",
                        "{\"dataset_id\":\"ds_denied\",\"original_version_mapping\":{\"doc_1\":\"v1\"},\"retrieval_fixture\":\"binding_denied\"}");
        String bindingId =
                jdbcTemplate.queryForObject(
                        "SELECT binding_id FROM binding WHERE logical_kb_id = ?", String.class, kbId);
        String threadId =
                jsonString(
                        mockMvc.perform(
                                        post("/api/v1/chats")
                                                .cookie(owner.session())
                                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "thread_id");

        String stream = askAndAwaitStreamError(owner, threadId, "What is the runbook?");
        assertThat(stream)
                .contains("event:error")
                .contains("\"category\":\"authorization\"")
                .contains("\"code\":\"KB_BINDING_ACCESS_MISSING\"")
                .contains(kbId)
                .contains(bindingId);
    }

    private String askAndAwaitStreamError(LoggedIn owner, String threadId, String question)
            throws Exception {
        MvcResult pending =
                mockMvc.perform(
                                post("/api/v1/chats/" + threadId + "/messages")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.TEXT_EVENT_STREAM)
                                        .content("{\"question\":\"" + question + "\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        return mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static String requestIdForEvent(String stream, String eventName) {
        int eventStart = stream.indexOf("event:" + eventName);
        if (eventStart < 0) {
            throw new AssertionError("Missing SSE event: " + eventName);
        }
        int dataStart = stream.indexOf("data:", eventStart);
        if (dataStart < 0) {
            throw new AssertionError("Missing SSE data for event: " + eventName);
        }
        int dataEnd = stream.indexOf('\n', dataStart);
        String data =
                stream.substring(
                        dataStart + "data:".length(), dataEnd < 0 ? stream.length() : dataEnd);
        return jsonString(data, "request_id");
    }

    private String activateDify(LoggedIn owner, String name) throws Exception {
        return activateDify(
                owner,
                name,
                """
                {
                  "dataset_id": "ds_1",
                  "original_version_mapping": {"doc_1": "v1"}
                }
                """);
    }

    private String awaitAssistantId(String threadId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            List<String> ids =
                    jdbcTemplate.queryForList(
                            "SELECT message_id FROM chat_message WHERE thread_id = ? AND message_role = 'assistant'",
                            String.class,
                            threadId);
            if (!ids.isEmpty()) {
                return ids.getFirst();
            }
            Thread.sleep(10);
        }
        throw new AssertionError("assistant message was not reserved before retrieval");
    }

    private String activateDify(LoggedIn owner, String name, String sourceIdentityJson) throws Exception {
        String logicalKbId = createDraft(owner, name, "private");
        mockMvc.perform(
                        patch("/api/v1/knowledge-bases/drafts/" + logicalKbId)
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "config_version": 1,
                                          "bindings": [{
                                            "provider_profile": "dify",
                                            "role": "canonical",
                                            "source_identity": %s
                                          }]
                                        }
                                        """
                                                .formatted(sourceIdentityJson.trim())))
                .andExpect(status().isOk());
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/drafts/" + logicalKbId + "/content-audit")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf()))
                .andExpect(status().isOk());
        LoggedIn admin = loginAdminKeepingOwner();
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/" + logicalKbId + "/activate")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capability").value("chat_ready"));
        return logicalKbId;
    }

    private String activateGitBrowseOnly(LoggedIn owner, String name) throws Exception {
        String logicalKbId = createDraft(owner, name, "private");
        mockMvc.perform(
                        patch("/api/v1/knowledge-bases/drafts/" + logicalKbId)
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "config_version": 1,
                                          "bindings": [{
                                            "provider_profile": "git_markdown",
                                            "role": "canonical",
                                            "source_identity": {"repo": "org/runbooks", "commit": "abc123def"}
                                          }]
                                        }
                                        """))
                .andExpect(status().isOk());
        LoggedIn admin = loginAdminKeepingOwner();
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/" + logicalKbId + "/activate")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capability").value("browse_only"));
        return logicalKbId;
    }

    private String createDraft(LoggedIn owner, String name, String discoverability) throws Exception {
        MvcResult created =
                mockMvc.perform(
                                post("/api/v1/knowledge-bases/drafts")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "name": "%s",
                                                  "description": "Chat fixture",
                                                  "discoverability": "%s",
                                                  "purpose": "support",
                                                  "classification": "internal",
                                                  "model_eligible": true
                                                }
                                                """
                                                        .formatted(name, discoverability)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return jsonString(created.getResponse().getContentAsString(), "logical_kb_id");
    }

    private LoggedIn loginOwner() throws Exception {
        LoggedIn owner = login("kb_owner");
        jdbcTemplate.update("UPDATE atlas_user SET model_entitled = 1 WHERE user_id = ?", owner.userId());
        return owner;
    }

    private LoggedIn loginAdminKeepingOwner() throws Exception {
        LoggedIn admin = login("atlas_admin");
        jdbcTemplate.update(
                "UPDATE atlas_user SET roles = ? WHERE user_id = ?",
                "[\"end_user\",\"kb_owner\",\"atlas_admin\"]",
                admin.userId());
        jdbcTemplate.update("UPDATE atlas_user SET model_entitled = 1 WHERE user_id = ?", admin.userId());
        return admin;
    }

    private LoggedIn login(String extraRole) throws Exception {
        MvcResult start =
                mockMvc.perform(get("/api/v1/auth/sso/start")).andExpect(status().isFound()).andReturn();
        Cookie state = start.getResponse().getCookie(sessionProperties.ssoStateCookieName());
        MvcResult callback =
                mockMvc.perform(get(start.getResponse().getRedirectedUrl()).cookie(state))
                        .andExpect(status().isFound())
                        .andReturn();
        Cookie session = callback.getResponse().getCookie(sessionProperties.getCookieName());
        String me =
                mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String userId = jsonString(me, "user_id");
        String roles =
                "kb_owner".equals(extraRole)
                        ? "[\"end_user\",\"kb_owner\"]"
                        : "atlas_admin".equals(extraRole)
                                ? "[\"end_user\",\"atlas_admin\"]"
                                : "[\"end_user\"]";
        jdbcTemplate.update("UPDATE atlas_user SET roles = ? WHERE user_id = ?", roles, userId);
        String csrfJson =
                mockMvc.perform(get("/api/v1/auth/csrf").cookie(session))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String csrf = csrfJson.replaceAll(".*\"csrf_token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        return new LoggedIn(session, csrf, userId);
    }

    private static String jsonString(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        int from = start + needle.length();
        int end = json.indexOf('"', from);
        return json.substring(from, end);
    }

    private record LoggedIn(Cookie session, String csrf, String userId) {}
}
