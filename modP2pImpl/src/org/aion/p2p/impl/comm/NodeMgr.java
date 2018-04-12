/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p.impl.comm;

import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.IP2pMgr;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class NodeMgr implements INodeMgr {

	private final static int TIMEOUT_INBOUND_NODES = 10000;
	private static final String BASE_PATH = System.getProperty("user.dir");
	private static final String PEER_LIST_FILE_PATH = BASE_PATH + "/config/peers.xml";

	private final Set<String> seedIps = new HashSet<>();

	// private final Map<Integer, Node> activeNodes = new ConcurrentHashMap<>();

	public List<Node> allStmNodes = new CopyOnWriteArrayList<>();

	// public void dumpAllNodeInfo() {
	// StringBuilder sb = new StringBuilder();
	// sb.append(" ==================== ALL PEERS METRIC
	// ===========================\n");
	// List<Node> all = allStmNodes;
	// all.sort((a, b) -> (int) (b.getBestBlockNumber() -
	// a.getBestBlockNumber()));
	// int cnt = 0;
	// for (Node n : all) {
	// char isSeed = n.getIfFromBootList() ? 'Y' : 'N';
	// sb.append(String.format(" %3d ID:%6s SEED:%c IP:%15s PORT:%5d
	// PORT_CONN:%5d
	// FC:%1d BB:%8d \n", cnt,
	// n.getIdShort(), isSeed, n.getIpStr(), n.getPort(), n.getConnectedPort(),
	// n.peerMetric.metricFailedConn, n.getBestBlockNumber()));
	// cnt++;
	// }
	// System.out.println(sb.toString());
	// }

	int selfNid;

	public NodeMgr(int nid) {
		this.selfNid = nid;
	}

	private final static char[] hexArray = "0123456789abcdef".toCharArray();

	private static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	/**
	 *
	 * @param selfShortId
	 *            String
	 */
	public String dumpNodeInfo(String selfShortId) {
		StringBuilder sb = new StringBuilder();
		sb.append("\n");
		sb.append(String.format(
				"================================================================== p2p-status-%6s ==================================================================\n",
				selfShortId));
		sb.append(String.format(
				"temp[%3d] inbound[%3d] outbound[%3d] active[%3d]                            s - seed node, td - total difficulty, # - block number, bv - binary version\n",
				0, 0, 0, 0));
		List<Node> sorted = this.allStmNodes;
		if (sorted.size() > 0) {
			sb.append("\n          s"); // id & seed
			sb.append("               td");
			sb.append("          #");
			sb.append("                                                             hash");
			sb.append("              ip");
			sb.append("  port");
			sb.append("     conn");
			sb.append("              bv    stat     cid\n");
			sb.append(
					"-------------------------------------------------------------------------------------------------------------------------------------------------------\n");
			sorted.sort((n1, n2) -> {
				int tdCompare = n2.getTotalDifficulty().compareTo(n1.getTotalDifficulty());
				if (tdCompare == 0) {
					Long n2Bn = n2.getBestBlockNumber();
					Long n1Bn = n1.getBestBlockNumber();
					return n2Bn.compareTo(n1Bn);
				} else
					return tdCompare;
			});
			for (Node n : sorted) {
				try {
					sb.append(String.format("id:%6s %c %16s %10d %64s %15s %5d %8s %15s %8s %12s\n", n.getIdShort(),
							n.getIfFromBootList() ? 'y' : ' ', n.getTotalDifficulty().toString(10),
							n.getBestBlockNumber(),
							n.getBestBlockHash() == null ? "" : bytesToHex(n.getBestBlockHash()), n.getIpStr(),
							n.getPort(), n.getConnection(), n.getBinaryVersion(), n.st.toString(), n.getCid()));
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
		sb.append("\n");
		return sb.toString();
	}

	// private void updateMetric(final Node _n) {
	// if (_n.hasFullInfo()) {
	// int fullHash = _n.getFullHash();
	// if (allNodes.containsKey(fullHash)) {
	// Node orig = allNodes.get(fullHash);
	//
	// // pull out metric.
	// _n.peerMetric = orig.peerMetric;
	// _n.copyNodeStatus(orig);
	// }
	// allNodes.put(fullHash, _n);
	// }
	// }

	// public void updateAllNodesInfo(INode _n) {
	// Node n = (Node) _n;
	//
	// if (n.hasFullInfo()) {
	// int fullHash = n.getFullHash();
	// if (allNodes.containsKey(fullHash)) {
	// Node orig = allNodes.get(fullHash);
	// // pull out metric.
	// orig.copyNodeStatus(n);
	// }
	// }
	// }

	public boolean validateNode(final Node _node, int selfNodeIdHash, byte[] selfIp, int selfPort) {
		boolean notNull = _node != null;
		// filter self
		boolean notSelfId = _node.getIdHash() != selfNodeIdHash;
		boolean notSameIpOrPort = !(Arrays.equals(selfIp, _node.getIp()) && selfPort == _node.getPort());

		// filter already active.
		// boolean notActive = !nodeMgr.hasActiveNode(_node.getIdHash());

		boolean notActive = !hasActiveNode(_node.getCid());

		// filter out conntected.
		boolean notOutbound = !(_node.st.stat == NodeStm.CONNECTTED);

		for (Node n : allStmNodes) {
			if ((n.getIdHash() > 0) && (n.getIdHash() == _node.getIdHash())) {
				return false;
			}
		}

		return notNull && notSelfId && notSameIpOrPort && notActive && notOutbound;
	}

	/**
	 * 
	 * Add nodes from active nodes response. Assume node have node id , but without
	 * channel.
	 * 
	 * @param _node
	 * @return
	 */
	public boolean validateNodeForAdd(final Node _node) {
		if (_node == null)
			return false;

		// either nid or cid.
		if (_node.getIdHash() == 0 && _node.getChannel() == null) {
			return false;
		}
		// filter self
		if (_node.getIdHash() == selfNid)
			return false;

		for (Node n : allStmNodes) {
			if ((n.getIdHash() != 0) && (n.getIdHash() == _node.getIdHash())) {
				return false;
			}
		}

		return true;
	}

	public void removeDups() {
		for (Node n : allStmNodes) {

			if (n.getCid() < 0 || n.getIdHash() == 0)
				continue;

			for (Node n1 : allStmNodes) {
				if (n == n1)
					continue;

				if (n1.getCid() < 0 || n1.getIdHash() == 0)
					continue;

				if (n.getIdHash() == n1.getIdHash() && (Arrays.compare(n.getIp(), n1.getIp()) == 0)
						&& n.getPort() == n1.getPort() && n.getCid() != n1.getCid()) {
					allStmNodes.remove(n1);
					try {
						n1.getChannel().close();
					} catch (Exception e) {
					}
				}
			}
		}
	}

	/**
	 * @param _ip
	 *            String
	 */
	public void seedIpAdd(String _ip) {
		this.seedIps.add(_ip);
	}

	public boolean isSeedIp(String _ip) {
		return this.seedIps.contains(_ip);
	}

	public int activeNodesSize() {
		int ret = 0;
		for (Node n : allStmNodes) {
			if (n.st.hasStat(NodeStm.ACTIVE)) {
				ret++;
			}
		}
		return ret;
	}

	public boolean hasActiveNode(int k) {
		for (Node n : allStmNodes) {
			if (n.st.hasStat(NodeStm.ACTIVE) && n.getCid() == k) {
				return true;
			}
		}
		return false;
	}

	public Node getActiveNode(int k) {
		for (Node n : allStmNodes) {
			if (n.st.hasStat(NodeStm.ACTIVE) && n.getCid() == k) {
				return n;
			}
		}
		return null;
	}

	public Map<Integer, INode> getActiveNodes() {
		Map<Integer, INode> ret = new HashMap<>();

		for (Node n : allStmNodes) {
			if (n.st.hasStat(NodeStm.ACTIVE)) {
				ret.put(n.getCid(), n);
			}
		}

		return ret;
	}

	public Node getRandomInitNode() {
		List<Node> ns = new ArrayList<>();

		for (Node n : allStmNodes) {
			if (n.st.hasStat(NodeStm.INIT)) {
				ns.add(n);
			}
		}

		if (ns.size() == 0)
			return null;

		int pos = (int) (Math.random() * ns.size());
		return ns.get(pos);
	}

	public void addStmNode(Node n) {
		if (this.validateNodeForAdd(n)) {
			// System.out.println("add node nid:" + n.getIdShort());
			this.allStmNodes.add(n);
		}
	}

	public void addStmNodeForBoot(Node n) {
		// System.out.println("add node nid:" + n.getIdHash());
		this.allStmNodes.add(n);
	}

	public Node allocNode(String ip, int p0, int p1) {
		Node n = new Node(ip, p0, p1);
		allStmNodes.add(n);
		return n;
	}

	public Node getStmNode(int cid, int st) {
		for (Node n : allStmNodes) {
			if (n.st.hasStat(st) && n.getCid() == cid) {
				return n;
			}
		}
		return null;
	}

	public boolean isDupNode(Node _node) {
		for (Node n : allStmNodes) {
			if (_node.getIdHash() == n.getIdHash() && Arrays.compare(_node.getIp(), n.getIp()) == 0
					&& _node.getPort() == n.getPort()) {
				return true;
			}
		}
		return false;
	}

	public void closeNode(Node n) {
		if (n.getChannel() != null) {
			try {
				n.getChannel().close();
			} catch (Exception e) {
			}
			this.allStmNodes.remove(n);
		}
	}

	public Node getStmNodeByNid(int nid, int st) {
		for (Node n : allStmNodes) {
			if (n.st.hasStat(st) && n.getIdHash() == nid) {
				return n;
			}
		}
		return null;
	}

	public List<Node> getStmNodeHS() {
		List<Node> ns = new ArrayList<>();
		for (Node n : allStmNodes) {
			if (n.st.shouldHS()) {
				ns.add(n);
			}
		}
		return ns;
	}

	public void removeClosed() {
		for (Node n : allStmNodes) {
			if (((n.getCid() < 0) && (!n.st.hasStat(NodeStm.INIT))) || n.st.hasStat(NodeStm.CLOSED))
				allStmNodes.remove(n);
		}
	}

	public List<Node> getActiveNodesList() {
		List<Node> ret = new ArrayList<>();
		for (Node n : allStmNodes) {
			if (n.st.hasStat(NodeStm.ACTIVE)) {
				ret.add(n);
			}
		}
		return ret;
	}

	// public Map<Integer, INode> getActiveNodesMap() {
	// return new HashMap(activeNodes);
	// }

	public INode getRandom() {
		int nodesCount = activeNodesSize();
		if (nodesCount > 0) {
			Random r = new Random(System.currentTimeMillis());
			List<Node> keysArr = getActiveNodesList();
			try {
				int randomNodeKeyIndex = r.nextInt(nodesCount);
				Node n = keysArr.get(randomNodeKeyIndex);
				return n;
			} catch (IllegalArgumentException e) {
				System.out.println("<p2p get-random-exception>");
				return null;
			}
		} else
			return null;
	}

	public INode getRandomRealtime(long bbn) {

		List<Integer> keysArr = new ArrayList<>();

		for (Node n : getActiveNodesList()) {
			if ((n.getBestBlockNumber() == 0) || (n.getBestBlockNumber() > bbn)) {
				keysArr.add(n.getIdHash());
			}
		}

		int nodesCount = keysArr.size();
		if (nodesCount > 0) {
			Random r = new Random(System.currentTimeMillis());

			try {
				int randomNodeKeyIndex = r.nextInt(keysArr.size());
				int randomNodeKey = keysArr.get(randomNodeKeyIndex);
				return this.getActiveNode(randomNodeKey);
			} catch (IllegalArgumentException e) {
				return null;
			}
		} else
			return null;
	}

	/**
	 * @param _nodeIdHash
	 *            int
	 * @param _shortId
	 *            String
	 * @param _p2pMgr
	 *            P2pMgr
	 */
	// public void moveOutboundToActive(int _cid, String _shortId, final IP2pMgr
	// _p2pMgr) {
	// for (Node n : this.allStmNodes) {
	// if (n.getCid() == _cid) {
	//
	// if (activeNodes.containsKey(_cid)) {
	// return;
	// } else {
	// activeNodes.put(_cid, n);
	//
	// if (_p2pMgr.isShowLog())
	// System.out.println("<p2p action=move-outbound-to-active channel-id=" + _cid +
	// ">");
	// }
	// }
	// }
	//
	// }

	/**
	 * @param _channelHashCode
	 *            int
	 * @param _p2pMgr
	 *            P2pMgr
	 */
	// public void moveInboundToActive(int _cid, final IP2pMgr _p2pMgr) {
	// for (Node n : this.allStmNodes) {
	// if (n.getCid() == _cid) {
	//
	// if (activeNodes.containsKey(_cid))
	// return;
	// else {
	// activeNodes.put(_cid, n);
	// if (_p2pMgr.isShowLog())
	// System.out.println("<p2p action=move-inbound-to-active channel-id=" + _cid +
	// ">");
	//
	// }
	// }
	// }
	//
	// }

	public void rmMetricFailedNodes() {

		// Iterator nodesIt = tempNodes.iterator();
		// while (nodesIt.hasNext()) {
		// Node n = (Node) nodesIt.next();
		// if (n.peerMetric.shouldNotConn() && !n.getIfFromBootList())
		// tempNodes.remove(n);
	}

	// public void rmTimeOutActives(IP2pMgr pmgr) {
	// long now = System.currentTimeMillis();
	//
	// OptionalDouble average = activeNodes.values().stream().mapToLong(n -> now -
	// n.getTimestamp()).average();
	// double timeout = average.orElse(4000) * 5;
	// timeout = Math.max(10000, Math.min(timeout, 60000));
	// if (pmgr.isShowLog()) {
	// System.out.printf("<p2p average-delay=%.0fms>\n", average.orElse(0));
	// }
	//
	// Iterator activeIt = activeNodes.keySet().iterator();
	// while (activeIt.hasNext()) {
	// int key = (int) activeIt.next();
	// Node node = getActiveNode(key);
	//
	// if (now - node.getTimestamp() > timeout || !node.getChannel().isConnected())
	// {
	//
	// pmgr.closeSocket(node.getChannel());
	// activeIt.remove();
	// if (pmgr.isShowLog())
	// System.out.println("<p2p-clear-active ip=" + node.getIpStr() + " node=" +
	// node.getIdShort() + ">");
	//
	// // if (this.observer != null)
	// // this.observer.removeActiveNode(key);
	// }
	// }
	// }

	public void dropActive(Integer nodeIdHash, IP2pMgr pmgr) {
		// Node node = activeNodes.remove(nodeIdHash);
		//
		// // if we tried to drop a node that is already dropped
		// if (node == null)
		// return;
		//
		// // this.observer.removeActiveNode(nodeIdHash);
		// pmgr.closeSocket(node.getChannel());
	}

	public void removeActive(Integer nodeIdHash, IP2pMgr pmgr) {
		dropActive(nodeIdHash, pmgr);
	}

	/**
	 * @param _p2pMgr
	 *            P2pMgr
	 */
	public void shutdown(final IP2pMgr _p2pMgr) {
		try {
			// activeNodes.forEach((k, n) -> _p2pMgr.closeSocket(n.getChannel()));
			// activeNodes.clear();
			// outboundNodes.forEach((k, n) ->
			// _p2pMgr.closeSocket(n.getChannel()));
			// outboundNodes.clear();
			// inboundNodes.forEach((k, n) ->
			// _p2pMgr.closeSocket(n.getChannel()));
			// inboundNodes.clear();

			this.allStmNodes.forEach((n) -> _p2pMgr.closeSocket(n.getChannel()));
		} catch (Exception e) {

		}
	}

	public void persistNodes() {
		XMLOutputFactory output = XMLOutputFactory.newInstance();
		output.setProperty("escapeCharacters", false);
		XMLStreamWriter sw = null;
		try {
			sw = output.createXMLStreamWriter(new FileWriter(PEER_LIST_FILE_PATH));
			sw.writeStartDocument("utf-8", "1.0");
			sw.writeCharacters("\r\n");
			sw.writeStartElement("aion-peers");

			for (Node node : allStmNodes) {
				sw.writeCharacters(node.toXML());
			}

			sw.writeCharacters("\r\n");
			sw.writeEndElement();
			sw.flush();
			sw.close();

		} catch (Exception e) {
			System.out.println("<error on-write-peers-xml-to-file>");
		} finally {
			if (sw != null) {
				try {
					sw.close();
				} catch (XMLStreamException e) {
					System.out.println("<error on-close-stream-writer>");
				}
			}
		}
	}

	public void ban(int cid) {
		Node node = this.getActiveNode(cid);
		if (node != null) {
			node.peerMetric.ban();
		}
	}
}
