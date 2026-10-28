package com.quizlive.service;

import com.quizlive.dto.ExamIncidentDto;
import com.quizlive.dto.IncidentReportRequest;
import com.quizlive.dto.IncidentReviewRequest;
import com.quizlive.model.*;
import com.quizlive.repository.ExamIncidentRepository;
import com.quizlive.repository.LiveSessionRepository;
import com.quizlive.repository.ParticipantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class IncidentService {

    private final ExamIncidentRepository incidentRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final ParticipantRepository participantRepository;

    public IncidentService(
            ExamIncidentRepository incidentRepository,
            LiveSessionRepository liveSessionRepository,
            ParticipantRepository participantRepository
    ) {
        this.incidentRepository = incidentRepository;
        this.liveSessionRepository = liveSessionRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional
    public ExamIncidentDto reportIncident(String sessionPin, IncidentReportRequest request) {
        LiveSession session = liveSessionRepository.findByPin(sessionPin)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found with PIN: " + sessionPin));

        Participant participant = participantRepository.findBySessionPinAndCandidateId(sessionPin, request.getCandidateId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Participant not recognized for this session"));

        // Deduplication / Debounce: If same participant reported same incident type within last 3 seconds, ignore or update
        Optional<ExamIncident> lastRecent = incidentRepository.findFirstByLiveSessionIdAndParticipantIdAndEventTypeOrderByServerTimestampDesc(
                session.getId(), participant.getId(), request.getEventType()
        );

        if (lastRecent.isPresent()) {
            LocalDateTime lastTime = lastRecent.get().getServerTimestamp();
            if (lastTime.isAfter(LocalDateTime.now().minusSeconds(3))) {
                // Return existing to prevent spamming
                return toDto(lastRecent.get());
            }
        }

        String details = request.getIncidentDetails() != null && !request.getIncidentDetails().trim().isEmpty()
                ? request.getIncidentDetails().trim()
                : "Incident recorded by proctoring monitor";

        if (details.length() > 500) {
            details = details.substring(0, 500);
        }

        ExamIncident incident = new ExamIncident(session, participant, request.getEventType(), details);
        ExamIncident saved = incidentRepository.save(incident);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ExamIncidentDto> getIncidentsForSession(String sessionPin) {
        LiveSession session = liveSessionRepository.findByPin(sessionPin)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found with PIN: " + sessionPin));

        return incidentRepository.findByLiveSessionIdOrderByServerTimestampDesc(session.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExamIncidentDto> getIncidentsForParticipant(String sessionPin, String candidateId) {
        LiveSession session = liveSessionRepository.findByPin(sessionPin)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found with PIN: " + sessionPin));

        Participant participant = participantRepository.findBySessionPinAndCandidateId(sessionPin, candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant not found"));

        return incidentRepository.findByLiveSessionIdAndParticipantIdOrderByServerTimestampDesc(session.getId(), participant.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getIncidentCountForParticipant(String sessionPin, String candidateId) {
        LiveSession session = liveSessionRepository.findByPin(sessionPin).orElse(null);
        if (session == null) return 0;
        Participant participant = participantRepository.findBySessionPinAndCandidateId(sessionPin, candidateId).orElse(null);
        if (participant == null) return 0;

        return incidentRepository.countByLiveSessionIdAndParticipantId(session.getId(), participant.getId());
    }

    @Transactional
    public ExamIncidentDto reviewIncident(Long incidentId, IncidentReviewRequest request) {
        ExamIncident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found with ID: " + incidentId));

        incident.setReviewStatus(request.getReviewStatus());
        if (request.getReviewNotes() != null) {
            incident.setReviewNotes(request.getReviewNotes().trim());
        }
        ExamIncident saved = incidentRepository.save(incident);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getIncidentSummary(String sessionPin) {
        LiveSession session = liveSessionRepository.findByPin(sessionPin)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found with PIN: " + sessionPin));

        List<ExamIncident> all = incidentRepository.findByLiveSessionIdOrderByServerTimestampDesc(session.getId());
        long total = all.size();

        Map<String, Long> byType = new HashMap<>();
        for (IncidentType type : IncidentType.values()) {
            byType.put(type.name(), 0L);
        }
        for (ExamIncident inc : all) {
            byType.put(inc.getEventType().name(), byType.getOrDefault(inc.getEventType().name(), 0L) + 1);
        }

        Map<String, Long> byParticipant = new HashMap<>();
        for (ExamIncident inc : all) {
            String name = inc.getParticipant().getNickname();
            byParticipant.put(name, byParticipant.getOrDefault(name, 0L) + 1);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalIncidents", total);
        summary.put("byType", byType);
        summary.put("byParticipant", byParticipant);
        summary.put("incidents", all.stream().map(this::toDto).toList());
        return summary;
    }

    private ExamIncidentDto toDto(ExamIncident incident) {
        ExamIncidentDto dto = new ExamIncidentDto();
        dto.setId(incident.getId());
        dto.setLiveSessionId(incident.getLiveSession().getId());
        dto.setSessionPin(incident.getLiveSession().getPin());
        dto.setParticipantId(incident.getParticipant().getId());
        dto.setCandidateId(incident.getParticipant().getCandidateId());
        dto.setNickname(incident.getParticipant().getNickname());
        dto.setEventType(incident.getEventType());
        dto.setIncidentDetails(incident.getIncidentDetails());
        dto.setServerTimestamp(incident.getServerTimestamp());
        dto.setReviewStatus(incident.getReviewStatus());
        dto.setReviewNotes(incident.getReviewNotes());
        return dto;
    }
}
