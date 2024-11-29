package project.answer;
import project.answer.Answer;

import org.springframework.data.repository.CrudRepository;
import project.survey.Survey;

import java.util.List;

public interface AnswerRepository extends CrudRepository<Answer, Integer> {
    //ToDo Milestone1: Add custom queries for answers here
    List<Answer> findBySurveyId(int surveyId);
}