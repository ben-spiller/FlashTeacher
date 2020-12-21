/**
 * 
 */
package benspiller.flashteacher.model;

import org.jdom.Element;

/**
 * A structure specifying scoring information for all the questions in the 
 * question list (corpus).
 *  
 * @author Ben
 */
public class QuestionSetScores
{
	public static final String ELEMENT_NAME = "previousQuestionSetScores";
	
	// sort Qs into buckets
	public int unknownAnswers; // whether 'passed' or new questions
	public int wrongAnswers;
	public int slowAnswers;
	public int quickAnswers;
	
	public int totalQuestions;

	// perentages for buckets
	public double unknownAnswersPercent; 
	public double wrongAnswersPercent;
	public double slowAnswersPercent;
	public double quickAnswersPercent;
	
	/**
	 * Average time to answer questions (excluding unknown/new answers).  
	 */
	public long averageTimeToAnswer; 
	
	public long averageTimePerCharacter;
	
	/**
	 * A score indicating how well-known the corpus is, out of 100
	 */
	public double questionSetPercentScore;
	
	/**
	 * A score indicating how many words are known and how well they're 
	 * known, with no upper bound
	 */
	public double knowledgeIndexScore;
	
	public QuestionSetScores()
	{
		// leave everything with a zero value
	}
	
	/**
	 * Load QuestionSetScores from an XML element.
	 * @param previousScoresElement May be <code>null</code>.
	 */
	public QuestionSetScores(Element previousScoresElement)
	{
		if (previousScoresElement == null)
			return;
		
		unknownAnswers = Integer.valueOf(previousScoresElement.getAttributeValue("unknownAnswers", "0"));
		wrongAnswers = Integer.valueOf(previousScoresElement.getAttributeValue("wrongAnswers", "0"));
		slowAnswers = Integer.valueOf(previousScoresElement.getAttributeValue("slowAnswers", "0"));
		quickAnswers = Integer.valueOf(previousScoresElement.getAttributeValue("quickAnswers", "0"));

		totalQuestions = Integer.valueOf(previousScoresElement.getAttributeValue("totalQuestions", "0"));

		if (totalQuestions > 0)
		{
			unknownAnswersPercent = 100d*unknownAnswers/totalQuestions;
			wrongAnswersPercent = 100d*wrongAnswers/totalQuestions;
			slowAnswersPercent = 100d*slowAnswers/totalQuestions;
			quickAnswersPercent = 100d*quickAnswers/totalQuestions;
		}
		
		averageTimeToAnswer = Long.valueOf(previousScoresElement.getAttributeValue("averageTimeToAnswer", "0"));
		averageTimePerCharacter = Long.valueOf(previousScoresElement.getAttributeValue("averageTimePerCharacter", "0"));

		questionSetPercentScore = Double.valueOf(previousScoresElement.getAttributeValue("questionSetPercentScore", "0"));
		knowledgeIndexScore = Double.valueOf(previousScoresElement.getAttributeValue("knowledgeIndexScore", "0"));
	}
	
	public Element saveToXMLElement()
	{
		Element result = new Element(ELEMENT_NAME);
		result.setAttribute("unknownAnswers", String.valueOf(unknownAnswers));
		result.setAttribute("wrongAnswers", String.valueOf(wrongAnswers));
		result.setAttribute("slowAnswers", String.valueOf(slowAnswers));
		result.setAttribute("quickAnswers", String.valueOf(quickAnswers));
		result.setAttribute("totalQuestions", String.valueOf(totalQuestions));
		result.setAttribute("averageTimeToAnswer", String.valueOf(averageTimeToAnswer));
		result.setAttribute("averageTimePerCharacter", String.valueOf(averageTimePerCharacter));
		result.setAttribute("questionSetPercentScore", String.valueOf(questionSetPercentScore));
		result.setAttribute("knowledgeIndexScore", String.valueOf(knowledgeIndexScore));

		return result;
	}	
}
