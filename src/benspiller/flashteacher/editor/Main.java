package benspiller.flashteacher.editor;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.jgoodies.looks.Options;
import com.jgoodies.looks.plastic.Plastic3DLookAndFeel;

import benspiller.flashteacher.model.ModelHolder;

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
		fileChooser.setFileFilter(new benspiller.flashteacher.utils.FileNameExtensionFilter("Question Files (*."+ModelHolder.EXTENSION_QUESTION_FILES+")", ModelHolder.EXTENSION_QUESTION_FILES));
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
		logger.log(java.util.logging.Level.INFO, Main.class.getName()+".main()");
		initLookAndFeel();
		
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
		
		logger.log(java.util.logging.Level.INFO, "Initial file = "+initialFile);
		
		if (initialFile == null)
			return;
		
		final File questionFile = initialFile;
		final EditWindow editWindow = new EditWindow(null);
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					editWindow.initialize(questionFile);
					editWindow.setVisible(true);
				} catch (Exception e)
				{
					logger.log(Level.SEVERE, "Fatal error: ", e);
					e.printStackTrace();
					System.exit(1);
				}
			}
		});
		logger.log(java.util.logging.Level.INFO, Main.class.getName()+".main() done");
	}

}
