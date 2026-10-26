package com.quizlive.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quizzes")
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExamStatus examStatus = ExamStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_change_policy", nullable = false, length = 30)
    private AnswerChangePolicy answerChangePolicy = AnswerChangePolicy.SINGLE_SELECTION;

    @Column(length = 50)
    private String facultyUsername;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    @JsonManagedReference
    private List<Question> questions = new ArrayList<>();

    public Quiz() {}

    public Quiz(String title) {
        this.title = title;
        this.examStatus = ExamStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.updatedAt = LocalDateTime.now();
    }

    public ExamStatus getExamStatus() {
        return examStatus;
    }

    public void setExamStatus(ExamStatus examStatus) {
        this.examStatus = examStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public AnswerChangePolicy getAnswerChangePolicy() {
        return answerChangePolicy != null ? answerChangePolicy : AnswerChangePolicy.SINGLE_SELECTION;
    }

    public void setAnswerChangePolicy(AnswerChangePolicy answerChangePolicy) {
        this.answerChangePolicy = answerChangePolicy;
        this.updatedAt = LocalDateTime.now();
    }

    public String getFacultyUsername() {
        return facultyUsername;
    }

    public void setFacultyUsername(String facultyUsername) {
        this.facultyUsername = facultyUsername;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public void setQuestions(List<Question> questions) {
        this.questions = questions;
        this.updatedAt = LocalDateTime.now();
    }

    public void addQuestion(Question question) {
        questions.add(question);
        question.setQuiz(this);
        this.updatedAt = LocalDateTime.now();
    }

    public void removeQuestion(Question question) {
        questions.remove(question);
        question.setQuiz(null);
        this.updatedAt = LocalDateTime.now();
    }

    public int calculateTotalMarks() {
        if (questions == null) return 0;
        return questions.stream()
                .mapToInt(q -> q.getMarks() != null ? q.getMarks() : 0)
                .sum();
    }
}
