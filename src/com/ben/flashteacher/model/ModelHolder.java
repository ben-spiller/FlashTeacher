package com.ben.flashteacher.model;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import org.apache.log4j.Logger;

import org.jdom.DocType;
import org.jdom.Document;
import org.jdom.Element;

import com.ben.flashteacher.utils.XMLUtils;

/**
 * Handles all file loading and saving for the model - options, questions and 
 * question history. 
 * @author Ben
 */
public class ModelHolder
{
	public static final String DEFAULT_QUESTION_FILE_DIRECTORY = "question_files";
	public static final String EXTENSION_QUESTION_FILES = "questions.xml";
	public static final String EXTENSION_HISTORY_FILES = System.getProperty("user.name", "user").toLowerCase()+".questionHistory";

	final Logger logger = Logger.getLogger(getClass().getName());
	
	File questionFile;
	File optionsFile;
	File historyFile;
	public ModelHolder(File questionFile)
	{
		try {
			questionFile = questionFile.getCanonicalFile();
		} catch (IOException e) { /* ignore */ }
		
		this.questionFile = questionFile;
		this.optionsFile = new File(questionFile.getParentFile(), questionFile.getName().replace("."+EXTENSION_QUESTION_FILES, "")+".options");
		this.historyFile = new File(questionFile.getParentFile(), questionFile.getName().replace("."+EXTENSION_QUESTION_FILES, "")+"."+EXTENSION_HISTORY_FILES);
	}
	
	/*private File getHistoryFile()
	{
		return historyFile;
	}
	private File getQuestionFile()
	{
		return questionFile;
	}
	private File getOptionsFile()
	{
		return optionsFile;
	}*/
	
	private Date lastLoaded = null;
	
	/**
	 * @return <code>true</code> if everything has already been loaded without 
	 * error. Returns <code>false</code> after a load() that has failed until 
	 * a load() that succeeds. 
	 */
	public boolean isLoaded()
	{
		return lastLoaded != null;
	}
	
	/**
	 * Force the options only to be loaded. Throws an exception on error. If no 
	 * exception is thrown here, the getOptions() method may be called safely 
	 * without fear of exceptions. 
	 * @throws IOException
	 */
	public void loadOptionsOnly() throws IOException
	{
		logger.info(getClass().getSimpleName()+ ".loadOptions()");

		lastLoaded = null;
		
		Document document = XMLUtils.loadXML(questionFile);
		Element questionListElement = document.getRootElement();

		options = new Options(questionListElement);
		
		logger.debug(getClass().getSimpleName()+".loadOptions() done");
	}
	
	
	/**
	 * Force everything to be loaded. Throws an exception on error. If no 
	 * exception is thrown here, the other get* methods may be called safely 
	 * without fear of exceptions. 
	 * @throws IOException
	 */
	public void load() throws IOException
	{
		logger.info(getClass().getSimpleName()+ ".load()");
		long time1 = System.currentTimeMillis();
		// always reload - otherwise state of QM may be wrong (need to reset it otherwise)
		/*// If already loaded and modification date hasn't changed, don't bother to reload
		if (lastLoaded != null && lastLoaded.after(new Date(questionFile.lastModified())))
		{
			logger.info("ModelHolder is not reloading question file because it hasn't changed since last load");
			return;
		}*/
		
		lastLoaded = null;
		
		Document document = XMLUtils.loadXML(questionFile);
		Element questionListElement = document.getRootElement();

		Element historyListElement = null;

		if (historyFile.exists())
		{
			historyListElement = XMLUtils.loadXML(historyFile).getRootElement();
		}
		else
			logger.info("Not loading history file because none exists (\""+historyFile+"\"");

		long time2 = System.currentTimeMillis();
		logger.info("Loaded XML data in "+(time2-time1)+" ms.");
		
		try
		{
			options = new Options(questionListElement); // this will probably be v quick so no point reporting the time

			time2 = System.currentTimeMillis();
			qm = new QuestionManager(questionListElement, historyListElement, options);
			
			long time3 = System.currentTimeMillis();
			logger.info("Loaded QuestionManager in "+(time3-time2)+" ms.");
			logger.info("Loaded model in "+(time3-time1)+" ms.");
		} catch (NumberFormatException e)
		{
			logger.error("Failed to load data from files: "+e.getMessage());
			logger.info("Stack trace is: ", e);
			throw new IOException("Invalid data encountered in XML file(s): "+e.getMessage(), e);
		}
		
		lastLoaded = new Date();
		logger.debug(getClass().getSimpleName()+".load() done");
	}
	
	public void saveHistory() throws IOException
	{
		logger.info(getClass().getSimpleName()+".saveHistory()");
		
		Element rootElement = qm.save();
		Document doc = new Document(rootElement);
		doc.setDocType(new DocType(rootElement.getName(), "questionHistory.dtd"));

		XMLUtils.saveXML(doc, historyFile);
		
		logger.debug(getClass().getSimpleName()+".saveHistory() done");
	}
	
	private QuestionManager qm = null;
	public QuestionManager getQuestionManager()
	{
		if (qm == null)
			throw new IllegalStateException("Internal error - QuestionManager has not been successfully loaded yet");
		return qm;
	}
	
	private Options options = null;
	public Options getOptions() 
	{
		if (options == null)
			throw new IllegalStateException("Internal error - Options data has not been successfully loaded yet");
		return options;
	}
}
