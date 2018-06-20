package limax.node.js;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public interface Module {
	default void link(EventLoop eventLoop, Object exports, Object require, Object module, String __filename,
			String __dirname, Object parameters) throws Exception {
		Class<?> clazz = getClass();
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				InputStream in = clazz.getResourceAsStream(clazz.getSimpleName() + ".js");) {
			byte[] piece = new byte[4096];
			for (int nread; (nread = in.read(piece)) != -1; baos.write(piece, 0, nread))
				;
			StringBuilder sb = new StringBuilder();
			sb.append("(function(exports, require, module, __filename, __dirname, parameters, java) {");
			sb.append(new String(baos.toByteArray(), StandardCharsets.UTF_8));
			sb.append("\n})");
			eventLoop.getInvocable().invokeMethod(eventLoop.getEngine().eval(sb.toString()), "call", null, exports,
					require, module, __filename, __dirname, parameters, this);
		}
	}
}
