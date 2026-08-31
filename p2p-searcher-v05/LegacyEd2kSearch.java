package app.p2psearchernext.android;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Native eD2K server-search client for Android.
 *
 * v0.5 follows the actual eDonkey server session sequence instead of sending
 * unauthenticated UDP queries:
 *   TCP connect -> OP_LOGINREQUEST -> OP_IDCHANGE -> OP_SEARCHREQUEST -> OP_SEARCHRESULT.
 *
 * It only discovers metadata and creates ed2k links; it never downloads file payloads.
 */
final class LegacyEd2kSearch {
    private LegacyEd2kSearch() { }

    private static final int OP_EDONKEYPROT = 0xE3;
    private static final int OP_PACKEDPROT = 0xD4;
    private static final int OP_LOGINREQUEST = 0x01;
    private static final int OP_SEARCHREQUEST = 0x16;
    private static final int OP_SEARCHRESULT = 0x33;
    private static final int OP_SERVERMESSAGE = 0x38;
    private static final int OP_IDCHANGE = 0x40;

    private static final int CT_NAME = 0x01;
    private static final int CT_VERSION = 0x11;
    private static final int CT_SERVER_FLAGS = 0x20;
    private static final int CT_EMULE_VERSION = 0xFB;
    private static final int EDONKEYVERSION = 0x3C;
    private static final int LOGIN_CAPS = 0x0004 | 0x0008 | 0x0010 | 0x0100;

    private static final int FT_FILENAME = 0x01;
    private static final int FT_FILESIZE = 0x02;
    private static final int FT_FILESIZE_HI = 0x3A;
    private static final int FT_FILETYPE = 0x03;
    private static final int FT_SOURCES = 0x15;
    private static final int FT_COMPLETE_SOURCES = 0x30;

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

    private static final String[] SERVER_MET_URLS = new String[]{
            "https://upd.emule-security.org/server.met",
            "https://shortypower.org/server.met"
    };

    private static final String[] FALLBACK_SERVERS = new String[]{
            "176.123.5.89:4725", "77.42.68.79:4232", "85.17.116.222:6082",
            "91.208.162.182:4232", "212.95.35.240:4232", "91.208.162.87:4232",
            "85.121.5.137:4232", "213.141.198.207:4232", "193.187.90.12:4661",
            "91.126.170.253:5687"
    };

    static SearchBatch search(String query) {
        SearchBatch batch = new SearchBatch();
        if (query == null || query.trim().length() == 0) return batch;
        try {
            List<Server> servers = loadServers(batch.errors);
            if (servers.isEmpty()) {
                batch.errors.add("eD2K：没有可用服务器");
                return batch;
            }
            int cap = Math.min(6, servers.size());
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(3, cap));
            ArrayList<Callable<NodeResult>> calls = new ArrayList<Callable<NodeResult>>();
            for (int i = 0; i < cap; i++) {
                final Server server = servers.get(i);
                final String term = query.trim();
                calls.add(new Callable<NodeResult>() {
                    @Override public NodeResult call() {
                        return tcpSearchServer(server, term);
                    }
                });
            }
            try {
                List<Future<NodeResult>> futures = pool.invokeAll(calls);
                int successfulLogins = 0;
                for (Future<NodeResult> future : futures) {
                    try {
                        NodeResult result = future.get();
                        if (result.loggedIn) successfulLogins++;
                        batch.items.addAll(result.items);
                        if (result.error.length() > 0) batch.errors.add(result.error);
                    } catch (Exception e) {
                        batch.errors.add("eD2K 节点：" + clean(e));
                    }
                }
                if (successfulLogins == 0) {
                    batch.errors.add("eD2K：未能登录现网服务器；请检查网络是否允许直连 TCP eD2K 端口");
                }
            } finally {
                pool.shutdownNow();
            }
            ArrayList<MainActivity.SearchResult> all = new ArrayList<MainActivity.SearchResult>(batch.items);
            batch.items.clear();
            batch.items.addAll(dedupe(all));
        } catch (Exception e) {
            batch.errors.add("eD2K：" + clean(e));
        }
        return batch;
    }

    private static NodeResult tcpSearchServer(Server server, String query) {
        NodeResult result = new NodeResult();
        Socket socket = new Socket();
        String node = server.host + ":" + server.port;
        try {
            socket.connect(new InetSocketAddress(server.host, server.port), 3500);
            socket.setSoTimeout(4500);
            socket.setTcpNoDelay(true);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            sendFrame(out, OP_LOGINREQUEST, buildLoginPayload());
            long loginDeadline = System.currentTimeMillis() + 6500L;
            String lastMessage = "";
            while (System.currentTimeMillis() < loginDeadline) {
                Frame frame;
                try { frame = readFrame(in); }
                catch (SocketTimeoutException e) { break; }
                if (frame.opcode == OP_SERVERMESSAGE) {
                    lastMessage = parseServerMessage(frame.payload);
                } else if (frame.opcode == OP_IDCHANGE) {
                    if (frame.payload.length < 4) throw new IOException("登录应答过短");
                    long clientId = le32(frame.payload, 0);
                    if (clientId == 0) throw new IOException("服务器拒绝登录" + (lastMessage.length() > 0 ? "：" + lastMessage : ""));
                    result.loggedIn = true;
                    break;
                }
            }
            if (!result.loggedIn) {
                result.error = "eD2K " + node + "：TCP 已连接但登录超时" +
                        (lastMessage.length() > 0 ? " · " + lastMessage : "");
                return result;
            }

            sendFrame(out, OP_SEARCHREQUEST, buildSearchPayload(query));
            long searchDeadline = System.currentTimeMillis() + 6500L;
            boolean gotAnswer = false;
            while (System.currentTimeMillis() < searchDeadline) {
                Frame frame;
                try { frame = readFrame(in); }
                catch (SocketTimeoutException e) { break; }
                if (frame.opcode == OP_SEARCHRESULT) {
                    gotAnswer = true;
                    parseTcpSearchReply(frame.payload, server, result.items);
                    if (!result.items.isEmpty()) break;
                } else if (frame.opcode == OP_SERVERMESSAGE) {
                    lastMessage = parseServerMessage(frame.payload);
                }
            }
            if (!gotAnswer) {
                result.error = "eD2K " + node + "：已登录，但服务器未返回搜索应答" +
                        (lastMessage.length() > 0 ? " · " + lastMessage : "");
            }
        } catch (Exception e) {
            result.error = "eD2K " + node + "：" + clean(e);
        } finally {
            try { socket.close(); } catch (Exception ignored) { }
        }
        return result;
    }

    private static byte[] buildLoginPayload() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(128);
        byte[] userHash = new byte[16];
        new SecureRandom().nextBytes(userHash);
        userHash[5] = 14;
        userHash[14] = 111;
        out.write(userHash);
        writeU32(out, 0);
        writeU16(out, 4662);
        writeU32(out, 4);
        writeStringTag(out, CT_NAME, "P2PSearcher Android");
        writeIntTag(out, CT_VERSION, EDONKEYVERSION);
        writeIntTag(out, CT_SERVER_FLAGS, LOGIN_CAPS);
        writeIntTag(out, CT_EMULE_VERSION, 0x03010000L);
        return out.toByteArray();
    }

    private static byte[] buildSearchPayload(String query) throws IOException {
        byte[] text = query.getBytes(StandardCharsets.UTF_8);
        if (text.length == 0 || text.length > 0xFFFF) throw new IOException("关键词长度无效");
        ByteArrayOutputStream out = new ByteArrayOutputStream(text.length + 3);
        out.write(0x01);
        writeU16(out, text.length);
        out.write(text);
        return out.toByteArray();
    }

    private static void sendFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        out.write(OP_EDONKEYPROT);
        writeU32(out, payload.length + 1L);
        out.write(opcode & 0xFF);
        out.write(payload);
        out.flush();
    }

    private static Frame readFrame(InputStream in) throws IOException {
        int protocol = in.read();
        if (protocol < 0) throw new EOFException("服务器关闭连接");
        long size = readU32(in);
        if (size < 1 || size > 8L * 1024L * 1024L) throw new IOException("异常 eD2K TCP 包长度 " + size);
        int opcode = in.read();
        if (opcode < 0) throw new EOFException("服务器关闭连接");
        byte[] payload = readExact(in, (int) size - 1);
        if (protocol == OP_PACKEDPROT) {
            payload = inflate(payload, 8 * 1024 * 1024);
            protocol = OP_EDONKEYPROT;
        }
        if (protocol != OP_EDONKEYPROT) throw new IOException("未知 eD2K 协议头 0x" + Integer.toHexString(protocol));
        return new Frame(opcode, payload);
    }

    private static byte[] inflate(byte[] data, int max) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(max, Math.max(256, data.length * 2)));
        byte[] buf = new byte[8192];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n > 0) {
                    if (out.size() + n > max) throw new IOException("压缩应答过大");
                    out.write(buf, 0, n);
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    break;
                }
            }
        } catch (DataFormatException e) {
            throw new IOException("无法解压服务器应答", e);
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    private static void parseTcpSearchReply(byte[] payload, Server server,
            List<MainActivity.SearchResult> out) throws IOException {
        Cursor c = new Cursor(payload, 0, payload.length);
        long count = c.u32();
        if (count < 0 || count > 5000) throw new IOException("异常搜索结果数 " + count);
        for (int i = 0; i < (int) count; i++) {
            MainActivity.SearchResult item = parseResult(c, server);
            if (item != null) out.add(item);
        }
    }

    private static String parseServerMessage(byte[] payload) {
        try {
            Cursor c = new Cursor(payload, 0, payload.length);
            int n = c.u16();
            if (n <= 0 || n > c.remaining()) return "";
            return c.utf8(n).trim().replace('\n', ' ').replace('\r', ' ');
        } catch (Exception ignored) {
            return "";
        }
    }

    private static MainActivity.SearchResult parseResult(Cursor c, Server server) throws IOException {
        byte[] hash = c.bytes(16);
        c.u32();
        c.u16();
        long tagCountLong = c.u32();
        if (tagCountLong < 0 || tagCountLong > 256) throw new IOException("异常 tag 数");
        String name = "";
        String type = "";
        long size = 0;
        long sizeHi = 0;
        long sources = -1;
        long complete = -1;
        for (int i = 0; i < (int) tagCountLong; i++) {
            Tag tag = readTag(c);
            if (tag.nameId == FT_FILENAME && tag.stringValue != null) name = tag.stringValue;
            else if (tag.nameId == FT_FILETYPE && tag.stringValue != null) type = tag.stringValue;
            else if (tag.nameId == FT_FILESIZE && tag.intValue >= 0) size = tag.intValue;
            else if (tag.nameId == FT_FILESIZE_HI && tag.intValue >= 0) sizeHi = tag.intValue;
            else if (tag.nameId == FT_SOURCES && tag.intValue >= 0) sources = tag.intValue;
            else if (tag.nameId == FT_COMPLETE_SOURCES && tag.intValue >= 0) complete = tag.intValue;
        }
        size = (sizeHi << 32) | (size & 0xFFFFFFFFL);
        if (name.trim().length() == 0 || size <= 0) return null;
        String md4 = hex(hash);
        MainActivity.SearchResult item = new MainActivity.SearchResult();
        item.source = "eD2K Server";
        item.title = name.trim();
        item.description = "来自 " + server.host + ":" + server.port +
                (complete >= 0 ? " · 完整来源 " + complete : "");
        item.contentType = type.length() > 0 ? "eD2K · " + type : "eD2K 元数据";
        item.rightsStatus = "P2P 网络发现 · 权利状态未知";
        item.size = size;
        item.ed2kHash = md4;
        item.ed2kLink = buildEd2kLink(name.trim(), size, md4);
        item.seeders = sources > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sources;
        item.leechers = -1;
        item.sourceCount = 1;
        item.verifiedOpen = false;
        return item;
    }

    private static Tag readTag(Cursor c) throws IOException {
        int type = c.u8();
        int nameId = 0;
        String nameText = "";
        if ((type & 0x80) != 0) {
            type &= 0x7F;
            nameId = c.u8();
        } else {
            int nameLength = c.u16();
            if (nameLength < 0 || nameLength > c.remaining()) throw new IOException("异常 tag 名");
            if (nameLength == 1) nameId = c.u8();
            else nameText = c.utf8(nameLength);
        }
        Tag tag = new Tag(nameId, nameText);
        if (type == TAGTYPE_STRING) {
            tag.stringValue = c.string16();
        } else if (type == TAGTYPE_UINT32) {
            tag.intValue = c.u32();
        } else if (type == TAGTYPE_UINT64) {
            tag.intValue = c.u64();
        } else if (type == TAGTYPE_UINT16) {
            tag.intValue = c.u16();
        } else if (type == TAGTYPE_UINT8) {
            tag.intValue = c.u8();
        } else if (type >= TAGTYPE_STR1 && type <= TAGTYPE_STR16) {
            tag.stringValue = c.utf8(type - TAGTYPE_STR1 + 1);
        } else if (type == TAGTYPE_HASH16) {
            c.skip(16);
        } else if (type == TAGTYPE_FLOAT32) {
            c.skip(4);
        } else if (type == TAGTYPE_BOOL) {
            c.skip(1);
        } else if (type == TAGTYPE_BOOLARRAY) {
            int bits = c.u16();
            c.skip((bits / 8) + 1);
        } else if (type == TAGTYPE_BLOB) {
            long n = c.u32();
            if (n > Integer.MAX_VALUE) throw new IOException("blob 过大");
            c.skip((int) n);
        } else if (type == TAGTYPE_BSOB) {
            c.skip(c.u8());
        } else {
            throw new IOException("未知 tag 类型 0x" + Integer.toHexString(type));
        }
        return tag;
    }

    private static void writeStringTag(ByteArrayOutputStream out, int nameId, String value) throws IOException {
        byte[] text = value.getBytes(StandardCharsets.UTF_8);
        out.write(TAGTYPE_STRING);
        writeU16(out, 1);
        out.write(nameId & 0xFF);
        writeU16(out, text.length);
        out.write(text);
    }

    private static void writeIntTag(ByteArrayOutputStream out, int nameId, long value) throws IOException {
        out.write(TAGTYPE_UINT32);
        writeU16(out, 1);
        out.write(nameId & 0xFF);
        writeU32(out, value);
    }

    private static List<Server> loadServers(List<String> errors) {
        for (String url : SERVER_MET_URLS) {
            try {
                byte[] bytes = MainActivity.Http.getBytes(url, 2 * 1024 * 1024);
                List<Server> servers = parseServerMet(bytes);
                if (!servers.isEmpty()) return servers;
            } catch (Exception ignored) { }
        }
        ArrayList<Server> fallback = new ArrayList<Server>();
        for (String value : FALLBACK_SERVERS) {
            int colon = value.lastIndexOf(':');
            if (colon <= 0) continue;
            try { fallback.add(new Server(value.substring(0, colon), Integer.parseInt(value.substring(colon + 1)))); }
            catch (Exception ignored) { }
        }
        if (!fallback.isEmpty()) errors.add("eD2K：实时 server.met 不可用，已使用内置备用节点");
        return fallback;
    }

    static List<Server> parseServerMet(byte[] bytes) throws IOException {
        Cursor c = new Cursor(bytes, 0, bytes.length);
        if (c.remaining() < 5) return new ArrayList<Server>();
        c.u8();
        long count = c.u32();
        if (count < 0 || count > 10000) throw new IOException("server.met 数量异常");
        ArrayList<Server> out = new ArrayList<Server>();
        for (int i = 0; i < (int) count && c.remaining() >= 10; i++) {
            long ip = c.u32();
            int port = c.u16();
            long tags = c.u32();
            String name = "";
            if (tags < 0 || tags > 256) throw new IOException("server.met tag 数异常");
            for (int t = 0; t < (int) tags; t++) {
                Tag tag = readTag(c);
                if (tag.stringValue != null && name.length() == 0) name = tag.stringValue;
            }
            String host = (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." +
                    ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
            if (port > 0 && !"0.0.0.0".equals(host)) out.add(new Server(host, port, name));
        }
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

    private static String buildEd2kLink(String name, long size, String hash) {
        try {
            String encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20").replace("%2F", "/");
            return "ed2k://|file|" + encoded + "|" + size + "|" + hash.toUpperCase(Locale.US) + "|/";
        } catch (Exception e) {
            return "ed2k://|file|" + name.replace("|", "%7C") + "|" + size + "|" + hash.toUpperCase(Locale.US) + "|/";
        }
    }

    private static byte[] readExact(InputStream in, int n) throws IOException {
        byte[] out = new byte[n];
        int pos = 0;
        while (pos < n) {
            int r = in.read(out, pos, n - pos);
            if (r < 0) throw new EOFException("服务器关闭连接");
            pos += r;
        }
        return out;
    }

    private static long readU32(InputStream in) throws IOException {
        byte[] b = readExact(in, 4);
        return le32(b, 0);
    }

    private static long le32(byte[] b, int p) {
        return (b[p] & 0xFFL) | ((b[p + 1] & 0xFFL) << 8) |
                ((b[p + 2] & 0xFFL) << 16) | ((b[p + 3] & 0xFFL) << 24);
    }

    private static String hex(byte[] value) {
        StringBuilder out = new StringBuilder(value.length * 2);
        for (byte b : value) out.append(String.format(Locale.US, "%02X", b & 0xFF));
        return out.toString();
    }

    private static void writeU16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void writeU32(OutputStream out, long value) throws IOException {
        out.write((int) (value & 0xFF));
        out.write((int) ((value >> 8) & 0xFF));
        out.write((int) ((value >> 16) & 0xFF));
        out.write((int) ((value >> 24) & 0xFF));
    }

    private static String clean(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().length() == 0 ? e.getClass().getSimpleName() : m.trim();
    }

    static final class SearchBatch {
        final ArrayList<MainActivity.SearchResult> items = new ArrayList<MainActivity.SearchResult>();
        final ArrayList<String> errors = new ArrayList<String>();
    }

    private static final class NodeResult {
        boolean loggedIn;
        String error = "";
        final ArrayList<MainActivity.SearchResult> items = new ArrayList<MainActivity.SearchResult>();
    }

    private static final class Frame {
        final int opcode;
        final byte[] payload;
        Frame(int opcode, byte[] payload) { this.opcode = opcode; this.payload = payload; }
    }

    static final class Server {
        final String host;
        final int port;
        final String name;
        Server(String host, int port) { this(host, port, ""); }
        Server(String host, int port, String name) { this.host = host; this.port = port; this.name = name == null ? "" : name; }
    }

    private static final class Tag {
        final int nameId;
        final String name;
        String stringValue;
        long intValue = -1;
        Tag(int nameId, String name) { this.nameId = nameId; this.name = name; }
    }

    private static final class Cursor {
        final byte[] data;
        final int end;
        int pos;
        Cursor(byte[] data, int offset, int length) {
            this.data = data;
            this.pos = Math.max(0, offset);
            this.end = Math.min(data.length, this.pos + Math.max(0, length));
        }
        int remaining() { return end - pos; }
        int u8() throws IOException { require(1); return data[pos++] & 0xFF; }
        int u16() throws IOException { require(2); int v = (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8); pos += 2; return v; }
        long u32() throws IOException {
            require(4);
            long v = (data[pos] & 0xFFL) | ((data[pos + 1] & 0xFFL) << 8) |
                    ((data[pos + 2] & 0xFFL) << 16) | ((data[pos + 3] & 0xFFL) << 24);
            pos += 4; return v;
        }
        long u64() throws IOException {
            require(8); long v = 0;
            for (int i = 0; i < 8; i++) v |= (data[pos + i] & 0xFFL) << (8 * i);
            pos += 8; return v;
        }
        byte[] bytes(int n) throws IOException { require(n); byte[] out = new byte[n]; System.arraycopy(data, pos, out, 0, n); pos += n; return out; }
        String utf8(int n) throws IOException { return new String(bytes(n), StandardCharsets.UTF_8); }
        String string16() throws IOException { int n = u16(); return utf8(n); }
        void skip(int n) throws IOException { require(n); pos += n; }
        void require(int n) throws IOException { if (n < 0 || remaining() < n) throw new IOException("截断的 eD2K 数据包"); }
    }
}
