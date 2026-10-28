package com.quizlive.repository;

import com.quizlive.model.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    List<Participant> findByLiveSessionIdOrderByScoreDesc(Long liveSessionId);
    List<Participant> findBySessionPinOrderByScoreDesc(String sessionPin);
    Optional<Participant> findBySessionPinAndCandidateId(String sessionPin, String candidateId);
    long countBySessionPin(String sessionPin);
}
