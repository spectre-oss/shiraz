// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.concurrent.*;

/** Actual nonblocking IP channels, with monotonic deadlines and no external service. */
final class NioNetworkChecks {
    static void require(boolean value,String message){NetworkChecks.require(value,message);}
    static void ready(Selector selector,SelectionKey key,int operation)throws Exception{
        key.interestOps(operation);long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(System.nanoTime()<end){
            selector.select(100);boolean available=selector.selectedKeys().contains(key)&&key.isValid()&&(key.readyOps()&operation)!=0;selector.selectedKeys().clear();
            if(available)return;
        }
        throw new SocketTimeoutException("NIO readiness deadline: "+operation);
    }
    static InetSocketAddress loopback(){return new InetSocketAddress("127.0.0.1",0);}
    static String run(String id)throws Exception{
        if(id.equals("network.nio_tcp")){
            try(ServerSocketChannel listener=ServerSocketChannel.open();SocketChannel client=SocketChannel.open();Selector selector=Selector.open()){
                listener.socket().bind(loopback());listener.configureBlocking(false);client.configureBlocking(false);
                SelectionKey accept=listener.register(selector,SelectionKey.OP_ACCEPT,"listener");
                boolean connected=client.connect(listener.getLocalAddress());SelectionKey connect=client.register(selector,connected?SelectionKey.OP_READ:SelectionKey.OP_CONNECT);
                if(!connected){ready(selector,connect,SelectionKey.OP_CONNECT);require(connect.isConnectable()&&client.finishConnect(),"NIO connection completion");}
                require(client.isConnected()&&!client.isConnectionPending(),"NIO connected state");
                ready(selector,accept,SelectionKey.OP_ACCEPT);require(accept.attachment().equals("listener"),"selector attachment");
                try(SocketChannel peer=listener.accept()){
                    require(peer!=null,"NIO accepted connection");peer.configureBlocking(false);SelectionKey read=peer.register(selector,SelectionKey.OP_READ);
                    ByteBuffer sent=ByteBuffer.wrap(new byte[]{0,42,(byte)255});while(sent.hasRemaining()){ready(selector,connect,SelectionKey.OP_WRITE);client.write(sent);}
                    ByteBuffer received=ByteBuffer.allocateDirect(8);received.position(2);received.limit(5);
                    while(received.hasRemaining()){ready(selector,read,SelectionKey.OP_READ);require(peer.read(received)>0,"NIO payload read progress");}
                    require(received.get(2)==0&&received.get(3)==42&&(received.get(4)&255)==255,"NIO direct buffer binary payload");
                    client.socket().shutdownOutput();ready(selector,read,SelectionKey.OP_READ);require(peer.read(ByteBuffer.allocate(1))==-1,"NIO half-close EOF");
                    ready(selector,read,SelectionKey.OP_WRITE);require(peer.write(ByteBuffer.wrap(new byte[]{77}))==1,"NIO reply write");peer.socket().shutdownOutput();
                    ready(selector,connect,SelectionKey.OP_READ);ByteBuffer reply=ByteBuffer.allocate(1);require(client.read(reply)==1&&reply.get(0)==77,"NIO reply");
                    ready(selector,connect,SelectionKey.OP_READ);reply.clear();require(client.read(reply)==-1,"NIO reply EOF");
                }
                connect.cancel();selector.selectNow();require(!connect.isValid(),"cancelled channel key");
                client.close();try{client.read(ByteBuffer.allocate(1));throw new AssertionError("closed NIO channel read succeeded");}catch(ClosedChannelException expected){}
            }
            return "Nonblocking TCP selector connect/accept/read/write, direct buffers, half-close, EOF and cancellation";
        }
        if(id.equals("network.nio_udp")){
            try(DatagramChannel server=DatagramChannel.open();DatagramChannel client=DatagramChannel.open();Selector selector=Selector.open()){
                server.socket().bind(loopback());client.socket().bind(loopback());server.configureBlocking(false);client.configureBlocking(false);
                SelectionKey key=server.register(selector,SelectionKey.OP_READ);require(server.receive(ByteBuffer.allocate(4))==null,"empty nonblocking datagram receive");
                ByteBuffer sent=ByteBuffer.wrap(new byte[]{99,0,42,(byte)255,99});sent.position(1);sent.limit(4);require(client.send(sent,server.getLocalAddress())==3&&sent.position()==4,"NIO datagram send range");
                ready(selector,key,SelectionKey.OP_READ);ByteBuffer received=ByteBuffer.allocateDirect(8);received.position(2);received.limit(5);SocketAddress remote=server.receive(received);
                require(remote.equals(client.getLocalAddress())&&received.position()==5&&received.get(2)==0&&received.get(3)==42&&(received.get(4)&255)==255,"NIO datagram payload/peer");
                server.connect(remote);client.connect(server.getLocalAddress());require(server.isConnected()&&client.isConnected(),"NIO connected UDP state");
                require(server.write(ByteBuffer.wrap(new byte[]{77}))==1,"NIO connected UDP reply write");SelectionKey replyKey=client.register(selector,SelectionKey.OP_READ);ready(selector,replyKey,SelectionKey.OP_READ);
                ByteBuffer reply=ByteBuffer.allocate(2);require(client.read(reply)==1&&reply.get(0)==77,"NIO connected UDP reply");server.disconnect();require(!server.isConnected(),"NIO UDP disconnect");
            }
            return "DatagramChannel nonblocking readiness, buffer range, source identity, connected reply and disconnect";
        }
        if(id.equals("network.nio_selector")){
            try(Selector selector=Selector.open()){
                require(selector.selectNow()==0,"empty selector readiness");
                Pipe pipe=Pipe.open();try{
                    pipe.source().configureBlocking(false);SelectionKey pipeKey=pipe.source().register(selector,SelectionKey.OP_READ);
                    require(pipe.sink().write(ByteBuffer.wrap(new byte[]{42}))==1,"pipe write");ready(selector,pipeKey,SelectionKey.OP_READ);
                    ByteBuffer payload=ByteBuffer.allocate(1);require(pipe.source().read(payload)==1&&payload.get(0)==42,"pipe readiness/payload");pipeKey.cancel();selector.selectNow();
                }finally{pipe.source().close();pipe.sink().close();}
                CountDownLatch entered=new CountDownLatch(1);FutureTask<Integer> waiting=new FutureTask<Integer>(()->{entered.countDown();return selector.select(5000);});
                Thread thread=new Thread(waiting,"probe-selector-wakeup");thread.setDaemon(true);thread.start();
                try{require(entered.await(1,TimeUnit.SECONDS),"selector worker started");selector.wakeup();require(waiting.get(1500,TimeUnit.MILLISECONDS)==0,"cross-thread selector wakeup");}
                finally{selector.wakeup();thread.join(2000);require(!thread.isAlive(),"selector worker deadline");}
                selector.close();require(!selector.isOpen(),"closed selector state");try{selector.selectNow();throw new AssertionError("closed selector accepted select");}catch(ClosedSelectorException expected){}
            }
            return "Selector empty polling, bounded cross-thread wakeup and closed-selector rejection";
        }
        throw new IllegalArgumentException("Unknown NIO network case: "+id);
    }
}
