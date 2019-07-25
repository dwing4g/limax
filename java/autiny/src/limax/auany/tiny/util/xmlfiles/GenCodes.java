package limax.auany.tiny.util.xmlfiles;

import java.io.File;
import java.io.FileOutputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import limax.util.XMLUtils;

public final class GenCodes {

	static void append(Element from, Element to) throws Exception {
		for (Element s : XMLUtils.getChildElements(from)) {
			final Node n = to.getOwnerDocument().importNode(s, true);
			to.appendChild(n);
		}
	}

	private static void append(Element root, Element zdb, File file) throws Exception {
		for (Element s : XMLUtils.getChildElements(XMLUtils.getRootElement(file))) {
			if (s.getTagName().equalsIgnoreCase("zdb")) {
				append(s, zdb);
			} else {
				final Node n = root.getOwnerDocument().importNode(s, true);
				root.appendChild(n);
			}
		}
	}

	private static void saveElementToFile(Element zdb, String name, File output) throws Exception {
		final Element root = XMLUtils.createRootElement(name);
		append(zdb, root);
		XMLUtils.prettySave(root.getOwnerDocument(), new FileOutputStream(output));
		System.out.println(output.getCanonicalPath() + " done!");
	}

	public static void main(String[] args) throws Exception {
		final File auanyxml = new File(args[0]);
		final File globalidxml = new File(args[1]);
		final File output = new File(args[2]);
		final File zdboutput = new File(args[3]);
		final File svroutput = new File(args[4]);

		final Element root = XMLUtils.createRootElement("application");
		final Document doc = root.getOwnerDocument();
		final Element zdb = doc.createElement("zdb");

		root.setAttribute("name", "limax");

		append(root, zdb, auanyxml);
		append(root, zdb, globalidxml);

		saveElementToFile(zdb, "zdb", zdboutput);
		saveElementToFile(root, "application", svroutput);

		root.appendChild(zdb);
		XMLUtils.prettySave(doc, new FileOutputStream(output));
		System.out.println(output.getCanonicalPath() + " done!");

	}

}
