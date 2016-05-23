package hu.akarnokd.reactiverpc;

import java.io.*;
import java.lang.reflect.Proxy;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;

import hu.akarnokd.rxjava2.disposables.Disposable;
import rx.Scheduler;
import rx.internal.util.RxJavaPluginUtils;
import rx.schedulers.Schedulers;

public final class RpcClient<T> {

    final Class<T> remoteAPI;
    
    final Object localAPI;
    
    final Scheduler dispatcher;
    
    private RpcClient(Class<T> remoteAPI, Object localAPI) {
        this.remoteAPI = remoteAPI;
        this.localAPI = localAPI;
        this.dispatcher = Schedulers.io();
    }
    
    public static RpcClient<Void> createLocal(Object localAPI) {
        Objects.requireNonNull(localAPI, "localAPI");
        return new RpcClient<>(null, localAPI);
    }
    
    public static <T> RpcClient<T> createRemote(Class<T> remoteAPI) {
        Objects.requireNonNull(remoteAPI, "remoteAPI");
        return new RpcClient<>(remoteAPI, null);
    }
    
    public static <T> RpcClient<T> createBidirectional(Class<T> remoteAPI, Object localAPI) {
        Objects.requireNonNull(remoteAPI, "remoteAPI");
        Objects.requireNonNull(localAPI, "localAPI");
        return new RpcClient<>(remoteAPI, localAPI);
    }
    
    public T connect(InetAddress endpoint, int port, Consumer<Disposable> close) {
        Socket socket;
        InputStream in;
        OutputStream out;
        
        try {
            socket = new Socket(endpoint, port);
            
            in = socket.getInputStream();
            out = socket.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        Scheduler.Worker reader = dispatcher.createWorker();
        Scheduler.Worker writer = dispatcher.createWorker();
        
        close.accept(() -> {
            reader.unsubscribe();
            writer.unsubscribe();
            
            try {
                socket.close();
            } catch (IOException ex) {
                RxJavaPluginUtils.handleException(ex);
            }
        });
        
        
        
        if (remoteAPI == null) {
            return null;
        }

        Map<String, Object> clientMap = RpcServiceMapper.clientServiceMap(remoteAPI);
        Map<String, Object> serverMap = RpcServiceMapper.serverServiceMap(localAPI);
        
        RpcIOManager[] io = { null };
        
        T api = remoteAPI.cast(Proxy.newProxyInstance(remoteAPI.getClassLoader(), new Class[] { remoteAPI }, 
        (o, m, args) -> {
            String name = m.getName();
            RsRpc a = m.getAnnotation(RsRpc.class);
            if (a == null) {
                throw new IllegalArgumentException("The method " + m.getName() + " is not a proper RsRpc method");
            }
            String aname = a.name();
            if (!aname.isEmpty()) {
                name = aname;
            }
            
            Object action = clientMap.get(name);
            if (action == null) {
                throw new IllegalArgumentException("The method " + m.getName() + " is not a proper RsRpc method");
            }
            return RpcServiceMapper.dispatchClient(name, action, args, io[0]);
        }));

        RpcStreamContextImpl<T> ctx = new RpcStreamContextImpl<>(endpoint, port, api);
        
        io[0] = new RpcIOManager(reader, in, writer, out, (streamId, function, iom) -> {
            Object action = serverMap.get(function);
            return RpcServiceMapper.dispatchServer(streamId, action, iom, ctx);
        }, false);

        return api;
    }
}
