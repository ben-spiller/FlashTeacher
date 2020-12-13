package com.ben.flashteacher;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.apache.log4j.Logger;

import com.ben.flashteacher.model.ModelHolder;
import com.jgoodies.looks.Options;
import com.jgoodies.looks.plastic.Plastic3DLookAndFeel;

public class Main {

	static final Logger logger = Logger.getLogger(Main.class.getName());

	private static void initLookAndFeel()
	{
		//PlasticLookAndFeel.setPlasticTheme(new DesertBlue());
		Options.setUseNarrowButtons(false);
		try {
			UIManager.setLookAndFeel(new Plastic3DLookAndFeel());
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	/**
	 * Asks the user to select a question file to open. 
	 * @return <code>null</code> if the user cancels the dialog. Otherwise, the 
	 * location of a file that exists. 
	 */
	private static File getQuestionFile()
	{
		JFileChooser fileChooser = new JFileChooser(ModelHolder.DEFAULT_QUESTION_FILE_DIRECTORY);
		fileChooser.setDialogTitle("Select Question File");
		fileChooser.setFileFilter(new com.ben.flashteacher.utils.FileNameExtensionFilter("Question Files (*."+ModelHolder.EXTENSION_QUESTION_FILES+")", ModelHolder.EXTENSION_QUESTION_FILES));
		if (fileChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
			return null;

		File result = fileChooser.getSelectedFile();
		try {
			result = result.getCanonicalFile();
		} catch (IOException e) { /* ignore */ }
		
		if (result.exists())
			return result;

		// if it doesn't exist, try again
		JOptionPane.showMessageDialog(null, "Question file \""+result.getPath()+"\" does not exist.", "File Not Found", JOptionPane.ERROR_MESSAGE);
		return getQuestionFile();
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		logger.info("");
		logger.info("===========================================================");
		logger.info("");
		logger.info(Main.class.getName()+".main()");
		
		if (args.length > 0 && args[0].equalsIgnoreCase("--editor"))
		{
			String[] newArgs = Arrays.copyOfRange(args, 1, args.length);
			com.ben.flashteacher.editor.Main.main(newArgs);
			return;
		}
		
		initLookAndFeel();
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			/* (non-Javadoc)
			 * @see java.lang.Thread$UncaughtExceptionHandler#uncaughtException(java.lang.Thread, java.lang.Throwable)
			 */
			public void uncaughtException(Thread t, Throwable e)
			{
				logger.error("Uncaught exception from thread "+t+": ", e);
				System.out.flush();
				e.printStackTrace();
				System.err.flush();
			}
		
		});		
		
		// get initial file, or exit if desired
		File initialFile = null; 
		if (args.length > 0)
			initialFile = new File(args[0]);
		
		if (initialFile != null && !initialFile.exists())
		{
			JOptionPane.showMessageDialog(null, "Question file \""+initialFile.getPath()+"\" does not exist.", "File Not Found", JOptionPane.ERROR_MESSAGE);
			initialFile = null;
		}

		if (initialFile == null)
			initialFile = getQuestionFile();
		
		logger.info("Initial file = "+initialFile);
		
		if (initialFile == null)
			return;
		
		final ModelHolder model = new ModelHolder(initialFile);
		
		final QuestionWindow questionWindow = new QuestionWindow(null);
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					questionWindow.initialize(model);
					questionWindow.setVisible(true);
				} catch (Exception e)
				{
					logger.fatal("Fatal error: ", e);
					System.exit(1);
				}
			}
		});
		logger.info(Main.class.getName()+".main() done");
	}

}
