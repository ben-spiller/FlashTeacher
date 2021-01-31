package benspiller.flashteacher;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import java.util.logging.Logger;

public class Messages
{
	static final Logger logger = Logger.getLogger(Messages.class.getName());

	private static final String BUNDLE_NAME = "benspiller.flashteacher.messages"; //$NON-NLS-1$

	private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle
			.getBundle(BUNDLE_NAME);

	private Messages()
	{
	}

	public static String getString(String key, Object... arguments)
	{
		try
		{
			String result = RESOURCE_BUNDLE.getString(key);
			if (arguments.length > 0)
				result = MessageFormat.format(result, arguments);
			return result;
		}
		catch (MissingResourceException e)
		{
			logger.log(java.util.logging.Level.FINE, "Missing resource: \n"+key+"=");
			return '!' + key + '!';
		}
	}
}
