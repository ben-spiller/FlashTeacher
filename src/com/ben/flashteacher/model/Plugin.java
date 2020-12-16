package com.ben.flashteacher.model;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import javax.swing.JPanel;

/**
 * This interface can be implemented to allow dynamic/programmatic creation 
 * of Questions (based on plugin-specific configuration from a question file) 
 * and/or customization of the UI for displaying questions. Plugins classes 
 * must provide a no-args public constructor. 
 * 
 * A single instance of the plugin is created for the lifetime of the process. 
 * Each time the question file needs to be loaded, {@link #loadQuestions(File, Map, JPanel)} 
 * is called, which acts as the entry point where plugins can initialize 
 * themselves.   
 * 
 * Although each question must still be described by a string, it can be 
 * an internal/non-user visible string for plugins that use mediums such as 
 * sound or image for the questions. Answers must always be given as a string. 
 * 
 * @author Ben
 */
public interface Plugin
{
	/**
	 * This method is called immediately after the plugin class is instantiated. 
	 * 
	 * A new instance of the plugin class is constructed every time the question 
	 * file is reloaded. 
	 * 
	 * @param questionFile The full path of the question file (in case you 
	 * want to load some other files relative to that location).
	 * @param properties Any plugin configuration, typically specified using 
	 * XML attributes on the plugin element. If any XML text is present in the 
	 * plugin node, it is available as an item with key xmlText. 
	 * @param questionFieldPanel The panel containing the question field. 
	 * Plugins can customize this by calling removeAll() to hide the usual 
	 * question text field and add additional controls (nothing that the parent 
	 * panel is configured as a GridBagLayout)
	 * @return The list of questions. Plugin must ensure these are unique. 
	 */
	public Collection<Question> loadQuestions(File questionFile, Map<String, String> properties, JPanel questionFieldPanel) throws Exception;
	
	/** 
	 * Notifies the plugin that a new question is being displayed. 
	 * This allows plugins to make a sound or update the questionFieldPanel 
	 * if they use a non-textual way of presenting questions. 
	 * 
	 * @param question
	 */
	default public void onQuestionChanged(Question question) {}
	
	/** Called during application shutdown to free any static OS resources (e.g. MIDI) 
	 * and/or background threads allocated by this plugin. */
	default public void close() {}
}
