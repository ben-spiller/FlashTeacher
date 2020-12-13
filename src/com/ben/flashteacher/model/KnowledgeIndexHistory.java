package com.ben.flashteacher.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

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
			add(new Date(date), Double.valueOf(dataElement.getAttributeValue("value", "0")));
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
			result.addContent(knowledgeHistoryElement);
		}
		return result;
	}
	
	/**
	 * Must be added in date order. 
	 * @param date
	 * @param knowledgeIndex
	 */
	public void add(Date date, double knowledgeIndex)
	{
		if (date == null) throw new IllegalArgumentException("Cannot add knowledge index with null date");
		dates.add(date.getTime());
		values.add(knowledgeIndex);
	}
	
	/**
	 * @return An array containing a copy of this data as a two-dimensional 
	 * array where the first dimension is an array of millisecond-date values 
	 * and the second is the knowledge index values. 
	 */
	public double[][] getData()
	{
		double[][] data = new double[2][dates.size()];
		for (int i = 0; i < dates.size(); i++)
		{
			data[0][i] = dates.get(i);
			data[1][i] = values.get(i);
		}
		return data;
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
