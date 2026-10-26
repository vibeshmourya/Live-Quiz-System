package com.quizlive.repository;

import com.quizlive.model.LiveSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {
    Optional<LiveSession> findByPin(String pin);
    boolean existsByPin(String pin);
}
