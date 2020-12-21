package benspiller.flashteacher.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import org.apache.log4j.Logger;

import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.JDOMParseException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

public class XMLUtils
{
	final static Logger logger = Logger.getLogger(XMLUtils.class.getName());
	static final Charset CHARSET = Charset.forName("UTF-8");
	
	/**
	 * Loads the specified JDOM XML document from a file. Throws a 
	 * readily-displayable error message in the event of a problem. 
	 * @param f
	 * @return The JDOM document
	 * @throws IOException
	 */
	public static Document loadXML(File f) throws IOException
	{
		SAXBuilder builder = new SAXBuilder();
		builder.setValidation(true);
		
		try {
			return builder.build(f);
		} catch (JDOMParseException e)
		{
			StringBuilder b = new StringBuilder();
			
			final int CONTEXT_LINES = 2;
			final String CONTEXT_PREFIX = "      ";
			
			BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), CHARSET));
			try {
				String line;
				int lineNumber = 0;
				while ((line = r.readLine()) != null)
				{
					lineNumber++;
					if (lineNumber > e.getLineNumber()+CONTEXT_LINES)
						break;
					if (lineNumber >= e.getLineNumber()-CONTEXT_LINES)
					{
						b.append('\n');
						b.append(CONTEXT_PREFIX);
						b.append(line);
					}
				}
			} catch (Exception e2)
			{
				logger.debug("Failed to extract relevant bit of XML for error message: "+e2.getMessage());
				// ignore
			} finally {
				r.close();
			}
			
			if (b.length() == 0)
				b.insert(0, e.getMessage()+".");
			else
				b.insert(0, e.getMessage());

			throw new IOException(b.toString(), e);
		} catch (JDOMException e)
		{
			throw new IOException(e);
		}	
	}
	
	public static void saveXML(Document doc, File file) throws IOException
	{
		Format format = Format.getPrettyFormat();
		//format.setTextMode(TextMode.NORMALIZE);
		format.setIndent("\t");
		format.setEncoding(CHARSET.name());
		XMLOutputter outputter = new XMLOutputter(format);
		OutputStream outputStream = new FileOutputStream(file);
		try {
			outputter.output(doc, outputStream);
		} finally {
			outputStream.close();
		}
		
	}
	
}
