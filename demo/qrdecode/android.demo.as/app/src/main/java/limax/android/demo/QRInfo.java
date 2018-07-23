package limax.android.demo;

import java.nio.charset.Charset;

public class QRInfo {
    public enum Status {
        SCAN_OK, ERR_POSITIONING, ERR_VERSION_INFO, ERR_ALIGNMENTS, ERR_FORMAT_INFO, ERR_UNRECOVERABLE_CODEWORDS, UNSUPPORTED_ENCODE_MODE
    }
    public final static int ECL_M = 0;
    public final static int ECL_L = 1;
    public final static int ECL_H = 2;
    public final static int ECL_Q = 3;

    private Status status;
    private boolean reverse;
    private boolean mirror;
    private int version;
    private int ecl;
    private int mask;
    private byte[] data;

    public QRInfo(int status, int reverse, int mirror, int version, int ecl, int mask, byte[] data){
        this.status = Status.values()[status];
        this.reverse = reverse != 0;
        this.mirror = mirror != 0;
        this.version = version;
        this.ecl = ecl;
        this.mask = mask;
        this.data = data;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("status=").append(status).append("\n");
        sb.append("reverse=").append(reverse).append("\n");
        sb.append("mirror=").append(mirror).append("\n");
        sb.append("version=").append(version).append("\n");
        sb.append("ecl:");
        switch (ecl) {
            case ECL_M:
                sb.append("M");
                break;
            case ECL_L:
                sb.append("L");
                break;
            case ECL_H:
                sb.append("H");
                break;
            case ECL_Q:
                sb.append("Q");
                break;
        }
        sb.append("\n");
        sb.append("mask=").append(mask);
        sb.append("\ndata=");
        sb.append(data == null ? "null" : new String(data, Charset.forName("UTF-8")));
        return sb.toString();
    }
}
