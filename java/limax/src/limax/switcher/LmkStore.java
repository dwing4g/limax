package limax.switcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.endpoint.LmkBundle;
import limax.pkix.LmkRequest;
import limax.util.Helper;
import limax.util.Trace;

public final class LmkStore {
	private static final OpenOption[] options = new OpenOption[] { StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.SYNC };
	private static final Path pathRequestStore = Paths.get(System.getProperty("java.io.tmpdir"), "LmkRequestStore");
	private static final Path pathDataStore = Paths.get(System.getProperty("java.io.tmpdir"), "LmkDataStore");
	private static List<LmkRequest.Context> requests;
	private static List<LmkInfo> lmkInfos;

	static {
		try {
			Files.createDirectories(pathRequestStore);
			try (Stream<Path> stream = Files.list(pathRequestStore)) {
				requests = stream.map(path -> {
					try {
						String fn = Helper.fromFileNameString(path.getFileName().toString());
						int pos = fn.lastIndexOf('@');
						return LmkRequest.buildLmkRequest(fn.substring(0, pos)).createContext(fn.substring(pos + 1));
					} catch (Exception e) {
						if (Trace.isErrorEnabled())
							Trace.error("LmkStore load <" + path + "> fail, drop it.", e);
						try {
							Files.deleteIfExists(path);
						} catch (IOException e1) {
						}
						return null;
					}
				}).filter(Objects::nonNull).collect(Collectors.toList());
			}
		} catch (Exception e) {
			if (Trace.isErrorEnabled())
				Trace.error("LmkStore loadRequestStore", e);
		}
		try {
			Files.createDirectories(pathDataStore);
			try (Stream<Path> stream = Files.list(pathDataStore)) {
				lmkInfos = stream.map(path -> {
					try {
						return new LmkInfo(Helper.fromFileNameString(path.getFileName().toString()),
								OctetsStream.wrap(Octets.wrap(Files.readAllBytes(path))).unmarshal_Octets());
					} catch (Exception e) {
						if (Trace.isErrorEnabled())
							Trace.error("LmkStore load <" + path + "> fail, drop it.", e);
						try {
							Files.deleteIfExists(path);
						} catch (IOException e1) {
						}
						return null;
					}
				}).filter(Objects::nonNull).collect(Collectors.toList());
			}
		} catch (Exception e) {
			if (Trace.isErrorEnabled())
				Trace.error("LmkStore loadDataStore", e);
		}
	}

	private LmkStore() {
	}

	static void save(LmkRequest.Context ctx, byte[] passphrase) throws IOException {
		Files.write(pathRequestStore.resolve(Helper.toFileNameString(ctx.getUid() + "@" + ctx.getHost())), passphrase,
				options);
	}

	static Octets save(LmkRequest.Context ctx, LmkBundle lmkBundle) throws Exception {
		Path pathRequest = pathRequestStore.resolve(Helper.toFileNameString(ctx.getUid() + "@" + ctx.getHost()));
		Octets lmkData = lmkBundle.save(Octets.wrap(Files.readAllBytes(pathRequest)));
		Files.write(pathDataStore.resolve(Helper.toFileNameString(ctx.getUid())),
				new OctetsStream().marshal(lmkData).getBytes(), options);
		Files.deleteIfExists(pathRequest);
		return lmkData;
	}

	public static void done(String uid) {
		try {
			Files.deleteIfExists(pathDataStore.resolve(Helper.toFileNameString(uid)));
		} catch (IOException e) {
		}
	}

	public static void recover(LmkMasquerade lmkMasquerade) {
		requests.forEach(ctx -> lmkMasquerade.scheduleRenew(ctx, null));
		requests.clear();
		lmkInfos.forEach(lmkInfo -> lmkMasquerade.recover(lmkInfo));
		lmkInfos.clear();
	}
}
