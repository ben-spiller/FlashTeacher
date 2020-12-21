package benspiller.flashteacher.model;

import org.apache.log4j.Logger;

class Scorer
{
	static final Logger logger = Logger.getLogger(Scorer.class);
	
	public static int getQuestionScore(QuestionHistory q)
	{
		int result = 30; // initial value if score is really bad
		if (q.passModeCounter == 0)
		{
			// assign the remaining 70% on a linear scale based on the average 
			// time to answer (assuming the maximum to be WRONG_ANSWER_TIME_PENALTY)
			// so if the user always gets it wrong it'll be 30%, if the time taken is 0 it'll be 100%
			result += (100-result) * (QuestionManager.WRONG_ANSWER_TIME_PENALTY-q.averageTimeToAnswer)/QuestionManager.WRONG_ANSWER_TIME_PENALTY;
		}
		else
		{
			// passCounter=3 -> result=0%
			// passCounter=1 -> result=20%
			result = result - (result * q.passModeCounter / QuestionManager.PASS_COUNTER_VALUE);
		}
		if (result < 0) result = 0;
		if (result > 100) result = 100;
		
		logger.info("Calculated score of "+result+" for question: "+q);

		return result;
	}
	
	public static QuestionSetScores getQuestionSetScores(QuestionManager qm)
	{
		QuestionSetScores result = new QuestionSetScores();
		
		for (QuestionHistory qh: qm.allQuestions)
		{
			result.totalQuestions++;
			
			if (qh.passModeCounter == 0)
			{
				if (qh.averageTimeToAnswer > QuestionManager.MAXIMUM_MILLIS_TO_RECORD_PER_ANSWER)
					result.wrongAnswers++;
				else if (qh.averageTimeToAnswer > 1*QuestionManager.MAXIMUM_MILLIS_TO_RECORD_PER_ANSWER/3)
					result.slowAnswers++;
				else
					result.quickAnswers++;
				
				result.averageTimeToAnswer += qh.averageTimeToAnswer;
			}
			else
				result.unknownAnswers++;
		}
		if (result.totalQuestions == 0) return result;
		
		int knownAnswers = result.totalQuestions - result.unknownAnswers;
		
		if (knownAnswers > 0)
			result.averageTimeToAnswer = result.averageTimeToAnswer / knownAnswers;
		
		result.averageTimePerCharacter = qm.getAverageTimePerCharacter();
		
		// now calculate derived values - the overall scores
		double timeToAnswerMetric = ( (QuestionManager.MAXIMUM_MILLIS_TO_RECORD_PER_ANSWER-(double)result.averageTimeToAnswer) / (double)QuestionManager.MAXIMUM_MILLIS_TO_RECORD_PER_ANSWER);
		
		logger.info("timeToAnswerMetric = "+timeToAnswerMetric);
		
		result.questionSetPercentScore = (
				// scale by the % of known answers, so max % is % of answers which are known (i.e. not passed)
				(100d * knownAnswers / result.totalQuestions) * 
				// of this percentage, scale linearly by the difference between avg time and max time (for a correct answer)
				timeToAnswerMetric
				);
		logger.info("questionSetPercentScore = "+result.questionSetPercentScore);
		if (result.questionSetPercentScore < 0) result.questionSetPercentScore = 0;
		if (result.questionSetPercentScore > 100) result.questionSetPercentScore = 100;
		
		result.knowledgeIndexScore = (
				knownAnswers * (0.5f + 0.5f*timeToAnswerMetric) );
		logger.info("knowledgeIndexScore = "+result.knowledgeIndexScore);
		if (result.knowledgeIndexScore < 0) result.knowledgeIndexScore = 0;

		// ... and the percentages
		result.unknownAnswersPercent = 100d*result.unknownAnswers/result.totalQuestions;
		result.wrongAnswersPercent = 100d*result.wrongAnswers/result.totalQuestions;
		result.slowAnswersPercent = 100d*result.slowAnswers/result.totalQuestions;
		result.quickAnswersPercent = 100d*result.quickAnswers/result.totalQuestions;
		
		return result;
	}
}
