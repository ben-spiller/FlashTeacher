package com.ben.flashteacher.utils;

import java.awt.AWTKeyStroke;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JComponent;

public class SwingUtils
{
	public static void addTabAsFocusKey(JComponent c)
	{
		Set<AWTKeyStroke> focusKeys;
		
		// bind our new forward focus traversal keys
		focusKeys = new HashSet<AWTKeyStroke>(
				c.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
				);
		focusKeys.add( AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB,0) );
		c.setFocusTraversalKeys(
				KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, 
				Collections.unmodifiableSet(focusKeys)
			);
		
		// bind our new backward focus traversal keys (to match previous)
		focusKeys = new HashSet<AWTKeyStroke>(
				c.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS)
				);
		focusKeys.add( AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, KeyEvent.SHIFT_MASK+KeyEvent.SHIFT_DOWN_MASK) );
		c.setFocusTraversalKeys(
				KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, 
				Collections.unmodifiableSet(focusKeys)
			);
	}
	
	public static void addEnterAsFocusKey(JComponent c)
	{
		Set<AWTKeyStroke> focusKeys;
		
		// bind our new forward focus traversal keys
		focusKeys = new HashSet<AWTKeyStroke>(
				c.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
				);
		focusKeys.add( AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_ENTER,0) );
		c.setFocusTraversalKeys(
				KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, 
				Collections.unmodifiableSet(focusKeys)
			);
	}
	
}
