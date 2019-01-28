package limax.xmlgen.java;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import limax.util.ElementHelper;
import limax.xmlgen.Manager;
import limax.xmlgen.Namespace;
import limax.xmlgen.Service;

class ManagerFormatter {
	private final Manager manager;
	private final Service service;

	ManagerFormatter(Manager manager, Service service) {
		this.manager = manager;
		this.service = service;
	}

	void make(Element parent) {
		final String elename = manager.isProvider() ? "Provider" : "Manager";
		Element self = getElementByAttrName(parent.getElementsByTagName(elename), manager.getName());
		if (null == self) {
			self = parent.getOwnerDocument().createElement(elename);
			self.setAttribute("name", manager.getName());
			parent.appendChild(self);
		}

		ElementHelper setter = new ElementHelper(self);
		setter.set("defaultStateClass", service.getFullName() + ".states." + manager.getName());

		if (manager.isProvider()) {
			if (manager.getSessionTimeout() > 0)
				setter.setIfEmpty("sessionTimeout", manager.getSessionTimeout());
			final Namespace ns = manager.bindProviderNamespace();
			setter.setIfEmpty("pvid", ns.getPvid());
			if (!ns.getViews().isEmpty())
				setter.set("viewManagerClass", service.getFullName() + "." + ns.getFullName() + ".ViewManager");
			final int managercount = self.getElementsByTagName("Manager").getLength();
			if (0 == managercount && !manager.getPort().isEmpty()) {
				final Element sub = self.getOwnerDocument().createElement("Manager");
				makeNetConf(sub);
				self.appendChild(sub);
			}
		} else {
			makeNetConf(self);
		}
	}

	private Element getElementByAttrName(NodeList list, String attr) {
		for (int i = 0; i < list.getLength(); ++i) {
			Element e = (Element) list.item(i);
			if (e.getAttribute("name").equals(attr))
				return e;
		}
		return null;
	}

	private void makeNetConf(Element self) {
		ElementHelper setter = new ElementHelper(self);

		setter.setIfEmpty("outputBufferSize", "8192");
		setter.setIfEmpty("inputBufferSize", "8192");
		setter.setIfEmpty("checkOutputBuffer", "false");

		if (manager.isServer()) {
			self.setAttribute("type", "server");
			setter.setIfEmpty("asynchronous", "false");
			setter.setIfEmpty("localPort", manager.getPort());
			setter.setIfEmpty("backlog", "32");
			setter.setIfEmpty("maxSize", "0");
			setter.setIfEmpty("autoStartListen", "true");
		} else {
			self.setAttribute("type", "client");
			setter.setIfEmpty("asynchronous", "false");
			setter.setIfEmpty("remoteIp", "127.0.0.1");
			setter.setIfEmpty("remotePort", manager.getPort());
			setter.setIfEmpty("autoReconnect", manager.isProvider());
			setter.setIfEmpty("connectTimeout", 5000);
		}
	}

}
