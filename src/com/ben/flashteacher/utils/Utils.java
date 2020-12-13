package com.ben.flashteacher.utils;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class Utils
{
	/**
	 * Adds a new value to a historic moving average. 
	 * @param historicValue
	 * @param newValue
	 * @param newValueWeight Between 0 and 1, the closer to 1, the more account 
	 * will be taken of the new value. 
	 * @return The new historic value. Note that this will be equal to newValue 
	 * if historicValue==0.
	 */
	public static long exponentialWeightedAverage(long historicValue, long newValue, float newValueWeight)
	{
		assert(newValueWeight >= 0 && newValueWeight <= 1);
		if (historicValue == 0)
			return newValue;
		
		return (long) (newValue*newValueWeight + historicValue*(1-newValueWeight));
	}
	
	/**
	 * Returns the element at the specified index in any iterable object
	 * @param <T>
	 * @param index
	 * @param collection
	 * @return
	 * @throws NoSuchElementException If the index is out of range. 
	 */
	public static <T> T getElementAt(int index, Iterable<T> collection) throws NoSuchElementException
	{
		Iterator<T> it = collection.iterator();
		while (index > 0)
		{
			it.next();
			index--;
		}
		return it.next();
	}
}
