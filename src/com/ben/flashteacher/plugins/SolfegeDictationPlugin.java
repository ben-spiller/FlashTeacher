package com.ben.flashteacher.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sound.midi.*;

import org.apache.log4j.Logger;
import org.jdom.Element;

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

	long timeBetweenNotesMillisMin;
	long timeBetweenNotesMillisMax;

	int doMin;
	int doMax;
	
	/** The instrument patches selected in the question file. If more then one, a random one is picked for each question. */
	Patch[] midiPatches;
	
	
	enum Solfege
	{

		Do(0),
		re(2),
		me(4),
		fa(5),
		so(7),
		la(9),
		ti(11);

		public final int semitonesAboveDo;
		Solfege(int semitonesAboveDo) { this.semitonesAboveDo=semitonesAboveDo; }
	}

	public Collection<Question> loadQuestions(Map<String, String> properties, Element e) throws Exception
	{
		initMIDI();
		timeBetweenNotesMillisMin = timeBetweenNotesMillisMax = Long.parseLong(properties.remove("timeBetweenNotesMillis"));

		doMin = doMax = Integer.parseInt(properties.remove("do")); // TODO: randomly pick a starting note within the range

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
			
        new MidiSequenceBuilder(midiPatches[0])
    		.addNote(60, 1000).addNote(60+12, 250).addNote(60+24, 750).play();

		// question = numbers, answer = words, and we normalize answer when checking
		String[] solfegeValues = properties.remove("solfegeValues").toLowerCase().split(" ");
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
		
		return qs;
	}
	
	/**
	 * Assumes s is already normalized to lowercase
	 * @param s
	 * @return
	 */
	/*
	int solfegeToSemitonesAboveDo(String s)
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
		return semitones;
	}*/

	@Override
	public void onShutdown()
	{
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
			super(String.join(" ", solfege), String.join(" ", solfege));
			logger.debug("Added question: "+this);
		}
		
		@Override
		protected boolean isAnswerCorrect(String answer, boolean caseSensitive)
		{
			// TODO: can support answers without spaces and the chromatic solfege symbols later, by normalizing the answer before validating it
			// TODO: test error handling here
			if (!answer.contains(" "))
				throw new IllegalArgumentException("Enter answers with spaces e.g. do re me");
			if (answer.contains(",") || answer.contains("'"))
				throw new IllegalArgumentException("Solfege symbols with ' and , not yet supported"); // will want to strip these out of the answer but not the question
			return super.isAnswerCorrect(answer, true);
		}
	}
	
	// Uses a static to avoid lifetime issues
	protected static Synthesizer midiSynth;
	protected static Sequencer sequencer;
	
    protected static MidiEvent makeMidiEvent(int command, int channel, int note, int velocity, int tick) throws Exception
    { 
		ShortMessage a = new ShortMessage(); 
		a.setMessage(command, channel, note, velocity); 
		return new MidiEvent(a, tick); 	
    } 
	
    static class MidiSequenceBuilder
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
    	void play() throws Exception
    	{
    	    sequencer.setSequence(sequence);
    	    sequencer.start();
    	}

    }
    
	static void initMIDI() throws MidiUnavailableException, Exception
	{
		if (midiSynth != null) return;

		long startTime = System.currentTimeMillis();

		logger.debug("Loading MIDI synthesizer");
		// Gathering the instruments list takes a while; then initalizing the sequencer, even longer
		midiSynth = MidiSystem.getSynthesizer();
		midiSynth.open();
		Instrument[] instr = midiSynth.getDefaultSoundbank().getInstruments();
		// NB: if we want to support other banks we need to use multiple ShortMessage.CONTROL messages to do it; probably not worth the hassle
		logger.info(instr.length+" MIDI instruments are available in bank 0: "+
				Arrays.asList(instr).stream().filter(i -> i.getPatch().getBank()==0) .map(i -> i.getName().trim()).collect(Collectors.joining(", ")));
		
		logger.debug("Loading MIDI sequencer");
        sequencer = MidiSystem.getSequencer();
        sequencer.setTempoInBPM(60.0f); // 1 beat per second
        sequencer.open();
        logger.info("Loaded MIDI system in "+(System.currentTimeMillis()-startTime)+" ms");
	}
}
