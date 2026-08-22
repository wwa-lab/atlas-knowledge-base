package com.atlas.knowledgebase.chat;

import com.atlas.knowledgebase.evidence.CitationAssembler;
import com.atlas.knowledgebase.evidence.CitationRecord;
import com.atlas.knowledgebase.evidence.CitationRepository;
import com.atlas.knowledgebase.retrieval.RetrievalTurn;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomic boundary for the winning assistant completion and its complete citation set. */
@Service
public class AssistantCompletionService {

    private final ChatMessageRepository messages;
    private final CitationAssembler assembler;
    private final CitationRepository citations;

    public AssistantCompletionService(
            ChatMessageRepository messages,
            CitationAssembler assembler,
            CitationRepository citations) {
        this.messages = messages;
        this.assembler = assembler;
        this.citations = citations;
    }

    /**
     * The completion CAS runs first. Only its winner assembles and replaces citations; any
     * invariant or persistence exception rolls the message transition back with the citation set.
     */
    @Transactional
    public CompletionResult complete(
            String messageId,
            String answerText,
            String coverageJson,
            RetrievalTurn turn,
            Instant completedAt) {
        int won = messages.completeIfInFlight(messageId, answerText, coverageJson, completedAt);
        if (won == 0) {
            return CompletionResult.lost();
        }
        CitationAssembler.Assembly assembly = assembler.assemble(messageId, turn, completedAt);
        citations.replaceForMessage(messageId, assembly.citations());
        return new CompletionResult(true, assembly.citations(), assembly.summaries());
    }

    public record CompletionResult(
            boolean won,
            List<CitationRecord> citations,
            List<Map<String, Object>> summaries) {
        public CompletionResult {
            citations = citations == null ? List.of() : List.copyOf(citations);
            summaries = summaries == null ? List.of() : List.copyOf(summaries);
        }

        private static CompletionResult lost() {
            return new CompletionResult(false, List.of(), List.of());
        }
    }
}
