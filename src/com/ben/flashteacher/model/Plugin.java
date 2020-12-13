package com.ben.flashteacher.model;

import java.util.Collection;
import java.util.Map;

import org.jdom.Element;

/**
 * This interface can be implemented to allow dynamic/programmatic creation 
 * of Questions (based on plugin-specific configuration from a question file) 
 * and/or customization of the UI for displaying questions. 
 * 
 * Although each question must still be described by a string, it can be 
 * an internal/non-user visible string for plugins that use mediums such as 
 * sound or image for the questions. 
 * 
 * Answers must always be given as a string. 
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
	 * @param e The DOM element containing the "plugin"
	 * @param properties Any plugin configuration, typically specified using 
	 * XML attributes on the plugin element. 
	 * @return The list of questions. Plugin must ensure these are unique. 
	 */
	public Collection<Question> loadQuestions(Map<String, String> properties, Element e) throws Exception;
	
	/** Called during application shutdown to free any static OS resources (e.g. MIDI) 
	 * and/or background threads allocated by this plugin. */
	default public void onShutdown() {}
}
