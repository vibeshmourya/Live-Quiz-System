package com.quizlive.repository;

import com.quizlive.model.ExamIncident;
import com.quizlive.model.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamIncidentRepository extends JpaRepository<ExamIncident, Long> {

    List<ExamIncident> findByLiveSessionIdOrderByServerTimestampDesc(Long liveSessionId);

    List<ExamIncident> findByLiveSessionIdAndParticipantIdOrderByServerTimestampDesc(Long liveSessionId, Long participantId);

    long countByLiveSessionIdAndParticipantId(Long liveSessionId, Long participantId);

    long countByLiveSessionIdAndParticipantIdAndEventType(Long liveSessionId, Long participantId, IncidentType eventType);

    long countByLiveSessionId(Long liveSessionId);

    Optional<ExamIncident> findFirstByLiveSessionIdAndParticipantIdAndEventTypeOrderByServerTimestampDesc(
            Long liveSessionId, Long participantId, IncidentType eventType);
}
