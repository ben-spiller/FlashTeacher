package benspiller.flashteacher;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
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
import org.jfree.chart.StandardChartTheme;
import org.jfree.chart.axis.AxisSpace;
import org.jfree.chart.axis.DateTickUnit;
import org.jfree.chart.axis.DateTickUnitType;
import org.jfree.chart.axis.TickUnitSource;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.renderer.xy.DefaultXYItemRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.xy.XYBarDataset;
import org.jfree.data.xy.XYDataset;

import com.jgoodies.forms.factories.DefaultComponentFactory;
import com.jgoodies.looks.Options;
import com.jgoodies.looks.plastic.Plastic3DLookAndFeel;

import benspiller.flashteacher.model.KnowledgeIndexHistory;
import benspiller.flashteacher.model.QuestionSetScores;

public class PerformanceWindow extends JDialog
{
	private static final long serialVersionUID = 1L;

	protected final Map<String, JTextComponent> textComponents = new HashMap<String, JTextComponent>();
	
	final JButton closeButton;
	final JProgressBar summaryBar;
	final DefaultXYDataset knowledgeDataSet;
	final DefaultXYDataset timeSpentDataSet;
	final JComboBox<TimeWindow> timeWindowComboBox;
	
	private final long nowMillis = new Date().getTime();

	private static final int DEFAULT_TIME_WINDOW_INDEX = 3;
	private static final int TIME_WINDOW_INDEX_ALL = 0;
	private final TimeWindow[] TIME_WINDOWS = { // must not be a static field - since current time (i.e. the upper bound) keeps changing
		new TimeWindow(Messages.getString("PerformanceWindow.graphPanel.graph.graphTimeWindow.all"), 		0),
		new TimeWindow(Messages.getString("PerformanceWindow.graphPanel.graph.graphTimeWindow.day"), 		-1), // special value representing today
		new TimeWindow(Messages.getString("PerformanceWindow.graphPanel.graph.graphTimeWindow.week"), 		1000L*60*60*24*7),
		new TimeWindow(Messages.getString("PerformanceWindow.graphPanel.graph.graphTimeWindow.2weeks"), 	1000L*60*60*24*7*2),
		new TimeWindow(Messages.getString("PerformanceWindow.graphPanel.graph.graphTimeWindow.month"), 		1000L*60*60*24*7*4),
		new TimeWindow(Messages.getString("PerformanceWindow.graphPanel.graph.graphTimeWindow.6months"), 	1000L*60*60*24*365/2),
	}; 
	
    private final DateFormat DATE_FORMAT_NO_TIME = new SimpleDateFormat("E d MMM yyyy");
	
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
				"averageTimeToAllowPerCharacter",
				null,
				"averageMinutesPerDay"
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

		// Graph panel; RangeAxis is y, DomainAxis is x
		Box graphPanel = Box.createVerticalBox();
		graphPanel.setBorder(BorderFactory.createEmptyBorder(0, BORDER_WIDTH/2, 0, 0));

		ChartFactory.setChartTheme(StandardChartTheme.createLegacyTheme());

		knowledgeDataSet = new DefaultXYDataset();
		knowledgeDataSet.addSeries("series", new double[][]{ {}, {} });
		JFreeChart knowledgeChart = ChartFactory.createTimeSeriesChart(null, null, Messages.getString("PerformanceWindow.graphPanel.graph.yAxisLabel"), 
				knowledgeDataSet, false, true, false);
		ChartPanel chartPanel = new ChartPanel(knowledgeChart);
		chartPanel.setMouseZoomable(false);
		chartPanel.setPopupMenu(null);

		DefaultXYItemRenderer renderer = new DefaultXYItemRenderer();
		renderer.setDefaultShapesFilled(true);
		renderer.setDefaultShapesVisible(true);
		final float SHAPE_RADIUS = 3.0f; 
		renderer.setSeriesShape(0, new Ellipse2D.Float(-SHAPE_RADIUS, -SHAPE_RADIUS, SHAPE_RADIUS*2, SHAPE_RADIUS*2));
		renderer.setSeriesStroke(0, new BasicStroke(2f));
		renderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator() {
			private static final long serialVersionUID = 1L;
			@Override
			public String generateToolTip(XYDataset dataset, int series, int item)
			{
				return Messages.getString("PerformanceWindow.graphPanel.knowledgeChart.tooltip", 
						((long)dataset.getYValue(series, item)),
						new Date((long)dataset.getXValue(series, item))
					);
			}
		});		
		knowledgeChart.getXYPlot().setRenderer(renderer);

		
		graphPanel.add(chartPanel);

		timeSpentDataSet = new DefaultXYDataset();
		timeSpentDataSet.addSeries("series", new double[][]{ {}, {} });
		JFreeChart timeSpentChart = ChartFactory.createTimeSeriesChart(null, null, Messages.getString("PerformanceWindow.graphPanel.timeSpentChart.yAxisLabel"), 
				new XYBarDataset(timeSpentDataSet, 1000*60*60*(24-2)), 
				false, true, false);
		chartPanel = new ChartPanel(timeSpentChart);
		chartPanel.setMouseZoomable(false);
		chartPanel.setPopupMenu(null);
		XYBarRenderer barRenderer = new XYBarRenderer();
		barRenderer.setSeriesPaint(0, new Color(168,255,174)); // green
		barRenderer.setDefaultItemLabelsVisible(true, true);
		barRenderer.setShadowVisible(false);
		timeSpentChart.getXYPlot().setRenderer(barRenderer);
		barRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator() {
			private static final long serialVersionUID = 1L;
			@Override
			public String generateToolTip(XYDataset dataset, int series, int item)
			{
				return Messages.getString("PerformanceWindow.graphPanel.timeSpentChart.tooltip", 
						((long)dataset.getYValue(series, item)),
						DATE_FORMAT_NO_TIME.format(new Date((long)dataset.getXValue(series, item)))
						);
			}
		});		
		
		// This ensures that both graphs' plot area start in the same place even though the knowledge axis may need more horizontal space
		AxisSpace as = new AxisSpace();
		as.add(60, RectangleEdge.LEFT);
		timeSpentChart.getXYPlot().setFixedRangeAxisSpace(as);
		knowledgeChart.getXYPlot().setFixedRangeAxisSpace(as);
		
		// Settings common to both charts go here
		for (JFreeChart chart: new JFreeChart[] {knowledgeChart, timeSpentChart})
		{
			chart.setBackgroundPaint(chartPanel.getBackground());
			chart.getXYPlot().setBackgroundPaint(Color.WHITE);
			chart.getXYPlot().setDomainGridlinePaint(Color.GRAY);
			chart.getXYPlot().setRangeGridlinePaint(Color.GRAY);

			chart.getXYPlot().getRangeAxis().setLabelPaint(chartPanel.getForeground());
			chart.getXYPlot().getRangeAxis().setTickLabelPaint(chartPanel.getForeground());
			chart.getXYPlot().getDomainAxis().setTickLabelPaint(chartPanel.getForeground());
			chart.getXYPlot().getRangeAxis().setLabelFont(chartPanel.getFont().deriveFont(16.0f));
			
			chart.getXYPlot().getDomainAxis().setStandardTickUnits(createDateTickUnits());
		}
		
		graphPanel.add(chartPanel);
		
		
		Dimension initialSize = new Dimension(550, 180*2);
		graphPanel.setMinimumSize(initialSize);
		graphPanel.setPreferredSize(initialSize);

		
		// Button panel
		JPanel timeWindowComboBoxPanel = new JPanel(new BorderLayout());
		timeWindowComboBox = new JComboBox<>(TIME_WINDOWS);
		timeWindowComboBox.setSelectedIndex(DEFAULT_TIME_WINDOW_INDEX);
		timeWindowComboBoxPanel.add(timeWindowComboBox, BorderLayout.WEST);

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
				
				for (JFreeChart chart: new JFreeChart[] {knowledgeChart, timeSpentChart})
				{
					if (timeWindow.getLowerBound() == 0)
					{
						chart.getXYPlot().getDomainAxis().setAutoRange(true); // times (x)
						chart.getXYPlot().getRangeAxis().setAutoRange(true); // index values (y)
					}
					else {
						long timeLowerBound = timeWindow.getLowerBound();
						chart.getXYPlot().getDomainAxis().setRangeWithMargins(timeLowerBound, nowMillis);
						
						chart.getXYPlot().getRangeAxis().setAutoRange(true); // index values (y)
						
						if (chart == knowledgeChart) {
							double minValue = -1d; // sentinel value
							for (KnowledgeIndexHistory.DataPoint dataPoint: knowledgeIndexHistory)
							{
								if (dataPoint.getDate() < timeLowerBound) continue;
								if (minValue < 0 || dataPoint.getValue() < minValue)
									minValue = dataPoint.getValue();
							}
							if (minValue < 0) minValue = 0;
							minValue = minValue*0.95; // create a margin so we can still see the bottom vlues
							//chart.getXYPlot().getRangeAxis().setLowerBound(minValue);
							//chart.getXYPlot().getRangeAxis().setLowerMargin(minValue);
						}
					}
				}
				timeSpentChart.getXYPlot().getRangeAxis().setLowerBound(0);
				
				// Since the timeSpentChart is quantitized into whole days, automatic range might leave it different to the 
				// other chart, so make that one the master so that everything lines up
				timeSpentChart.getXYPlot().getDomainAxis().setLowerBound(knowledgeChart.getXYPlot().getDomainAxis().getLowerBound());
				timeSpentChart.getXYPlot().getDomainAxis().setUpperBound(knowledgeChart.getXYPlot().getDomainAxis().getUpperBound());

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
		
		knowledgeDataSet.addSeries("series", knowledgeIndexHistory.getKnowledgeIndexArray());
		timeSpentDataSet.addSeries("series", knowledgeIndexHistory.getTimeSpentArray());
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
			else if ("detailsPanel.averageMinutesPerDay".equals(s))
				args = new Object[]{ knowledgeIndexHistory.getAverageMinutesPerDayThisWeek() };
			else
				throw new RuntimeException("Unhandled text component: "+s);
			
			textComponents.get(s).setText(Messages.getString("PerformanceWindow."+s+".text", args));
		}
		
		// update the range
		int selected = timeWindowComboBox.getSelectedIndex();
		timeWindowComboBox.setSelectedIndex(-1);
		timeWindowComboBox.setSelectedIndex(selected);
		
		// if less than 3 data points in this period, or it's less than the current selection (e.g. 2 weeks but we have only one day's data), reset to showing everything
		if (knowledgeIndexHistory.getKnowledgeIndexArray()[0].length < 3 || 
				knowledgeIndexHistory.getKnowledgeIndexArray()[0][knowledgeIndexHistory.getKnowledgeIndexArray().length-1]-knowledgeIndexHistory.getKnowledgeIndexArray()[0][0] < timeWindowComboBox.getItemAt(selected).periodMillis)
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
        DateFormat f3 = new SimpleDateFormat("E HH:mm");
        DateFormat f4 = new SimpleDateFormat("E d MMM, HH:mm");
        DateFormat f5 = new SimpleDateFormat("E d MMM");
        DateFormat f6 = new SimpleDateFormat("MMM yyyy");
        DateFormat f7 = new SimpleDateFormat("yyyy");
        
        f3.setTimeZone(zone);
        f4.setTimeZone(zone);
        f5.setTimeZone(zone);
        f6.setTimeZone(zone);
        f7.setTimeZone(zone);
        
        // hours
        units.add(new DateTickUnit(DateTickUnitType.HOUR, 1, 
                DateTickUnitType.MINUTE, 5, f3));
        units.add(new DateTickUnit(DateTickUnitType.HOUR, 2, 
                DateTickUnitType.MINUTE, 10, f3));
        units.add(new DateTickUnit(DateTickUnitType.HOUR, 4, 
                DateTickUnitType.MINUTE, 30, f3));
        units.add(new DateTickUnit(DateTickUnitType.HOUR, 6, 
                DateTickUnitType.HOUR, 1, f3));
        units.add(new DateTickUnit(DateTickUnitType.HOUR, 12, 
                DateTickUnitType.HOUR, 1, f4));

        // days
        units.add(new DateTickUnit(DateTickUnitType.DAY, 1, 
                DateTickUnitType.DAY, 1, f5));
        units.add(new DateTickUnit(DateTickUnitType.DAY, 2, 
                DateTickUnitType.DAY, 1, f5));
        units.add(new DateTickUnit(DateTickUnitType.DAY, 7, 
                DateTickUnitType.DAY, 1, f5));
        units.add(new DateTickUnit(DateTickUnitType.DAY, 15, 
                DateTickUnitType.DAY, 1, f5));

        // months
        units.add(new DateTickUnit(DateTickUnitType.MONTH, 1, 
                DateTickUnitType.DAY, 1, f6));
        units.add(new DateTickUnit(DateTickUnitType.MONTH, 2, 
                DateTickUnitType.DAY, 1, f6));
        units.add(new DateTickUnit(DateTickUnitType.MONTH, 3, 
                DateTickUnitType.MONTH, 1, f6));
        units.add(new DateTickUnit(DateTickUnitType.MONTH, 4,  
                DateTickUnitType.MONTH, 1, f6));
        units.add(new DateTickUnit(DateTickUnitType.MONTH, 6,  
                DateTickUnitType.MONTH, 1, f6));

        // years
        units.add(new DateTickUnit(DateTickUnitType.YEAR, 1,  
                DateTickUnitType.MONTH, 1, f7));
        units.add(new DateTickUnit(DateTickUnitType.YEAR, 2,  
                DateTickUnitType.MONTH, 3, f7));
        units.add(new DateTickUnit(DateTickUnitType.YEAR, 5,  
                DateTickUnitType.YEAR, 1, f7));
        units.add(new DateTickUnit(DateTickUnitType.YEAR, 10,  
                DateTickUnitType.YEAR, 1, f7));
        units.add(new DateTickUnit(DateTickUnitType.YEAR, 25, 
                DateTickUnitType.YEAR, 5, f7));
        units.add(new DateTickUnit(DateTickUnitType.YEAR, 50, 
                DateTickUnitType.YEAR, 10, f7));
        units.add(new DateTickUnit(DateTickUnitType.YEAR, 100, 
                DateTickUnitType.YEAR, 20, f7));

        return units;

    }
	
    /**
     * An object held in the time range combo box.
     */
    class TimeWindow
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
    			periodMillis = nowMillis - c.getTimeInMillis();
    		}
    		
    		this.periodMillis = periodMillis;
    	}
    	
    	@Override
    	public String toString()
    	{
    		return name;
    	}
    	
    	/** NB: the upper bound is getNowMillis */
    	public long getLowerBound()
    	{
    		if (periodMillis == 0)
    			return 0;
    		return nowMillis - periodMillis;
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
		knowledgeIndexHistory.add(new Date(new Date().getTime()-1000L*60*60*24*7), 6600d, 1*60000);
		knowledgeIndexHistory.add(new Date(new Date().getTime()-1000L*60*60*24*9), 7000d, 5*60000);
		knowledgeIndexHistory.add(new Date(new Date().getTime()-1000L*60*60*24*1), 1400000d, 7*60000);
		knowledgeIndexHistory.add(new Date(new Date().getTime()-1000L*60*60*5), 9000d, 1*60000);
		knowledgeIndexHistory.add(new Date(new Date().getTime()-1000L*60*60*4), 10000d, 2*60000);
		knowledgeIndexHistory.add(new Date(new Date().getTime()-1000L*60*60*1), 9600d, 3*60000);
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
