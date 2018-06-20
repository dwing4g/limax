package limax.auany.appconfig;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

abstract class Provider {
	enum Status {
		OFF, ON, ON_PAY
	}

	private final int id;
	private final String key;
	private volatile Status status = Status.OFF;
	private volatile String json = "";

	Provider(int id, String key) {
		this.id = id;
		this.key = key;
	}

	Element createElement(Document doc) {
		Element e = doc.createElement("provider");
		e.setAttribute("id", String.valueOf(id));
		e.setAttribute("key", key);
		return e;
	}

	final int getId() {
		return id;
	}

	final boolean verifyKey(String key) {
		return key.equals(this.key);
	}

	final String getJSON() {
		return json;
	}

	final Status getStatus() {
		return status;
	}

	boolean updateJSON(String json) {
		if (json.equals(this.json))
			return false;
		this.json = json;
		return true;
	}

	boolean updateStatus(Status status) {
		if (this.status == status)
			return false;
		if (status == Status.OFF)
			json = "";
		this.status = status;
		return true;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Provider))
			return false;
		Provider p = (Provider) o;
		return p.id == this.id && p.key.equals(this.key);
	}
}
