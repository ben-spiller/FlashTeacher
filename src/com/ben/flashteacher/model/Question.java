package com.ben.flashteacher.model;

import java.text.Collator;
import java.text.Normalizer;
import java.text.Normalizer.Form;

import org.apache.log4j.Logger;

class Question 
{
	static final Logger logger = Logger.getLogger(Question.class);
	
	private String question;
	private String answer;
	
	public Question(String question, String answer) {
		question = question.replaceAll("  *", " ").trim();
		answer = answer.replaceAll("  *", " ").trim();
		
		// perform unicode normalization in case of differences in ordering of accents, etc.
		// store in decomposed state
		this.question = Normalizer.normalize(question, Form.NFD);
		this.answer = Normalizer.normalize(answer, Form.NFD);
	}
	
	
	protected boolean isAnswerCorrect(String answer, boolean caseSensitive)
	{
		/*logger.trace("isAnswerCorrect: user answer = \""+answer+"\", correct answer = \""+this.answer+"\"");
		for (char x: answer.toCharArray())
			logger.trace("   char = "+x+" ("+((int)x)+")");
		logger.trace("correct answer is: ");
		for (char x: this.answer.toCharArray())
			logger.trace("   char = "+x+" ("+((int)x)+")");*/
		
		// do a proper comparison, ignoring accents, etc
		answer = Normalizer.normalize(answer.replaceAll("  *", " ").trim(), Form.NFD);

		String correctAnswer = this.answer;

		if (!caseSensitive)
		{
			correctAnswer = correctAnswer.toLowerCase();
			answer = answer.toLowerCase();
		}
		
		return collator.equals(correctAnswer, answer);
	}
	static final Collator collator = Collator.getInstance();
	
	public String getQuestion() {
		return question;
	}
	
	public String getAnswer() {
		return answer;
	}
	
	/** Returns true if the question text of each Question object is the same. 
	 * Ignores the answer. 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Question)
			return collator.equals( ((Question)obj).question, this.question);
		return false;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return question.hashCode();
	}
	
	@Override
	public String toString()
	{
		return "Question(\""+question+"\", \""+answer+"\")";
	}
}
