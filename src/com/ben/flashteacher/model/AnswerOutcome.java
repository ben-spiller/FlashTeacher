package com.ben.flashteacher.model;

/**
 * A structure to hold details about whether a question was answered correctly. 
 * If not, it may be able to provide hints as to why not. 
 * @author Ben
 */
public class AnswerOutcome
{
	protected AnswerOutcome(boolean correct, boolean isOneCharacterShort, String answerIsForThisQuestion)
	{
		this.correct = correct;
		//this.isOneCharacterShort = isOneCharacterShort;
		//this.answerIsForThisQuestion = answerIsForThisQuestion;
	}
	protected boolean correct;
	public boolean isCorrect()
	{
		return correct;
	}
	
	//protected String answerIsForThisQuestion;
	/**
	 * @return <code>null</code>, unless this is the correct answer, but the 
	 * question is wrong
	 */
	/*
	public String getQuestionForThisAnswer()
	{
		return answerIsForThisQuestion;
	}
	
	protected boolean isOneCharacterShort;
	public boolean isOneCharacterShort()
	{
		return isOneCharacterShort;
	}*/
}
