package com.mypan.service.dto.responseWrite;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public final class LimitedInputStream extends FilterInputStream {

    private long remaining;
    private final RandomAccessFile rafToClose;

    public LimitedInputStream(InputStream in, long limit, RandomAccessFile rafToClose) {
        super(in);
        this.remaining = Math.max(0, limit);
        this.rafToClose = rafToClose;
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) return -1;
        int b = super.read();
        if (b != -1) remaining--;
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (remaining <= 0) return -1;
        len = (int) Math.min(len, remaining);
        int n = super.read(b, off, len);
        if (n != -1) remaining -= n;
        return n;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            if (rafToClose != null) {
                try { rafToClose.close(); } catch (Exception ignore) {}
            }
        }
    }
}
