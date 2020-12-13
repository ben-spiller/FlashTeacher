package com.ben.flashteacher.model;

import java.util.Comparator;
import java.util.Date;

import org.jdom.Element;

/**
 * Holds the information about previous performance on a question from the 
 * question set. Also knows how to serialize/deserialize itself from XML.  
 * @author Ben
 */
class QuestionHistory
{
	public static final String ELEMENT_NAME = "question";
	
	/**
	 * Set to some number X when the question is passed (and to 1 when a 
	 * question is newly added. Question is treated as high priority until 
	 * this is reduced to 0, as the user probably can't get it without 
	 * clicking the pass button. 
	 */
	public int passModeCounter;
	
	/**
	 * Average time to answer this question, already adjusted for the number 
	 * of characters in the answer. This time is also affected by any wrong 
	 * answers given. 
	 */
	public long averageTimeToAnswer;
	
	/**
	 * A small number of questions are prioritized until their performance 
	 * is improved, above other questions at a similar performance level. 
	 */
	public boolean isPrioritized;
	
	public Date timeLastAsked;
	
	public final Question question;
	
	/**
	 * Creates a QuestionHistory for a newly-added/updated question. 
	 */
	public QuestionHistory(Question question)
	{
		this.passModeCounter = 1;
		this.averageTimeToAnswer = 0;
		this.isPrioritized = false;
		this.timeLastAsked = null;
		this.question = question;
	}
	
	/**
	 * Creates a QuestionHistory for a pre-existing question, using the 
	 * information from the specified XML node. 
	 * @param questionHistoryElement
	 */
	public QuestionHistory(Question question, Element questionHistoryElement)
	{
		this.passModeCounter = Integer.valueOf(questionHistoryElement.getAttributeValue("passModeCounter", "1"));
		this.averageTimeToAnswer = Long.valueOf(questionHistoryElement.getAttributeValue("averageTimeToAnswer", "0"));
		this.isPrioritized = Boolean.valueOf(questionHistoryElement.getAttributeValue("isPrioritized", "false"));
		long timeLastAsked = Long.valueOf(questionHistoryElement.getAttributeValue("timeLastAsked", "0"));
		if (timeLastAsked > 0)
			this.timeLastAsked = new Date(timeLastAsked);
		else
			this.timeLastAsked = null;
		this.question = question;
		
	}
	
	public Element saveToXMLElement()
	{
		Element result = new Element(ELEMENT_NAME);
		result.setAttribute("passModeCounter", String.valueOf(passModeCounter));
		result.setAttribute("averageTimeToAnswer", String.valueOf(averageTimeToAnswer));
		result.setAttribute("isPrioritized", String.valueOf(isPrioritized));
		result.setAttribute("questionText", question.getQuestion());
		result.setAttribute("answerText", question.getAnswer());
		if (timeLastAsked == null)
			result.setAttribute("timeLastAsked", String.valueOf(0));
		else
			result.setAttribute("timeLastAsked", String.valueOf(timeLastAsked.getTime()));

		return result;
	}
	
	@Override
	public String toString()
	{
		return "QuestionHistory("+
		"\""+question.getQuestion()+"\", "+
		"passModeCounter="+passModeCounter+", "+
		"isPrioritized="+isPrioritized+", "+
		"averageTimeToAnswer="+averageTimeToAnswer+", "+
		"timeLastAsked="+timeLastAsked+
		")";
	}
	
	/** Determines equality based on the equality of the question it contains 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj)
	{
		if (obj instanceof QuestionHistory)
			return ((QuestionHistory)obj).question.equals(question);
		return false;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode()
	{
		return question.hashCode();
	}
	
	/**
	 * A comparator that orders QuestionHistory objects with those asked most 
	 * recently at the end of the list.  
	 */
	public static final Comparator<QuestionHistory> TIME_LAST_ASKED_COMPARATOR = new Comparator<QuestionHistory>(){
		public int compare(QuestionHistory o1, QuestionHistory o2)
		{
			long o1TimeLastAsked = (o1.timeLastAsked == null) ? 0 : o1.timeLastAsked.getTime();
			long o2TimeLastAsked = (o2.timeLastAsked == null) ? 0 : o2.timeLastAsked.getTime();
			
			if (o1TimeLastAsked < o2TimeLastAsked)
				return -1; // o1 comes before o2 if o1 has an earlier (lower) time
			else if (o1TimeLastAsked > o2TimeLastAsked)
				return +1;
			else
				return 0;
		}
	};
	
	/**
	 * A comparator that orders QuestionHistory objects with the worst (longest) 
	 * average answer times at the end of the list.  
	 */
	public static final Comparator<QuestionHistory> AVERAGE_TIME_TO_ANSWER_COMPARATOR = new Comparator<QuestionHistory>(){
		public int compare(QuestionHistory o1, QuestionHistory o2)
		{
			if (o1.averageTimeToAnswer < o2.averageTimeToAnswer)
				return -1; // o1 comes before o2 if o1 has a better (lower) time
			else if (o1.averageTimeToAnswer > o2.averageTimeToAnswer)
				return +1;
			else
				return 0;
		}
	};	
}
