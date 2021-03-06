package benspiller.flashteacher.plugins;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.sound.midi.*;
import java.awt.*;
import javax.swing.*;

import java.util.logging.Level;
import java.util.logging.Logger;

import benspiller.flashteacher.model.Plugin;
import benspiller.flashteacher.model.Question;

/**
 * A FlashTeacher plugin that asks questions by playing some notes (using MIDI) 
 * and requesting the user to convert them into solfege (do, re, me, etc) 
 * names.
 * 
 * @author Ben
 */
public class SolfegeDictationPlugin implements Plugin
{
	private static final Logger logger = Logger.getLogger(SolfegeDictationPlugin.class.getName());

	final Random random = new Random();
	
	/** True if this is the first question since this set of questions was loaded/started. */
	boolean isFirstQuestion;

	
	// Configuration properties:
	
	int timeBetweenNotesMillisMin = 800;
	int timeBetweenNotesMillisMax = timeBetweenNotesMillisMin;

	int doMin = 60;
	int doMax = doMin;
	
	/** The instrument patches selected in the question file. If more then one, a random one is picked for each question. */
	Patch[] midiPatches;
	
	/** The instrument patch used for this question. */
	Patch currentPatch;
	/** The pitch used for "do" in this session, where 60 = C4. */
	int currentDo;
	
	
	/** Null for none, else an instrument to continuously sound "do" */
	Patch dronePatch = null;
	
	/** The number of additional random notes to play after each question. 
	 * 
	 * This is useful for improving the ability to recognize one or more notes 
	 * without being confused by following notes. Accurately recognizing the initial note  
	 * when followed by some random ones is a good skill to build in isolation before increasing the 
	 * number of notes to be recognized. 
	 */
	int additionalRandomNotes;
	
	/** The possible solfege values in this question set; used for generating additional random notes */
	String[] possibleSolfegeValues;

	/*
	 * The entrypoint for plugins. 
	 */
	@Override
	public Collection<Question> loadQuestions(File questionFile, Map<String, String> properties, JPanel questionFieldPanel) throws Exception
	{
		initMIDI();
		String[] range = parseRange(properties.remove("noteDurationMillis"));
		timeBetweenNotesMillisMin = Integer.parseInt(range[0]);
		timeBetweenNotesMillisMax = Integer.parseInt(range[1]);
		
		range = parseRange(properties.remove("doNote"));
		doMin = Note.find(range[0]).midiNote;
		doMax = Note.find(range[1]).midiNote;
		currentDo = randomInt(doMin, doMax);
		
		isFirstQuestion = true; // so we can play the tonic anchor at the beginning

		String[] instruments = valueOrDefault(properties.remove("instruments"), "Piano").toLowerCase().split(",");
		
		long startTime = System.currentTimeMillis();

		additionalRandomNotes = Integer.valueOf(valueOrDefault(properties.remove("additionalRandomNotes"), "0"));
		
		String droneInstrument = properties.remove("droneInstrument");
		if (droneInstrument != null)
			for (Instrument i: allInstruments)
				if (i.getName().toLowerCase().contains(droneInstrument.toLowerCase().trim()))
				{
					dronePatch = i.getPatch();
					logger.log(java.util.logging.Level.INFO, "Found requested MIDI instrument '"+i.getName().trim()+"' at program "+i.getPatch().getProgram());
					droneInstrument = null;
					break;
				}
		if (droneInstrument != null && droneInstrument.length()>0 && dronePatch == null)
			throw new IllegalArgumentException("Cannot find instrument for drone: "+droneInstrument);
			
		midiPatches = new Patch[instruments.length];
		for (int p = 0; p < midiPatches.length; p++)
		{
			String patchName = instruments[p].toLowerCase().trim();
			for (Instrument i: allInstruments)
				if (i.getName().toLowerCase().contains(patchName))
				{
					midiPatches[p] = i.getPatch();
					logger.log(java.util.logging.Level.INFO, "Found requested MIDI instrument '"+i.getName().trim()+"' at program "+i.getPatch().getProgram());
					break;
				}
			if (midiPatches[p] == null) throw new IllegalArgumentException("Cannot find any instrument name on this machine containing: \""+instruments[p]+"\"");
		}
		logger.log(java.util.logging.Level.FINE, "Loaded instruments in "+(System.currentTimeMillis()-startTime)+" ms");
			
		String[] solfegeValues = normalizeSolfegeString(properties.remove("solfegeValues")).split(" ");
		possibleSolfegeValues = solfegeValues;
		
		int notesPerQuestion = Integer.parseInt(properties.remove("notesPerQuestion"));

		if (!properties.isEmpty())
			throw new IllegalArgumentException("Unexpected plugin properties: "+properties.keySet());
		
		List<Question> qs = new ArrayList<>();
		generateQuestionsFor(new String[0], solfegeValues, notesPerQuestion, qs);
		
		initQuestionUI(new File(questionFile.getParentFile(), "solfege_hand_signs"), questionFieldPanel, solfegeValues, false);

		return qs;
	}
	
	/** Returns the specified String unless it is null or empty, in which case defaultValue is returned instead. 
	 */
	static String valueOrDefault(String value, String defaultValue)
	{
		if (value == null || value.trim().length()==0) return defaultValue;
		return value;
	}
	
	/** Convert either or a single value or a min-max range into an array with two elements. */
	private static String[] parseRange(String x) {
		String[] result = x.replace(" ", "").split("-");
		if (result.length == 1) return new String[] {result[0], result[0]};
		return result;		
	}
	
	@Override
	public boolean checkAnswer(Question question, String answer)
	{
		// Don't bother to make user get the octave right, it'd an unnecessary distraction
		// The following will throw an exception if the answer isn't a valid solfege string
		boolean result = stripSolfegeOctaves(question.getAnswer()).equalsIgnoreCase(normalizeSolfegeString(stripSolfegeOctaves(answer)));

		// This is to avoid typos (pressing enter too early) counting as wrong answers
		if (stripSolfegeOctaves(question.getAnswer()).length() != normalizeSolfegeString(stripSolfegeOctaves(answer)).length())
			throw new IllegalArgumentException("There should be "+question.getAnswer().split(" ").length+" note(s) in the answer");
		
		// on correct answer, play it again to solidify the learning
		if (result)
			currentQuestionMidiSequence.play();
		
		return result;
	}
	
	
	@Override
	public void stop()
	{
		if (sequencer != null)
			sequencer.stop();
	}
	
	@Override
	public void close()
	{
		if (sequencer == null) return;
		
		logger.log(java.util.logging.Level.INFO, "Shutting down MIDI");
		sequencer.close();
		sequencer = null;
		logger.log(java.util.logging.Level.FINE, "Done shutting down MIDI");
	}

	
	
	protected Map<String, Icon> solfegeIcons = null;
	
	/**
	 * Initialize the UI by replacing the normal question textual display with 
	 * controls for playing individual solfege notes, in case the user wants to 
	 * remind themselves what "do" sounds like or try something out. 
	 * @param questionFieldPanel
	 * @param solfegeValues
	 * @param solfegePlayerOnly true if this UI is being used as a standalone application (not from FlashTeacher)
	 */
	protected void initQuestionUI(File handSignsDir, JPanel questionFieldPanel, String[] solfegeValues, boolean solfegePlayerOnly)
	{
		questionFieldPanel.removeAll();
		
		int y = 0;

		JLabel l;
		Insets insets = new Insets(0,20,0,5);
		
		List<String> allSolfege = Arrays.asList("do", "re", "me", "fa", "so", "la", "ti");
		if (solfegeIcons == null) // load on first use; save in a static to avoid having to dispose them
		{
			solfegeIcons = new HashMap<>();
			for (String s: allSolfege) {
				File f = new File(handSignsDir, s+".png");
				if (f.exists())
					solfegeIcons.put(s, new ImageIcon(f.getPath()));
			}
		}
		
		// We want to display all solfege values from low->high, not merely the ones that are included as possible answers 
		// (to avoid confusing user's mental model by having gaps)
		// Similarly, to avoid cognitive friction when user later adds additional notes, display one extra note above and below the range for the current question 
		final int SOLFEGE_BUFFER = (solfegePlayerOnly) ? 0 : 1; 
		
		int solfegeIndex = allSolfege.indexOf(stripSolfegeOctaves(solfegeValues[0]));
		solfegeIndex = (solfegeIndex-SOLFEGE_BUFFER + allSolfege.size()) % allSolfege.size();
		int solfegeOctave = getSolfegeOctaves(solfegeValues[0]);
		if (SOLFEGE_BUFFER == 1 && "ti".equals(allSolfege.get(solfegeIndex)))
			solfegeOctave -= 1;
		
		List<String> labels = new ArrayList<>();
		while (solfegeOctave <= getSolfegeOctaves(solfegeValues[solfegeValues.length-1])+1) // this condition is not normally used - it's just to make infinite loops impossible
		{
			String octaveSuffix = "";
			for (int i = 0; i < solfegeOctave; i++) octaveSuffix += OCTAVE_UP;
			for (int i = 0; i > solfegeOctave; i--) octaveSuffix += OCTAVE_DOWN;
			
			labels.add(allSolfege.get(solfegeIndex)+octaveSuffix);
			solfegeIndex += 1;
			
			if (labels.size() > SOLFEGE_BUFFER+1 && labels.get(labels.size()-1-SOLFEGE_BUFFER).equals(solfegeValues[solfegeValues.length-1]))
				break;
			
			if (solfegeIndex == allSolfege.size()) 
			{
				solfegeIndex = 0;
				solfegeOctave += 1;
			}
		}
		
		Collections.reverse(labels);
		
		for (final String s : labels)
		{
			l = new JLabel(s);
			l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			l.setHorizontalAlignment(SwingConstants.CENTER);
			l.setFont(questionFieldPanel.getFont());
			if (s.startsWith("do"))
				l.setFont(l.getFont().deriveFont(Font.BOLD).deriveFont(Collections.singletonMap(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON)));
			l.setIcon(solfegeIcons.get(stripSolfegeOctaves(s)));
			//l.setBorder(BorderFactory.createLineBorder(Color.black));
			
			l.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e)
				{
					playOneNote(currentDo + solfegeToSemitonesAboveDo(s));
				}
			});
			questionFieldPanel.add(l, new GridBagConstraints(
					1, y++, 2, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, insets, 0, 15
			));
		}
		
		if (solfegePlayerOnly) return;
		
		insets = new Insets(5,20,5,5);

		l = new JLabel("<html><br>Listen to this sequence, and enter the solfege symbols. \"Do\" is "+Note.NOTES[currentDo]+". <br>"
				+ "(e.g. \""+String.join(" ", solfegeValues)+"\"; the shorthand \"drm\" is equivalent to \"do re me\")</html>");
		questionFieldPanel.add(l, new GridBagConstraints(
				1, y++, 1, 1, 1.0, 0.0, GridBagConstraints.SOUTHWEST, GridBagConstraints.HORIZONTAL, insets, 0, 0
		));
		
		JButton playbutton = new JButton("Play again");
		playbutton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				currentQuestionMidiSequence.play();
			}
		});
		questionFieldPanel.add(playbutton, new GridBagConstraints(
				2, y-1, 1, 1, 0.0, 0.0, GridBagConstraints.SOUTHEAST, 0, insets, 0, 0
		));

	}
	
	/** Returns a random integer within the specified range inclusive */
	private int randomInt(int from, int to)
	{
		return random.nextInt(1+to-from)+from;
	}
	
	protected MidiSequenceBuilder currentQuestionMidiSequence;
	@Override
	public void onQuestionChanged(Question question)
	{
		// select instrument patch randomly per question
		currentPatch = midiPatches[random.nextInt(midiPatches.length)];

		try {
			currentQuestionMidiSequence = new MidiSequenceBuilder(currentPatch);
			
			if (isFirstQuestion)
			{
				// anchor the tonic for the first question
				currentQuestionMidiSequence.addNote(currentDo+solfegeToSemitonesAboveDo("do"), timeBetweenNotesMillisMin);
				currentQuestionMidiSequence.addNote(currentDo-1, timeBetweenNotesMillisMin); // "ti,"
				currentQuestionMidiSequence.addNote(currentDo+solfegeToSemitonesAboveDo("re"), timeBetweenNotesMillisMin);
				currentQuestionMidiSequence.addNote(currentDo+solfegeToSemitonesAboveDo("do"), timeBetweenNotesMillisMin);
				currentQuestionMidiSequence.addRest(timeBetweenNotesMillisMax);
				isFirstQuestion = false;
			}
			
			for (String solfege: question.getQuestion().split(" "))
				currentQuestionMidiSequence.addNote(currentDo+solfegeToSemitonesAboveDo(solfege), 
						randomInt(timeBetweenNotesMillisMin, timeBetweenNotesMillisMax));
			for (int i = 0; i < additionalRandomNotes; i++)
				currentQuestionMidiSequence.addNote(currentDo+solfegeToSemitonesAboveDo(possibleSolfegeValues[randomInt(0, possibleSolfegeValues.length-1)]), 
						randomInt(timeBetweenNotesMillisMin, timeBetweenNotesMillisMax));

			currentQuestionMidiSequence.play();
		} catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}
	
	/** Normalizes capitalization and single-letter short names in a string of multiple solfege values. Leaves octave designation in place. */
	protected String normalizeSolfegeString(String s)
	{
		s = s.toLowerCase();
		String[] solfegeValues = (s.contains(" ")) ? s.split(" ") : s.split("");
		
		// This is just in case s is a single solfege note (with no spaces). 
		// Not very a very efficient solution, but this method is currently not performance-critical
		try {
			return normalizeSolfegeNote(s);
		} catch (IllegalArgumentException ex)
		{
			// Do nothing, continue to following loop
		}
		
		for (int i = 0; i < solfegeValues.length; i++)
		{
			solfegeValues[i] = normalizeSolfegeNote(solfegeValues[i]);
		}
		return String.join(" ", solfegeValues);

	}
	
	protected String normalizeSolfegeNote(String note)
	{
		String v = note;
		Matcher m = OCTAVE_REGEX.matcher(v);
		String octaves = (m.find()) ? m.group() : "";
		v = v.replace(octaves, ""); // strip off octaves before re-adding after normalization
		switch(v)
		{
		case "t": v = "ti"; break;
		case "l": v = "la"; break;
		case "s": v = "so"; break;
		case "f": v = "fa"; break;
		case "m": v = "me"; break;
		case "r": v = "re"; break;
		case "d": v = "do"; break;
		case "ti": case "la": case "so": case "fa": case "me": case "re": case "do": break;
		default:
			throw new IllegalArgumentException("Expecting a solfege symbol such as 'do' but got: '"+note+"'");
		}
		return v + octaves;
	}

	
	static final String OCTAVE_DOWN = ",";
	static final String OCTAVE_UP = "'";
	static final Pattern OCTAVE_REGEX = Pattern.compile("["+OCTAVE_DOWN+OCTAVE_UP+"]+");
	static String stripSolfegeOctaves(String s)
	{
		return s.replaceAll(OCTAVE_REGEX.pattern(), "");
	}
	
	static int getSolfegeOctaves(String s)
	{
		return (s.length() - s.replace(OCTAVE_UP, "").length()) - (s.length() - s.replace(OCTAVE_DOWN, "").length());
	}
	
	/**
	 * Assumes s is already normalized to lowercase etc
	 * @param s
	 * @return
	 */
	int solfegeToSemitonesAboveDo(String s)
	{
		int semitones = 12*getSolfegeOctaves(s);

		switch(stripSolfegeOctaves(s))
		{
		case "ti": semitones += 2;
		case "la": semitones += 2;
		case "so": semitones += 2;
		case "fa": semitones += 1;
		case "me": semitones += 2;
		case "re": semitones += 2;
		case "do": break;
		default: 
			throw new IllegalArgumentException("Expecting a solfege symbol such as 'do' but got: '"+s+"'");
		}
		return semitones;
	}

	
	static class Note
	{
		final int midiNote;
		/** In "scientific" notation i.e. C4=middle C (NB: some MIDI software uses C3 or C5 for middle C) */
		final String displayName;
		Note(int midiNote)
		{
			this.midiNote = midiNote;
			String note;
			// For simplicity, don't support both # and b designations; b's is better to standardize 
			// on because it matches the names of the associated keys (e.g. Bb not A#)
			switch(midiNote % 12)
			{
			case 0: note = "C"; break;
			case 1: note = "Db"; break;
			case 2: note = "D"; break;
			case 3: note = "Eb"; break;
			case 4: note = "E"; break;
			case 5: note = "F"; break;
			case 6: note = "Gb"; break;
			case 7: note = "G"; break;
			case 8: note = "Ab"; break;
			case 9: note = "A"; break;
			case 10: note = "Bb"; break;
			case 11: note = "B"; break;
			default: throw new RuntimeException("Logic error in application");
			}
			int octave = -1 + (midiNote-(midiNote%12))/12;
			displayName = note+octave;
		}
		@Override
		public String toString()
		{
			return displayName+" (MIDI "+midiNote+")";
		}
		
		/** All possible MIDI notes */
		public static final Note[] NOTES;
		static {
			NOTES = new Note[127];
			for (int i = 0; i < 127; i++) NOTES[i] = new Note(i);
		}
		
		public static Note find(String displayName)
		{
			displayName = displayName.replace(" ", "");
			if (displayName.contains("#")) throw new IllegalArgumentException("Notes must be specified with 'b' not '#'");
			for (int i = 0; i < 127; i++) 
				if (NOTES[i].displayName.equalsIgnoreCase(displayName) || displayName.equals(String.valueOf(i))) return NOTES[i];
			throw new IllegalArgumentException("Unknown note: '"+displayName+"'; correct format for notes is C4, Bb6, etc");
		}
	}

	
	private void generateQuestionsFor(String[] accumulator, String[] possibleSolfege, int remainingNotes, List<Question> questions)
	{
		// since this is a combinatorial explosion, avoid generating too many
		if (questions.size() > 10*1000) throw new IllegalArgumentException("Cannot create this many questions - breached limit after "+questions.size());
		
		String[] x = Arrays.copyOf(accumulator, accumulator.length+1);
		for (int i = 0; i < possibleSolfege.length; i++)
		{
			// avoid repeated notes
			if (accumulator.length>0 && possibleSolfege[i] == accumulator[accumulator.length-1] )
				continue;
			
			x[x.length-1] = possibleSolfege[i];
			if (remainingNotes == 1) {
				questions.add(new SolfegeQuestion(x));
			}
			else
				generateQuestionsFor(x, possibleSolfege, remainingNotes-1, questions);
		}
	}

	class SolfegeQuestion extends Question
	{
		public SolfegeQuestion(String[] solfege) {
			super(String.join(" ", solfege), String.join(" ", solfege), false);
			logger.log(java.util.logging.Level.FINE, "Added question: "+this);
		}
	}
	
    class MidiSequenceBuilder
    {
    	private long lastTickMillis;
    	
    	private final Sequence sequence;
    	private final Track track;
    	private final List<String> contents = new ArrayList<>();
    	MidiSequenceBuilder(Patch instrumentPatch) throws Exception
    	{
            sequence = new Sequence(Sequence.PPQ, 1000); // each tick is 1/1000 of a second

    	    contents.add("patch="+instrumentPatch.getProgram());
    	    
    	    if (dronePatch != null) {
    	    	Track droneTrack = sequence.createTrack();
        	    int velocity = 127/2;
        	    // use an instrument that goes on forever
        	    droneTrack.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, 1, dronePatch.getProgram(), 0), 0));
        	    droneTrack.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 1, currentDo, velocity), lastTickMillis));
        	    droneTrack.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 1, currentDo, velocity), lastTickMillis+1000*60*60));
    	    }
    	    track = sequence.createTrack();
    	    track.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, instrumentPatch.getProgram(), 0), 0));
    	}
    	
    	MidiSequenceBuilder addNote(int note, int noteMillis) throws Exception
    	{
    	    contents.add("note="+note+" durationMillis="+noteMillis);
    	    int velocity = 127; // loudest
   		    track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, note, velocity), lastTickMillis));
   		    lastTickMillis += noteMillis;
   		    // depending on patch the note may die away naturally or it may not; either way we need a NOTE_OFF to make sure 
   		    // the final note doesn't get cut off immediately at the end of the sequence
   		    track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, note, velocity), lastTickMillis));
   		    return this;
    	}
    	MidiSequenceBuilder addRest(int noteMillis)
    	{
    		lastTickMillis += noteMillis;
    		contents.add("restMillis="+noteMillis);
    		return this;
    	}
    	/** Asynchronously play this sequence. Any currently playing sequence is implicitly stopped first. */
    	void play()
    	{
    		if (contents.size() > 2) logger.log(java.util.logging.Level.INFO, "Playing MIDI sequence: "+contents);
    		try {
	    	    sequencer.setSequence(sequence);
				sequencer.setTickPosition(0);
	            sequencer.setTempoInBPM(60.0f); // 1 beat per second
	    	    sequencer.start();
    		} catch (Exception ex)
    		{
    			logger.log(java.util.logging.Level.SEVERE, "Cannot play midi sequence: ", ex);
    			throw new RuntimeException(ex);
    		}
    	}
    }

	protected void playOneNote(int note)
	{
		try {
	        new MidiSequenceBuilder(currentPatch)
	        	.addNote(note, timeBetweenNotesMillisMax).play();
		} catch (Exception ex)
		{
			logger.log(java.util.logging.Level.SEVERE, "Failed to play MIDI: ", ex);
			throw new RuntimeException(ex);
		}

	}
	
	protected Sequencer sequencer;
	protected Instrument[] allInstruments;
    
	private long midiLastUsedTime;
	void initMIDI() throws MidiUnavailableException, Exception
	{
		// On Windows 10 MIDI sometimes gets into a bad state if devices change e.g. turning off/on speakers overnight. 
		// Since that's hard to detect, reset midi after 6 hours of non-use
		if (midiLastUsedTime != 0 && System.currentTimeMillis()-midiLastUsedTime > 1000*60*60*6) {
			logger.log(java.util.logging.Level.INFO, "Resetting MIDI as it's been several hours since it was last used");
			close();
		}
		midiLastUsedTime = System.currentTimeMillis();
		
		if (sequencer != null) return; // it's time-consuming, so use cached version from a previous load
		
		
		long startTime = System.currentTimeMillis();
		logger.log(java.util.logging.Level.FINE, "Loading MIDI sequencer");
        sequencer = MidiSystem.getSequencer();
        sequencer.open();
        logger.log(java.util.logging.Level.INFO, "Loaded MIDI sequencer in "+(System.currentTimeMillis()-startTime)+" ms");
        
		if (logger.isLoggable(Level.FINE))
			for (MidiDevice.Info i : MidiSystem.getMidiDeviceInfo())
				logger.log(java.util.logging.Level.FINE, "Got MIDI device: "+i.getName());

		logger.log(java.util.logging.Level.FINE, "Loading MIDI synthesizer");
		startTime = System.currentTimeMillis();
		Synthesizer midiSynth = MidiSystem.getSynthesizer();
        logger.log(java.util.logging.Level.INFO, "Loaded MIDI synthesizer in "+(System.currentTimeMillis()-startTime)+" ms");

        startTime = System.currentTimeMillis();
		
        allInstruments = midiSynth.getDefaultSoundbank().getInstruments();
		// NB: if we want to support other banks we need to use multiple ShortMessage.CONTROL messages to do it; probably not worth the hassle
		logger.log(java.util.logging.Level.INFO, allInstruments.length+" MIDI instruments are available in bank 0: "+
				Arrays.asList(allInstruments).stream().filter(i -> i.getPatch().getBank()==0) .map(i -> i.getName().trim()).collect(Collectors.joining(", ")));
        logger.log(java.util.logging.Level.INFO, "Loaded MIDI instruments in "+(System.currentTimeMillis()-startTime)+" ms");

	}
	
	/**
	 * Not part of the plugin itself, but in this case it's quite handy to be able to 
	 * access the functionality of playing solfege notes in a standalone application too.  
	 */
	private void showStandaloneGUI()
	{
		logger.log(java.util.logging.Level.INFO, "Loading as standalone application");
		try 
		{ 
			initMIDI();

			currentDo = Note.find("C3").midiNote;
			currentPatch = allInstruments[0].getPatch();
	
			// No room on screen for all of them! Focus on octave below and a few notes from octave above
			String[] solfegeValues = normalizeSolfegeString("do, re, me, fa, so, la, ti, do re me fa so la ti do' re' me'").split(" ");
			//String[] solfegeValues = normalizeSolfegeString("so, la, ti, do re me fa so la ti do' re' me' fa' so' la' ti' do''").split(" ");
	
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			
			JFrame frame = new JFrame("Solfege Player");
			frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	
			final int SPACING = 15;
			JPanel contentPanePanel = new JPanel(new GridBagLayout());
			contentPanePanel.setBorder(BorderFactory.createEmptyBorder(SPACING, SPACING, SPACING, SPACING));
			
			frame.setContentPane(contentPanePanel);
			
			JPanel questionFieldPanel = new JPanel(new GridBagLayout());
			questionFieldPanel.setBackground(Color.WHITE);
			questionFieldPanel.setBorder(BorderFactory.createLoweredBevelBorder());
			
			int y = 0;
			contentPanePanel.add(questionFieldPanel, new GridBagConstraints(
					0, y++, 5, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0,0,SPACING,0), 0, 0
			));

			contentPanePanel.add(new JLabel("Instrument:"), new GridBagConstraints(
					0, y, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0,0,SPACING,SPACING), 0, 0
			));
			JComboBox<Instrument> instrumentCombo = new JComboBox<>(allInstruments);
			instrumentCombo.setMaximumRowCount(20);
			instrumentCombo.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e)
				{
					currentPatch = instrumentCombo.getItemAt(instrumentCombo.getSelectedIndex()).getPatch();
					playOneNote(currentDo);
				}
			});
			contentPanePanel.add(instrumentCombo, new GridBagConstraints(
					1, y++, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0,0,SPACING,0), 0, 0
			));

			contentPanePanel.add(new JLabel("Note for 'do' (C4 = middle C):"), new GridBagConstraints(
					0, y, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0,0,SPACING,SPACING), 0, 0
			));
			JComboBox<Note> doNoteCombo = new JComboBox<>(Note.NOTES);
			doNoteCombo.setMaximumRowCount(20);
			doNoteCombo.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e)
				{
					currentDo = doNoteCombo.getItemAt(doNoteCombo.getSelectedIndex()).midiNote;
					playOneNote(currentDo);
				}
			});
			doNoteCombo.setSelectedItem(Note.find("C3"));
			contentPanePanel.add(doNoteCombo, new GridBagConstraints(
					1, y++, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0,0,SPACING,0), 0, 0
			));

			
		    questionFieldPanel.setFont(new Font("Tahoma", 0, 25));
			initQuestionUI(new File("question_files", "solfege_hand_signs"), questionFieldPanel, solfegeValues, true);
			frame.pack();
			frame.setVisible(true);
			
		} catch (Exception ex) {
			logger.log(java.util.logging.Level.SEVERE, "Failed to initialize: ", ex);
			close();
			throw new RuntimeException(ex);
		}

	}
	public static void main(String[] args)
	{
		new SolfegeDictationPlugin().showStandaloneGUI();
	}
	
}
