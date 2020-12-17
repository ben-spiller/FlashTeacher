package com.ben.flashteacher.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.swing.JPanel;

import org.apache.log4j.Logger;
import org.jdom.Comment;
import org.jdom.Element;
import com.ben.flashteacher.utils.Utils;

/**
 * QuestionManager is the main model class, holding the list of available questions and 
 * answers, and also information about how the user is or has performed on each question.  
 * 
 * A new instance is created every time the user clicks the "start" button. 
 * 
 * @author Ben
 */
public class QuestionManager 
{
	final Logger logger = Logger.getLogger(getClass().getName());
	
	final static Random random = new Random();
	
	/*
	 * 
	 * Parameters governing question selection
	 * 
	 */
	
	/**
	 * A cap on the time per-character time allowed to enter the characters of 
	 * the answer. 
	 */
	static final int MAXIMUM_MILLIS_TO_ALLOW_PER_CHARACTER = 10*1000;
	
	/**
	 * The time to assign to any wrong answer. 
	 */
	static final long WRONG_ANSWER_TIME_PENALTY = 1000L*50;

	/**
	 * The maximum possible time that will be recorded for any question. 
	 * (note that the WRONG_ANSWER_TIME_PENALTY is larger than this value)
	 */
	static final long MAXIMUM_MILLIS_TO_RECORD_PER_ANSWER = 1000L*30;

	/**
	 * The pass mode assigned when the user passes a question. 
	 */
	static final int PASS_COUNTER_VALUE = 3;

	static final float QUESTION_SELECTION_PROBABILITY_PRIORITIZED = 0.5f;
	static final float QUESTION_SELECTION_PROBABILITY_BAD_TIME = 0.3f;
	
	/**
	 * Equal to x, when a question is selected randomly from the least-recently-
	 * asked x% of the question corpus. 
	 */
	static final int QUESTION_SELECTION_LEAST_RECENTLY_ASKED_BUCKET_PERCENTAGE = 10; // 10%
	
	/**
	 * Equal to x, when a question is selected randomly from the worst x 
	 * question-answer times in the corpus. 
	 */
	static final int QUESTION_SELECTION_BAD_TIMES_BUCKET_SIZE = 20;

	/**
	 * The maximum number of questions to keep in the 'very bad' bucket at 
	 * any one time - to allow the user to properly get one lot of questions 
	 * right before tackling the rest! 
	 */
	static final int MAXIMUM_PRIORITIZED_QUESTIONS_BUCKET_SIZE = 10;
	
	/*
	 * 
	 * Data structures
	 * 
	 */
	
	/**
	 * Contains a QuestionHistory object for all questions we are using. Gets  
	 * re-sorted by timeLastAsked. 
	 */
	final List<QuestionHistory> allQuestions = new ArrayList<QuestionHistory>();


	/**
	 * A list of questions that were not passed (i.e. that have a time 
	 * associated with them). Gets re-sorted by timeToAnswer.
	 */
	final List<QuestionHistory> nonPassedQuestions = new ArrayList<QuestionHistory>();

	/**
	 * A set of questions that were passed and are also marked as prioritized 
	 * questions (based on the principal that we should concentrate on learning 
	 * a small number of questions at a time). 
	 */
	final Set<QuestionHistory> prioritizedQuestions = new HashSet<QuestionHistory>();
	
	// (note nonPassedQuestions + prioritizedQuestions + <passed but non-prioritized Qs> = allQuestions) 

	QuestionHistory currentQuestion = null;
	
	/**
	 * Indicates whether the currentQuestion has just been updated, or whether 
	 * the user has already had a failed attempt to answer this question (with 
	 * no intervening gap) - indicating that we should not update the question 
	 * history (until we move to the next question). 
	 */
	boolean firstAttemptAtQuestion;
	
	/**
	 * The score of the previous question, as a %
	 */
	int lastQuestionScore = 0;
	
	/**
	 * The score of the previous question, before the last answer. 
	 */
	int lastQuestionPreviousButOneScore = 0;
	int lastQuestionPreviousScore = 0;
	
	final QuestionSetScores previousQuestionSetScores;
	final KnowledgeIndexHistory knowledgeIndexHistory;
	
	final boolean caseSensitive;
	
	int questionsAnswered = 0;
	long startTimeMillis;

	/**
	 * @param questionListElement The XML element containing the list of 
	 * question data the user has selected.
	 * @param historyElement The XML element containing the associated 
	 * question history.
	 * @throws IOException If the input file is invalid
	 */
	@SuppressWarnings("unchecked")
	public QuestionManager(List<Question> questions, Element questionHistoryElement, Options options, JPanel questionFieldPanel) throws IOException
	{
		startTimeMillis = System.currentTimeMillis();
	
		caseSensitive = options.isCaseSensitive();

		Map<String, Question> loadedQuestions = new HashMap<>(); // keyed by question string 
		for (Question q: questions)
		{
			Question duplicateQuestion = loadedQuestions.put(q.getQuestion(), q);
			if (duplicateQuestion != null)
				throw new IOException("Invalid question file - question appears more than once: \""+duplicateQuestion+"\"");
		}
		
		// Then, load history file and start populating allQuestions
		if (questionHistoryElement != null)
		{
			Element historyListElement = questionHistoryElement.getChild("questionHistoryList");
			if (historyListElement != null)
				for (Element historyElement: (List<Element>)historyListElement.getChildren())
				{
					String questionText = historyElement.getAttributeValue("questionText");
					String answerText = historyElement.getAttributeValue("answerText");
					
					Question existingQuestion = loadedQuestions.get(questionText);
					
					// ignore history if the question or answer has changed - the 
					// question list is the only authoritative source of data
					
					if (existingQuestion == null)
					{
						logger.info("Ignoring question which is no longer in the question file: \""+questionText+"\"");
						continue;
					}
					if (!existingQuestion.isAnswerCorrect(answerText, caseSensitive))
					{
						logger.info("Answer has changed, so ignoring history for question: \""+existingQuestion+"\"");
						continue;
					}
	
					// remove existing QH object and replace with one that includes 
					// history from this file (this relies on QH equality being based only on the questionText; it's also a bit inefficient)
					QuestionHistory newQuestion = new QuestionHistory( existingQuestion, historyElement);
					loadedQuestions.remove(questionText);
					allQuestions.add(newQuestion);
				}
			
			previousQuestionSetScores = new QuestionSetScores(questionHistoryElement.getChild(QuestionSetScores.ELEMENT_NAME)); // we handle null element here correctly
			knowledgeIndexHistory = new KnowledgeIndexHistory(questionHistoryElement.getChild(KnowledgeIndexHistory.ELEMENT_NAME));
		}
		else
		{
			previousQuestionSetScores = new QuestionSetScores();
			knowledgeIndexHistory = new KnowledgeIndexHistory();
		}
		
		// finally add new questions that aren't in the history yet
		for (Question q: loadedQuestions.values()) {
			logger.info("Adding new question: "+q);
			allQuestions.add(new QuestionHistory(q));
		}
		
		if (allQuestions.size() < 2)
			throw new IOException("Invalid question file - the file contains less than two questions!");

		
		averageTimePerCharacter = previousQuestionSetScores.averageTimePerCharacter;
		
		for (QuestionHistory qh: allQuestions)
			if (qh.passModeCounter == 0)
				nonPassedQuestions.add(qh);
			else if (qh.isPrioritized)
			{
				if (prioritizedQuestions.size() < MAXIMUM_PRIORITIZED_QUESTIONS_BUCKET_SIZE)
					prioritizedQuestions.add(qh);
				else
					qh.isPrioritized = false;
			}
		
		moveToNextQuestion();
	}
	
	Element save() throws IOException
	{
		calculateScores();
		
		// add Q history; first sort by time to answer so we can look inside the file manually and see which are worse 
		Element questionHistoryListElement = new Element("questionHistoryList");
		questionHistoryListElement.addContent(new Comment("Question history is sorted with longest time-to-answer (including penalties from wrong answers) at the top: "));
		Collections.sort(allQuestions, QuestionHistory.AVERAGE_TIME_TO_ANSWER_COMPARATOR);
		Collections.reverse(allQuestions);
		for (QuestionHistory q: allQuestions)
			questionHistoryListElement.addContent(q.saveToXMLElement());

		// add to root element
		Element historyRootElement = new Element("questionHistory");
		historyRootElement.addContent(questionHistoryListElement);
		historyRootElement.addContent(getQuestionSetScores().saveToXMLElement());
		historyRootElement.addContent(getKnowledgeIndexHistory().saveToXMLElement());

		return historyRootElement;
	}
	
	public Question getCurrentQuestion() {
		return currentQuestion.question;
	}
	
	public String getCurrentAnswer() {
		return currentQuestion.question.getAnswer();
	}	
	
	/**
	 * @return The score for the question just answered, as a percentage
	 */
	public int getQuestionScore() {
		return lastQuestionScore;
	}
	
	/**
	 * @return The score for the question just answered before it was answered, as a percentage
	 */
	public int getQuestionPreviousScore() {
		return lastQuestionPreviousButOneScore; // must be the previous must one, 
			// otherwise it would be for the question NOW being answered, not 
			// the question just answered 
	}	
	
	/**
	 * Calculates scores; multiple invocations per QuestionManager instance will 
	 * be ignored. 
	 * The time this method is called is the time the question scores are 
	 * associated with in the history. 
	 * Call getQuestionSetScores() or getKnowledgeIndexHistory() to get the 
	 * scores. 
	 */
	public void calculateScores()
	{
		if (questionSetScores != null)
		{
			logger.info("calculateScores already called once - ignoring call");
			return;
		}
		questionSetScores = Scorer.getQuestionSetScores(this);
		knowledgeIndexHistory.add(new Date(), questionSetScores.knowledgeIndexScore);
	}
	
	protected QuestionSetScores questionSetScores = null;
	/**
	 * Must not be called until calculateScores() has been called. 
	 * @return
	 */
	public QuestionSetScores getQuestionSetScores() {
		if (questionSetScores == null) throw new IllegalStateException("Cannot call getQuestionSetScores() until calculateScores() has been called");
		return questionSetScores;
	}
	
	/**
	 * @return The scores for the last time the questions were attempted. 
	 */
	public QuestionSetScores getPreviousQuestionSetScores() {
		return previousQuestionSetScores;
	}
	
	public KnowledgeIndexHistory getKnowledgeIndexHistory() {
		return knowledgeIndexHistory;
	}
	
	private enum QuestionTypeSelectionMethod
	{
		UNKNOWN("<unknown question selection method>"),
		PRIORITIZED_LIST_PASSED("Question is from the prioritised 'passed' question list"),
		PRIORITIZED_LIST_NEVER_ASKED("Question is from the prioritised 'never asked' question list"),
		BAD_TIMES_LIST("Question is from the bad times question list"),
		LEAST_RECENTLY_ASKED_LIST("Question had not been asked for a long time"),
		RANDOM("Question selected randomly");
		private final String displayText;
		QuestionTypeSelectionMethod(String displayText) { this.displayText = displayText; }
		@Override public String toString() { return displayText; }
	}

	private QuestionTypeSelectionMethod questionTypeSelectionMethod = QuestionTypeSelectionMethod.UNKNOWN;
	
	/**
	 * This is mostly just for debugging purposes, not sure if it should be 
	 * exposed to the user normally
	 * @return A string explanation of how the current question was selected 
	 * (e.g. 'Random question', 'Frequently wrong', ...)
	 */
	public String getQuestionSelectionMethod()
	{
		return questionTypeSelectionMethod.toString();
	}
	
	protected void moveToNextQuestion()
	{
		// update and sort buckets
		Collections.sort(allQuestions, QuestionHistory.TIME_LAST_ASKED_COMPARATOR);
		Collections.sort(nonPassedQuestions, QuestionHistory.AVERAGE_TIME_TO_ANSWER_COMPARATOR);
		checkPrioritizations();
		
		if (logger.isTraceEnabled())
		{
			logger.trace("allQuestions = \n"+allQuestions+"\n");
			logger.trace("nonPassedQuestions = \n"+nonPassedQuestions+"\n");
			logger.trace("prioritizedQuestions = \n"+prioritizedQuestions+"\n");
			Set<QuestionHistory> otherQs = new HashSet<QuestionHistory>();
			otherQs.addAll(allQuestions);
			otherQs.removeAll(nonPassedQuestions);
			otherQs.removeAll(prioritizedQuestions);
			logger.trace("other questions = \n"+otherQs+"\n");
		}
		
		QuestionHistory nextQuestion = null;
	
		// list of prioritized, list of non-prioritized ordered by timeToAnswer, and by timeLastAsked
		
		float questionTypeSelectValue = random.nextFloat();
		
		if (questionTypeSelectValue < QUESTION_SELECTION_PROBABILITY_PRIORITIZED)
		{
			logger.debug("QuestionManager.moveToNextQuestion: selecting from Qs in prioritized list");
			
			// select randomly from the prioritized list
			if (prioritizedQuestions.size() > 0)
			{
				nextQuestion = Utils.getElementAt(random.nextInt(prioritizedQuestions.size()), prioritizedQuestions);
				questionTypeSelectionMethod = (nextQuestion.timeLastAsked==null)? QuestionTypeSelectionMethod.PRIORITIZED_LIST_NEVER_ASKED : QuestionTypeSelectionMethod.PRIORITIZED_LIST_PASSED;
			}
			else
				logger.debug("QuestionManager.moveToNextQuestion: prioritized list is empty, trying another strategy");
			
		} else if (questionTypeSelectValue < QUESTION_SELECTION_PROBABILITY_PRIORITIZED+QUESTION_SELECTION_PROBABILITY_BAD_TIME)
		{
			// select randomly from the worst 20 times - excluding prioritized Qs (as long as there are at least that many - otherwise it doesn't make sense to do this)
			logger.debug("QuestionManager.moveToNextQuestion: selecting from list of Qs with bad times");
			
			questionTypeSelectionMethod = QuestionTypeSelectionMethod.BAD_TIMES_LIST;
			
			if (nonPassedQuestions.size() >= QUESTION_SELECTION_BAD_TIMES_BUCKET_SIZE)
				nextQuestion = nonPassedQuestions.get(nonPassedQuestions.size()-QUESTION_SELECTION_BAD_TIMES_BUCKET_SIZE+random.nextInt(QUESTION_SELECTION_BAD_TIMES_BUCKET_SIZE));
			else
				logger.debug("QuestionManager.moveToNextQuestion: nonPassedQuestions list is not big enough, trying another strategy");
				
		}
		else
		{
			// selecting a random Q which has not been asked for ages
		}
		
		if (nextQuestion == null || nextQuestion == currentQuestion)
		{
			logger.debug("QuestionManager.moveToNextQuestion: selecting a random Q which has not been asked for ages");
			questionTypeSelectionMethod = QuestionTypeSelectionMethod.LEAST_RECENTLY_ASKED_LIST;

			int maxIndex = (int)Math.floor(QUESTION_SELECTION_LEAST_RECENTLY_ASKED_BUCKET_PERCENTAGE * allQuestions.size() / 100d);

			// only use this method if maxIndex>4, otherwise there's no element of randomness and we'd just keep getting 
			// questions in the same order as last time, which we want to avoid
			if (maxIndex > 4 && maxIndex <= allQuestions.size())
			{	
				int rnd = random.nextInt(maxIndex);
				nextQuestion = allQuestions.get(rnd);
			}
			else
				logger.debug("QuestionManager.moveToNextQuestion: not enough questions (maxIndex="+maxIndex+"), trying another strategy");
		}

		while (nextQuestion == null || nextQuestion == currentQuestion)
		{
			logger.debug("QuestionManager.moveToNextQuestion: fallback - selecting a random question from the entire list");
			// select 
			questionTypeSelectionMethod = QuestionTypeSelectionMethod.RANDOM;

			nextQuestion = allQuestions.get(random.nextInt(allQuestions.size()));
			
		}
		
		logger.trace("moveToNextQuestion: nextQuestion = "+nextQuestion+", with answer \""+nextQuestion.question.getAnswer()+"\"");
		
		currentQuestion = nextQuestion;
		lastQuestionPreviousButOneScore = lastQuestionPreviousScore;
		lastQuestionPreviousScore = Scorer.getQuestionScore(currentQuestion);
		nextQuestion.timeLastAsked = new Date();
		nextQuestion.totalTimesAsked++;
		firstAttemptAtQuestion = true;
	}
	
	/**
	 * Called when the user has provided an answer for the current question. Returns <code>true</code> 
	 * if the answer is correct. Records information about whether the question was answered correctly, 
	 * and advances to the next question. 
	 * @param answer
	 * @param timeToAnswer In milliseconds
	 * @param characterTimes A list of times (in millis) taken to type each 
	 * character of the answer. Used as part of an ongoing estimate of what 
	 * grace period to allow for each character in the answer. 
	 * @return
	 * @throws IllegalArgumentException If the specified answer is not merely incorrect but actually not a permitted answer 
	 * (this doesn't count as a wrong answer)
	 */
	public AnswerOutcome answerQuestion(String answer, long timeToAnswer, List<Long> characterTimes) throws IllegalArgumentException
	{
		boolean result = currentQuestion.question.isAnswerCorrect(answer, caseSensitive);
		
		logger.debug("answerQuestion - "+((result) ? "correct" : "wrong!"));
		
		if (result)
		{
			// Do time adjustments
			if (getAverageTimePerCharacter() > 0) {
				logger.debug("Time to answer          = "+timeToAnswer+"ms");
				timeToAnswer = timeToAnswer - currentQuestion.question.getAnswer().replaceAll(" ", "").length() * getAverageTimePerCharacter();
				logger.debug("Adjusted time to answer = "+timeToAnswer+"ms");
			}
			
			if (timeToAnswer < 0) {
				timeToAnswer = 0; // wow, very quick!
			}
			
			// Only use the character times if the answer was given correctly - 
			// otherwise there will be lots of thinking time in there too
			if (result && timeToAnswer < getMaximumAnswerTime())
				adjustAverageTimePerCharacter(characterTimes);
			
			if (timeToAnswer > getMaximumAnswerTime())
				timeToAnswer = getMaximumAnswerTime();
			
			questionsAnswered++;
		}
		else
			timeToAnswer = getTimePenaltyForWrongAnswers();
		
		// Record the results of answering the question in the history (unless 
		// this is a second attempt, etc.)
		if (firstAttemptAtQuestion)
		{
			QuestionHistory history = currentQuestion;
			logger.debug("answerQuestion: history was:    "+history);
			history.averageTimeToAnswer = Utils.exponentialWeightedAverage(history.averageTimeToAnswer, timeToAnswer, 0.6f);
			
			if (history.passModeCounter > 0 && result) // decrement pass counter for priority Qs - but only if they got the right answer!
			{
				history.passModeCounter--;
				if (history.passModeCounter == 0) 
				{ 
					if (history.isPrioritized)
					{
						history.isPrioritized = false;
						prioritizedQuestions.remove(currentQuestion);
					}
					nonPassedQuestions.add(history);
				}
			}

			logger.debug("answerQuestion: history is now: "+history);
		}
		firstAttemptAtQuestion = false;

		lastQuestionScore = Scorer.getQuestionScore(currentQuestion);
		questionSetScores = null;

		// select next question if applicable
		if (result)
			moveToNextQuestion();
		
		return new AnswerOutcome(result, false, null);
	}
	
	/**
	 * Called when the user doesn't know the answer for the current question. 
	 * Returns the correct answer for display to the user, and advances to 
	 * the next question (which is probably the same one again, till they get 
	 * it right!). 
	 * @return The correct answer for this question. 
	 */
	public String passQuestion()
	{
		if (currentQuestion.passModeCounter == 0)
			nonPassedQuestions.remove(currentQuestion);
		
		currentQuestion.passModeCounter = PASS_COUNTER_VALUE;
		currentQuestion.averageTimeToAnswer = MAXIMUM_MILLIS_TO_RECORD_PER_ANSWER;
		
		lastQuestionScore = Scorer.getQuestionScore(currentQuestion);
		questionSetScores = null;
		
		// if the user now gets the right answer, don't reward them for it!
		firstAttemptAtQuestion = false;
		
		// Don't move on to next question
		
		// return the correct answer
		return currentQuestion.question.getAnswer();
	}
	
	
	/**
	 * Checks that the number of questions marked as prioritized is the minimum 
	 * of the number of passed/new questions and the maximum allowed prioritized Qs - 
	 * and prioritizes more questions if it is not. 
	 * 
	 */
	protected void checkPrioritizations()
	{
		if (prioritizedQuestions.size() < MAXIMUM_PRIORITIZED_QUESTIONS_BUCKET_SIZE)
		{
			logger.debug("checkPrioritization: trying to find question(s) to prioritize");
			
			try {
				
				// Before including new/never-asked questions, first try to fill the prioritized bucket 
				// with already-asked questions that were passed, since 
				// otherwise the newly added questions (perhaps hundreds at a time) would starve  
				// our ability to focus on fixing question the user "passes" - getting 
				// those fixed is more improtant than introducing new material
				
				for (QuestionHistory qh: allQuestions)
				{
					if (qh.passModeCounter > 0 && !qh.isPrioritized && qh.timeLastAsked != null)
					{
						logger.trace("checkPrioritization: prioritizing passed question "+qh.question);
						qh.isPrioritized = true;
						prioritizedQuestions.add(qh);
						
						if (prioritizedQuestions.size() >= MAXIMUM_PRIORITIZED_QUESTIONS_BUCKET_SIZE) return;
					}
				}
				
				for (QuestionHistory qh: allQuestions)
				{
					if (qh.passModeCounter > 0 && !qh.isPrioritized)
					{
						logger.trace("checkPrioritization: prioritizing new/never-asked "+qh.question);
						qh.isPrioritized = true;
						prioritizedQuestions.add(qh);
						
						if (prioritizedQuestions.size() >= MAXIMUM_PRIORITIZED_QUESTIONS_BUCKET_SIZE) return;
					}
				}
			} 
			finally 
			{
				logger.debug("checkPrioritization: total prioritized questions = "+prioritizedQuestions.size());
			}
			
		}
	}
	
	/**
	 * @return The time to assign to any wrong answer. In milliseconds. 
	 */
	protected long getTimePenaltyForWrongAnswers()
	{
		return WRONG_ANSWER_TIME_PENALTY;
	}
	
	/**
	 * @return The maximum amount of time that will be recorded for a user's 
	 * answer to a question; once this is reached the user is assumed to be 
	 * foolish or absent so to avoid skewing the stats too much the time 
	 * won't increase further. In milliseconds. 
	 */
	protected long getMaximumAnswerTime()
	{
		return MAXIMUM_MILLIS_TO_RECORD_PER_ANSWER;
	}
	
	protected long averageTimePerCharacter = 0;
	protected long getAverageTimePerCharacter()
	{
		return averageTimePerCharacter;
	}
	
	protected void adjustAverageTimePerCharacter(List<Long> characterTimes)
	{
		if (characterTimes == null || characterTimes.size() < 2)
			return;
		
		// ignore the first time, as it probably involves lots of 'thinking' 
		// time rather than just time taken to find the right key and press it
		
		long averageTimePerCharacter = 0;
		for (int i = 1; i < characterTimes.size(); i++) // ignore first
		{
			if (characterTimes.get(i) < MAXIMUM_MILLIS_TO_ALLOW_PER_CHARACTER) // cap the maximum time to keep it sensible
				averageTimePerCharacter += characterTimes.get(i);
		}
		averageTimePerCharacter = averageTimePerCharacter / (characterTimes.size()-1);
		
		logger.debug("adjustAverageTimePerCharacter(): Old averageTimePerCharacter = "+this.averageTimePerCharacter+"ms");
		
		// Use the average of all but the first if 
		// initially zero, then after that use an exponentially-weighted 
		// moving average.
		this.averageTimePerCharacter = Utils.exponentialWeightedAverage(
				this.averageTimePerCharacter, averageTimePerCharacter, 0.15f);

		logger.debug("adjustAverageTimePerCharacter(): New averageTimePerCharacter = "+this.averageTimePerCharacter+"ms");

	}

	/**
	 * @return <code>true</code> if it's OK to display a timer to the user; 
	 * <code>false</code> if the time isn't going to be recorded and therefore 
	 * doesn't matter. 
	 */
	public boolean shouldDisplayTimer()
	{
		return firstAttemptAtQuestion;
	}
	
	public String getSessionStatus()
	{
		if (questionsAnswered == 0) return "";
		return " - answered "+questionsAnswered+" in "+((System.currentTimeMillis()-startTimeMillis)/1000/60)+" mins";
	}
}
