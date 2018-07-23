package limax.executable;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ByteLookupTable;
import java.awt.image.ColorConvertOp;
import java.awt.image.ConvolveOp;
import java.awt.image.IndexColorModel;
import java.awt.image.Kernel;
import java.awt.image.LookupOp;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.imageio.ImageIO;

import limax.codec.QrCode;
import limax.util.Elapse;

public final class QrCodeTool {

	private static void usage() {
		System.out.println("Usage: java -jar limax.jar qrcode encode <filename> <(L|M|Q|H)> <text>");
		System.out.println(
				"       java -jar limax.jar qrcode decode <filename> [sample_granularity=1] [bwthreshold=64] [meanfilter=2]");
		System.exit(0);
	}

	private static void encode(String[] args) throws Exception {
		if (args.length != 4)
			usage();
		String filename = args[1];
		int ecl = 0;
		switch (args[2]) {
		case "L":
			ecl = QrCode.ECL_L;
			break;
		case "M":
			ecl = QrCode.ECL_M;
			break;
		case "Q":
			ecl = QrCode.ECL_Q;
			break;
		case "H":
			ecl = QrCode.ECL_H;
			break;
		default:
			usage();
		}
		QrCode qrcode = QrCode.encode(args[3].getBytes(StandardCharsets.UTF_8), ecl);
		if (filename.endsWith(".svg")) {
			try (FileOutputStream fos = new FileOutputStream(filename)) {
				fos.write(qrcode.toSvgXML().getBytes());
			}
			return;
		}
		boolean[] modules = qrcode.getModules();
		int size = (int) Math.sqrt(modules.length);
		byte[] gray = new byte[] { 0, (byte) 255 };
		IndexColorModel icm = new IndexColorModel(1, 2, gray, gray, gray);
		int border = 4;
		int scale = 10;
		BufferedImage img = new BufferedImage((size + border * 2) * scale, (size + border * 2) * scale,
				BufferedImage.TYPE_BYTE_BINARY, icm);
		for (int i = 0; i < size + border * 2; i++)
			for (int j = 0; j < size + border * 2; j++) {
				int color = i >= border && i < size + border && j >= border && j < size + border
						&& modules[(j - 4) * size + i - 4] ? 0 : -1;
				for (int k = 0; k < scale; k++)
					for (int l = 0; l < scale; l++)
						img.setRGB(i * scale + k, j * scale + l, color);
			}
		int pos = filename.lastIndexOf(".");
		ImageIO.write(img, pos == -1 ? "bmp" : filename.substring(pos + 1), new File(filename));
	}

	private static void decode(String[] args) throws Exception {
		if (args.length < 2 || args.length > 5)
			usage();
		int sample_granularity = args.length > 2 ? Integer.parseInt(args[2]) : 1;
		int bwthreshold = args.length > 3 ? Integer.parseInt(args[3]) : 64;
		int meanfilter = (args.length > 4 ? Integer.parseInt(args[4]) : 2) * 2 + 1;
		BufferedImage img = ImageIO.read(new File(args[1]));
		Elapse elapse = new Elapse();
		byte[] threshold = new byte[256];
		Arrays.fill(threshold, bwthreshold, 256, (byte) 255);
		float[] mean = new float[meanfilter * meanfilter];
		Arrays.fill(mean, 1.0f / meanfilter / meanfilter);
		BufferedImageOp grayFilter = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
		BufferedImageOp meanFilter = new ConvolveOp(new Kernel(meanfilter, meanfilter, mean));
		BufferedImageOp bwFilter = new LookupOp(new ByteLookupTable(0, threshold), null);
		BufferedImage dst = bwFilter.filter(meanFilter.filter(grayFilter.filter(img, null), null), null);
		int height = dst.getHeight();
		int width = dst.getWidth();
		byte[] data = new byte[height * width];
		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++)
				data[y * width + x] = (byte) dst.getRGB(x, y);
		long imageOpElapsed = elapse.elapsedAndReset() / 1000000;
		QrCode.Info info = QrCode.decode(data, width, height, sample_granularity);
		long decodeOpElapsed = elapse.elapsed() / 1000000;
		System.out.format("%s\nImageOpElapsed:%dms, DecodeOpElapsed: %dms", info, imageOpElapsed, decodeOpElapsed);
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 2)
			usage();
		switch (args[0]) {
		case "encode":
			encode(args);
			break;
		case "decode":
			decode(args);
			break;
		default:
			usage();
		}
	}
}
