package limax.auany;

import limax.xmlconfig.Service;

public final class Main {

	public static void main(String[] args) throws Exception {
		Service.run(args.length > 0 ? args[0] : "service-auany.xml");
	}

}
