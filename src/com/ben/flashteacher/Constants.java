package com.ben.flashteacher;

import java.awt.Image;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.ImageIcon;

public final class Constants
{
	//static final Logger logger = Logger.getLogger(Constants.class);
	
	/**
	 * Automatically incremented by the build
	 */
	private static final String BUILD_NUMBER = "73";
	
	/**
	 * The full 4-part version number
	 */
	public static final String VERSION_NUMBER = "1.1."+BUILD_NUMBER;
	
	public static final String COPYRIGHT = "Copyright (c) 2007-2020 Ben Spiller.";
	
	public static final ImageIcon APPLICATION_ICON_16x16 = getIcon("flashteacher_16x16_256.gif");
	public static final ImageIcon APPLICATION_ICON_32x32 = getIcon("flashteacher_32x32_256.gif");
	public static final ImageIcon APPLICATION_ICON_48x48 = getIcon("flashteacher_48x48_256.gif");
	public static final List<? extends Image> APPLICATION_ICONS = Collections.unmodifiableList(Arrays.asList(new Image[]{
			APPLICATION_ICON_16x16.getImage(), 
			APPLICATION_ICON_32x32.getImage(), 
			APPLICATION_ICON_48x48.getImage()
	}));

	private static ImageIcon getIcon(String resourceName)
	{
		return new ImageIcon(Constants.class.getResource("resources/"+resourceName), resourceName);
	}
}
