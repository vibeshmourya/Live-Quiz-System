package com.quizlive.service;

import com.quizlive.dto.AnswerOptionDto;
import com.quizlive.dto.QuestionDto;
import com.quizlive.dto.QuizDto;
import com.quizlive.model.AnswerOption;
import com.quizlive.model.ExamStatus;
import com.quizlive.model.Question;
import com.quizlive.model.Quiz;
import com.quizlive.repository.QuestionRepository;
import com.quizlive.repository.QuizRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;

    public QuizService(QuizRepository quizRepository, QuestionRepository questionRepository) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public List<QuizDto> getAllQuizzes(boolean isFaculty) {
        return quizRepository.findAll().stream()
                .filter(q -> isFaculty || q.getExamStatus() == ExamStatus.LIVE || q.getExamStatus() == ExamStatus.SCHEDULED)
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuizDto getQuizById(Long id, boolean isFaculty) {
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quiz / Examination not found with id: " + id));

        // Confidentiality check: students must never view DRAFT examinations
        if (!isFaculty && quiz.getExamStatus() == ExamStatus.DRAFT) {
            throw new IllegalArgumentException("Examination is confidential and not published yet.");
        }

        return toDto(quiz);
    }

    @Transactional(readOnly = true)
    public QuizDto getFacultyPreview(Long id) {
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quiz / Examination not found with id: " + id));
        return toDto(quiz);
    }

    @Transactional
    public QuizDto saveOrUpdateDraft(QuizDto dto, String facultyUsername) {
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Quiz title cannot be empty");
        }

        Quiz quiz;
        if (dto.getId() != null) {
            quiz = quizRepository.findById(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Quiz not found with id: " + dto.getId()));

            if (quiz.getExamStatus() == ExamStatus.LIVE) {
                throw new IllegalStateException("Cannot edit questions while the examination is currently LIVE");
            }
            if (quiz.getExamStatus() == ExamStatus.COMPLETED) {
                throw new IllegalStateException("Cannot edit a completed examination");
            }

            quiz.setTitle(dto.getTitle().trim());
            quiz.setUpdatedAt(LocalDateTime.now());
            if (dto.getExamStatus() != null) {
                quiz.setExamStatus(dto.getExamStatus());
            }
            if (dto.getAnswerChangePolicy() != null) {
                quiz.setAnswerChangePolicy(dto.getAnswerChangePolicy());
            }
        } else {
            quiz = new Quiz(dto.getTitle().trim());
            quiz.setFacultyUsername(facultyUsername);
            quiz.setExamStatus(dto.getExamStatus() != null ? dto.getExamStatus() : ExamStatus.DRAFT);
            if (dto.getAnswerChangePolicy() != null) {
                quiz.setAnswerChangePolicy(dto.getAnswerChangePolicy());
            }
            quiz.setCreatedAt(LocalDateTime.now());
            quiz.setUpdatedAt(LocalDateTime.now());
        }

        // Rebuild questions safely
        quiz.getQuestions().clear();

        if (dto.getQuestions() != null && !dto.getQuestions().isEmpty()) {
            int order = 0;
            for (QuestionDto qDto : dto.getQuestions()) {
                Question question = new Question();
                question.setQuestionText(qDto.getQuestionText() != null ? qDto.getQuestionText().trim() : "");
                question.setTimeLimit(qDto.getTimeLimit() != null && qDto.getTimeLimit() > 0 ? qDto.getTimeLimit() : 20);
                question.setMarks(qDto.getMarks() != null && qDto.getMarks() > 0 ? qDto.getMarks() : 10);
                question.setOrderIndex(order++);

                List<AnswerOptionDto> options = qDto.getOptions();
                if (options != null) {
                    for (int i = 0; i < options.size(); i++) {
                        AnswerOptionDto optDto = options.get(i);
                        String label = optDto.getOptionLabel() != null && !optDto.getOptionLabel().isEmpty()
                                ? optDto.getOptionLabel().trim().toUpperCase()
                                : String.valueOf((char) ('A' + i));

                        boolean correct = Boolean.TRUE.equals(optDto.getIsCorrect());
                        AnswerOption option = new AnswerOption(
                                label,
                                optDto.getOptionText() != null ? optDto.getOptionText().trim() : "",
                                correct
                        );
                        question.addOption(option);
                    }
                }
                quiz.addQuestion(question);
            }
        }

        Quiz saved = quizRepository.save(quiz);
        return toDto(saved);
    }

    @Transactional
    public QuizDto duplicateQuestion(Long quizId, Long questionId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found with id: " + quizId));

        if (quiz.getExamStatus() == ExamStatus.LIVE || quiz.getExamStatus() == ExamStatus.COMPLETED) {
            throw new IllegalStateException("Cannot duplicate questions during or after live examination");
        }

        Question original = quiz.getQuestions().stream()
                .filter(q -> q.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Question not found with id: " + questionId));

        Question duplicate = new Question();
        duplicate.setQuestionText(original.getQuestionText() + " (Copy)");
        duplicate.setTimeLimit(original.getTimeLimit());
        duplicate.setMarks(original.getMarks());
        duplicate.setOrderIndex(quiz.getQuestions().size());

        for (AnswerOption opt : original.getOptions()) {
            duplicate.addOption(new AnswerOption(opt.getOptionLabel(), opt.getOptionText(), opt.getIsCorrect()));
        }

        quiz.addQuestion(duplicate);
        quiz.setUpdatedAt(LocalDateTime.now());
        Quiz saved = quizRepository.save(quiz);
        return toDto(saved);
    }

    @Transactional
    public QuizDto deleteQuestion(Long quizId, Long questionId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found with id: " + quizId));

        if (quiz.getExamStatus() == ExamStatus.LIVE || quiz.getExamStatus() == ExamStatus.COMPLETED) {
            throw new IllegalStateException("Cannot delete questions during or after live examination");
        }

        Question target = quiz.getQuestions().stream()
                .filter(q -> q.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Question not found with id: " + questionId));

        quiz.removeQuestion(target);

        // Re-index remaining questions
        for (int i = 0; i < quiz.getQuestions().size(); i++) {
            quiz.getQuestions().get(i).setOrderIndex(i);
        }

        quiz.setUpdatedAt(LocalDateTime.now());
        Quiz saved = quizRepository.save(quiz);
        return toDto(saved);
    }

    @Transactional
    public QuizDto reorderQuestions(Long quizId, List<Long> questionIdsInOrder) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found with id: " + quizId));

        if (quiz.getExamStatus() == ExamStatus.LIVE || quiz.getExamStatus() == ExamStatus.COMPLETED) {
            throw new IllegalStateException("Cannot reorder questions during or after live examination");
        }

        for (int i = 0; i < questionIdsInOrder.size(); i++) {
            Long qId = questionIdsInOrder.get(i);
            for (Question q : quiz.getQuestions()) {
                if (q.getId().equals(qId)) {
                    q.setOrderIndex(i);
                    break;
                }
            }
        }

        quiz.getQuestions().sort((a, b) -> Integer.compare(a.getOrderIndex(), b.getOrderIndex()));
        quiz.setUpdatedAt(LocalDateTime.now());
        Quiz saved = quizRepository.save(quiz);
        return toDto(saved);
    }

    @Transactional
    public QuizDto updateExamStatus(Long quizId, ExamStatus newStatus) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found with id: " + quizId));

        quiz.setExamStatus(newStatus);
        quiz.setUpdatedAt(LocalDateTime.now());
        Quiz saved = quizRepository.save(quiz);
        return toDto(saved);
    }

    @Transactional
    public void deleteQuiz(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found with id: " + quizId));
        if (quiz.getExamStatus() == ExamStatus.LIVE) {
            throw new IllegalStateException("Cannot delete an examination that is currently LIVE");
        }
        quizRepository.delete(quiz);
    }

    public QuizDto toDto(Quiz quiz) {
        QuizDto dto = new QuizDto();
        dto.setId(quiz.getId());
        dto.setTitle(quiz.getTitle());
        dto.setExamStatus(quiz.getExamStatus());
        dto.setAnswerChangePolicy(quiz.getAnswerChangePolicy());
        dto.setFacultyUsername(quiz.getFacultyUsername());
        dto.setCreatedAt(quiz.getCreatedAt());
        dto.setUpdatedAt(quiz.getUpdatedAt());

        int totalMarks = 0;
        List<QuestionDto> qDtos = new ArrayList<>();
        if (quiz.getQuestions() != null) {
            for (Question q : quiz.getQuestions()) {
                QuestionDto qDto = new QuestionDto();
                qDto.setId(q.getId());
                qDto.setQuestionText(q.getQuestionText());
                qDto.setTimeLimit(q.getTimeLimit());
                qDto.setMarks(q.getMarks());
                qDto.setOrderIndex(q.getOrderIndex());

                if (q.getMarks() != null) {
                    totalMarks += q.getMarks();
                }

                List<AnswerOptionDto> optDtos = new ArrayList<>();
                if (q.getOptions() != null) {
                    for (AnswerOption opt : q.getOptions()) {
                        optDtos.add(new AnswerOptionDto(opt.getId(), opt.getOptionLabel(), opt.getOptionText(), opt.getIsCorrect()));
                    }
                }
                qDto.setOptions(optDtos);
                qDtos.add(qDto);
            }
        }
        dto.setQuestions(qDtos);
        dto.setTotalMarks(totalMarks);
        dto.setQuestionCount(qDtos.size());
        return dto;
    }

    @PostConstruct
    @Transactional
    public void seedInitialQuizIfEmpty() {
        if (quizRepository.count() == 0) {
            Quiz quiz = new Quiz("Web Architecture & Networking Master Quiz");
            quiz.setExamStatus(ExamStatus.SCHEDULED);
            quiz.setFacultyUsername("faculty");

            // Question 1
            Question q1 = new Question();
            q1.setQuestionText("Which protocol provides full-duplex communication over a single TCP connection in real time?");
            q1.setTimeLimit(20);
            q1.setMarks(10);
            q1.setOrderIndex(0);
            q1.addOption(new AnswerOption("A", "HTTP/1.1 Long Polling", false));
            q1.addOption(new AnswerOption("B", "WebSocket", true));
            q1.addOption(new AnswerOption("C", "Server-Sent Events (SSE)", false));
            q1.addOption(new AnswerOption("D", "DNS Lookup", false));
            quiz.addQuestion(q1);

            // Question 2
            Question q2 = new Question();
            q2.setQuestionText("What is the primary purpose of the STOMP protocol when paired with WebSockets?");
            q2.setTimeLimit(20);
            q2.setMarks(10);
            q2.setOrderIndex(1);
            q2.addOption(new AnswerOption("A", "Encrypting database transactions", false));
            q2.addOption(new AnswerOption("B", "Compiling CSS style sheets", false));
            q2.addOption(new AnswerOption("C", "Providing a simple text-oriented messaging pattern (pub/sub)", true));
            q2.addOption(new AnswerOption("D", "Compressing PNG media assets", false));
            quiz.addQuestion(q2);

            // Question 3
            Question q3 = new Question();
            q3.setQuestionText("In relational databases like MySQL, which key constraint prevents duplicate entries across defined columns?");
            q3.setTimeLimit(20);
            q3.setMarks(10);
            q3.setOrderIndex(2);
            q3.addOption(new AnswerOption("A", "UNIQUE Constraint", true));
            q3.addOption(new AnswerOption("B", "CHECK Constraint", false));
            q3.addOption(new AnswerOption("C", "FOREIGN KEY Constraint", false));
            q3.addOption(new AnswerOption("D", "DEFAULT Clause", false));
            quiz.addQuestion(q3);

            // Question 4
            Question q4 = new Question();
            q4.setQuestionText("In React, which hook is best suited to manage subscription listeners and cleanup when unmounting?");
            q4.setTimeLimit(25);
            q4.setMarks(10);
            q4.setOrderIndex(3);
            q4.addOption(new AnswerOption("A", "useReducer", false));
            q4.addOption(new AnswerOption("B", "useContext", false));
            q4.addOption(new AnswerOption("C", "useEffect with return cleanup function", true));
            q4.addOption(new AnswerOption("D", "useRef", false));
            quiz.addQuestion(q4);

            quizRepository.save(quiz);
        } else {
            // Ensure existing quizzes have SCHEDULED status if null
            for (Quiz q : quizRepository.findAll()) {
                if (q.getExamStatus() == null) {
                    q.setExamStatus(ExamStatus.SCHEDULED);
                    quizRepository.save(q);
                }
            }
        }
    }
}
