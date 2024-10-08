package com.google.protobuf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;


/**
 * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/12/8
 * @Description:protobuf的一个工具类，作用是把bte数组或者是ByteBuffer中的数据包装到ByteString对象中
 */
public class ZeroByteStringHelper {

    /**
     * Wrap a byte array into a ByteString.
     */
    public static ByteString wrap(final byte[] bs) {
        return ByteString.wrap(bs);
    }

    /**
     * Wrap a byte array into a ByteString.
     * @param bs     the byte array
     * @param offset read start offset in array
     * @param len    read data length
     *@return the    result byte string.
     */
    public static ByteString wrap(final byte[] bs, final int offset, final int len) {
        return ByteString.wrap(bs, offset, len);
    }

    /**
     * Wrap a byte buffer into a ByteString.
     */
    public static ByteString wrap(final ByteBuffer buf) {
        return ByteString.wrap(buf);
    }

    /**
     * Carry the byte[] from {@link ByteString}, if failed,
     * then call {@link ByteString#toByteArray()}.
     *
     * @param byteString the byteString source data
     * @return carried bytes
     */
    public static byte[] getByteArray(final ByteString byteString) {
        final BytesCarrier carrier = new BytesCarrier();
        try {
            byteString.writeTo(carrier);
            if (carrier.isValid()) {
                return carrier.getValue();
            }
        } catch (final IOException ignored) {
            // ignored
        }
        return byteString.toByteArray();
    }

    /**
     * Concatenate the given strings while performing various optimizations to
     * slow the growth rate of tree depth and tree node count. The result is
     * either a {@link ByteString.LeafByteString} or a
     * {@link RopeByteString} depending on which optimizations, if any, were
     * applied.
     *
     * <p>Small pieces of length less than {@link
     * ByteString#CONCATENATE_BY_COPY_SIZE} may be copied by value here, as in
     * BAP95.  Large pieces are referenced without copy.
     *
     * <p>Most of the operation here is inspired by the now-famous paper <a
     *  * href="https://web.archive.org/web/20060202015456/http://www.cs.ubc.ca/local/reading/proceedings/spe91-95/spe/vol25/issue12/spe986.pdf">
     *
     * @param left  string on the left
     * @param right string on the right
     * @return concatenation representing the same sequence as the given strings
     */
    public static ByteString concatenate(final ByteString left, final ByteString right) {
        return RopeByteString.concatenate(left, right);
    }

    /**
     * @see #concatenate(ByteString, ByteString)
     */
    public static ByteString concatenate(final List<ByteBuffer> byteBuffers) {
        final int size = byteBuffers.size();
        if (size == 0) {
            return null;
        }
        if (size == 1) {
            return wrap(byteBuffers.get(0));
        }

        ByteString left = null;
        for (final ByteBuffer buf : byteBuffers) {
            if (buf.remaining() > 0) {
                if (left == null) {
                    left = wrap(buf);
                } else {
                    left = concatenate(left, wrap(buf));
                }
            }
        }
        return left;
    }
}
