package limax.pkix.tool;

import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPasswordField;
import javax.swing.JRootPane;

public final class AuthCode {
	private final Mac mac;
	private long recent;

	private AuthCode(byte[] keyServer, char[] keyApp) throws Exception {
		this.mac = Mac.getInstance("HmacSHA256");
		this.mac.init(new SecretKeySpec(keyServer, 0, keyServer.length, "HmacSHA256"));
		byte[] key = this.mac.doFinal(new String(keyApp).getBytes(StandardCharsets.UTF_8));
		this.mac.init(new SecretKeySpec(key, 0, key.length, "HmacSHA256"));
	}

	private static void runUI(byte[] keyServer) {
		AtomicReference<AuthCode> ref = new AtomicReference<>();
		JFrame frame = new JFrame();
		frame.setUndecorated(true);
		frame.getRootPane().setWindowDecorationStyle(JRootPane.INFORMATION_DIALOG);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setAlwaysOnTop(true);
		frame.setResizable(false);
		frame.setLocationByPlatform(true);
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		float res = toolkit.getScreenResolution();
		Font font = Font.decode("Dialog.bold").deriveFont(res / 4);
		Clipboard clipboard = toolkit.getSystemClipboard();
		JPasswordField passwordField = new JPasswordField(6);
		JButton button = new JButton("authCode");
		button.setFont(font);
		passwordField.setFont(font);
		passwordField.addActionListener(l -> {
			try {
				ref.set(new AuthCode(keyServer, passwordField.getPassword()));
			} catch (Exception e) {
			}
			frame.remove(passwordField);
			frame.add(button);
			frame.pack();
		});
		button.addActionListener(l -> clipboard.setContents(new StringSelection(ref.get().totpValue()), (c, t) -> {
		}));
		frame.add(passwordField);
		frame.pack();
		frame.setVisible(true);
	}

	private String totpValue(long t) {
		return Base64.getEncoder().encodeToString(mac.doFinal(String.format("%d", t).getBytes()));
	}

	private String totpValue() {
		return totpValue(System.currentTimeMillis() / 10000);
	}

	public static void main(String[] args) throws Exception {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try (InputStream in = AuthCode.class.getResourceAsStream("AuthCode.key")) {
			while (true) {
				int c = in.read();
				if (c == -1)
					break;
				os.write(c);
			}
		}
		runUI(os.toByteArray());
	}

	private boolean verify(String in, long t) {
		if (!in.equals(totpValue(t)))
			return false;
		recent = t;
		return true;
	}

	synchronized boolean verify(String in) {
		long t = System.currentTimeMillis() / 10000;
		return recent < t - 1 && (verify(in, t) || verify(in, t - 1) || verify(in, t + 1));
	}

	static AuthCode create(byte[] keyServer, char[] keyApp) throws Exception {
		String myName = AuthCode.class.getName();
		Manifest manifest = new Manifest();
		Attributes attributes = manifest.getMainAttributes();
		attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		attributes.put(Attributes.Name.MAIN_CLASS, myName);
		try (JarOutputStream jos = new JarOutputStream(new FileOutputStream("authcode.jar"), manifest)) {
			ZipEntry entry = new ZipEntry(myName.replace('.', '/') + ".class");
			byte[] data = new byte[65536];
			int len;
			try (InputStream in = AuthCode.class.getResourceAsStream("AuthCode.class")) {
				len = in.read(data);
			}
			entry.setSize(len);
			jos.putNextEntry(entry);
			jos.write(data, 0, len);
			entry = new ZipEntry(myName.replace('.', '/') + ".key");
			entry.setSize(keyServer.length);
			jos.putNextEntry(entry);
			jos.write(keyServer, 0, keyServer.length);
		}
		return new AuthCode(keyServer, keyApp);
	}
}
