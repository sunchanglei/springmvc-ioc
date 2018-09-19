package com.scl.tomcat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class NIOServer {
    private Selector selector;

    public void init() throws IOException {
        //创建一个选择器
        selector = Selector.open();
        //创建一个ServerSocketChanel
        ServerSocketChannel channel = ServerSocketChannel.open();
        //设置非阻塞
        channel.configureBlocking(false);
        //打开一个ServerSocket
        ServerSocket serverSocket = channel.socket();
        //绑定地址
        serverSocket.bind(new InetSocketAddress(8080));
        //注册事件
        channel.register(this.selector, SelectionKey.OP_ACCEPT);

        System.out.println("server.init...");
    }

    public void start() throws IOException {
        while(true) {
            this.selector.select();
            Iterator<SelectionKey> iterator = this.selector.selectedKeys().iterator();
            while(iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if(key.isAcceptable()) {
                    //这个请求是客户端的连接请求事件
                    accept(key);
                } else if(key.isReadable()) {
                    //如果这个请求是读事件
                    read(key);
                }
            }
            System.out.println("server.start...");
        }
    }

    private void accept(SelectionKey key) throws IOException {
        //事件中传过来的,key我们把这个通道拿到
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel channel = serverSocketChannel.accept();
        //把这个设置为非阻塞
        channel.configureBlocking(false);
        //注册读事件
        channel.register(selector, SelectionKey.OP_READ);
        System.out.println("server.accept...");
    }

    private void read(SelectionKey key) throws IOException {
        //创建一个缓冲区
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        SocketChannel channel = (SocketChannel)key.channel();
        //我们把通道的数据填入缓冲区
        channel.read(buffer);
        String request = new String(buffer.array()).trim();
        System.out.println("客户端的请求内容" + request);
        //把我们的html内容返回给客户端

        String outString = "HTTP/1.1 200 OK\n"
                +"Content-Type:text/html; charset=UTF-8\n\n"
                +"<html>\n"
                +"<head>\n"
                +"<title>first page</title>\n"
                +"</head>\n"
                +"<body>\n"
                +"hello fomcat\n"
                +"</body>\n"
                +"</html>";

        ByteBuffer outbuffer = ByteBuffer.wrap(outString.getBytes());
        channel.write(outbuffer);
        channel.close();
        System.out.println("server.read...");
    }

    public static void main(String[] args) {
        NIOServer server = new NIOServer();
        try {
            server.init();
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

