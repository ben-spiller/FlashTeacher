package com.ben.flashteacher;

import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import org.apache.log4j.Logger;

import com.ben.flashteacher.model.AnswerOutcome;
import com.ben.flashteacher.model.ModelHolder;
import com.ben.flashteacher.model.Options;
import com.ben.flashteacher.model.QuestionManager;
import com.ben.flashteacher.utils.AbstractResourceAction;

public class QuestionWindow extends JFrame {
	private static final long serialVersionUID = 1L;

	final Logger logger = Logger.getLogger(getClass().getName());
	
	protected static final Color ANSWER_COLOR_STANDARD = Color.BLACK; 
	protected static final Color ANSWER_COLOR_CORRECT = Color.BLUE; 
	protected static final Color ANSWER_COLOR_WRONG = Color.RED; 
	protected static final Color ANSWER_COLOR_PASSED = Color.ORANGE; 
	
	protected static final int TEXT_FIELD_FLASH_PERIOD = 400;
	protected static final int MOVE_TO_NEXT_QUESTION_TIMEOUT = 5000;
	protected static final int UPDATE_TIME_TAKEN_PERIOD = 100;
	
	protected ModelHolder model;
	
	final JTextPane questionField;
	final JTextField answerField;
	
	final JButton startStopButton;
	final JButton passButton;
	//final JButton aboutButton;
	
	final JLabel statusLabel;
	
	final JProgressBar statusProgressBar;
	
	final SimpleAttributeSet questionAttributes = new SimpleAttributeSet();
	
	final TextFieldFlasher answerFieldFlasher;
	/**
	 * Moves out of the answered correct/wrong states after a few seconds
	 */
	final Timer moveStateTimer;

	/**
	 * Updates the time taken to answer the current question when in answering 
	 * mode. 
	 */
	final Timer updateTimeTimer;

	/**
	 * Not actually a Java Timer, but a simple class that counts up the time 
	 * taken to answer questions, and also to enter individual characters. 
	 */
	final AnswerTimer answerTimer = new AnswerTimer();
	
	/**
	 * <code>true</code> if at least one question has been answered in this run 
	 * - used to determine whether to save the history. 
	 */
	private boolean someQuestionsAnswered = false;
	
	PerformanceWindow performanceWindow = null;
	
	public QuestionWindow(Window owner) {
		super();//owner, ModalityType.APPLICATION_MODAL);
		setTitle(Messages.getString("QuestionWindow.title")); //$NON-NLS-1$
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		setIconImages( Constants.APPLICATION_ICONS );

		moveStateTimer = new Timer(MOVE_TO_NEXT_QUESTION_TIMEOUT, new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				switch (currentState)
				{
				case AnsweredCorrect:
				case AnsweredWrong:
					moveToState(States.Answering);
					break;
					
				default:
					logger.warn("Not expecting moveStateTimer to be called from state "+currentState);
				}
			}
		});
		moveStateTimer.setRepeats(false);
		
		updateTimeTimer = new Timer(UPDATE_TIME_TAKEN_PERIOD, new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				if (currentState != States.Answering) return;
				
				if (model.getQuestionManager().shouldDisplayTimer())
					statusLabel.setText(Messages.getString("QuestionWindow.statusLabel.text."+States.Answering, answerTimer.getTimeSoFar()/1000.0D, model.getQuestionManager().getQuestionSelectionMethod()));
				else
					statusLabel.setText(Messages.getString("QuestionWindow.statusLabel.text."+States.Answering+".noTimer", model.getQuestionManager().getQuestionSelectionMethod()));
			}
		});
		updateTimeTimer.setInitialDelay(0);
		
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				logger.info("windowClosing");
				exitAction.actionPerformed(null);
			}
		
			@Override
			public void windowClosed(WindowEvent e)
			{
				logger.info("windowClosed");
				
				QuestionManager.shutdownPlugins();
				 
				// Move to the stopped state, so that timers are stopped, etc. 
				// and so we try to save any state
				moveToState(States.ReadyToStart);
				logger.info("windowClosed: closed main window.");
			}
		
		});
		
		questionField = new JTextPane();
	    StyleConstants.setAlignment(questionAttributes, StyleConstants.ALIGN_CENTER);
	    questionField.setParagraphAttributes( questionAttributes, false);
	    questionField.setEditable(false);

		answerField = new JTextField();
		answerField.setDisabledTextColor(answerField.getForeground());
		answerFieldFlasher = new TextFieldFlasher(answerField, TEXT_FIELD_FLASH_PERIOD);
		answerField.addKeyListener(answerTimer);
		
		startStopButton = new JButton(startAction);
		
		passButton = new JButton(Messages.getString("QuestionWindow.passButton.text")); //$NON-NLS-1$
		passButton.setMnemonic('P');
		passButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				moveToState(States.PassedQuestion);
			}
		
		});

		//aboutButton = new JButton(aboutAction);

		statusProgressBar = new JProgressBar(0, 100);
		statusProgressBar.setPreferredSize(new Dimension(40, statusProgressBar.getPreferredSize().height));
		statusProgressBar.setMinimumSize(statusProgressBar.getPreferredSize());
		
		statusLabel = new JLabel();
		statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
		
		final int SPACING = 15;
		JPanel contentPanePanel = new JPanel(new GridBagLayout());
		contentPanePanel.setBorder(BorderFactory.createEmptyBorder(SPACING, SPACING, SPACING, SPACING));
		
		setContentPane(contentPanePanel);
		
		JPanel questionFieldPanel = new JPanel(new GridBagLayout());
		questionFieldPanel.setBackground(questionField.getBackground());
		questionFieldPanel.setBorder(BorderFactory.createLoweredBevelBorder());
		questionFieldPanel.add(questionField, new GridBagConstraints(
				0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(5,20,5,20), 0, 0
		));
		
		contentPanePanel.add(questionFieldPanel, new GridBagConstraints(
				0, 0, 5, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0,0,SPACING,0), 0, 0
		));
		contentPanePanel.add(answerField, new GridBagConstraints(
				0, 1, 5, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0,0,SPACING,0), 0, 0
		));
		contentPanePanel.add(startStopButton, new GridBagConstraints(
				0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,SPACING), 0, 0
		));
		contentPanePanel.add(passButton, new GridBagConstraints(
				1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,SPACING), 0, 0
		));
		

		contentPanePanel.add(statusLabel, new GridBagConstraints(
				2, 2, 1, 1, 1.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0, 0
		));

		contentPanePanel.add(statusProgressBar, new GridBagConstraints(
				3, 2, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.HORIZONTAL, new Insets(0,SPACING,0,0), 0, 0
		));

		/*contentPanePanel.add(aboutButton, new GridBagConstraints(
				4, 2, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,SPACING,0,0), 0, 0
		));*/

		setJMenuBar(createMenuBar());
		
		contentPanePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
				KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
    			"handleEnterKeyPress");
		contentPanePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
				KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
    			"handleSpaceKeyPress");
		
		contentPanePanel.getActionMap().put("handleEnterKeyPress", enterKeyPressedAction);
		contentPanePanel.getActionMap().put("handleSpaceKeyPress", spaceKeyPressedAction);

		setPreferredSize(new Dimension(750, 300));
		pack();
		setLocationRelativeTo(owner);
		
	}
	
	@SuppressWarnings("serial")
	protected JMenuBar createMenuBar()
	{
		JMenuBar menuBar = new JMenuBar();
		JMenu menu;
		
		menuBar.add(menu = new JMenu(new AbstractResourceAction("QuestionWindow.menus.file") {
			@Override public void handleActionPerformed(ActionEvent e) {} 
		}));
		
		menu.add(new JMenuItem(showPerformanceWindowAction));
		
		menu.add(new JSeparator());
		
		menu.add(new JMenuItem(exitAction));

		menuBar.add(menu = new JMenu(new AbstractResourceAction("QuestionWindow.menus.help") {
			@Override public void handleActionPerformed(ActionEvent e) {} 
		}));
		menu.add(new JMenuItem(aboutAction));
		return menuBar;
	}

	protected States currentState;
	
	/**
	 * Must be called before showing the dialog each time. 
	 * @param model Assumes load() has already been called
	 */
	public void initialize(ModelHolder model)
	{
		logger.debug(getClass().getSimpleName()+" initialize");
	
		this.model = model;
		// try to load the model now so we can initialize the display, but if 
		// there's a problem don't tell the user till they press Start
		try {
			model.loadOptionsOnly();
			initializeFromOptions();
		} catch (IOException e)
		{
			logger.info("Failed to load model in initialize() - will try again when user presses Start");
		}
		
		currentState = States.ReadyToStart;
		moveToState(currentState);
		
		setLocationRelativeTo(getOwner());
	}
	
	/**
	 * Initializes this window using the options specified in the model. This 
	 * method assumes that the model has been loaded. 
	 */
	protected void initializeFromOptions()
	{
		Options options = model.getOptions();
	    StyleConstants.setFontFamily(questionAttributes, options.getQuestionFontFamily());
	    StyleConstants.setFontSize(questionAttributes, options.getQuestionFontSize());
	    questionField.setParagraphAttributes( questionAttributes, false);
	    
	    Font answerFont = new Font(options.getAnswerFontFamily(), 0, options.getAnswerFontSize());
	    answerField.setFont(answerFont);
	    if (options.isAnswerRightToLeft())
	    	answerField.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
	    else
	    	answerField.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);

	}
	
	protected void moveToState(States newState)
	{
		final States oldState = this.currentState;
		logger.info("Moving from state "+oldState+" to "+newState);
		
		this.currentState = newState;
		
		// Disable running flash timer, if any
		answerFieldFlasher.stopFlashing();
		
		// Disable any pending state change
		moveStateTimer.stop();
		updateTimeTimer.stop();
		
		Object[] statusParam = new Object[]{0.0D};
		
		switch (newState)
		{
		case ReadyToStart:			
			questionField.setText(Messages.getString("QuestionWindow.readyMessage")); //$NON-NLS-1$
			answerField.setText("");
			answerField.setEnabled(false);
			answerField.setForeground(ANSWER_COLOR_STANDARD);
			aboutAction.setEnabled(true);
			passButton.setEnabled(false);
			statusProgressBar.setVisible(false);
			
			startStopButton.setAction(startAction);
			break;
			
		case Answering:
			questionField.setText(model.getQuestionManager().getCurrentQuestion());
			
			// leave the old text in place if they answered wrongly
			if (oldState != States.AnsweredWrong)
			{
				answerField.setText("");
				
			}
			else
				answerField.selectAll();

			// make sure we don't allow the 'space' keypress which caused the 
			// calling of the current method to result in an initial space
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					answerField.setText(answerField.getText().trim());
				}
			});

			answerField.setEnabled(true);
			answerField.setForeground(ANSWER_COLOR_STANDARD);
			passButton.setEnabled(true);
			aboutAction.setEnabled(false);
			statusProgressBar.setVisible(false);

			answerTimer.startTiming();
			updateTimeTimer.start();
			break;
			
		case AnsweredCorrect:
			int score = model.getQuestionManager().getQuestionScore(); // percentage score
			statusParam = new Object[]{score, score-model.getQuestionManager().getQuestionPreviousScore()};
			answerField.setEnabled(false);
			answerField.setForeground(ANSWER_COLOR_CORRECT);
			passButton.setEnabled(false);
			aboutAction.setEnabled(false);
			answerFieldFlasher.startFlashing();
			moveStateTimer.start();
			statusProgressBar.setVisible(true);
			statusProgressBar.setValue(score);
			someQuestionsAnswered = true;

			break;
			
		case AnsweredWrong:
			answerField.setEnabled(false);
			answerField.setForeground(ANSWER_COLOR_WRONG);
			answerField.requestFocusInWindow();
			answerFieldFlasher.startFlashing();
			moveStateTimer.start();
			passButton.setEnabled(false);
			aboutAction.setEnabled(false);
			statusProgressBar.setVisible(false);

			someQuestionsAnswered = true;

			break;
			
		case PassedQuestion:
			answerField.setText(model.getQuestionManager().passQuestion());
			answerField.setEnabled(false);
			answerField.setForeground(ANSWER_COLOR_PASSED);
			answerField.requestFocusInWindow();
			answerFieldFlasher.startFlashing();
			passButton.setEnabled(false);
			aboutAction.setEnabled(false);
			statusProgressBar.setVisible(false);

			break;
		default:
			assert(false);

		}
		
		statusLabel.setText(Messages.getString("QuestionWindow.statusLabel.text."+currentState.name(), statusParam)); //$NON-NLS-1$
		
		if (newState != States.ReadyToStart)
			startStopButton.setAction(stopAction);
		
		answerField.setDisabledTextColor(answerField.getForeground());

		// may need to save at this point
		if (newState == States.ReadyToStart)
			try {
				if (oldState != States.ReadyToStart && someQuestionsAnswered)
				{
					logger.info("Saving question history");
					model.saveHistory();
					someQuestionsAnswered = false;
				}
				else
					logger.debug("NOT saving question history (someQuestionsAnswered="+someQuestionsAnswered+")");
			} catch (IOException ioe)
			{
				JOptionPane.showMessageDialog(this, ioe.getMessage(), "Failed to save question history", JOptionPane.ERROR_MESSAGE);
			}

		if (newState == States.ReadyToStart)
			startStopButton.requestFocusInWindow();
		else
			answerField.requestFocusInWindow();
		
		String title = Messages.getString("QuestionWindow.title"); //$NON-NLS-1$
		if (model.isLoaded() && currentState != States.ReadyToStart)
			title += model.getQuestionManager().getSessionStatus();
		setTitle(title);
		
	}
	
	final Action startAction = new AbstractResourceAction("QuestionWindow.startAction") { 
		private static final long serialVersionUID = 0L;
		
		public void handleActionPerformed(ActionEvent e)
		{
			logger.debug("QuestionWindow.startAction");
			if (currentState != States.ReadyToStart)
			{
				logger.warn("QuestionWindow.startAction called, but currentState="+currentState);
				return;
			}
			try {
				model.load();
			} catch (IOException ioe)
			{
				JOptionPane.showMessageDialog(QuestionWindow.this, ioe.getMessage(), "Invalid File Format", JOptionPane.ERROR_MESSAGE);
				return;
			}
			moveToState(States.Answering);
		}
	
	};
	final Action stopAction = new AbstractResourceAction("QuestionWindow.stopAction") {
		private static final long serialVersionUID = 0L;
		public void handleActionPerformed(ActionEvent e)
		{
			logger.debug("QuestionWindow.stopAction");

			boolean displayPerformanceWindow = true; //someQuestionsAnswered;
			
			moveToState(States.ReadyToStart);
			
			if (displayPerformanceWindow)
				showPerformanceWindowAction.actionPerformed(e);
		}
	};

	
	final Action enterKeyPressedAction = new AbstractAction() {
		private static final long serialVersionUID = 0L;
		public void actionPerformed(ActionEvent e)
		{
			logger.debug("QuestionWindow.enterKeyPressedAction");

			switch (currentState)
			{
				case ReadyToStart:
					startAction.actionPerformed(null);
					break;
					
				case AnsweredCorrect:
				case AnsweredWrong: 
				case PassedQuestion:
					moveToState(States.Answering);
					break;
					
				case Answering:
					// ignore it if the user hasn't actually entered anything
					if (answerField.getText().trim().length() == 0)
						break;
					
					List<Long> characterTimes = new ArrayList<Long>();
					long timeToAnswer = answerTimer.stopTiming(characterTimes);
					String correctAnswer = model.getQuestionManager().getCurrentAnswer();
					
					try {
						AnswerOutcome outcome = model.getQuestionManager().answerQuestion(answerField.getText(), timeToAnswer, characterTimes);
						if (outcome.isCorrect())
						{
							// display the canonical version, which may or may not be the same (e.g. capitalization may be incorrect yet allowed in user answer)
							answerField.setText(correctAnswer); 
							moveToState(States.AnsweredCorrect);
						}
						else
							moveToState(States.AnsweredWrong);
					} catch (IllegalArgumentException ex) {
						JOptionPane.showMessageDialog(
								QuestionWindow.this, 
								"This answer was not entered in the expected format: "+ex.getMessage(), 
								"Invalid answer", 
								JOptionPane.ERROR_MESSAGE);

					}
					break;
			}
			
		}
	};	
	
	final Action spaceKeyPressedAction = new AbstractAction() {
		private static final long serialVersionUID = 0L;
		public void actionPerformed(ActionEvent e)
		{
			logger.debug("QuestionWindow.spaceKeyPressedAction");

			switch (currentState)
			{
			case AnsweredCorrect:
			case AnsweredWrong: 
			case PassedQuestion:
				enterKeyPressedAction.actionPerformed(e);
				break;
				
			case Answering:
			case ReadyToStart:
				// ignore spaces in these cases
				break;
			}
		}
	};	

	final Action exitAction = new AbstractResourceAction("QuestionWindow.actions.file.exit") {
		private static final long serialVersionUID = 0L;
		public void handleActionPerformed(ActionEvent e)
		{
			logger.debug("QuestionWindow.exitAction");
			if (!canCloseWindow()) return;
			
			// return to start state so that we save history, etc.
			moveToState(States.ReadyToStart);
			
			// Dispose this window, which should close the application
			logger.info("windowClosing: closing main window");
			dispose();

		}
	};
	final Action aboutAction = new AbstractResourceAction("QuestionWindow.actions.help.about") {
		private static final long serialVersionUID = 0L;
		AboutDialog aboutDialog = null;
		public void handleActionPerformed(ActionEvent e)
		{
			logger.debug("QuestionWindow.closeAction");
			if (aboutDialog == null) aboutDialog = new AboutDialog(QuestionWindow.this);
			aboutDialog.setLocationRelativeTo(QuestionWindow.this);
			aboutDialog.setVisible(true);
		}
	};
	
	final Action showPerformanceWindowAction = new AbstractResourceAction("QuestionWindow.actions.file.showPerformanceWindow") {
		private static final long serialVersionUID = 0L;
		public void handleActionPerformed(ActionEvent e)
		{
			logger.debug("QuestionWindow.showPerformanceWindow");
			if (performanceWindow == null) performanceWindow = new PerformanceWindow(QuestionWindow.this);
			if (!model.isLoaded())
			{
				try {
					model.load();
				} catch (IOException ioe)
				{
					JOptionPane.showMessageDialog(QuestionWindow.this, ioe.getMessage(), "Invalid File Format", JOptionPane.ERROR_MESSAGE);
					return;
				}
				performanceWindow.initialize(
						model.getQuestionManager().getPreviousQuestionSetScores(), 
						model.getQuestionManager().getPreviousQuestionSetScores(), 
						model.getQuestionManager().getKnowledgeIndexHistory());				
			}
			else
			{
				model.getQuestionManager().calculateScores();
				performanceWindow.initialize(
						model.getQuestionManager().getPreviousQuestionSetScores(), 
						model.getQuestionManager().getQuestionSetScores(), 
						model.getQuestionManager().getKnowledgeIndexHistory());
			}
			performanceWindow.setLocationRelativeTo(QuestionWindow.this);
			performanceWindow.setVisible(true);
		}
	};
	
	/**
	 * @return <code>true</code> if this window should be allowed to close now.  
	 */
	protected boolean canCloseWindow()
	{
		if (currentState == States.ReadyToStart)
			return true;
		
		return JOptionPane.showConfirmDialog(
				this, 
				"Are you sure you want to close this program?", 
				"Confirm Exit", 
				JOptionPane.OK_CANCEL_OPTION, 
				JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
	}

	private static class AnswerTimer implements KeyListener
	{
		private final Logger logger = Logger.getLogger(getClass().getName());
		
		private long startTime = 0;
		private long lastCharacterTime = 0;
		
		private List<Long> characterTimes = new ArrayList<Long>();
		
		public void startTiming()
		{
			logger.debug("startTiming()");
			startTime = System.currentTimeMillis();
			lastCharacterTime = startTime;
			characterTimes.clear();
		}
		
		/**
		 * @param characterTimes A list whose contents will be populated with 
		 * a list of the times (in milliseconds) taken to type each character, 
		 * ignoring spaces, backspaces, and characters following a backspace.   
		 * @return The total time taken before stopTiming() was called, in 
		 * milliseconds. 
		 */
		public long stopTiming(List<Long> characterTimes)
		{
			logger.debug("stopTiming()");
			if (startTime <= 0) return 0;
			
			long stopTime = System.currentTimeMillis();
			
			characterTimes.clear();
			characterTimes.addAll(this.characterTimes);
			
			long totalTime = stopTime - startTime;
			startTime = 0;
			
			logger.info("stopTiming: total time      = "+totalTime);
			logger.info("stopTiming: character times = "+characterTimes);
			
			return totalTime;
		}
		
		public long getTimeSoFar()
		{
			if (startTime > 0)
			{
				long time = System.currentTimeMillis() - startTime;
				//if (time > model.getQuestionManager().getMaximumAnswerTime())
				//	time = model.getQuestionManager().getMaximumAnswerTime();
				
				return time;
			}
			return 0;
		}

		public void keyPressed(KeyEvent e) { }
		public void keyReleased(KeyEvent e) { }

		/* (non-Javadoc)
		 * @see java.awt.event.KeyListener#keyTyped(java.awt.event.KeyEvent)
		 */
		public void keyTyped(KeyEvent e)
		{
			if (startTime < 0) return; // do nothing if stopped
			
			logger.debug("Got character: '"+e.getKeyChar()+"' ("+(int)e.getKeyChar()+")");
			long now = System.currentTimeMillis();

			if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE || e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyChar()==8) { 
				logger.debug("Ignoring delete/backspace keypress");
				lastCharacterTime = 0; // ignore the time of the next character
			}
			else if (e.getKeyCode() == KeyEvent.VK_ENTER) { 
				logger.debug("Ignoring enter keypress");
				lastCharacterTime = 0; // ignore the time of the next character
			}
			else 
			{
				if (lastCharacterTime > 0 && lastCharacterTime < now) // ignore 0 time periods, and also don't record when lastCharacterTime==0
				{
					lastCharacterTime = now-lastCharacterTime;
					
					if (Character.isWhitespace(e.getKeyChar()))
					{ 
						lastCharacterTime = now; 
					}
					else 
					{
						logger.debug("Adding character: '"+e.getKeyChar()+"' ("+(int)e.getKeyChar()+") with time: "+lastCharacterTime);
						characterTimes.add(lastCharacterTime);
						lastCharacterTime = now; 
					}
				}
			}
			
			
		}
		
		
	}

	
	/**
	 * A simple class that flashes the text of a text field on and off 
	 * periodically. 
	 */
	private static class TextFieldFlasher implements ActionListener
	{
		final JTextField textField;
		final Timer timer;
		String flashedText = null;
		
		public TextFieldFlasher(JTextField textField, int flashPeriod)
		{
			this.textField = textField;
			this.timer = new Timer(flashPeriod, this);
		}
		
		public void startFlashing()
		{
			timer.start();
		}
		
		public void stopFlashing()
		{
			timer.stop();
			if (flashedText != null)
			{
				textField.setText(flashedText);
				flashedText = null;
			}
		}
		
		public void actionPerformed(ActionEvent e)
		{
			if (!timer.isRunning()) return;
			if (flashedText != null)
			{
				textField.setText(flashedText);
				flashedText = null;
			}
			else
			{
				flashedText = textField.getText();
				textField.setText("");
			}
			
		}
	}
	
}

