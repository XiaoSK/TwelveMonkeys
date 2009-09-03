package com.twelvemonkeys.io;

import com.twelvemonkeys.lang.Validate;

import java.io.IOException;
import java.io.InputStream;

/**
 * Represents a cached seekable stream, that reads through a cache.
 *
 * @author <a href="mailto:harald.kuhr@gmail.com">Harald Kuhr</a>
 * @author last modified by $Author: haku $
 * @version $Id: //depot/branches/personal/haraldk/twelvemonkeys/release-2/twelvemonkeys-core/src/main/java/com/twelvemonkeys/io/AbstractCachedSeekableStream.java#2 $
 */
abstract class AbstractCachedSeekableStream extends SeekableInputStream {
    /** The backing stream */
    protected final InputStream mStream;

    /** The stream positon in the backing stream (mStream) */
    protected long mStreamPosition;

    private StreamCache mCache;

    protected AbstractCachedSeekableStream(final InputStream pStream, final StreamCache pCache) {
        Validate.notNull(pStream, "stream");
        Validate.notNull(pCache, "cache");

        mStream = pStream;
        mCache = pCache;
    }

    protected final StreamCache getCache() {
        return mCache;
    }

    @Override
    public int available() throws IOException {
        long avail = mStreamPosition - mPosition + mStream.available();
        return avail > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) avail;
    }

    public int read() throws IOException {
        checkOpen();
        int read;

        if (mPosition == mStreamPosition) {
            // TODO: Read more bytes here!
            // TODO: Use buffer if not in-memory cache? (See FileCacheSeekableStream overrides).
            // Read a byte from the stream
            read = mStream.read();

            if (read >= 0) {
                mStreamPosition++;
                mCache.write(read);
            }
        }
        else {
            // ..or read byte from the cache
            syncPosition();
            read = mCache.read();
        }

        // TODO: This field is not REALLY considered accessible.. :-P
        if (read != -1) {
            mPosition++;
        }

        return read;
    }

    @Override
    public int read(byte[] pBytes, int pOffset, int pLength) throws IOException {
        checkOpen();
        int length;

        if (mPosition == mStreamPosition) {
            // Read bytes from the stream
            length = mStream.read(pBytes, pOffset, pLength);

            if (length > 0) {
                mStreamPosition += length;
                mCache.write(pBytes, pOffset, length);
            }
        }
        else {
            // ...or read bytes from the cache
            syncPosition();
            length = mCache.read(pBytes, pOffset, pLength);
        }

        // TODO: This field is not REALLY considered accessible.. :-P
        if (length > 0) {
            mPosition += length;
        }

        return length;
    }

    protected final void syncPosition() throws IOException {
        if (mCache.getPosition() != mPosition) {
            mCache.seek(mPosition); // Assure EOF is correctly thrown
        }
    }

    public final boolean isCached() {
        return true;
    }

    public abstract boolean isCachedMemory();

    public abstract boolean isCachedFile();

    protected void seekImpl(long pPosition) throws IOException {
        if (mStreamPosition < pPosition) {
            // Make sure we append at end of cache
            if (mCache.getPosition() != mStreamPosition) {
                mCache.seek(mStreamPosition);
            }

            // Read diff from stream into cache
            long left = pPosition - mStreamPosition;

            // TODO: Use fixed buffer, instead of allocating here...
            int bufferLen = left > 1024 ? 1024 : (int) left;
            byte[] buffer = new byte[bufferLen];

            while (left > 0) {
                int length = buffer.length < left ? buffer.length : (int) left;
                int read = mStream.read(buffer, 0, length);

                if (read > 0) {
                    mCache.write(buffer, 0, read);
                    mStreamPosition += read;
                    left -= read;
                }
                else if (read < 0) {
                    break;
                }
            }
        }
        else if (mStreamPosition >= pPosition) {
            // Seek backwards into the cache
            mCache.seek(pPosition);
        }

//        System.out.println("pPosition:        " + pPosition);
//        System.out.println("mPosition:        " + mPosition);
//        System.out.println("mStreamPosition:  " + mStreamPosition);
//        System.out.println("mCache.mPosition: " + mCache.getPosition());

        // NOTE: If mPosition == pPosition then we're good to go
    }

    protected void flushBeforeImpl(long pPosition) {
        mCache.flush(pPosition);
    }

    protected void closeImpl() throws IOException {
        mCache.flush(mPosition);
        mCache = null;
        mStream.close();
    }

    /**
     * An abstract stream cache.
     *
     * @author <a href="mailto:harald.kuhr@gmail.com">Harald Kuhr</a>
     * @author last modified by $Author: haku $
     * @version $Id: //depot/branches/personal/haraldk/twelvemonkeys/release-2/twelvemonkeys-core/src/main/java/com/twelvemonkeys/io/AbstractCachedSeekableStream.java#2 $
     */
    public static abstract class StreamCache {

        /**
         * Creates a {@code StreamCache}.
         */
        protected StreamCache() {
        }

        /**
         * Writes a single byte at the current read/write position. The read/write position will be increased by one.
         *
         * @param pByte the byte value to write.
         *
         * @throws IOException if an I/O exception occurs in the cache backing mechanism.
         */
        abstract void write(int pByte) throws IOException;

        /**
         * Writes a series of bytes at the current read/write position. The read/write position will be increased by
         * {@code pLength}.
         * <p/>
         * This implementation invokes {@link #write(int)} {@code pLength} times.
         * Subclasses may override this method for performance.
         *
         * @param pBuffer the bytes to write.
         * @param pOffset the starting offset into the buffer.
         * @param pLength the number of bytes to write from the buffer.
         *
         * @throws IOException if an I/O exception occurs in the cache backing mechanism.
         */
        void write(final byte[] pBuffer, final int pOffset, final int pLength) throws IOException {
            for (int i = 0; i < pLength; i++) {
                write(pBuffer[pOffset + i]);
            }
        }

        /**
         * Reads a single byte a the current read/write position. The read/write position will be increased by one.
         *
         * @return the value read, or {@code -1} to indicate EOF.
         *
         * @throws IOException if an I/O exception occurs in the cache backing mechanism.
         */
        abstract int read() throws IOException;

        /**
         * Writes a series of bytes at the current read/write position. The read/write position will be increased by
         * {@code pLength}.
         * <p/>
         * This implementation invokes {@link #read()} {@code pLength} times.
         * Subclasses may override this method for performance.
         *
         * @param pBuffer the bytes to write
         * @param pOffset the starting offset into the buffer.
         * @param pLength the number of bytes to write from the buffer.
         * @return the number of bytes read, or {@code -1} to indicate EOF.
         *
         * @throws IOException if an I/O exception occurs in the cache backing mechanism.
         */
        int read(final byte[] pBuffer, final int pOffset, final int pLength) throws IOException {
            int count = 0;
            for (int i = 0; i < pLength; i++) {
                int read = read();
                if (read >= 0) {
                    pBuffer[pOffset + i] = (byte) read;
                    count++;
                }
                else {
                    break;
                }
            }
            return count;
        }

        /**
         * Repositions the current cache read/write position to the given position.
         *
         * @param pPosition the new read/write position
         *
         * @throws IOException if an I/O exception occurs in the cache backing mechanism.
         */
        abstract void seek(long pPosition) throws IOException;

        /**
         * Optionally flushes any data prior to the given position.
         * <p/>
         * Attempting to perform a seek operation, and/or a read or write operation to a position equal to or before
         * the flushed position may result in exceptions or undefined behaviour.
         * <p/>
         * Subclasses should override this method for performance reasons, to avoid holding on to unnecessary resources.
         * This implementation does nothing.
         *
         * @param pPosition the last position to flush.
         */
        void flush(final long pPosition) {
        }

        /**
         * Returns the current cache read/write position.
         *
         * @return the current cache read/write postion.
         *
         * @throws IOException if the position can't be determined because of a problem in the cache backing mechanism.
         */
        abstract long getPosition() throws IOException;
    }
}
