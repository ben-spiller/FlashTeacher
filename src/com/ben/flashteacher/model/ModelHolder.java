package com.ben.flashteacher.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JPanel;

import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.DocType;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Text;

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
	 * exception is thrown here, then getOptions() method may be called safely 
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
	 * @param questionFieldPanel The UI for the question field in case a plugin 
	 * wants to customize it. 
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	public void load(JPanel questionFieldPanel) throws IOException
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

		// Stash the plugin class names we had before, as to keep things simple we 
		// don't support changing the plugin class names each time the question file is loaded
		Set<String> pluginClasses = new HashSet<>(plugins.keySet());
		
		int question = 0; // for error messages
		List<Question> allQuestions = new ArrayList<>();
		for (Element questionElement: (List<Element>)questionListElement.getChildren())
		{
			if ("plugin".equals(questionElement.getName()))
			{
				Map<String, String> props = new HashMap<>();
				if (questionElement.getTextNormalize().trim().length() != 0)
					props.put("xmlText", questionElement.getTextNormalize().trim());
				for (Object a: questionElement.getAttributes())
					props.put( ((Attribute)a).getName(), ((Attribute)a).getValue());
				for (Element prop: (List<Element>)questionElement.getChildren())
					if ("property".equals(prop.getName()))
						props.put(prop.getAttributeValue("name"), prop.getAttributeValue("value"));
					else
						throw new IOException("Unexpected element under plugin: '"+prop.getName()+"'");
				
				logger.info("Loading plugin with properties: "+props);
				
				String className = props.remove("class");
				Plugin p = plugins.get(className);
				if (p == null)
					try
					{
						logger.info("Creating plugin class: "+className);
						Class<?> c = Class.forName(className);
						p = (Plugin)c.newInstance();
						plugins.put(className, p); // add it here in case there are exceptions later
					} catch (Exception ex)
					{
						throw new IOException("Cannot instantiate <plugin> class: "+ex, ex);
					}
				try {
					for (Question q: p.loadQuestions(questionFile, props, questionFieldPanel))
						allQuestions.add( q);
				} catch (Exception ex)
				{
					throw new IOException("Plugin failed to load questions: "+ex, ex);
				}
				logger.info("Loaded "+allQuestions.size()+" questions using plugin");
				
			} else if (!"question".equals(questionElement.getName()))
			{
				throw new IOException("Unknown element: "+questionElement.getName());
			} else { // <question> element
				
				question++;
				String questionText = questionElement.getChildTextNormalize("questionText");
				
				StringBuilder answerText = new StringBuilder();
				Element answerTextElement = questionElement.getChild("answerText");
				if (answerTextElement != null)
					for (Content c: (List<Content>)answerTextElement.getContent())
					{
						String contentText = null;
						if (c instanceof Text)
							contentText = ((Text)c).getTextNormalize();
						else if (c instanceof Element)
							contentText = ((Element)c).getTextNormalize();
						// otherwise ignore
						
						if (contentText != null)
						{
							//if (isAnswerRightToLeft)
							//	answerText.insert(0, contentText);
							//else
								answerText.append(contentText);
						}
					}
				
				if (questionText == null || answerText.length() == 0) // probably never happens due to DTD validation
					throw new IOException("Invalid question file - question or answer value #"+question+" is null");
				
				allQuestions.add(new Question(questionText, answerText.toString(), options.isCaseSensitive));
			}
		}


		if (!pluginClasses.isEmpty() && !plugins.keySet().equals(pluginClasses))
			throw new RuntimeException("The question file was changed to have different plugin classes; please restart the application after making such changes");
		
		logger.info("Loaded questions in "+(System.currentTimeMillis()-time2)+" ms.");

		try
		{
			options = new Options(questionListElement); // this will probably be v quick so no point reporting the time

			time2 = System.currentTimeMillis();
			qm = new QuestionManager(allQuestions, historyListElement, options, questionFieldPanel);
			
			long time3 = System.currentTimeMillis();
			logger.info("Loaded QuestionManager in "+(time3-time2)+" ms.");
			logger.info("Loaded entire model in "+(time3-time1)+" ms.");
		} catch (NumberFormatException e)
		{
			logger.error("Failed to load data from files: "+e.getMessage());
			logger.info("Stack trace is: ", e);
			throw new IOException("Invalid data encountered in XML file(s): "+e.getMessage(), e);
		}
		
		lastLoaded = new Date();
		logger.debug(getClass().getSimpleName()+".load() done");
	}
	
	/** This ensures we have a singleton of each plugin class (typically just one), 
	 * and also allows us to provide a shutdown mechanism, in case they have background threads or shared static state to dispose of. */
	private static final Map<String, Plugin> plugins = new HashMap<>();
	
	public static void shutdownPlugins()
	{
		for (Plugin p: plugins.values())
			p.close();
	}

	public void onQuestionChanged()
	{
		for (Plugin p: plugins.values())
			p.onQuestionChanged(getQuestionManager().getCurrentQuestion());
	}

	/** Checks if a answer just entered by the user is correct, using a plugin if there is one. 
	 * This also gives the plugin a chance to play extra sound etc in response to their answer. */
	public boolean checkAnswer(Question question, String answer)
	{
		for (Plugin p: plugins.values())
			return p.checkAnswer(question, answer);
		return question.isAnswerCorrect(answer);
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
