package benspiller.flashteacher.model;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jdom.Element;

/**
 * Holds a list of (Date, knowledgeIndex) tuples. Also provides the mechanism 
 * to serialize these to/from XML.   
 * @author Ben
 */
public class KnowledgeIndexHistory implements Iterable<KnowledgeIndexHistory.DataPoint>
{
	public static final String ELEMENT_NAME = "knowledgeIndexHistory";
	
	private final List<Long> dates = new ArrayList<Long>(); 
	private final List<Double> values = new ArrayList<Double>();
	private final List<Long> sessionDurationMillis = new ArrayList<Long>();
	
	public KnowledgeIndexHistory()
	{
		
	}
	
	/**
	 * Constructs this object from an XML element. 
	 * @param knowledgeIndexHistory May be <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public KnowledgeIndexHistory(Element knowledgeIndexHistory)
	{
		if (knowledgeIndexHistory == null) return;
		for (Element dataElement: (List<Element>)knowledgeIndexHistory.getChildren())
		{
			long date = Long.valueOf(dataElement.getAttributeValue("date", "0"));
			if (date <= 0) continue;
			add(new Date(date), 
					Double.valueOf(dataElement.getAttributeValue("value", "0")), 
					Long.valueOf(dataElement.getAttributeValue("sessionDurationMillis", "0")));
		}
	}
	
	public Element saveToXMLElement()
	{
		Element result = new Element(ELEMENT_NAME);
		for (int i = 0; i < dates.size(); i++)
		{
			Element knowledgeHistoryElement = new Element("knowledgeIndexData");
			knowledgeHistoryElement.setAttribute("date", String.valueOf(dates.get(i)));
			knowledgeHistoryElement.setAttribute("value", String.valueOf(values.get(i)));
			knowledgeHistoryElement.setAttribute("sessionDurationMillis", String.valueOf(sessionDurationMillis.get(i)));
			result.addContent(knowledgeHistoryElement);
		}
		return result;
	}
	
	/**
	 * Must be added in date order. 
	 * @param date
	 * @param knowledgeIndex
	 */
	public void add(Date date, double knowledgeIndex, long sessionDurationMillis)
	{
		if (date == null) throw new IllegalArgumentException("Cannot add knowledge index with null date");
		dates.add(date.getTime());
		values.add(knowledgeIndex);
		this.sessionDurationMillis.add(sessionDurationMillis);
	}
	
	/**
	 * @return An array containing a copy of this data as a two-dimensional 
	 * array where data[0] is an array of millisecond-date values 
	 * and the data[1] is the knowledge index values. Suitable for charting.  
	 */
	public double[][] getKnowledgeIndexArray()
	{
		double[][] data = new double[2][dates.size()];
		for (int i = 0; i < dates.size(); i++)
		{
			data[0][i] = dates.get(i);
			data[1][i] = values.get(i);
			//data[2][i] = timePerSessionSecs.get(i);
		}
		return data;
	}

	/**
	 * @return An array containing a copy of this data as a two-dimensional 
	 * array where data[0] is an array of millisecond-date values 
	 * and the data[1] is the duration values in minutes. Suitable for charting.  
	 */
	public double[][] getTimeSpentArray()
	{
		Map<Long,Long> byday = new HashMap<>();
		for (int i = 0; i < dates.size(); i++)
		{
			long day = removeTimeFromDateTime(dates.get(i));
			byday.put(day, sessionDurationMillis.get(i)/1000/60 + byday.getOrDefault(day, 0L));
		}
		double[][] data = new double[2][byday.size()];
		List<Long> days = new ArrayList<>(byday.keySet());
		Collections.sort(days);
		for (int i = 0; i < days.size(); i++)
		{
			data[0][i] = days.get(i) + 1000*60*60*12;
			data[1][i] = byday.get(days.get(i));
		}
		
		return data;
	}

	public static long removeTimeFromDateTime(long millis)
	{
		// suspect there's a better way to do this using java.time.* but it's not obvious
		
		Calendar cal = Calendar.getInstance(); // this is very non-thread-safe
		cal.setTimeInMillis(millis);
		cal.set(Calendar.AM_PM, Calendar.AM);
		cal.set(Calendar.HOUR, 0); // middle of the day
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTimeInMillis(); // without time
	}
	
	public double getAverageMinutesPerDayThisWeek()
	{
		int days = 7;
		long endOfToday = removeTimeFromDateTime(System.currentTimeMillis())+1000*60*60*24;
		long aWeekAgo = endOfToday-1000*60*60*24*days;
		
		long totalMillisSpent = 0;
		for (int i = 0; i < dates.size(); i++)
		{
			if (dates.get(i) > aWeekAgo)
				totalMillisSpent += sessionDurationMillis.get(i);
		}
		return (totalMillisSpent/1000.0/60)/days;
	}

	public long totalTimeSpentMillis()
	{
		long totalMillisSpent = 0;
		for (int i = 0; i < dates.size(); i++)
		{
			totalMillisSpent += sessionDurationMillis.get(i);
		}
		return totalMillisSpent;
	}

	/** Returns an iterator over the data points in this object, returned in 
	 * ascending date order. 
	 * @see java.lang.Iterable#iterator()
	 */
	public Iterator<DataPoint> iterator()
	{
		return new Iterator<DataPoint>() {
		
			int i = 0;
			public void remove()
			{
				throw new UnsupportedOperationException();
			}
		
			public DataPoint next()
			{
				DataPoint result = new DataPoint(dates.get(i),values.get(i));
				i = i+1;
				return result;
			}
		
			public boolean hasNext()
			{
				return i < dates.size();
			}
		
		};
	}
	
	public static class DataPoint
	{
		protected long date;
		protected double value;
		public long getDate() { return date; }
		public double getValue() { return value; }
		private DataPoint(long date, double value) {
			this.date = date;
			this.value = value;
		}
	}
}
