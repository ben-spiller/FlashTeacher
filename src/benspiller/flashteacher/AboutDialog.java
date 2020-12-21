package benspiller.flashteacher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

public class AboutDialog extends JDialog
{
	private static final long serialVersionUID = 1L;

	public AboutDialog(Window owner)
	{
		super(owner, ModalityType.APPLICATION_MODAL);
		setTitle(Messages.getString("AboutDialog.title")); //$NON-NLS-1$
		setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

		final int BORDER_WIDTH = 10;
		
		JPanel textPanel = new JPanel(new BorderLayout(BORDER_WIDTH, 0));
		textPanel.setBackground(Color.WHITE);
		textPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createEtchedBorder(), 
				BorderFactory.createEmptyBorder(BORDER_WIDTH, BORDER_WIDTH, BORDER_WIDTH, BORDER_WIDTH))
				);

		JLabel iconLabel = new JLabel( Constants.APPLICATION_ICON_48x48 );
		iconLabel.setVerticalAlignment(SwingConstants.TOP);
		textPanel.add(iconLabel, BorderLayout.WEST);

		
		JTextArea textArea = new JTextArea();//5, Constants.COPYRIGHT.length()+5);
		textArea.setText(Messages.getString("AboutDialog.text", 
				Messages.getString("application.name"), 
				Constants.VERSION_NUMBER, 
				Constants.COPYRIGHT
				));
		textArea.setBackground(Color.WHITE);
		textArea.setEditable(false);
		textPanel.add(textArea, BorderLayout.CENTER);
		
		JPanel buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(BORDER_WIDTH, BORDER_WIDTH, BORDER_WIDTH, BORDER_WIDTH));

		
		JButton closeButton = new JButton(Messages.getString("AboutDialog.closeButton.text"));
		closeButton.setMnemonic((int)closeButton.getText().toUpperCase().charAt(0));
		buttonPanel.add(closeButton, BorderLayout.EAST);
		getRootPane().setDefaultButton(closeButton);
		
		getContentPane().setLayout(new BorderLayout(0, BORDER_WIDTH));
		getContentPane().add(textPanel, BorderLayout.CENTER);
		getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		
		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				setVisible(false);
			}
		});
		
		pack();
		closeButton.requestFocusInWindow();
	}


}
