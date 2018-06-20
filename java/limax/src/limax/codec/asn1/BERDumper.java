package limax.codec.asn1;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import limax.codec.Codec;
import limax.codec.CodecException;

public class BERDumper implements Codec {
	private static class Walk implements Visitor {
		private int level = 0;
		private final PrintStream out;

		public Walk(PrintStream out) {
			this.out = out;
		}

		@Override
		public void enter() {
			level++;
		}

		@Override
		public void leave() {
			level--;
		}

		@Override
		public void update(ASN1Object obj) {
			for (int i = 0; i < level; i++)
				out.print("    ");
			out.println(obj);
		}
	}

	private final List<ASN1Object> r = new ArrayList<>();
	private final Codec codec = new DecodeBER(r);
	private final Walk walk;

	public BERDumper(OutputStream out) {
		this.walk = new Walk(new PrintStream(out));
	}

	@Override
	public void update(byte[] data, int off, int len) throws CodecException {
		codec.update(data, off, len);
	}

	@Override
	public void update(byte c) throws CodecException {
		codec.update(c);
	}

	@Override
	public void flush() throws CodecException {
		codec.flush();
		for (ASN1Object o : r)
			o.visit(walk);
	}

	public static void dump(byte[] data) throws CodecException {
		BERDumper dumper = new BERDumper(System.out);
		dumper.update(data, 0, data.length);
		dumper.flush();
	}
}
