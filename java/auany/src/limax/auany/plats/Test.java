package limax.auany.plats;

import java.util.function.BiConsumer;

import org.w3c.dom.Element;

import limax.auany.PlatProcess;
import limax.defines.ErrorCodes;
import limax.defines.ErrorSource;
import limax.endpoint.AuanyService.Result;
import limax.http.HttpHandler;
import limax.util.ElementHelper;

public final class Test implements PlatProcess {

	private String password = "123456";

	@Override
	public void init(Element ele, BiConsumer<String, HttpHandler> httphandlers) {
		final String temp = new ElementHelper(ele).getString("password");
		if (!temp.isEmpty())
			password = temp;
	}

	@Override
	public void check(String username, String token, Result result) {
		result.apply(ErrorSource.LIMAX, token.equals(password) ? ErrorCodes.SUCCEED : ErrorCodes.AUANY_BAD_TOKEN,
				username);
	}

}
