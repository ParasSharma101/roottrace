package com.roottrace.support;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** In-process development SMTP receiver. Never forwards or delivers outside loopback. */
public final class LocalSmtpServer implements AutoCloseable {
    private final ServerSocket server;
    private final ExecutorService threads=Executors.newVirtualThreadPerTaskExecutor();
    private final List<Received> received=new CopyOnWriteArrayList<>();
    public LocalSmtpServer() {
        try { server=new ServerSocket(0,50,InetAddress.getLoopbackAddress()); }
        catch(IOException error) { throw new UncheckedIOException(error); }
        threads.submit(() -> { while(!server.isClosed()) { try { Socket socket=server.accept(); threads.submit(() -> receive(socket)); } catch(IOException ignored) { break; } } });
    }
    public int port() { return server.getLocalPort(); }
    public List<Received> forRecipient(String email) { return received.stream().filter(item -> item.recipient().equalsIgnoreCase(email)).toList(); }
    private void receive(Socket socket) {
        try(socket; var input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                var output=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true)) {
            reply(output,"220 localhost test SMTP"); String recipient=""; String line;
            while((line=input.readLine())!=null) {
                String upper=line.toUpperCase(Locale.ROOT);
                if(upper.startsWith("EHLO") || upper.startsWith("HELO")) reply(output,"250 localhost");
                else if(upper.startsWith("RCPT TO:")) { recipient=line.substring(line.indexOf('<')+1,line.indexOf('>')); reply(output,"250 OK"); }
                else if(upper.equals("DATA")) {
                    reply(output,"354 End with dot"); var data=new StringBuilder();
                    while((line=input.readLine())!=null && !line.equals(".")) data.append(line.startsWith("..")?line.substring(1):line).append("\r\n");
                    var message=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(data.toString().getBytes(StandardCharsets.UTF_8)));
                    received.add(new Received(recipient,message.getSubject(),message.getContent().toString())); reply(output,"250 queued");
                } else if(upper.equals("QUIT")) { reply(output,"221 bye"); break; }
                else reply(output,"250 OK");
            }
        } catch(Exception ignored) { /* Connection closure is normal for failure tests. */ }
    }
    private void reply(PrintWriter output,String text) { output.print(text+"\r\n"); output.flush(); }
    @Override public void close() throws IOException { server.close(); threads.shutdownNow(); }
    public record Received(String recipient,String subject,String body) { @Override public String toString() { return "CapturedTestEmail[redacted]"; } }
}
