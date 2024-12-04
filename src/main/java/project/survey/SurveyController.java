package project.survey;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import project.answer.Answer;
import project.answer.AnswerRepository;
import project.answer.MultipleChoiceAnswer;
import project.answer.NumericRangeAnswer;
import project.answer.TextAnswer;
import project.question.Question;
import project.question.QuestionRepository;
import project.user.User;
import project.user.UserRepository;

@Controller
@RequestMapping("/api/surveys")
public class SurveyController {
    private final SurveyRepository surveyRepository;
    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    @Autowired
    public SurveyController(SurveyRepository surveyRepository,
            AnswerRepository answerRepository, UserRepository userRepository,
            QuestionRepository questionRepository) {
        this.surveyRepository = surveyRepository;
        this.answerRepository = answerRepository;
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteSurvey(@PathVariable Integer id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Survey survey = surveyRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Survey not found"));

            // Check if the user owns this survey
            if (!survey.getCreatorId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("You can only delete your own surveys");
            }

            // Delete survey logic...
            surveyRepository.deleteById(survey.getSurveyId());
            return ResponseEntity.ok("Survey deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to delete survey");
        }
    }

    @GetMapping("/{surveyId}/edit")
    public String editSurvey(@PathVariable Integer surveyId, Model model) {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid survey ID"));

        // Load questions eagerly to prevent LazyInitializationException
        survey.getSurveyQuestions().size();
        model.addAttribute("survey", survey);
        return "edit-survey";
    }

    @PostMapping("/{surveyId}/update")
    public ResponseEntity<String> updateSurvey(@PathVariable Integer surveyId,
            @RequestBody Survey updatedSurvey, HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Survey existingSurvey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found"));

        // Update basic survey information
        existingSurvey.setSurveyName(updatedSurvey.getSurveyName());
        existingSurvey.setSurveyDescription(updatedSurvey.getSurveyDescription());
        existingSurvey.setIsOpen(updatedSurvey.getIsOpen());
        existingSurvey.setIsAnonymous(updatedSurvey.getIsAnonymous());
        existingSurvey.setExpirationDate(updatedSurvey.getExpirationDate());

        // Handle question updates
        existingSurvey.removeAllQuestions();
        if (updatedSurvey.getSurveyQuestions() != null) {
            for (Question question : updatedSurvey.getSurveyQuestions()) {
                question.setSurvey(existingSurvey);
                existingSurvey.addQuestion(question);
            }
        }

        surveyRepository.save(existingSurvey);
        return ResponseEntity.ok("Survey updated successfully");
    }

    @GetMapping("/home")
    public String showIndex() {
        return "index";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("survey", new Survey());
        return "create-survey";
    }

    @PostMapping("/save")
    public ResponseEntity<Survey> createSurvey(@RequestBody Survey survey, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        survey.setCreatorId(user.getId().intValue()); // Convert Long to Integer
        survey.setIsOpen(true);
        survey.setCreatedAt(new Date());
        Survey savedSurvey = surveyRepository.save(survey);
        return ResponseEntity.ok(savedSurvey);
    }

    @GetMapping("/{surveyId}")
    public ResponseEntity<Survey> getSurvey(@PathVariable Integer surveyId) {
        List<Survey> surveys = surveyRepository.findBySurveyId(surveyId);
        if (surveys.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(surveys.get(0));
    }

    @GetMapping("/list")
    public String listSurveys(Model model) {
        Iterable<Survey> surveys = surveyRepository.findAll();
        model.addAttribute("surveys", surveys);
        return "survey-list-user"; // Name of the template above
    }

    @GetMapping("/{surveyId}/answer")
    public String getSurveyToAnswer(@PathVariable Integer surveyId, Model model) {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid survey ID"));
        
        User creator = userRepository.findById(Long.valueOf(survey.getCreatorId()))
                .orElseThrow(() -> new IllegalArgumentException("Creator not found"));
        
        model.addAttribute("survey", survey);
        model.addAttribute("creator", creator);
        return "answer-survey";
    }

    @GetMapping("/list-open")
    public String listOpenSurveys(Model model, HttpSession session) {
        List<Survey> openSurveys;
        User user = (User) session.getAttribute("user");
        
        if (user == null) {
            // Not logged in - show only anonymous surveys
            openSurveys = surveyRepository.findByIsOpenTrueAndIsAnonymousTrue();
        } else {
            // Logged in - show all open surveys
            openSurveys = surveyRepository.findByIsOpenTrue();
        }
        
        model.addAttribute("surveys", openSurveys);
        return "survey-list-user";
    }

    // New mapping for View Survey page
    @GetMapping("/survey-list-admin")
    public String viewSurveyPage() {
        return "survey-list-admin";
    }

    @GetMapping("/list-json")
    public ResponseEntity<List<Survey>> getAllSurveys(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Survey> surveys = surveyRepository.findByCreatorId(user.getId().intValue());
        surveys.forEach(survey -> survey.getSurveyQuestions().size()); // Trigger loading of questions
        return ResponseEntity.ok(surveys);
    }

    @GetMapping("/{surveyId}/generate")
    public String generateReport(@PathVariable Integer surveyId, Model model,HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/api/surveys/list-open";
        }
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid survey ID"));
        model.addAttribute("survey", survey);
        return "survey-report"; // Ensure this matches the template name
    }

    @GetMapping("/{surveyId}/answers/{questionId}")
    public ResponseEntity<List<Answer>> getQuestionAnswers(
            @PathVariable Integer surveyId,
            @PathVariable Integer questionId) {
        List<Answer> answers = answerRepository.findBySurveyIdAndQuestionId(surveyId, questionId);
        return ResponseEntity.ok(answers);
    }

    @PostMapping("/{surveyId}/toggle-status")
    public ResponseEntity<String> toggleSurveyStatus(@PathVariable Integer surveyId,
            @RequestBody Map<String, Boolean> request) {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid survey ID"));

        boolean newStatus = request.get("isOpen");
        survey.setIsOpen(newStatus);
        surveyRepository.save(survey);

        return ResponseEntity.ok("Survey status updated successfully");
    }

    // handles submitting survey answers
        @PostMapping(value = "/{surveyId}/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> submitSurveyAnswers(
            @PathVariable Integer surveyId, 
            @RequestBody Map<String, String> answers,
            HttpSession session) {
        
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found"));
                
        if (!survey.getIsOpen()) {
            return ResponseEntity.badRequest().body("Survey is closed");
        }
        
        User currentUser = (User) session.getAttribute("user");
        
        for (Map.Entry<String, String> entry : answers.entrySet()) {
            Integer questionId = Integer.parseInt(entry.getKey().replace("question_", ""));
            Question question = getQuestionById(questionId);
            Answer answer = createAnswerFromQuestion(entry, question);
            
            // Set userId only for non-anonymous surveys and logged-in users
            if (!survey.getIsAnonymous() && currentUser != null) {
                answer.setUserId(currentUser.getId().intValue());
            }
            
            answer.setSurveyId(surveyId);
            answer.setQuestionId(questionId);
            answerRepository.save(answer);
        }
        
        return ResponseEntity.ok("Survey answers submitted successfully");
    }

    private Answer createAnswerFromQuestion(Map.Entry<String, String> entry, Question question) {
        Answer answer = null;

        // create answer type based on questions type
        switch (question.getType()) {
            case "TEXT":
                answer = new TextAnswer();
                ((TextAnswer) answer).setText(entry.getValue());
                break;
            case "MULTIPLE_CHOICE":
                answer = new MultipleChoiceAnswer();
                ((MultipleChoiceAnswer) answer).setSelectedChoice(Integer.parseInt(entry.getValue()));
                break;
            case "NUMERIC_RANGE":
                answer = new NumericRangeAnswer();
                ((NumericRangeAnswer) answer).setChoice(Integer.parseInt(entry.getValue()));
                break;
            default:
                throw new IllegalArgumentException("Unsupported question type: " + question.getType());
        }

        return answer;
    }

    private boolean isAdmin(HttpSession session) {
        User user = (User) session.getAttribute("user");
        return user != null && user.getRole() == User.Role.ADMIN;
    }

    private Question getQuestionById(Integer questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid question ID"));
    }

    private Integer getQuestionIdFromEntry(Map.Entry<String, String> entry) {
        return Integer.parseInt(entry.getKey()); // convert key to question ID
    }

}