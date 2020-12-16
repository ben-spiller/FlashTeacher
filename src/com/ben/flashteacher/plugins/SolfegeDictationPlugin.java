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

// TODO: midi can take forever to initialize, so display progress if it does

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

	int timeBetweenNotesMillisMin;
	int timeBetweenNotesMillisMax;

	int doMin;
	int doMax;
	
	/** The instrument patches selected in the question file. If more then one, a random one is picked for each question. */
	Patch[] midiPatches;
	
	/** The instrument patch used for this question. */
	Patch currentPatch;
	/** The pitch used for "do" in this session, where 60 = C4. */
	int currentDo;
	
	Random random = new Random();
	
	public Collection<Question> loadQuestions(File questionFile, Map<String, String> properties, JPanel questionFieldPanel) throws Exception
	{
		initMIDI();
		timeBetweenNotesMillisMin = timeBetweenNotesMillisMax = Integer.parseInt(properties.remove("timeBetweenNotesMillis"));

		doMin = doMax = Integer.parseInt(properties.remove("do")); // TODO: randomly pick a starting note within the range
		currentDo = doMin;

		String[] instruments = properties.remove("instruments").toLowerCase().split(",");
		if (instruments.length==0) instruments = new String[] {"Piano"};
		
		midiPatches = new Patch[instruments.length];
		Instrument[] availableInstruments = midiSynth.getDefaultSoundbank().getInstruments();
		for (int p = 0; p < midiPatches.length; p++)
		{
			String patchName = instruments[p].toLowerCase().trim();
			for (Instrument i: availableInstruments)
				if (i.getName().toLowerCase().contains(patchName))
				{
					midiPatches[p] = i.getPatch();
					logger.info("Found requested MIDI instrument: "+i.getName().trim());
					break;
				}
			if (midiPatches[p] == null) throw new IllegalArgumentException("Cannot find any instrument name on this machine containing: \""+instruments[p]+"\"");
		}
		currentPatch = midiPatches[0]; // TODO: select randomly, per question
			
		String[] solfegeValues = normalizeSolfegeString(properties.remove("solfegeValues")).split(" ");
		
		/*
		int i = 0;
		for (String s: solfegeValues)
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
			semitonesFromDo[i++] = semitones;
		}
		*/
			
		int notesPerQuestion = Integer.parseInt(properties.remove("notesPerQuestion"));

		if (!properties.isEmpty())
			throw new IllegalArgumentException("Unexpected plugin properties: "+properties.keySet());
		
		List<Question> qs = new ArrayList<>();
		generateQuestionsFor(new String[0], solfegeValues, notesPerQuestion, qs);
		
		initQuestionUI(new File(questionFile.getParentFile(), "solfege_hand_signs"), questionFieldPanel, solfegeValues);

		return qs;
	}
	
	Map<String, Icon> solfegeIcons = null;
	
	/**
	 * Initialize the UI by replacing the normal question textual display with 
	 * controls for playing individual solfege notes, in case the user wants to 
	 * remind themselves what "do" sounds like or try something out. 
	 * @param questionFieldPanel
	 * @param solfegeValues
	 */
	void initQuestionUI(File handSignsDir, JPanel questionFieldPanel, String[] solfegeValues)
	{
		questionFieldPanel.removeAll();
		
		int y = 100;
		
		Insets insets = new Insets(5,20,5,5);
		JLabel l = new JLabel("<html><br>Listen to this sequence, and enter the solfege symbols. \"Do\" is "+midiNoteToDisplayName(currentDo)+". <br>"
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

		y = 0;
		
		// we want to display all solfege values from low->high, not merely the ones that are included as possible answers 
		// (to avoid confusing user's mental model)
		List<String> labels = new ArrayList<>();
		
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
		
		int solfegeIndex = allSolfege.indexOf(solfegeValues[0]);
		while (!allSolfege.get(solfegeIndex).equals(solfegeValues[solfegeValues.length-1]))
		{
			labels.add(allSolfege.get(solfegeIndex));
			solfegeIndex++;
			if (solfegeIndex == allSolfege.size()-1) solfegeIndex = 0;
		}
		Collections.reverse(labels);
		
		insets.bottom = insets.top = 0;
		
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
					try {
				        new MidiSequenceBuilder(currentPatch)
				        	.addNote(currentDo + solfegeToSemitonesAboveDo(s), timeBetweenNotesMillisMax).play();
					} catch (Exception ex)
					{
						logger.error("Failed to play MIDI: ", ex);
						throw new RuntimeException(ex);
					}
				}
			});
			questionFieldPanel.add(l, new GridBagConstraints(
					1, y++, 2, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, insets, 0, 15
			));
		}
	}
	
	/** Returns a random integer within the specified range inclusive */
	private int randomInt(int from, int to)
	{
		return random.nextInt(1+from-to)+from;
	}
	
	MidiSequenceBuilder currentQuestionMidiSequence;
	@Override
	public void onQuestionChanged(Question question)
	{
		// TODO: pick a patch at random
		try {
			currentQuestionMidiSequence = new MidiSequenceBuilder(currentPatch);
			// TODO: first question
			
			for (String solfege: question.getQuestion().split(" "))
				currentQuestionMidiSequence.addNote(currentDo+solfegeToSemitonesAboveDo(solfege), randomInt(timeBetweenNotesMillisMin, timeBetweenNotesMillisMin));
			currentQuestionMidiSequence.play();
		} catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}
	
	String normalizeSolfegeString(String s)
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
	
	String midiNoteToDisplayName(int note)
	{
		// TODO: make this work
		return "C4";
	}

	@Override
	public void close()
	{
		if (midiSynth == null) return;
		
		logger.info("Shutting down MIDI");
		midiSynth.close();
		midiSynth = null;
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
	
	protected Synthesizer midiSynth;
	protected Sequencer sequencer;
	
    protected static MidiEvent makeMidiEvent(int command, int channel, int note, int velocity, int tick) throws Exception
    { 
		ShortMessage a = new ShortMessage(); 
		a.setMessage(command, channel, note, velocity); 
		return new MidiEvent(a, tick); 	
    } 
	
    class MidiSequenceBuilder
    {
    	private long lastTickMillis;
    	
    	private final Sequence sequence;
    	private Track track;
    	MidiSequenceBuilder(Patch instrumentPatch) throws Exception
    	{
            sequence = new Sequence(Sequence.PPQ, 1000); // each tick is 1/1000 of a second
    	    track = sequence.createTrack();

    	    track.add(makeMidiEvent(ShortMessage.PROGRAM_CHANGE, 0, instrumentPatch.getProgram(), 0, 0));
    	}
    	
    	MidiSequenceBuilder addNote(int note, int noteMillis) throws Exception
    	{
    		logger.info("MIDI sequence: adding note "+note+" with duration "+noteMillis+" ms to "+sequence);
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
    		return this;
    	}
    	/** Asynchronously play this sequence. Any currently playing sequence is implicitly stopped first. */
    	void play()
    	{
    		try {
	    	    sequencer.setSequence((Sequence)null);
	    	    sequencer.setSequence(sequence);
	    	    sequencer.start();
    		} catch (Exception ex)
    		{
    			logger.error("Cannot play midi sequence: ", ex);
    			throw new RuntimeException(ex);
    		}
    	}

    }
    
	void initMIDI() throws MidiUnavailableException, Exception
	{
		if (midiSynth != null) return;

		long startTime = System.currentTimeMillis();

		logger.debug("Loading MIDI sequencer");
        sequencer = MidiSystem.getSequencer();
        sequencer.setTempoInBPM(60.0f); // 1 beat per second
        sequencer.open();
        logger.info("Loaded MIDI sequencer in "+(System.currentTimeMillis()-startTime)+" ms");

        startTime = System.currentTimeMillis();
		logger.debug("Loading MIDI synthesizer");
		// Gathering the instruments list takes a while; then initalizing the sequencer, even longer
		midiSynth = MidiSystem.getSynthesizer();
		midiSynth.open();
        logger.info("Loaded MIDI synthesizer in "+(System.currentTimeMillis()-startTime)+" ms");

        startTime = System.currentTimeMillis();
        Instrument[] instr = midiSynth.getDefaultSoundbank().getInstruments();
		// NB: if we want to support other banks we need to use multiple ShortMessage.CONTROL messages to do it; probably not worth the hassle
		logger.info(instr.length+" MIDI instruments are available in bank 0: "+
				Arrays.asList(instr).stream().filter(i -> i.getPatch().getBank()==0) .map(i -> i.getName().trim()).collect(Collectors.joining(", ")));
        logger.info("Loaded MIDI instruments in "+(System.currentTimeMillis()-startTime)+" ms");
		
	}
}
