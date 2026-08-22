package com.atlas.knowledgebase.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlas.knowledgebase.access.KbAccessService;
import com.atlas.knowledgebase.adapters.EvidenceResolver;
import com.atlas.knowledgebase.chat.ChatMessageRecord;
import com.atlas.knowledgebase.chat.ChatMessageRepository;
import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.BindingRepository;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class EvidenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");
    private static final String LOCATOR =
            """
            {"repository":"org/repo","commit_sha":"abc1234","path":"a.md",
             "line_range":[1,2],"stable_source_id":"source_1","atlas_fixture":true,
             "move_mapping":{"moved_to_locator":{"repository":"org/repo",
             "commit_sha":"def5678","path":"b.md","line_range":[1,2],
             "stable_source_id":"source_1","atlas_fixture":true}}}
            """;
    private static final String SOURCE_IDENTITY =
            "{\"repo\":\"org/repo\",\"atlas_fixture\":true}";

    private CitationRepository citations;
    private ChatMessageRepository messages;
    private LogicalKnowledgeBaseRepository knowledgeBases;
    private BindingRepository bindings;
    private KbAccessService access;
    private EvidenceAuditService audit;
    private EvidenceResolver resolver;
    private AtlasUserRecord user;
    private CitationRecord citation;
    private EvidenceService service;

    @BeforeEach
    void setUp() {
        citations = mock(CitationRepository.class);
        messages = mock(ChatMessageRepository.class);
        knowledgeBases = mock(LogicalKnowledgeBaseRepository.class);
        bindings = mock(BindingRepository.class);
        access = mock(KbAccessService.class);
        audit = mock(EvidenceAuditService.class);
        resolver = mock(EvidenceResolver.class);
        user = new AtlasUserRecord("usr_1", "subject", "Owner", "owner@example.test", "[]", true, NOW, NOW);
        citation = completeCitation();
        when(resolver.providerProfiles()).thenReturn(Set.of("git_markdown"));
        service = serviceWith(List.of(resolver));
    }

    @Test
    void missingAndCrossUserCitationReturnsOnlyGenericNotFound() {
        when(citations.findOwnedByCitationId("cit_missing", user.userId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.drawer(user, "cit_missing"))
                .isInstanceOfSatisfying(
                        EvidenceException.class,
                        error -> {
                            assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
                            assertThat(error.code()).isEqualTo("EVIDENCE_NOT_FOUND");
                            assertThat(error.details()).isEmpty();
                        });
        verify(audit).generic(user.userId(), "evidence_view", "not_found", "not_found", "unavailable");
        verify(audit, never()).owned(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void legacyIncompleteCitationFailsClosedBeforeResolverDispatch() {
        CitationRecord incomplete =
                new CitationRecord(
                        citation.citationId(),
                        citation.messageId(),
                        citation.logicalKbId(),
                        citation.bindingId(),
                        citation.provider(),
                        citation.locatorJson(),
                        citation.versionLabel(),
                        citation.excerpt(),
                        null,
                        citation.owner(),
                        citation.classification(),
                        citation.sourceUpdatedAt(),
                        citation.atlasVerifiedAt(),
                        citation.resolveStatus());
        when(citations.findOwnedByCitationId(incomplete.citationId(), user.userId()))
                .thenReturn(Optional.of(incomplete));

        assertThatThrownBy(() -> service.drawer(user, incomplete.citationId()))
                .isInstanceOfSatisfying(
                        EvidenceException.class,
                        error -> {
                            assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                            assertThat(error.code()).isEqualTo("EVIDENCE_RESOLUTION_UNKNOWN");
                            assertThat(error.details())
                                    .containsEntry("verification_mode", "none")
                                    .containsEntry("provider_verified", false);
                        });
        verify(resolver, never()).authorize(org.mockito.ArgumentMatchers.any());
        verify(resolver, never()).resolve(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void currentAuthorizationDenialEmitsOperationAndDenialAudit() {
        arrangeOwnedCitation();
        when(access.authorized(user, kb())).thenReturn(false);

        assertThatThrownBy(() -> service.drawer(user, citation.citationId()))
                .isInstanceOfSatisfying(
                        EvidenceException.class,
                        error -> {
                            assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
                            assertThat(error.code()).isEqualTo("EVIDENCE_ACCESS_DENIED");
                        });
        verify(audit).owned(user.userId(), citation, "evidence_view", "deny", "denied", "authorization");
        verify(audit).owned(user.userId(), citation, "authorization_denied", "deny", "denied", "authorization");
        verify(resolver, never()).resolve(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fixtureMovedResponseNeverExposesTargetLocatorOrHash() throws Exception {
        arrangeOwnedCitation();
        when(access.authorized(user, kb())).thenReturn(true);
        when(resolver.authorize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(EvidenceResolver.AuthorizationResult.authorized());
        JsonNode moved = new ObjectMapper().readTree(LOCATOR).path("move_mapping").path("moved_to_locator");
        when(resolver.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(EvidenceResolver.Result.fixtureMoved(moved));

        assertThatThrownBy(() -> service.openOriginal(user, citation.citationId(), null))
                .isInstanceOfSatisfying(
                        EvidenceException.class,
                        error -> {
                            assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(error.code()).isEqualTo("EVIDENCE_MOVED");
                            assertThat(error.details())
                                    .containsEntry("provider", "git_markdown")
                                    .containsEntry("verification_mode", "fixture")
                                    .containsEntry("provider_verified", false)
                                    .containsEntry("stable_source_id", "source_1")
                                    .doesNotContainKeys(
                                            "moved_to_locator_id",
                                            "moved_to_locator",
                                            "navigation_url");
                        });
    }

    @Test
    void unknownProviderFailsClosedBeforeAnyResolution() {
        arrangeOwnedCitation();
        when(access.authorized(user, kb())).thenReturn(true);
        service = serviceWith(List.of());

        assertThatThrownBy(() -> service.drawer(user, citation.citationId()))
                .isInstanceOfSatisfying(
                        EvidenceException.class,
                        error -> assertThat(error.code()).isEqualTo("EVIDENCE_RESOLUTION_UNKNOWN"));
        verify(resolver, never()).authorize(org.mockito.ArgumentMatchers.any());
        verify(resolver, never()).resolve(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void invalidLiveMoveTargetFailsClosedWithProviderVerificationMode() throws Exception {
        citation = liveCitation();
        arrangeOwnedCitation(citation, liveBinding());
        when(access.authorized(user, kb())).thenReturn(true);
        when(resolver.authorize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(EvidenceResolver.AuthorizationResult.authorized());
        when(resolver.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        new EvidenceResolver.Result(
                                EvidenceResolver.Status.MOVED,
                                EvidenceResolver.VerificationMode.PROVIDER,
                                true,
                                null,
                                null,
                                Optional.of(
                                        new ObjectMapper()
                                                .readTree(
                                                        """
                                                        {"repository":"org/repo","commit_sha":"def5678",
                                                         "path":"b.md","line_range":[1,2],
                                                         "stable_source_id":"different"}
                                                        """))));

        assertThatThrownBy(() -> service.openOriginal(user, citation.citationId(), null))
                .isInstanceOfSatisfying(
                        EvidenceException.class,
                        error -> {
                            assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                            assertThat(error.code()).isEqualTo("EVIDENCE_RESOLUTION_UNKNOWN");
                            assertThat(error.details())
                                    .containsEntry("verification_mode", "provider")
                                    .containsEntry("provider_verified", false)
                                    .doesNotContainKey("moved_to_locator_id");
                        });
    }

    @Test
    void liveResolverExceptionRemainsAProviderInconclusiveFailure() {
        citation = liveCitation();
        arrangeOwnedCitation(citation, liveBinding());
        when(access.authorized(user, kb())).thenReturn(true);
        when(resolver.authorize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(EvidenceResolver.AuthorizationResult.authorized());
        when(resolver.resolve(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("provider unavailable"));

        assertThatThrownBy(() -> service.drawer(user, citation.citationId()))
                .isInstanceOfSatisfying(
                        EvidenceException.class,
                        error ->
                                assertThat(error.details())
                                        .containsEntry("verification_mode", "provider")
                                        .containsEntry("provider_verified", false));
    }

    @Test
    void liveAuthorizationExceptionFailsClosedAndAuditsTheView() {
        citation = liveCitation();
        arrangeOwnedCitation(citation, liveBinding());
        when(access.authorized(user, kb())).thenReturn(true);
        when(resolver.authorize(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("provider authorization unavailable"));

        assertThatThrownBy(() -> service.drawer(user, citation.citationId()))
                .isInstanceOfSatisfying(
                        EvidenceException.class,
                        error ->
                                assertThat(error.details())
                                        .containsEntry("verification_mode", "provider")
                                        .containsEntry("provider_verified", false));
        verify(audit).owned(user.userId(), citation, "evidence_view", "unknown", "unknown", "unknown");
        verify(resolver, never()).resolve(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void liveUnknownResolverModeIsNormalizedToProviderInconclusive() {
        citation = liveCitation();
        arrangeOwnedCitation(citation, liveBinding());
        when(access.authorized(user, kb())).thenReturn(true);
        when(resolver.authorize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(EvidenceResolver.AuthorizationResult.authorized());
        when(resolver.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(EvidenceResolver.Result.unknown(EvidenceResolver.VerificationMode.NONE));

        assertThatThrownBy(() -> service.drawer(user, citation.citationId()))
                .isInstanceOfSatisfying(
                        EvidenceException.class,
                        error ->
                                assertThat(error.details())
                                        .containsEntry("verification_mode", "provider")
                                        .containsEntry("provider_verified", false));
    }

    @Test
    void adapterReceivesUserScopedContextAndCannotMutateDrawerCoordinates() {
        arrangeOwnedCitation();
        when(access.authorized(user, kb())).thenReturn(true);
        when(resolver.authorize(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(
                        invocation -> {
                            EvidenceResolver.AuthorizationRequest request = invocation.getArgument(0);
                            assertThat(request.authorizationContext())
                                    .isEqualTo(
                                            new EvidenceResolver.AuthorizationContext(
                                                    user.userId(), "bnd_1", "app"));
                            ((ObjectNode) request.locator().locator())
                                    .put("path", "adapter-mutated.md");
                            ((ObjectNode) request.authoritativeSourceIdentity())
                                    .put("repo", "evil/repo");
                            return EvidenceResolver.AuthorizationResult.authorized();
                        });
        when(resolver.resolve(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(
                        invocation -> {
                            EvidenceResolver.Request request = invocation.getArgument(0);
                            assertThat(request.authorizationContext().userId())
                                    .isEqualTo(user.userId());
                            ((ObjectNode) request.locator().locator())
                                    .put("path", "resolver-mutated.md");
                            return EvidenceResolver.Result.fixtureOk(null);
                        });

        Map<String, Object> projection = service.drawer(user, citation.citationId());

        assertThat(((JsonNode) projection.get("locator")).path("path").asText())
                .isEqualTo("a.md");
    }

    private void arrangeOwnedCitation() {
        arrangeOwnedCitation(citation, binding());
    }

    private void arrangeOwnedCitation(
            CitationRecord ownedCitation, BindingRecord currentBinding) {
        when(citations.findOwnedByCitationId(ownedCitation.citationId(), user.userId()))
                .thenReturn(Optional.of(ownedCitation));
        when(knowledgeBases.findById(ownedCitation.logicalKbId())).thenReturn(Optional.of(kb()));
        when(bindings.findById(ownedCitation.bindingId())).thenReturn(Optional.of(currentBinding));
        when(messages.findById(ownedCitation.messageId())).thenReturn(Optional.of(message()));
    }

    private EvidenceService serviceWith(List<EvidenceResolver> availableResolvers) {
        ObjectMapper mapper = new ObjectMapper();
        return new EvidenceService(
                citations,
                messages,
                knowledgeBases,
                bindings,
                access,
                new EvidenceLocatorValidator(mapper),
                new EvidenceSourceContinuity(mapper),
                new EvidenceResolverRegistry(availableResolvers),
                new EvidenceNavigationPolicy(),
                audit,
                mapper);
    }

    private CitationRecord completeCitation() {
        return new CitationRecord(
                "cit_1",
                "msg_1",
                "lkb_1",
                "bnd_1",
                "git_markdown",
                LOCATOR,
                "abc1234",
                "Short exact excerpt",
                "Runbook",
                "Owner",
                "internal",
                null,
                NOW,
                "ok");
    }

    private CitationRecord liveCitation() {
        return new CitationRecord(
                "cit_1",
                "msg_1",
                "lkb_1",
                "bnd_1",
                "git_markdown",
                """
                {"repository":"org/repo","commit_sha":"abc1234","path":"a.md",
                 "line_range":[1,2],"stable_source_id":"source_1"}
                """,
                "abc1234",
                "Short exact excerpt",
                "Runbook",
                "Owner",
                "internal",
                null,
                NOW,
                "ok");
    }

    private LogicalKnowledgeBaseRecord kb() {
        return new LogicalKnowledgeBaseRecord(
                "lkb_1",
                "KB",
                "description",
                user.userId(),
                "private",
                "support",
                "internal",
                true,
                "chat_ready",
                "active",
                "healthy",
                1,
                null,
                false,
                null,
                NOW,
                NOW,
                NOW);
    }

    private BindingRecord binding() {
        return new BindingRecord(
                "bnd_1",
                "lkb_1",
                "git_markdown",
                SOURCE_IDENTITY,
                "canonical",
                "app",
                "healthy",
                true,
                false,
                true,
                "{}",
                "{}",
                "owner",
                "{}",
                1,
                NOW,
                NOW);
    }

    private BindingRecord liveBinding() {
        return new BindingRecord(
                "bnd_1",
                "lkb_1",
                "git_markdown",
                "{\"repo\":\"org/repo\"}",
                "canonical",
                "app",
                "healthy",
                true,
                false,
                true,
                "{}",
                "{}",
                "owner",
                "{}",
                1,
                NOW,
                NOW);
    }

    private ChatMessageRecord message() {
        return new ChatMessageRecord(
                "msg_1",
                "thr_1",
                "assistant",
                "completed",
                null,
                "answer",
                "[\"lkb_1\"]",
                "[{\"binding_id\":\"bnd_1\",\"binding_role\":\"canonical\"}]",
                "{}",
                "{}",
                null,
                "internal",
                "req_1",
                NOW,
                NOW);
    }
}
