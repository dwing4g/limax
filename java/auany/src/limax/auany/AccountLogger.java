package limax.auany;

import org.w3c.dom.Element;

public interface AccountLogger extends AutoCloseable {
	void initialize(Element e) throws Exception;

	void link(int appid, long sessionid, String uid);

	void relink(int appid, long sessionid, String uidsrc, String uiddst);
}
