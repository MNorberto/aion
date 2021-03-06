/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.p2p.impl1.tasks;

import static org.aion.p2p.impl1.P2pMgr.p2pLOG;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import org.aion.p2p.Header;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Msg;

/** @author chris */
public class TaskWrite implements Runnable {

    private final String nodeShortId;
    private final SocketChannel sc;
    private final Msg msg;
    private final ChannelBuffer channelBuffer;
    private final IP2pMgr p2pMgr;

    TaskWrite(
            final String _nodeShortId,
            final SocketChannel _sc,
            final Msg _msg,
            final ChannelBuffer _cb,
            final IP2pMgr _p2pMgr) {
        this.nodeShortId = _nodeShortId;
        this.sc = _sc;
        this.msg = _msg;
        this.channelBuffer = _cb;
        this.p2pMgr = _p2pMgr;
    }

    @Override
    public void run() {
        // reset allocated buffer and clear messages if the channel is closed
        if (channelBuffer.isClosed()) {
            channelBuffer.refreshHeader();
            channelBuffer.refreshBody();
            p2pMgr.dropActive(channelBuffer.getNodeIdHash(), "close-already");
            return;
        }

        try {
            channelBuffer.lock.lock();

            /*
             * @warning header set len (body len) before header encode
             */
            byte[] bodyBytes = msg.encode();
            int bodyLen = bodyBytes == null ? 0 : bodyBytes.length;
            Header h = msg.getHeader();
            h.setLen(bodyLen);
            byte[] headerBytes = h.encode();

            if (p2pLOG.isTraceEnabled()) {
                p2pLOG.trace(
                        "write id:{} {}-{}-{}",
                        nodeShortId,
                        h.getVer(),
                        h.getCtrl(),
                        h.getAction());
            }

            ByteBuffer buf = ByteBuffer.allocate(headerBytes.length + bodyLen);
            buf.put(headerBytes);
            if (bodyBytes != null) {
                buf.put(bodyBytes);
            }
            buf.flip();

            long t1 = System.nanoTime(), t2;
            int wrote = 0;
            try {
                do {
                    int result = sc.write(buf);
                    wrote += result;

                    if (result == 0) {
                        // @Attention:  very important sleep , otherwise when NIO write buffer full,
                        // without sleep will hangup this thread.
                        Thread.sleep(0, 1);
                    }

                    t2 = System.nanoTime() - t1;
                } while (buf.hasRemaining() && (t2 < 100_000_000));

                if (p2pLOG.isTraceEnabled() && (t2 > 10_000_000)) {
                    p2pLOG.trace(
                            "msg write: id {} size {} time {} ms length {}",
                            nodeShortId,
                            wrote,
                            t2,
                            buf.array().length);
                }

            } catch (ClosedChannelException ex1) {
                if (p2pLOG.isDebugEnabled()) {
                    p2pLOG.debug("closed-channel-exception node=" + this.nodeShortId, ex1);
                }

                channelBuffer.setClosed();
            } catch (IOException ex2) {
                if (p2pLOG.isDebugEnabled()) {
                    p2pLOG.debug(
                            "write-msg-io-exception node="
                                    + this.nodeShortId
                                    + " headerBytes="
                                    + String.valueOf(headerBytes.length)
                                    + " bodyLen="
                                    + String.valueOf(bodyLen)
                                    + " time="
                                    + String.valueOf(System.nanoTime()- t1)
                                    + "ns",
                            ex2);
                }

                if (ex2.getMessage().equals("Broken pipe")) {
                    channelBuffer.setClosed();
                }
            }
        } catch (Exception e) {
            p2pLOG.error("TaskWrite exception.", e);
        } finally {
            channelBuffer.lock.unlock();
        }
    }
}
