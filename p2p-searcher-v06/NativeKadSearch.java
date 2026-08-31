package app.p2psearchernext.android;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Small native Kad2 metadata-search client for Android.
 *
 * It does not download payload files. It only performs Kad routing/keyword
 * lookups and returns file metadata/ED2K links.
 *
 * Flow:
 *   nodes.dat -> UDP Kad2 bootstrap/routing -> XOR-nearest contacts
 *   -> KADEMLIA2_SEARCH_KEY_REQ -> KADEMLIA2_SEARCH_RES.
 */
final class NativeKadSearch {
    private NativeKadSearch() { }

    private static final int OP_KADEMLIAHEADER = 0xE4;
    private static final int OP_KADEMLIAPACKEDPROT = 0xE5;
    private static final int KADEMLIA2_BOOTSTRAP_REQ = 0x01;
    private static final int KADEMLIA2_BOOTSTRAP_RES = 0x09;
    private static final int KADEMLIA2_REQ = 0x21;
    private static final int KADEMLIA2_RES = 0x29;
    private static final int KADEMLIA2_SEARCH_KEY_REQ = 0x33;
    private static final int KADEMLIA2_SEARCH_RES = 0x3B;
    private static final int KADEMLIA2_PING = 0x60;
    private static final int KADEMLIA2_PONG = 0x61;
    private static final int KADEMLIA_FIND_VALUE = 0x02;

    private static final int FT_FILENAME = 0x01;
    private static final int FT_FILESIZE = 0x02;
    private static final int FT_FILETYPE = 0x03;
    private static final int FT_SOURCES = 0x15;
    private static final int FT_COMPLETE_SOURCES = 0x30;
    private static final int FT_FILESIZE_HI = 0x3A;

    private static final int TAGTYPE_HASH16 = 0x01;
    private static final int TAGTYPE_STRING = 0x02;
    private static final int TAGTYPE_UINT32 = 0x03;
    private static final int TAGTYPE_FLOAT32 = 0x04;
    private static final int TAGTYPE_BOOL = 0x05;
    private static final int TAGTYPE_BOOLARRAY = 0x06;
    private static final int TAGTYPE_BLOB = 0x07;
    private static final int TAGTYPE_UINT16 = 0x08;
    private static final int TAGTYPE_UINT8 = 0x09;
    private static final int TAGTYPE_BSOB = 0x0A;
    private static final int TAGTYPE_UINT64 = 0x0B;
    private static final int TAGTYPE_STR1 = 0x11;
    private static final int TAGTYPE_STR16 = 0x20;

    private static final String[] NODES_URLS = new String[] {
            "https://upd.emule-security.org/nodes.dat",
            "https://www.nodes-dat.com/dl.php?load=nodes&trace=39513030.1944"
    };

    static SearchBatch search(String query) {
        SearchBatch batch = new SearchBatch();
        if (query == null || query.trim().length() == 0) return batch;

        String normalized = query.trim().toLowerCase(Locale.ROOT);
        String[] words = normalized.split("[^\\p{L}\\p{N}_]+", -1);
        String keyword = "";
        for (String w : words) {
            if (w.length() > keyword.length()) keyword = w;
        }
        if (keyword.length() == 0) keyword = normalized;

        try {
            byte[] target = MD4.digest(keyword.getBytes(StandardCharsets.UTF_8));
            List<Node> seeds = loadNodes(batch.errors);
            if (seeds.isEmpty()) {
                batch.errors.add("Kad：没有可用 nodes.dat 节点");
                return batch;
            }

            DatagramSocket socket = new DatagramSocket(null);
            try {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(0));
                socket.setSoTimeout(700);

                LinkedHashMap<String, Node> known = new LinkedHashMap<String, Node>();
                for (Node n : seeds) known.put(n.key(), n);

                int bootstrapCount = Math.min(24, seeds.size());
                for (int i = 0; i < bootstrapCount; i++) {
                    Node n = seeds.get((i * Math.max(1, seeds.size() / bootstrapCount)) % seeds.size());
                    send(socket, n, KADEMLIA2_BOOTSTRAP_REQ, new byte[0]);
                    send(socket, n, KADEMLIA2_PING, new byte[0]);
                }
                receiveWindow(socket, 2200, target, known, batch.rawItems, normalized, false);

                Set<String> asked = new HashSet<String>();
                for (int round = 0; round < 3; round++) {
                    List<Node> closest = closestNodes(known.values(), target, 18);
                    int sent = 0;
                    for (Node n : closest) {
                        if (asked.contains(n.key())) continue;
                        asked.add(n.key());
                        if (n.id == null || n.id.length != 16) continue;
                        ByteArrayOutputStream p = new ByteArrayOutputStream(33);
                        p.write(KADEMLIA_FIND_VALUE);
                        p.write(target);
                        p.write(n.id);
                        send(socket, n, KADEMLIA2_REQ, p.toByteArray());
                        sent++;
                        if (sent >= 8) break;
                    }
                    receiveWindow(socket, 1700, target, known, batch.rawItems, normalized, false);
                    if (sent == 0) break;
                }

                List<Node> closest = closestNodes(known.values(), target, 24);
                int queries = 0;
                for (Node n : closest) {
                    ByteArrayOutputStream p = new ByteArrayOutputStream(18);
                    p.write(target);
                    writeU16(p, 0);
                    send(socket, n, KADEMLIA2_SEARCH_KEY_REQ, p.toByteArray());
                    queries++;
                    if (queries >= 16) break;
                }
                receiveWindow(socket, 5000, target, known, batch.rawItems, normalized, true);

                if (batch.rawItems.isEmpty()) {
                    batch.errors.add("Kad：已发送原生 Kad2 查询，但本次没有收到可解析的关键词结果；部分现网节点可能要求 Kad UDP 混淆/验证密钥");
                }
            } finally {
                socket.close();
            }

            batch.items.addAll(dedupe(batch.rawItems));
        } catch (Exception e) {
            batch.errors.add("Kad：" + clean(e));
        }
        return batch;
    }

    private static void receiveWindow(DatagramSocket socket, long millis, byte[] target,
            LinkedHashMap<String, Node> known, List<MainActivity.SearchResult> out,
            String fullQuery, boolean collectSearch) throws IOException {
        long end = System.currentTimeMillis() + millis;
        byte[] buf = new byte[65507];
        while (System.currentTimeMillis() < end) {
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(dp);
            } catch (SocketTimeoutException e) {
                continue;
            }
            byte[] data = Arrays.copyOf(dp.getData(), dp.getLength());
            if (data.length < 2) continue;
            int proto = data[0] & 0xFF;
            int opcode = data[1] & 0xFF;
            byte[] payload;
            if (proto == OP_KADEMLIAPACKEDPROT) {
                try { payload = inflateKad(Arrays.copyOfRange(data, 2, data.length)); }
                catch (Exception ex) { continue; }
            } else if (proto == OP_KADEMLIAHEADER) {
                payload = Arrays.copyOfRange(data, 2, data.length);
            } else {
                continue;
            }
            if (opcode == KADEMLIA2_BOOTSTRAP_RES) {
                parseBootstrapResponse(payload, known);
            } else if (opcode == KADEMLIA2_RES) {
                parseRoutingResponse(payload, target, known);
            } else if (opcode == KADEMLIA2_SEARCH_RES && collectSearch) {
                parseSearchResponse(payload, target, fullQuery, out);
            } else if (opcode == KADEMLIA2_PONG) {
            }
        }
    }

    private static void parseBootstrapResponse(byte[] payload, LinkedHashMap<String, Node> known) {
        try {
            Cursor c = new Cursor(payload);
            if (c.remaining() >= 21) {
                int probableCount = (payload[19] & 0xFF) | ((payload[20] & 0xFF) << 8);
                if (21L + 25L * probableCount <= payload.length) {
                    c.skip(19);
                    int count = c.u16();
                    readPeerList(c, count, known);
                    return;
                }
            }
            c = new Cursor(payload);
            if (c.remaining() >= 2) {
                int count = c.u16();
                if (2L + 25L * count <= payload.length) readPeerList(c, count, known);
            }
        } catch (Exception ignored) { }
    }

    private static void parseRoutingResponse(byte[] payload, byte[] expectedTarget,
            LinkedHashMap<String, Node> known) {
        try {
            Cursor c = new Cursor(payload);
            if (c.remaining() < 17) return;
            byte[] target = c.bytes(16);
            if (!Arrays.equals(target, expectedTarget)) return;
            int count = c.u8();
            readPeerList(c, count, known);
        } catch (Exception ignored) { }
    }

    private static void readPeerList(Cursor c, int count, LinkedHashMap<String, Node> known) throws IOException {
        if (count < 0 || count > 256) return;
        for (int i = 0; i < count && c.remaining() >= 25; i++) {
            byte[] id = c.bytes(16);
            long ip = c.u32();
            int udp = c.u16();
            int tcp = c.u16();
            int version = c.u8();
            Node n = new Node(id, ipString(ip), udp, tcp, version);
            if (n.valid()) known.put(n.key(), n);
        }
    }

    private static void parseSearchResponse(byte[] payload, byte[] expectedTarget,
            String fullQuery, List<MainActivity.SearchResult> out) {
        try {
            Cursor c = new Cursor(payload);
            if (c.remaining() < 34) return;
            c.skip(16);
            byte[] target = c.bytes(16);
            if (!Arrays.equals(target, expectedTarget)) return;
            int count = c.u16();
            if (count < 0 || count > 512) return;
            for (int i = 0; i < count && c.remaining() >= 18; i++) {
                byte[] fileHash = c.bytes(16);
                int tags = c.u16();
                if (tags < 0 || tags > 128) return;
                String name = "";
                String type = "";
                long size = 0;
                long sizeHi = 0;
                long sources = -1;
                long complete = -1;
                for (int t = 0; t < tags; t++) {
                    Tag tag = readTag(c);
                    if (tag.nameId == FT_FILENAME && tag.stringValue != null) name = tag.stringValue;
                    else if (tag.nameId == FT_FILETYPE && tag.stringValue != null) type = tag.stringValue;
                    else if (tag.nameId == FT_FILESIZE && tag.intValue >= 0) size = tag.intValue;
                    else if (tag.nameId == FT_FILESIZE_HI && tag.intValue >= 0) sizeHi = tag.intValue;
                    else if (tag.nameId == FT_SOURCES && tag.intValue >= 0) sources = tag.intValue;
                    else if (tag.nameId == FT_COMPLETE_SOURCES && tag.intValue >= 0) complete = tag.intValue;
                }
                size = (sizeHi << 32) | (size & 0xFFFFFFFFL);
                if (name.trim().length() == 0 || size <= 0) continue;
                if (!matchesAllWords(name, fullQuery)) continue;
                String md4 = hex(fileHash);
                MainActivity.SearchResult item = new MainActivity.SearchResult();
                item.source = "Kad2 原生";
                item.title = name.trim();
                item.description = "Kad 关键词索引" + (complete >= 0 ? " · 完整来源 " + complete : "");
                item.contentType = type.length() > 0 ? "Kad/eD2K · " + type : "Kad/eD2K 元数据";
                item.rightsStatus = "P2P 网络发现 · 权利状态未知";
                item.size = size;
                item.ed2kHash = md4;
                item.ed2kLink = buildEd2kLink(item.title, size, md4);
                item.seeders = sources > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sources;
                item.leechers = -1;
                item.sourceCount = 1;
                out.add(item);
            }
        } catch (Exception ignored) { }
    }

    private static boolean matchesAllWords(String filename, String query) {
        String f = filename.toLowerCase(Locale.ROOT);
        String[] ws = query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_]+");
        for (String w : ws) if (w.length() > 0 && !f.contains(w)) return false;
        return true;
    }

    private static byte[] inflateKad(byte[] packed) throws IOException {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater();
        inflater.setInput(packed);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(65536, packed.length * 4));
        byte[] tmp = new byte[4096];
        try {
            while (!inflater.finished() && !inflater.needsInput()) {
                int n = inflater.inflate(tmp);
                if (n == 0) break;
                out.write(tmp, 0, n);
                if (out.size() > 2 * 1024 * 1024) throw new IOException("Kad 压缩包过大");
            }
        } catch (java.util.zip.DataFormatException e) {
            throw new IOException(e);
        } finally { inflater.end(); }
        return out.toByteArray();
    }

    private static final SecureRandom RNG = new SecureRandom();

    private static void send(DatagramSocket socket, Node node, int opcode, byte[] payload) {
        if (!node.valid()) return;
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream(payload.length + 2);
            b.write(OP_KADEMLIAHEADER);
            b.write(opcode);
            b.write(payload);
            byte[] raw = b.toByteArray();
            InetAddress address = InetAddress.getByName(node.host);
            socket.send(new DatagramPacket(raw, raw.length, address, node.udpPort));
            if (node.version >= 6 && node.id != null && node.id.length == 16) {
                byte[] crypt = encryptKad(raw, node.id);
                socket.send(new DatagramPacket(crypt, crypt.length, address, node.udpPort));
            }
        } catch (Exception ignored) { }
    }

    private static byte[] encryptKad(byte[] raw, byte[] receiverKadId) throws Exception {
        int randomPart = RNG.nextInt(65536);
        byte[] kd = new byte[18];
        System.arraycopy(receiverKadId, 0, kd, 0, 16);
        kd[16] = (byte)(randomPart & 0xFF);
        kd[17] = (byte)((randomPart >>> 8) & 0xFF);
        byte[] key = MessageDigest.getInstance("MD5").digest(kd);
        RC4 rc4 = new RC4(key);

        byte[] out = new byte[16 + raw.length];
        int marker;
        do { marker = RNG.nextInt(256) & 0xFC; } while (marker == OP_KADEMLIAHEADER || marker == OP_KADEMLIAPACKEDPROT || marker == 0xC5 || marker == 0xD4 || marker == 0xA3 || marker == 0xB2);
        out[0] = (byte)marker;
        out[1] = (byte)(randomPart & 0xFF);
        out[2] = (byte)((randomPart >>> 8) & 0xFF);
        byte[] body = new byte[13 + raw.length];
        body[0] = 0x39; body[1] = 0x5F; body[2] = 0x2E; body[3] = (byte)0xC1;
        body[4] = 0;
        System.arraycopy(raw, 0, body, 13, raw.length);
        rc4.crypt(body, 0, body.length);
        System.arraycopy(body, 0, out, 3, body.length);
        return out;
    }

    private static List<Node> loadNodes(List<String> errors) {
        for (String url : NODES_URLS) {
            try {
                byte[] data = MainActivity.Http.getBytes(url, 4 * 1024 * 1024);
                List<Node> parsed = parseNodesDat(data);
                if (!parsed.isEmpty()) return parsed;
            } catch (Exception ignored) { }
        }
        String[] fallback = new String[] {
                "219.144.245.34:13002:14341:4:d7717c40f7bddd97cbfc2eb480f82646",
                "222.45.49.107:19868:11031:4:d747be77a01809987a14b109264d900e",
                "101.68.242.56:22109:12509:4:d79d7cbb17a644fdca67afeda5123be3",
                "14.222.231.26:6784:13886:4:d79d468b724cff4e9eab7b56536505d0",
                "115.50.243.157:19714:9656:4:d602539713f1d464f7a8c882f9d00005"
        };
        ArrayList<Node> out = new ArrayList<Node>();
        for (String s : fallback) {
            String[] p = s.split(":");
            try { out.add(new Node(unhex(p[4]), p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]))); }
            catch (Exception ignored) { }
        }
        errors.add("Kad：实时 nodes.dat 获取失败，已尝试原 P2PSearcher 内置节点作为备用");
        return out;
    }

    static List<Node> parseNodesDat(byte[] data) throws IOException {
        Cursor c = new Cursor(data);
        if (c.remaining() < 4) return Collections.emptyList();
        long first = c.u32();
        int version;
        long count;
        boolean wide = false;
        if (first != 0) {
            version = 0;
            count = first;
        } else {
            if (c.remaining() < 8) return Collections.emptyList();
            version = (int)c.u32();
            if (version == 3) {
                long edition = c.u32();
                count = c.u32();
                wide = edition != 1;
            } else {
                count = c.u32();
                wide = version >= 2;
            }
        }
        if (count < 0 || count > 100000) throw new IOException("nodes.dat 数量异常");
        ArrayList<Node> out = new ArrayList<Node>();
        for (int i = 0; i < (int)count; i++) {
            if (c.remaining() < (wide ? 34 : 25)) break;
            byte[] id = c.bytes(16);
            long ip = c.u32();
            int udp = c.u16();
            int tcp = c.u16();
            int kadVersion = c.u8();
            if (wide) c.skip(9);
            Node n = new Node(id, ipString(ip), udp, tcp, kadVersion);
            if (n.valid() && kadVersion >= 2) out.add(n);
        }
        return out;
    }

    private static List<Node> closestNodes(java.util.Collection<Node> input, final byte[] target, int max) {
        ArrayList<Node> out = new ArrayList<Node>();
        for (Node n : input) if (n.valid() && n.id != null && n.id.length == 16) out.add(n);
        Collections.sort(out, new Comparator<Node>() {
            @Override public int compare(Node a, Node b) {
                for (int i = 0; i < 16; i++) {
                    int da = (a.id[i] ^ target[i]) & 0xFF;
                    int db = (b.id[i] ^ target[i]) & 0xFF;
                    if (da != db) return da < db ? -1 : 1;
                }
                return 0;
            }
        });
        if (out.size() > max) return new ArrayList<Node>(out.subList(0, max));
        return out;
    }

    private static List<MainActivity.SearchResult> dedupe(List<MainActivity.SearchResult> input) {
        LinkedHashMap<String, MainActivity.SearchResult> map = new LinkedHashMap<String, MainActivity.SearchResult>();
        for (MainActivity.SearchResult item : input) {
            String key = item.ed2kHash.toLowerCase(Locale.US) + ":" + item.size;
            MainActivity.SearchResult old = map.get(key);
            if (old == null) map.put(key, item);
            else {
                old.seeders = Math.max(old.seeders, item.seeders);
                old.sourceCount++;
            }
        }
        return new ArrayList<MainActivity.SearchResult>(map.values());
    }

    private static Tag readTag(Cursor c) throws IOException {
        int type = c.u8();
        int nameId = 0;
        if ((type & 0x80) != 0) {
            type &= 0x7F;
            nameId = c.u8();
        } else {
            int nameLength = c.u16();
            if (nameLength == 1) nameId = c.u8();
            else c.skip(nameLength);
        }
        Tag tag = new Tag(nameId);
        if (type == TAGTYPE_STRING) tag.stringValue = c.string16();
        else if (type == TAGTYPE_UINT32) tag.intValue = c.u32();
        else if (type == TAGTYPE_UINT64) tag.intValue = c.u64();
        else if (type == TAGTYPE_UINT16) tag.intValue = c.u16();
        else if (type == TAGTYPE_UINT8) tag.intValue = c.u8();
        else if (type >= TAGTYPE_STR1 && type <= TAGTYPE_STR16) tag.stringValue = c.utf8(type - TAGTYPE_STR1 + 1);
        else if (type == TAGTYPE_HASH16) c.skip(16);
        else if (type == TAGTYPE_FLOAT32) c.skip(4);
        else if (type == TAGTYPE_BOOL) c.skip(1);
        else if (type == TAGTYPE_BOOLARRAY) { int bits = c.u16(); c.skip((bits / 8) + 1); }
        else if (type == TAGTYPE_BLOB) { long n = c.u32(); if (n > Integer.MAX_VALUE) throw new IOException("blob"); c.skip((int)n); }
        else if (type == TAGTYPE_BSOB) c.skip(c.u8());
        else throw new IOException("未知 Kad tag 0x" + Integer.toHexString(type));
        return tag;
    }

    private static String buildEd2kLink(String name, long size, String hash) {
        try {
            String encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20").replace("%2F", "/");
            return "ed2k://|file|" + encoded + "|" + size + "|" + hash.toUpperCase(Locale.US) + "|/";
        } catch (Exception e) {
            return "ed2k://|file|" + name.replace("|", "%7C") + "|" + size + "|" + hash.toUpperCase(Locale.US) + "|/";
        }
    }

    private static String ipString(long ip) {
        return ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF);
    }

    private static void writeU16(ByteArrayOutputStream out, int v) { out.write(v & 0xFF); out.write((v >>> 8) & 0xFF); }
    private static String clean(Exception e) { String s = e.getMessage(); return s == null || s.trim().length() == 0 ? e.getClass().getSimpleName() : s.trim(); }
    private static String hex(byte[] b) { StringBuilder s = new StringBuilder(b.length * 2); for (byte x : b) s.append(String.format(Locale.US, "%02X", x & 0xFF)); return s.toString(); }
    private static byte[] unhex(String s) { byte[] b = new byte[s.length()/2]; for(int i=0;i<b.length;i++) b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16); return b; }

    static final class SearchBatch {
        final ArrayList<MainActivity.SearchResult> items = new ArrayList<MainActivity.SearchResult>();
        final ArrayList<MainActivity.SearchResult> rawItems = new ArrayList<MainActivity.SearchResult>();
        final ArrayList<String> errors = new ArrayList<String>();
    }

    static final class Node {
        final byte[] id; final String host; final int udpPort; final int tcpPort; final int version;
        Node(byte[] id, String host, int udpPort, int tcpPort, int version) {
            this.id=id; this.host=host; this.udpPort=udpPort; this.tcpPort=tcpPort; this.version=version;
        }
        boolean valid() { return host != null && host.length() > 0 && udpPort > 0 && udpPort <= 65535 && !"0.0.0.0".equals(host); }
        String key() { return host + ":" + udpPort; }
    }

    private static final class Tag { final int nameId; String stringValue; long intValue=-1; Tag(int id){nameId=id;} }

    private static final class Cursor {
        final byte[] data; int pos;
        Cursor(byte[] data){this.data=data;}
        int remaining(){return data.length-pos;}
        void require(int n)throws IOException{if(n<0||remaining()<n)throw new IOException("截断的 Kad 数据包");}
        int u8()throws IOException{require(1);return data[pos++]&0xFF;}
        int u16()throws IOException{require(2);int v=(data[pos]&0xFF)|((data[pos+1]&0xFF)<<8);pos+=2;return v;}
        long u32()throws IOException{require(4);long v=(data[pos]&255L)|((data[pos+1]&255L)<<8)|((data[pos+2]&255L)<<16)|((data[pos+3]&255L)<<24);pos+=4;return v;}
        long u64()throws IOException{require(8);long v=0;for(int i=0;i<8;i++)v|=(data[pos+i]&255L)<<(8*i);pos+=8;return v;}
        byte[] bytes(int n)throws IOException{require(n);byte[] b=Arrays.copyOfRange(data,pos,pos+n);pos+=n;return b;}
        String utf8(int n)throws IOException{return new String(bytes(n),StandardCharsets.UTF_8);}
        String string16()throws IOException{return utf8(u16());}
        void skip(int n)throws IOException{require(n);pos+=n;}
    }

    private static final class RC4 {
        private final byte[] s = new byte[256];
        private int i, j;
        RC4(byte[] key) {
            for (int n=0;n<256;n++) s[n]=(byte)n;
            int q=0;
            for (int n=0;n<256;n++) {
                q=(q+(s[n]&255)+(key[n%key.length]&255))&255;
                byte t=s[n];s[n]=s[q];s[q]=t;
            }
        }
        void crypt(byte[] b,int off,int len){
            for(int n=0;n<len;n++){
                i=(i+1)&255;j=(j+(s[i]&255))&255;byte t=s[i];s[i]=s[j];s[j]=t;
                int k=s[((s[i]&255)+(s[j]&255))&255]&255;b[off+n]^=(byte)k;
            }
        }
    }

    /** RFC1320 MD4, kept local so Android does not depend on a provider exposing MD4. */
    private static final class MD4 {
        static byte[] digest(byte[] msg) {
            int orig = msg.length;
            long bits = ((long) orig) * 8L;
            int padded = ((orig + 9 + 63) / 64) * 64;
            byte[] data = Arrays.copyOf(msg, padded);
            data[orig] = (byte)0x80;
            for (int i=0;i<8;i++) data[padded-8+i]=(byte)(bits >>> (8*i));
            int a=0x67452301,b=0xefcdab89,c=0x98badcfe,d=0x10325476;
            int[] x=new int[16];
            for(int off=0;off<data.length;off+=64){
                for(int i=0;i<16;i++){int p=off+i*4;x[i]=(data[p]&255)|((data[p+1]&255)<<8)|((data[p+2]&255)<<16)|(data[p+3]<<24);}
                int aa=a,bb=b,cc=c,dd=d;
                a=ff(a,b,c,d,x[0],3); d=ff(d,a,b,c,x[1],7); c=ff(c,d,a,b,x[2],11); b=ff(b,c,d,a,x[3],19);
                a=ff(a,b,c,d,x[4],3); d=ff(d,a,b,c,x[5],7); c=ff(c,d,a,b,x[6],11); b=ff(b,c,d,a,x[7],19);
                a=ff(a,b,c,d,x[8],3); d=ff(d,a,b,c,x[9],7); c=ff(c,d,a,b,x[10],11); b=ff(b,c,d,a,x[11],19);
                a=ff(a,b,c,d,x[12],3); d=ff(d,a,b,c,x[13],7); c=ff(c,d,a,b,x[14],11); b=ff(b,c,d,a,x[15],19);
                a=gg(a,b,c,d,x[0],3); d=gg(d,a,b,c,x[4],5); c=gg(c,d,a,b,x[8],9); b=gg(b,c,d,a,x[12],13);
                a=gg(a,b,c,d,x[1],3); d=gg(d,a,b,c,x[5],5); c=gg(c,d,a,b,x[9],9); b=gg(b,c,d,a,x[13],13);
                a=gg(a,b,c,d,x[2],3); d=gg(d,a,b,c,x[6],5); c=gg(c,d,a,b,x[10],9); b=gg(b,c,d,a,x[14],13);
                a=gg(a,b,c,d,x[3],3); d=gg(d,a,b,c,x[7],5); c=gg(c,d,a,b,x[11],9); b=gg(b,c,d,a,x[15],13);
                a=hh(a,b,c,d,x[0],3); d=hh(d,a,b,c,x[8],9); c=hh(c,d,a,b,x[4],11); b=hh(b,c,d,a,x[12],15);
                a=hh(a,b,c,d,x[2],3); d=hh(d,a,b,c,x[10],9); c=hh(c,d,a,b,x[6],11); b=hh(b,c,d,a,x[14],15);
                a=hh(a,b,c,d,x[1],3); d=hh(d,a,b,c,x[9],9); c=hh(c,d,a,b,x[5],11); b=hh(b,c,d,a,x[13],15);
                a=hh(a,b,c,d,x[3],3); d=hh(d,a,b,c,x[11],9); c=hh(c,d,a,b,x[7],11); b=hh(b,c,d,a,x[15],15);
                a+=aa;b+=bb;c+=cc;d+=dd;
            }
            byte[] out=new byte[16]; put(out,0,a);put(out,4,b);put(out,8,c);put(out,12,d);return out;
        }
        private static int f(int x,int y,int z){return(x&y)|(~x&z);} private static int g(int x,int y,int z){return(x&y)|(x&z)|(y&z);} private static int h(int x,int y,int z){return x^y^z;}
        private static int rol(int x,int s){return(x<<s)|(x>>>(32-s));}
        private static int ff(int a,int b,int c,int d,int x,int s){return rol(a+f(b,c,d)+x,s);} private static int gg(int a,int b,int c,int d,int x,int s){return rol(a+g(b,c,d)+x+0x5a827999,s);} private static int hh(int a,int b,int c,int d,int x,int s){return rol(a+h(b,c,d)+x+0x6ed9eba1,s);}
        private static void put(byte[] o,int p,int v){o[p]=(byte)v;o[p+1]=(byte)(v>>>8);o[p+2]=(byte)(v>>>16);o[p+3]=(byte)(v>>>24);}
    }
}
