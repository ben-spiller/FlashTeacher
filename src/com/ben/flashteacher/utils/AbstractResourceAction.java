package com.ben.flashteacher.utils;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import com.ben.flashteacher.Messages;

public abstract class AbstractResourceAction extends AbstractAction
{		
	private static final long serialVersionUID = 0L;
	
	final protected String resourceKey;
	public AbstractResourceAction(String resourceKey)
	{
		super();
		this.resourceKey = resourceKey;
		String name = Messages.getString(resourceKey+".name");
		putValue(AbstractResourceAction.NAME, name.replace("&&", "<&amp;>").replace("&", "").replace("<&amp;>", "&"));
		
		if (name.contains("&") && name.indexOf('&') < name.length()-1);
		{
			char mnemonic = name.charAt(name.indexOf('&')+1);
			putValue(AbstractResourceAction.MNEMONIC_KEY, (int)Character.toUpperCase(mnemonic));
		}
	}
	
	public final void actionPerformed(ActionEvent e)
	{
		handleActionPerformed(e);
	}
	
	public abstract void handleActionPerformed(ActionEvent e);

}
