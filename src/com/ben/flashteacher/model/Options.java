package com.ben.flashteacher.model;

import java.awt.Font;

import org.jdom.Element;

/**
 * A model class containing options for flash teacher. At present 
 * there is a different instance of this object associated with 
 * every file for translation. 
 * 
 * @author Ben
 */
public class Options 
{
	protected String answerFontFamily;
	protected String questionFontFamily;
	protected int answerFontSize;
	protected int questionFontSize;
	boolean isAnswerRightToLeft;
	boolean isCaseSensitive;

	public Options(Element optionsElement)
	{
		answerFontFamily = optionsElement.getAttributeValue("answerFontFamily", Font.DIALOG_INPUT);
		questionFontFamily = optionsElement.getAttributeValue("questionFontFamily", Font.DIALOG);
		answerFontSize = Integer.parseInt(optionsElement.getAttributeValue("answerFontSize")); // see questionList.dtd for default
		questionFontSize = Integer.parseInt(optionsElement.getAttributeValue("questionFontSize"));
		isAnswerRightToLeft = Boolean.valueOf(optionsElement.getAttributeValue("isAnswerRightToLeft"));
		isCaseSensitive = Boolean.valueOf(optionsElement.getAttributeValue("isCaseSensitive"));
	}
	
	public String getQuestionFontFamily()
	{
		return questionFontFamily;
	}
	
	public int getQuestionFontSize()
	{
		return questionFontSize;
	}	
	
	public String getAnswerFontFamily()
	{
		return answerFontFamily;
	}
	
	public int getAnswerFontSize()
	{
		return answerFontSize;
	}

	public boolean isAnswerRightToLeft()
	{
		return isAnswerRightToLeft;
	}

	public boolean isCaseSensitive()
	{
		return isCaseSensitive;
	}
	
}
