package limax.auany.tiny.util.xmlfiles;

import java.io.File;
import java.io.FileOutputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import limax.util.XMLUtils;

public final class MergerXml {

	public static void main(String[] args) throws Exception {
		final File input = new File(args[0]);
		final File output = new File(args[1]);

		final Element root = XMLUtils.createRootElement("application");
		final Document doc = root.getOwnerDocument();

		final Element zdb = XMLUtils.getRootElement(
				MergerXml.class.getClassLoader().getResourceAsStream("limax/auany/tiny/util/xmlfiles/cache.zdb.xml"));
		final Element svr = XMLUtils.getRootElement(
				MergerXml.class.getClassLoader().getResourceAsStream("limax/auany/tiny/util/xmlfiles/cache.svr.xml"));

		final Element inputroot = XMLUtils.getRootElement(input);
		root.setAttribute("name", "limax");
		for (Element s : XMLUtils.getChildElements(inputroot)) {
			if (s.getTagName().equalsIgnoreCase("zdb"))
				GenCodes.append(s, zdb);
			else
				root.appendChild(doc.importNode(s, true));
		}
		root.appendChild(doc.importNode(zdb, true));
		GenCodes.append(svr, root);

		XMLUtils.prettySave(doc, new FileOutputStream(output));
		System.out.println(output.getCanonicalPath() + " done!");
	}
}
