package com.ben.flashteacher;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.geom.Ellipse2D;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.text.JTextComponent;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateTickUnit;
import org.jfree.chart.axis.TickUnitSource;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.renderer.xy.DefaultXYItemRenderer;
import org.jfree.data.xy.DefaultXYDataset;

import com.ben.flashteacher.model.KnowledgeIndexHistory;
import com.ben.flashteacher.model.QuestionSetScores;
import com.jgoodies.forms.factories.DefaultComponentFactory;
import com.jgoodies.looks.Options;
import com.jgoodies.looks.plastic.Plastic3DLookAndFeel;

public class PerformanceWindow extends JDialog
{
	private static final long serialVersionUID = 1L;

	protected final Map<String, JTextComponent> textComponents = new HashMap<String, JTextComponent>();
	
	final JButton closeButton;
	final JProgressBar summaryBar;
	final DefaultXYDataset xyDataSet;
	final JComboBox timeWindowComboBox;
	
	private static final int DEFAULT_TIME_WINDOW_INDEX = 3;
	private static final int TIME_WINDOW_INDEX_ALL = 0;
	private static final TimeWindow[] TIME_WINDOWS = {
		new TimeWindow(Messages.getString("PerformanceWindow.graphPanel.graph.graphTimeWindow.all"), 		0),
		new TimeWindow(Messages.getString("PerformanceWindow.graphPanel.graph.graphTimeWindow.day"), 		-1), // special value representing today
		new TimeWindow(Messages.getString("PerformanceWindow.graphPanel.graph.graphTimeWindow.week"), 		1000L*60*60*24*7),
		new TimeWindow(Messages.getString("PerformanceWindow.graphPanel.graph.graphTimeWindow.2weeks"), 	1000L*60*60*24*7*2),
		new TimeWindow(Messages.getString("PerformanceWindow.graphPanel.graph.graphTimeWindow.month"), 		1000L*60*60*24*7*4),
		new TimeWindow(Messages.getString("PerformanceWindow.graphPanel.graph.graphTimeWindow.6months"), 	1000L*60*60*24*365/2),
	}; 
	
	public PerformanceWindow(Window owner)
	{
		super(owner, ModalityType.APPLICATION_MODAL);
		setTitle(Messages.getString("PerformanceWindow.title")); //$NON-NLS-1$
		setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

		final int BORDER_WIDTH = 10;
		JTextField textComponent;
		
		// Summary panel
		Box summaryPanel = Box.createVerticalBox();
		summaryPanel.setBorder(BorderFactory.createEmptyBorder(0, BORDER_WIDTH, 0, 0));

		textComponent = new JTextField();
		textComponent.setBackground(getBackground());
		textComponent.setEditable(false);
		textComponent.setBorder(BorderFactory.createEmptyBorder());
		textComponent.setFont(textComponent.getFont().deriveFont(Font.BOLD));
		textComponents.put("summaryPanel.summary", textComponent);
		
		summaryBar = new JProgressBar(0, 100);
		
		summaryPanel.add(textComponent);
		summaryPanel.add(Box.createVerticalStrut(BORDER_WIDTH));
		summaryPanel.add(summaryBar);

		// Details panel
		GridLayout gridLayout = new GridLayout(0, 2);
		gridLayout.setVgap(3);
		gridLayout.setHgap(BORDER_WIDTH*2);
		JPanel detailsPanel = new JPanel(new BorderLayout());
		detailsPanel.setBorder(BorderFactory.createEmptyBorder(0, BORDER_WIDTH, 0, 0));

		JPanel innerDetailsPanel = new JPanel(gridLayout);
		detailsPanel.add(innerDetailsPanel, BorderLayout.WEST);

		String[] detailsKeys = new String[]{
				"unknownAnswers", 
				"wrongAnswers", 
				"slowAnswers", 
				"quickAnswers", 
				null, 
				"totalQuestions", 
				null, 
				"averageTimeToAnswer", 
				"averageTimeToAllowPerCharacter"
				};
		for (String key: detailsKeys)
		{
			if (key == null)
			{
				innerDetailsPanel.add(new JLabel());
				innerDetailsPanel.add(new JLabel());
				continue;
			}
			textComponent = new JTextField();
			textComponent.setHorizontalAlignment(JTextField.RIGHT);
			textComponent.setBackground(getBackground());
			textComponent.setEditable(false);
			textComponent.setBorder(BorderFactory.createEmptyBorder());
			textComponents.put("detailsPanel."+key, textComponent);
			
			JLabel label = new JLabel(Messages.getString("PerformanceWindow.detailsPanel."+key+".label"));
			label.setHorizontalAlignment(JLabel.LEFT);
			
			innerDetailsPanel.add(label);
			innerDetailsPanel.add(textComponent);
		}

		// Graph panel
		Box graphPanel = Box.createVerticalBox();
		graphPanel.setBorder(BorderFactory.createEmptyBorder(0, BORDER_WIDTH/2, 0, 0));

		xyDataSet = new DefaultXYDataset();
		xyDataSet.addSeries("series", new double[][]{ {}, {} });
		final JFreeChart chart = ChartFactory.createTimeSeriesChart(null, null, Messages.getString("PerformanceWindow.graphPanel.graph.yAxisLabel"), xyDataSet, false, false, false);
		ChartPanel chartPanel = new ChartPanel(chart);
		chartPanel.setMouseZoomable(false);
		chartPanel.setPopupMenu(null);
		
		
		// RangeAxis is y, DomainAxis is x
		//chart.getXYPlot().getRangeAxis().setTickLabelsVisible(false); 
		chart.getXYPlot().getDomainAxis().setStandardTickUnits(createDateTickUnits());

		DefaultXYItemRenderer renderer = new DefaultXYItemRenderer();
		renderer.setShapesFilled(true);
		renderer.setShapesVisible(true);
		final float SHAPE_RADIUS = 3.0f; 
		renderer.setShape(new Ellipse2D.Float(-SHAPE_RADIUS, -SHAPE_RADIUS, SHAPE_RADIUS*2, SHAPE_RADIUS*2));
		renderer.setStroke(new BasicStroke(1.3f));
		chart.getXYPlot().setRenderer(renderer);

		Dimension initialSize = new Dimension(550, 180);
		graphPanel.setMinimumSize(initialSize);
		graphPanel.setPreferredSize(initialSize);
		
		JPanel timeWindowComboBoxPanel = new JPanel(new BorderLayout());
		timeWindowComboBox = new JComboBox(TIME_WINDOWS);
		timeWindowComboBox.setSelectedIndex(DEFAULT_TIME_WINDOW_INDEX);
		timeWindowComboBoxPanel.add(timeWindowComboBox, BorderLayout.WEST);

		graphPanel.add(chartPanel);
		/*graphPanel.add(Box.createVerticalStrut(BORDER_WIDTH/2));
		graphPanel.add(Box.createVerticalStrut(BORDER_WIDTH));
		graphPanel.add(new JSeparator());
		graphPanel.add(Box.createVerticalStrut(BORDER_WIDTH/2));*/
		

		// Button panel
		JPanel buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.setBorder(BorderFactory.createEmptyBorder());
		
		closeButton = new JButton(Messages.getString("PerformanceWindow.closeButton.text"));
		closeButton.setMnemonic((int)closeButton.getText().toUpperCase().charAt(0));
		getRootPane().setDefaultButton(closeButton);

		buttonPanel.add(timeWindowComboBoxPanel, BorderLayout.WEST);

		buttonPanel.add(closeButton, BorderLayout.EAST);

		// Parent panel
		Box topPanels = Box.createVerticalBox();
		
		topPanels.add(DefaultComponentFactory.getInstance().createSeparator(Messages.getString("PerformanceWindow.summaryPanel.title")));
		topPanels.add(Box.createVerticalStrut(BORDER_WIDTH));
		topPanels.add(summaryPanel);
		topPanels.add(Box.createVerticalStrut(BORDER_WIDTH*2));
		
		topPanels.add(DefaultComponentFactory.getInstance().createSeparator(Messages.getString("PerformanceWindow.detailsPanel.title")));
		topPanels.add(Box.createVerticalStrut(BORDER_WIDTH));
		topPanels.add(detailsPanel);
		topPanels.add(Box.createVerticalStrut(BORDER_WIDTH*2));
		
		topPanels.add(DefaultComponentFactory.getInstance().createSeparator(Messages.getString("PerformanceWindow.graphPanel.title")));
		
		JPanel dialogPanel = new JPanel(new BorderLayout(0, BORDER_WIDTH));
		dialogPanel.setBorder(BorderFactory.createEmptyBorder(BORDER_WIDTH*2, BORDER_WIDTH*2, BORDER_WIDTH*2, BORDER_WIDTH*2));
		dialogPanel.add(topPanels, BorderLayout.NORTH);
		dialogPanel.add(graphPanel, BorderLayout.CENTER);
		dialogPanel.add(buttonPanel, BorderLayout.SOUTH);
		setContentPane(dialogPanel);

		// listeners
		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				setVisible(false);
			}
		});
		
		timeWindowComboBox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e)
			{
				if (e.getStateChange() != ItemEvent.SELECTED) return;
				
				TimeWindow timeWindow = (TimeWindow)timeWindowComboBox.getSelectedItem();
				if (timeWindow.getLowerBound() == 0)
				{
					chart.getXYPlot().getDomainAxis().setAutoRange(true); // times (x)
					chart.getXYPlot().getRangeAxis().setAutoRange(true); // index values (y)
				}
				else {
					long timeLowerBound = timeWindow.getLowerBound();
					chart.getXYPlot().getDomainAxis().setRangeWithMargins(timeLowerBound, new Date().getTime());
					
					chart.getXYPlot().getRangeAxis().setAutoRange(true); // index values (y)
					
					double minValue = -1d; // sentinel value
					for (KnowledgeIndexHistory.DataPoint dataPoint: knowledgeIndexHistory)
					{
						if (dataPoint.getDate() >= timeLowerBound && (minValue < 0 || dataPoint.getValue()-2 < minValue))
							minValue = dataPoint.getValue()-2;
					}
					if (minValue < 0) minValue = 0;
					chart.getXYPlot().getRangeAxis().setLowerBound(minValue);
				}
			}
		
		});
		
		pack();
	}
	
	private KnowledgeIndexHistory knowledgeIndexHistory;
	
	/**
	 * Must be called to initialize the dialog's data every time it is about to 
	 * be made visible
	 * @param previousScores May be <code>null</code>.
	 * @param scores May be <code>null</code>.
	 * @param knowledgeIndexHistory 
	 */
	public void initialize(QuestionSetScores previousScores, QuestionSetScores scores, 
			KnowledgeIndexHistory knowledgeIndexHistory)
	{
		if (scores == null)
			scores = new QuestionSetScores(); // initialize to all zeroes
		if (previousScores == null)
			previousScores = scores;
		
		xyDataSet.addSeries("series", knowledgeIndexHistory.getData());
		this.knowledgeIndexHistory = knowledgeIndexHistory;

		summaryBar.setValue((int)scores.questionSetPercentScore);
		
		for (String s: textComponents.keySet())
		{
			Object[] args;
			if ("summaryPanel.summary".equals(s))
				args = new Object[]{ scores.questionSetPercentScore, scores.questionSetPercentScore-previousScores.questionSetPercentScore };
			else if ("detailsPanel.unknownAnswers".equals(s))
				args = new Object[]{ scores.unknownAnswers, scores.unknownAnswersPercent, scores.unknownAnswersPercent-previousScores.unknownAnswersPercent };
			else if ("detailsPanel.wrongAnswers".equals(s))
				args = new Object[]{ scores.wrongAnswers, scores.wrongAnswersPercent, scores.wrongAnswersPercent-previousScores.wrongAnswersPercent };
			else if ("detailsPanel.slowAnswers".equals(s))
				args = new Object[]{ scores.slowAnswers, scores.slowAnswersPercent, scores.slowAnswersPercent-previousScores.slowAnswersPercent };
			else if ("detailsPanel.quickAnswers".equals(s))
				args = new Object[]{ scores.quickAnswers, scores.quickAnswersPercent, scores.quickAnswersPercent-previousScores.quickAnswersPercent };
			else if ("detailsPanel.totalQuestions".equals(s))
				args = new Object[]{ scores.totalQuestions, scores.totalQuestions-previousScores.totalQuestions };
			else if ("detailsPanel.averageTimeToAnswer".equals(s))
				args = new Object[]{ scores.averageTimeToAnswer/1000d, (previousScores.averageTimeToAnswer == 0) ? 0 : 100d*(scores.averageTimeToAnswer-previousScores.averageTimeToAnswer)/previousScores.averageTimeToAnswer };
			else if ("detailsPanel.averageTimeToAllowPerCharacter".equals(s))
				args = new Object[]{ scores.averageTimePerCharacter/1000d, (previousScores.averageTimePerCharacter == 0) ? 0 : 100d*(scores.averageTimePerCharacter-previousScores.averageTimePerCharacter)/previousScores.averageTimePerCharacter };
			else
				throw new RuntimeException("Unhandled text component: "+s);
			
			textComponents.get(s).setText(Messages.getString("PerformanceWindow."+s+".text", args));
		}
		
		// update the range
		int selected = timeWindowComboBox.getSelectedIndex();
		timeWindowComboBox.setSelectedIndex(-1);
		timeWindowComboBox.setSelectedIndex(selected);
		
		// if less than 3 data points in this period, reset to showing everything
		int dataPoints = 0;
		long timeLowerBound = ((TimeWindow)timeWindowComboBox.getSelectedItem()).getLowerBound();
		for (KnowledgeIndexHistory.DataPoint dataPoint: knowledgeIndexHistory)
			if (dataPoint.getDate() >= timeLowerBound)
				dataPoints++;
		if (dataPoints < 3)
			timeWindowComboBox.setSelectedIndex(TIME_WINDOW_INDEX_ALL);
		
		closeButton.requestFocusInWindow();
	}
	
    /**
     * Returns a collection of standard date tick units.  
     * 
     * @return A collection of standard date tick units.
     */
    protected static TickUnitSource createDateTickUnits() {
    	TimeZone zone = TimeZone.getDefault();
        TickUnits units = new TickUnits();

        // date formatters - we improve on the defaults here, to include day of week etc
        DateFormat f1 = new SimpleDateFormat("HH:mm:ss.SSS");
        DateFormat f2 = new SimpleDateFormat("HH:mm:ss");
        DateFormat f3 = new SimpleDateFormat("HH:mm");
        DateFormat f4 = new SimpleDateFormat("E d MMM, HH:mm");
        DateFormat f5 = new SimpleDateFormat("E d MMM");
        DateFormat f6 = new SimpleDateFormat("MMM yyyy");
        DateFormat f7 = new SimpleDateFormat("yyyy");
        
        f1.setTimeZone(zone);
        f2.setTimeZone(zone);
        f3.setTimeZone(zone);
        f4.setTimeZone(zone);
        f5.setTimeZone(zone);
        f6.setTimeZone(zone);
        f7.setTimeZone(zone);
        
        // milliseconds
        units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 1, f1));
        units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 5, 
                DateTickUnit.MILLISECOND, 1, f1));
        units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 10, 
                DateTickUnit.MILLISECOND, 1, f1));
        units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 25, 
                DateTickUnit.MILLISECOND, 5, f1));
        units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 50, 
                DateTickUnit.MILLISECOND, 10, f1));
        units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 100, 
                DateTickUnit.MILLISECOND, 10, f1));
        units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 250, 
                DateTickUnit.MILLISECOND, 10, f1));
        units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 500, 
                DateTickUnit.MILLISECOND, 50, f1));

        // seconds
        units.add(new DateTickUnit(DateTickUnit.SECOND, 1, 
                DateTickUnit.MILLISECOND, 50, f2));
        units.add(new DateTickUnit(DateTickUnit.SECOND, 5, 
                DateTickUnit.SECOND, 1, f2));
        units.add(new DateTickUnit(DateTickUnit.SECOND, 10, 
                DateTickUnit.SECOND, 1, f2));
        units.add(new DateTickUnit(DateTickUnit.SECOND, 30, 
                DateTickUnit.SECOND, 5, f2));

        // minutes
        units.add(new DateTickUnit(DateTickUnit.MINUTE, 1, 
                DateTickUnit.SECOND, 5, f3));
        units.add(new DateTickUnit(DateTickUnit.MINUTE, 2, 
                DateTickUnit.SECOND, 10, f3));
        units.add(new DateTickUnit(DateTickUnit.MINUTE, 5, 
                DateTickUnit.MINUTE, 1, f3));
        units.add(new DateTickUnit(DateTickUnit.MINUTE, 10, 
                DateTickUnit.MINUTE, 1, f3));
        units.add(new DateTickUnit(DateTickUnit.MINUTE, 15, 
                DateTickUnit.MINUTE, 5, f3));
        units.add(new DateTickUnit(DateTickUnit.MINUTE, 20, 
                DateTickUnit.MINUTE, 5, f3));
        units.add(new DateTickUnit(DateTickUnit.MINUTE, 30, 
                DateTickUnit.MINUTE, 5, f3));

        // hours
        units.add(new DateTickUnit(DateTickUnit.HOUR, 1, 
                DateTickUnit.MINUTE, 5, f3));
        units.add(new DateTickUnit(DateTickUnit.HOUR, 2, 
                DateTickUnit.MINUTE, 10, f3));
        units.add(new DateTickUnit(DateTickUnit.HOUR, 4, 
                DateTickUnit.MINUTE, 30, f3));
        units.add(new DateTickUnit(DateTickUnit.HOUR, 6, 
                DateTickUnit.HOUR, 1, f3));
        units.add(new DateTickUnit(DateTickUnit.HOUR, 12, 
                DateTickUnit.HOUR, 1, f4));

        // days
        units.add(new DateTickUnit(DateTickUnit.DAY, 1, 
                DateTickUnit.HOUR, 1, f5));
        units.add(new DateTickUnit(DateTickUnit.DAY, 2, 
                DateTickUnit.HOUR, 1, f5));
        units.add(new DateTickUnit(DateTickUnit.DAY, 7, 
                DateTickUnit.DAY, 1, f5));
        units.add(new DateTickUnit(DateTickUnit.DAY, 15, 
                DateTickUnit.DAY, 1, f5));

        // months
        units.add(new DateTickUnit(DateTickUnit.MONTH, 1, 
                DateTickUnit.DAY, 1, f6));
        units.add(new DateTickUnit(DateTickUnit.MONTH, 2, 
                DateTickUnit.DAY, 1, f6));
        units.add(new DateTickUnit(DateTickUnit.MONTH, 3, 
                DateTickUnit.MONTH, 1, f6));
        units.add(new DateTickUnit(DateTickUnit.MONTH, 4,  
                DateTickUnit.MONTH, 1, f6));
        units.add(new DateTickUnit(DateTickUnit.MONTH, 6,  
                DateTickUnit.MONTH, 1, f6));

        // years
        units.add(new DateTickUnit(DateTickUnit.YEAR, 1,  
                DateTickUnit.MONTH, 1, f7));
        units.add(new DateTickUnit(DateTickUnit.YEAR, 2,  
                DateTickUnit.MONTH, 3, f7));
        units.add(new DateTickUnit(DateTickUnit.YEAR, 5,  
                DateTickUnit.YEAR, 1, f7));
        units.add(new DateTickUnit(DateTickUnit.YEAR, 10,  
                DateTickUnit.YEAR, 1, f7));
        units.add(new DateTickUnit(DateTickUnit.YEAR, 25, 
                DateTickUnit.YEAR, 5, f7));
        units.add(new DateTickUnit(DateTickUnit.YEAR, 50, 
                DateTickUnit.YEAR, 10, f7));
        units.add(new DateTickUnit(DateTickUnit.YEAR, 100, 
                DateTickUnit.YEAR, 20, f7));

        return units;

    }
	
    /**
     * An object held in the time range combo box.
     */
    static class TimeWindow
    {
    	private String name;
    	private long periodMillis;
    	public TimeWindow(String name, long periodMillis)
    	{
    		this.name = name;
    		if (periodMillis == -1) // special value representing today 
    		{
    			Calendar c = Calendar.getInstance();
    			c.set(Calendar.AM_PM, Calendar.AM);
    			c.set(Calendar.HOUR, 0);
    			c.set(Calendar.MINUTE, 0);
    			c.set(Calendar.SECOND, 0);
    			c.set(Calendar.MILLISECOND, 0);
    			periodMillis = new Date().getTime() - c.getTimeInMillis();
    		}
    		
    		this.periodMillis = periodMillis;
    	}
    	
    	@Override
    	public String toString()
    	{
    		return name;
    	}
    	
    	public long getLowerBound()
    	{
    		if (periodMillis == 0)
    			return 0;
    		return new Date().getTime() - periodMillis;
    	}
    	
    }
	
	public static void main(String[] args)
	{
		Options.setUseNarrowButtons(false);
		try {
			UIManager.setLookAndFeel(new Plastic3DLookAndFeel());
		} catch (Exception e) { e.printStackTrace(); }
		
		final PerformanceWindow window = new PerformanceWindow(null);
		
		QuestionSetScores previousScores = new QuestionSetScores();
		QuestionSetScores scores = new QuestionSetScores();
		previousScores.questionSetPercentScore = 74;
		scores.questionSetPercentScore=77;

		scores.unknownAnswers = 4;
		scores.unknownAnswersPercent = 60;
		previousScores.unknownAnswersPercent = 78;

		KnowledgeIndexHistory knowledgeIndexHistory = new KnowledgeIndexHistory();
		knowledgeIndexHistory.add(new Date(new Date().getTime()-1000L*60*60*24*7*23), 66d);
		knowledgeIndexHistory.add(new Date(new Date().getTime()-1000L*60*60*24*9), 70d);
		knowledgeIndexHistory.add(new Date(new Date().getTime()-1000L*60*60*24*1), 140d);
		knowledgeIndexHistory.add(new Date(new Date().getTime()-1000L*60*60*5), 90d);
		knowledgeIndexHistory.add(new Date(new Date().getTime()-1000L*60*60*4), 100d);
		knowledgeIndexHistory.add(new Date(new Date().getTime()-1000L*60*60*1), 96d);
		window.initialize(previousScores, scores, knowledgeIndexHistory);
		window.setLocationRelativeTo(null);
		window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		window.closeButton.addActionListener(new ActionListener() {
		
			public void actionPerformed(ActionEvent e)
			{
				System.exit(0);
			}
		
		});		
		
		window.setVisible(true);
	}
}
