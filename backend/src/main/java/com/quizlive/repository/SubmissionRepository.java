package com.quizlive.repository;

import com.quizlive.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    boolean existsByLiveSessionIdAndParticipantIdAndQuestionId(Long liveSessionId, Long participantId, Long questionId);

    Optional<Submission> findByLiveSessionIdAndParticipantIdAndQuestionId(Long liveSessionId, Long participantId, Long questionId);

    List<Submission> findByLiveSessionIdAndQuestionId(Long liveSessionId, Long questionId);

    long countByLiveSessionIdAndQuestionId(Long liveSessionId, Long questionId);

    long countByLiveSessionIdAndQuestionIdAndIsCorrectTrue(Long liveSessionId, Long questionId);

    long countByLiveSessionIdAndParticipantIdAndIsCorrectTrue(Long liveSessionId, Long participantId);

    long countByLiveSessionIdAndParticipantIdAndIsCorrectFalse(Long liveSessionId, Long participantId);

    @Query("SELECT s.selectedLabel, COUNT(s) FROM Submission s WHERE s.liveSession.id = :sessionId AND s.question.id = :questionId GROUP BY s.selectedLabel")
    List<Object[]> countBySelectedLabelForQuestion(@Param("sessionId") Long sessionId, @Param("questionId") Long questionId);
}
