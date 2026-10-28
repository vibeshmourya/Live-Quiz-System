package com.quizlive;

import com.quizlive.dto.QuizDto;
import com.quizlive.model.*;
import com.quizlive.repository.*;
import com.quizlive.service.QuizService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class QuizDuplicationTest {

    @Autowired
    private QuizService quizService;

    @Autowired
    private QuizRepository quizRepository;

    @Test
    @DisplayName("Should successfully duplicate a quiz, copy questions and options, and reset status to DRAFT")
    void testDuplicateQuiz() {
        Quiz original = new Quiz("Data Structures Final Exam");
        original.setFacultyUsername("prof_vibesh");
        original.setExamStatus(ExamStatus.SCHEDULED);
        original.setAnswerChangePolicy(AnswerChangePolicy.ALLOW_RESELECTION);
        original.setRequireFullscreen(true);
        original.setMonitorTabSwitch(true);
        original.setRestrictCopyPaste(true);

        List<Question> questions = new ArrayList<>();
        Question q1 = new Question();
        q1.setQuestionText("What is the time complexity of searching in a Balanced Binary Search Tree?");
        q1.setTimeLimit(30);
        q1.setMarks(2);
        q1.setOrderIndex(0);
        q1.setQuiz(original);

        List<AnswerOption> options = new ArrayList<>();
        AnswerOption optA = new AnswerOption("A", "O(1)", false);
        optA.setQuestion(q1);

        AnswerOption optB = new AnswerOption("B", "O(log N)", true);
        optB.setQuestion(q1);

        options.add(optA);
        options.add(optB);
        q1.setOptions(options);
        questions.add(q1);
        original.setQuestions(questions);

        original = quizRepository.save(original);

        QuizDto duplicated = quizService.duplicateQuiz(original.getId(), "prof_vibesh");

        assertNotNull(duplicated);
        assertNotEquals(original.getId(), duplicated.getId(), "Duplicated quiz must have a new unique ID");
        assertEquals("Copy of Data Structures Final Exam", duplicated.getTitle());
        assertEquals(ExamStatus.DRAFT, duplicated.getExamStatus(), "Duplicated quiz must be in DRAFT status");
        assertEquals(1, duplicated.getQuestions().size(), "Questions must be cloned");
        assertEquals("What is the time complexity of searching in a Balanced Binary Search Tree?",
                duplicated.getQuestions().get(0).getQuestionText());
        assertEquals(2, duplicated.getQuestions().get(0).getOptions().size());
        assertTrue(duplicated.getRequireFullscreen());
        assertTrue(duplicated.getMonitorTabSwitch());
    }
}
