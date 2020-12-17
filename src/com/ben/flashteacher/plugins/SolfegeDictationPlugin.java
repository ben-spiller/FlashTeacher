package com.ben.flashteacher.plugins;

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
import java.util.stream.Collectors;

import javax.sound.midi.*;
import java.awt.*;
import javax.swing.*;

import org.apache.log4j.Logger;
import com.ben.flashteacher.model.Plugin;
import com.ben.flashteacher.model.Question;

/**
 * A FlashTeacher plugin that asks questions by playing some notes (using MIDI) 
 * and requesting the user to convert them into solfege (do, re, me, etc) 
 * names.
 * 
 * @author Ben
 */
public class SolfegeDictationPlugin implements Plugin
{
	private static final Logger logger = Logger.getLogger(SolfegeDictationPlugin.class);

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
	
	Random random = new Random();
	
	/** True if this is the first question since this set of questions was loaded/started. */
	boolean isFirstQuestion;
	
	public Collection<Question> loadQuestions(File questionFile, Map<String, String> properties, JPanel questionFieldPanel) throws Exception
	{
		initMIDI();
		timeBetweenNotesMillisMin = timeBetweenNotesMillisMax = Integer.parseInt(properties.remove("timeBetweenNotesMillis"));

		doMin = doMax = Integer.parseInt(properties.remove("do")); // TODO: randomly pick a starting note within the range
		currentDo = doMin;
		isFirstQuestion = true;

		String[] instruments = properties.remove("instruments").toLowerCase().split(",");
		if (instruments.length==0) instruments = new String[] {"Piano"};
		
		midiPatches = new Patch[instruments.length];
		long startTime = System.currentTimeMillis();
		for (int p = 0; p < midiPatches.length; p++)
		{
			String patchName = instruments[p].toLowerCase().trim();
			for (Instrument i: allInstruments)
				if (i.getName().toLowerCase().contains(patchName))
				{
					midiPatches[p] = i.getPatch();
					logger.info("Found requested MIDI instrument '"+i.getName().trim()+"' at program "+i.getPatch().getProgram());
					break;
				}
			if (midiPatches[p] == null) throw new IllegalArgumentException("Cannot find any instrument name on this machine containing: \""+instruments[p]+"\"");
		}
		logger.debug("Loaded instruments in "+(System.currentTimeMillis()-startTime)+" ms");
			
		String[] solfegeValues = normalizeSolfegeString(properties.remove("solfegeValues")).split(" ");
		
		int notesPerQuestion = Integer.parseInt(properties.remove("notesPerQuestion"));

		if (!properties.isEmpty())
			throw new IllegalArgumentException("Unexpected plugin properties: "+properties.keySet());
		
		List<Question> qs = new ArrayList<>();
		generateQuestionsFor(new String[0], solfegeValues, notesPerQuestion, qs);
		
		initQuestionUI(new File(questionFile.getParentFile(), "solfege_hand_signs"), questionFieldPanel, solfegeValues, false);

		return qs;
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
		
		// TODO: will need some extra logic here to cope with additional do/re/me octaves. TODO: also we should make sure that solfegeValues doesn't include duplicates
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
		
		// we want to display all solfege values from low->high, not merely the ones that are included as possible answers 
		// (to avoid confusing user's mental model by having gaps)
		List<String> labels = new ArrayList<>();
		int solfegeIndex = allSolfege.indexOf(solfegeValues[0]);
		String finalDisplayedSolfege = solfegeValues[solfegeValues.length-1];
		if (allSolfege.indexOf(finalDisplayedSolfege) < 0) throw new RuntimeException("Assertion failure - cannot find '"+finalDisplayedSolfege+"'"); // to avoid infinite looping
		while (true)
		{
			labels.add(allSolfege.get(solfegeIndex));
			
			if (allSolfege.get(solfegeIndex).equals(finalDisplayedSolfege))
				break;
			
			solfegeIndex++;
			if (solfegeIndex == allSolfege.size()) solfegeIndex = 0;
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
			l.setIcon(solfegeIcons.get(s));
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
		return random.nextInt(1+from-to)+from;
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
				currentQuestionMidiSequence.addNote(currentDo+solfegeToSemitonesAboveDo(solfege), randomInt(timeBetweenNotesMillisMin, timeBetweenNotesMillisMin));
			currentQuestionMidiSequence.play();
		} catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}
	
	protected String normalizeSolfegeString(String s)
	{
		s = s.toLowerCase();
		// TODO: cope with optional lack of spaces
		String[] solfegeValues = s.split(" ");
		for (int i = 0; i < solfegeValues.length; i++)
			switch(solfegeValues[i])
			{
			case "t": solfegeValues[i] = "ti"; break;
			case "l": solfegeValues[i] = "la"; break;
			case "s": solfegeValues[i] = "so"; break;
			case "f": solfegeValues[i] = "fa"; break;
			case "m": solfegeValues[i] = "me"; break;
			case "r": solfegeValues[i] = "re"; break;
			case "d": solfegeValues[i] = "do"; break;
			case "ti": case "la": case "so": case "fa": case "me": case "re": case "do": break;
			default:
				throw new IllegalArgumentException("Expecting a solfege symbol such as 'do' but got: '"+solfegeValues[i]+"'");
			}
		return String.join(" ", solfegeValues);

	}
	
	static final String OCTAVE_DOWN = ",";
	static final String OCTAVE_UP = "'";
	String stripSolfegeOctaves(String s)
	{
		return s.replace(OCTAVE_DOWN, "").replace(OCTAVE_UP, "");
	}
	
	/**
	 * Assumes s is already normalized to lowercase
	 * @param s
	 * @return
	 */
	int solfegeToSemitonesAboveDo(String s)
	{
		int semitones = 0;
		// TODO: support multiple octaves here
		switch(s)
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

	@Override
	public void close()
	{
		if (sequencer == null) return;
		
		logger.info("Shutting down MIDI");
		sequencer.close();
		sequencer = null;
		logger.debug("Done shutting down MIDI");
	}
	
	/*
	private String normalizeSolfege(String x)
	{
		x = x.toLowerCase();
		String[] elements = (x.contains(" ")) ? x.split(" ") : x.split("");
		String result = "";
		for (e: elements)
		{
			int semitones = 0;
			switch(s)
			{
			case "t": case "ti": semitones += 2;
			case "l": case "la": semitones += 2;
			case "s": case "so": semitones += 2;
			case "f": case "fa": semitones += 1;
			case "m": case "me": semitones += 2;
			case "r": case "re": semitones += 2;
			case "d": case "do": break;
			default:
				throw new IllegalArgumentException("Expecting a solfege symbol such as 'do' but got: '"+s+"'");
			}

		}
		return result;
	}*/
	
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
			super(String.join(" ", solfege), stripSolfegeOctaves(String.join(" ", solfege)));
			logger.debug("Added question: "+this);
		}
		
		@Override
		protected boolean isAnswerCorrect(String answer, boolean caseSensitive)
		{
			// TODO: can support answers without spaces and the chromatic solfege symbols later, by normalizing the answer before validating it
			// TODO: test error handling here
			answer = stripSolfegeOctaves(answer);
			if (!answer.contains(" "))
				throw new IllegalArgumentException("Enter answers with spaces e.g. do re me");
			return super.isAnswerCorrect(answer, true);
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
    	    track = sequence.createTrack();

    	    contents.add("patch="+instrumentPatch.getProgram());
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
    		if (contents.size() > 2) logger.info("Playing MIDI sequence: "+contents);
    		
    		try {
	    	    sequencer.setSequence(sequence);
				sequencer.setTickPosition(0);
	            sequencer.setTempoInBPM(60.0f); // 1 beat per second
	    	    sequencer.start();
    		} catch (Exception ex)
    		{
    			logger.error("Cannot play midi sequence: ", ex);
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
			logger.error("Failed to play MIDI: ", ex);
			throw new RuntimeException(ex);
		}

	}
	
	protected Sequencer sequencer;
	protected Instrument[] allInstruments;
    
	void initMIDI() throws MidiUnavailableException, Exception
	{
		if (sequencer != null) return; // it's time-consuming, so use cached version from a previous load
		
		long startTime = System.currentTimeMillis();
		logger.debug("Loading MIDI sequencer");
        sequencer = MidiSystem.getSequencer();
        sequencer.open();
        logger.info("Loaded MIDI sequencer in "+(System.currentTimeMillis()-startTime)+" ms");
        
		if (logger.isDebugEnabled())
			for (MidiDevice.Info i : MidiSystem.getMidiDeviceInfo())
				logger.debug("Got MIDI device: "+i.getName());

		logger.debug("Loading MIDI synthesizer");
		startTime = System.currentTimeMillis();
		Synthesizer midiSynth = MidiSystem.getSynthesizer();
        logger.info("Loaded MIDI synthesizer in "+(System.currentTimeMillis()-startTime)+" ms");

        startTime = System.currentTimeMillis();
		
        allInstruments = midiSynth.getDefaultSoundbank().getInstruments();
		// NB: if we want to support other banks we need to use multiple ShortMessage.CONTROL messages to do it; probably not worth the hassle
		logger.info(allInstruments.length+" MIDI instruments are available in bank 0: "+
				Arrays.asList(allInstruments).stream().filter(i -> i.getPatch().getBank()==0) .map(i -> i.getName().trim()).collect(Collectors.joining(", ")));
        logger.info("Loaded MIDI instruments in "+(System.currentTimeMillis()-startTime)+" ms");

	}
	
	/**
	 * Not part of the plugin itself, but in this case it's quite handy to be able to 
	 * access the functionality of playing solfege notes in a standalone application too.  
	 */
	private void showStandaloneGUI()
	{
		logger.info("Loading as standalone application");
		try 
		{ 
			initMIDI();

			currentDo = doMin;
			currentPatch = allInstruments[0].getPatch();
	
			String[] solfegeValues = normalizeSolfegeString("do re me fa so la ti").split(" ");
	
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
			doNoteCombo.setSelectedItem(Note.find("C4"));
			contentPanePanel.add(doNoteCombo, new GridBagConstraints(
					1, y++, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0,0,SPACING,0), 0, 0
			));

			
		    questionFieldPanel.setFont(new Font("Tahoma", 0, 25));
			initQuestionUI(new File("question_files", "solfege_hand_signs"), questionFieldPanel, solfegeValues, true);
			frame.pack();
			frame.setVisible(true);
			
		} catch (Exception ex) {
			logger.error("Failed to initialize: ", ex);
			throw new RuntimeException(ex);
		}

	}
	public static void main(String[] args)
	{
		new SolfegeDictationPlugin().showStandaloneGUI();
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
				if (NOTES[i].displayName.equalsIgnoreCase(displayName)) return NOTES[i];
			throw new IllegalArgumentException("Unknown note: '"+displayName+"'; correct format for notes is C4, Bb6, etc");
		}
	}
}
