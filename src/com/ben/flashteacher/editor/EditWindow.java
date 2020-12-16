package com.ben.flashteacher.editor;

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.log4j.Logger;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.StyledEditorKit.StyledTextAction;

import org.jdom.Document;
import org.jdom.Element;

import com.ben.flashteacher.Messages;
import com.ben.flashteacher.model.Options;
import com.ben.flashteacher.utils.AbstractResourceAction;
import com.ben.flashteacher.utils.SwingUtils;
import com.ben.flashteacher.utils.XMLUtils;

public class EditWindow extends JFrame {
	private static final long serialVersionUID = 1L;

	final Logger logger = Logger.getLogger(getClass().getName());
	
	final JTextPane questionField;
	final JTextPane answerField;
	
	final JButton newQuestionButton;
	final JToggleButton emButton; 
	
	final JList classList;
	final DefaultListModel classModel = new DefaultListModel();
	
	final SimpleAttributeSet questionAttributes = new SimpleAttributeSet();
	final SimpleAttributeSet answerAttributes = new SimpleAttributeSet();
	
	final JLabel statusLabel;
	
	protected Document xmlDocument;
	protected File questionFile;
	
	int questionCount = 0;
	
	public EditWindow(Window owner) {
		super();//owner, ModalityType.APPLICATION_MODAL);
		setTitle(Messages.getString("EditWindow.title")); //$NON-NLS-1$
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				logger.info("QuestionWindow.windowClosing");
				if (!canCloseWindow()) return;
				
				// Dispose this window, which should close the application
				dispose();
			}
		
			@Override
			public void windowClosed(WindowEvent e)
			{
				logger.info("QuestionWindow.windowClosed");
			}
		
		});
		
		questionField = new JTextPane();
	    questionField.setParagraphAttributes(questionAttributes, false);
	    questionField.setEditable(true);
	    NewLineFilter.register(questionField);

		answerField = new JTextPane();
		answerField.setParagraphAttributes(answerAttributes, false);
	    NewLineFilter.register(answerField);
		
		newQuestionButton = new JButton(newQuestionAction);
		newQuestionButton.setMnemonic('a');
		emButton = new JToggleButton(new EmAction(answerField));
		emButton.setMnemonic('e');
		
		statusLabel = new JLabel();
		statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		
		classList = new JList(classModel);
		classList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		final JTextField newClassField = new JTextField();

		final int SPACING = 15;

		JPanel classPanel = new JPanel(new BorderLayout(0, SPACING));
		classPanel.setPreferredSize(new Dimension(100, 100));
		classPanel.add(new JScrollPane(classList), BorderLayout.CENTER);
		classPanel.add(newClassField, BorderLayout.SOUTH);

		
		JPanel mainPanel = new JPanel(new GridBagLayout());
		
		mainPanel.add(new JScrollPane(questionField), new GridBagConstraints(
				0, 0, 4, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0,0,SPACING,0), 0, 0
		));
		mainPanel.add(new JScrollPane(answerField), new GridBagConstraints(
				0, 1, 4, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0,0,SPACING,0), 0, 0
		));
		mainPanel.add(newQuestionButton, new GridBagConstraints(
				0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,SPACING), 0, 0
		));
		mainPanel.add(emButton, new GridBagConstraints(
				1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,SPACING), 0, 0
		));
		mainPanel.add(statusLabel, new GridBagConstraints(
				2, 2, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0, 0
		));
		
		JPanel contentPanePanel = new JPanel(new GridBagLayout());
		contentPanePanel.setBorder(BorderFactory.createEmptyBorder(SPACING, SPACING, SPACING, SPACING));
		
		setContentPane(contentPanePanel);

		contentPanePanel.add(mainPanel, new GridBagConstraints(
				0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0,0,0,0), 0, 0
		));
		contentPanePanel.add(classPanel, new GridBagConstraints(
				1, 0, 1, 1, 0.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0,SPACING,0,0), 0, 0
		));


		
		SwingUtils.addTabAsFocusKey(questionField);
		SwingUtils.addEnterAsFocusKey(questionField);
		
		SwingUtils.addTabAsFocusKey(answerField);
		SwingUtils.addEnterAsFocusKey(answerField);
		
		newClassField.addActionListener(new ActionListener() {
			
			public void actionPerformed(ActionEvent e)
			{
				String s = newClassField.getText().trim();
				if (s.length() == 0) return;
				if (classModel.contains(s)) return;
				classModel.addElement(s);
			}
		
		});		

		setPreferredSize(new Dimension(600, 300));
		pack();
		setLocationRelativeTo(owner);
		
	}

	/**
	 * Must be called before showing the dialog each time. 
	 * @param model Assumes load() has already been called
	 */
	public void initialize(File questionFile)
	{
		logger.info(getClass().getSimpleName()+".initialize()");
		this.questionFile = questionFile;
		
		try {
			this.xmlDocument = XMLUtils.loadXML(questionFile);
			
			Element questionListElement = xmlDocument.getRootElement();
			this.questionCount = questionListElement.getChildren().size();
			updateStatus();

			SortedSet<String> classes = new TreeSet<String>();
			for (Object e: questionListElement.getChildren())
			{
				String classString = ((Element)e).getAttributeValue("class", "");
				for (String s: classString.split(" "))
				{
					s = s.trim();
					if (s.length() > 0) 
						classes.add(s);
				}
			}
			classModel.removeAllElements();
			for (String s: classes)
				classModel.addElement(s);
			
			initializeFromOptions(new Options(xmlDocument.getRootElement()));
		} catch (IOException ioe)
		{
			JOptionPane.showMessageDialog(EditWindow.this, ioe.getMessage(), "Invalid File Format", JOptionPane.ERROR_MESSAGE);
			throw new RuntimeException(ioe);
		}
		
		
		setLocationRelativeTo(getOwner());
	}
	
	/**
	 * Initializes this window using the options specified in the model. This 
	 * method assumes that the model has been loaded. 
	 */
	protected void initializeFromOptions(Options options)
	{
	    StyleConstants.setFontFamily(questionAttributes, options.getQuestionFontFamily());
	    StyleConstants.setFontSize(questionAttributes, options.getQuestionFontSize());
	    questionField.setParagraphAttributes( questionAttributes, false);

	    StyleConstants.setFontFamily(answerAttributes, options.getAnswerFontFamily());
	    StyleConstants.setFontSize(answerAttributes, options.getAnswerFontSize());
	    StyleConstants.setSpaceAbove(answerAttributes, 5.0f);
	    StyleConstants.setSpaceBelow(answerAttributes, 5.0f);
	    answerField.setParagraphAttributes( answerAttributes, false);

	    /*Font answerFont = new Font(options.getAnswerFontFamily(), 0, options.getAnswerFontSize());
	    answerField.setFont(answerFont);*/
	    if (options.isAnswerRightToLeft())
	    	answerField.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
	    else
	    	answerField.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
	    

	}
	
	final Action newQuestionAction = new AbstractResourceAction("EditWindow.newQuestionAction") { 
		private static final long serialVersionUID = 0L;
		
		public void handleActionPerformed(ActionEvent e)
		{
			logger.debug("EditWindow#newQuestionAction.handleActionPerformed()");
			String questionText = questionField.getText().trim();
			if (questionText.length() == 0)
			{
				questionField.requestFocusInWindow();
				return;
			}
			if (answerField.getText().trim().length() == 0)
			{
				answerField.requestFocusInWindow();
				return;
			}
			
			SortedSet<String> classes = new TreeSet<String>();
			for (Object s: classList.getSelectedValuesList())
				classes.add((String)s);
			
			// Generate the XML
			Element questionElement = new Element("question");
			if (classes.size() > 0)
			{
				String classesValue = "";
				for (String s: classes)
					classesValue += s+" ";
				questionElement.setAttribute("class", classesValue.trim());
			}
			
			Element questionTextElement = new Element("questionText");
			questionElement.addContent(questionTextElement);
			questionTextElement.setText(questionText);
			
			Element answerTextElement = new Element("answerText");
			questionElement.addContent(answerTextElement);
			javax.swing.text.Element[] rootElements = answerField.getStyledDocument().getRootElements();
			for (int i = 0; i < rootElements.length; i++)
			{
				javax.swing.text.Element rootElement = rootElements[i];
				for (int j = 0; j < rootElement.getElementCount(); j++)
				{
					javax.swing.text.Element paragraphElement = rootElement.getElement(j);
					for (int k = 0; k < paragraphElement.getElementCount(); k++)
					{
						javax.swing.text.Element textElement = paragraphElement.getElement(k);

						String text;
						try {
							text = answerField.getText(textElement.getStartOffset(), textElement.getEndOffset()-textElement.getStartOffset());
						} catch (BadLocationException ble)
						{
							throw new RuntimeException(ble);
						}
						
						if (EmAction.isEm(textElement.getAttributes()))
						{
							Element emElement = new Element("em");
							emElement.setText(text);
							answerTextElement.addContent(emElement);
						} 
						else 
						{
							answerTextElement.addContent(text);
						}
						
					}
				}
			}
				
			xmlDocument.getRootElement().addContent(questionElement);
			questionCount++;
			updateStatus();
			fileChanged = true;
			
			questionField.setText("");
			answerField.setText("");
			EmAction.setEm(answerField.getInputAttributes(), false);
			
			questionField.requestFocusInWindow();
		}
	
	};
	
	boolean fileChanged = false;
	
	protected void updateStatus()
	{
		statusLabel.setText(Messages.getString("EditWindow.statusLabel.text", questionCount));
	}
	
	/**
	 * @return <code>true</code> if this window should be allowed to close now.  
	 */
	protected boolean canCloseWindow()
	{
		if (!fileChanged) return true;
		
		
		int result = JOptionPane.showConfirmDialog( 
				this, 
				"Do you want to save these changes before closing? \nFile name is \""+questionFile+"\".", 
				"Confirm Exit", 
				JOptionPane.YES_NO_CANCEL_OPTION, 
				JOptionPane.QUESTION_MESSAGE);
		switch (result)
		{
		case JOptionPane.CANCEL_OPTION: return false;
		case JOptionPane.NO_OPTION: return true;
		case JOptionPane.YES_OPTION:
			try
			{
				// First backup old question file
				File backupFile = File.createTempFile("FlashTeacher_"+questionFile.getName(), null);
				logger.info("Writing backup file \""+backupFile+"\"...");
				
				BufferedInputStream bis = new BufferedInputStream(new FileInputStream(questionFile));	
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(backupFile));	
				byte inArr[] = new byte[bis.available()];	
				int size = bis.read(inArr,0,inArr.length);	
				bos.write(inArr,0,size);	
				bis.close();	
				bos.close();
				
				// Then save the new file
				logger.info("Writing data file...");
				XMLUtils.saveXML(xmlDocument, questionFile);
				logger.info("Save complete.");
				return true;
			} catch (IOException ioe)
			{
				JOptionPane.showMessageDialog(EditWindow.this, ioe.getMessage(), "Save Failed", JOptionPane.ERROR_MESSAGE);
				return false;
			}
		default: return false; // never happens
		}
	}

}

/**
 * A document filter (e.g. for a JTextPane) that filters out newline 
 * characters. 
 * @author Ben
 */
class NewLineFilter extends DocumentFilter
{
	@Override
	public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException
	{
		if (string != null) 
			string = string.replace("\n", "").replace("\r","");
		super.insertString(fb, offset, string, attr);
	}

	@Override
	public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
	{
		if (text != null) 
			text = text.replace("\n", "").replace("\r","");
		super.replace(fb, offset, length, text, attrs);
	}

	static void register(JTextPane p)
	{
		StyledDocument styledDoc = p.getStyledDocument();
	    AbstractDocument doc = (AbstractDocument)styledDoc;
	    doc.setDocumentFilter(new NewLineFilter());
	}
}

class EmAction extends StyledTextAction {

	private JTextPane textPane;
	private static final long serialVersionUID = 1L;
	
	/**
     * Constructs a new BoldAction.
     */
	public EmAction(final JTextPane textPane) {
	    super(Messages.getString("EditWindow.emAction.name"));
	    this.textPane = textPane;
	    textPane.addCaretListener(new CaretListener() {
		
			public void caretUpdate(CaretEvent e)
			{
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						EmAction.this.putValue(SELECTED_KEY, new Boolean(isEm(textPane.getInputAttributes())));
					}
				});
			}
		
		});
	}

	public static boolean isEm(AttributeSet attr)
	{
		return StyleConstants.isBold(attr);
	}

	public static void setEm(MutableAttributeSet attr, boolean value)
	{
		StyleConstants.setBold(attr, value);
	}

    /**
     * Toggles the bold attribute.
     *
     * @param e the action event
     */
    public void actionPerformed(ActionEvent e) {
	    JEditorPane editor = getEditor(e);
	    if (editor != null) 
	    {
			StyledEditorKit kit = getStyledEditorKit(editor);
			
			MutableAttributeSet attr = kit.getInputAttributes();
			SimpleAttributeSet sas = new SimpleAttributeSet();
			setEm(sas, !isEm(attr));
			setCharacterAttributes(editor, sas, false);
			
			textPane.requestFocusInWindow();
		}
    }
}