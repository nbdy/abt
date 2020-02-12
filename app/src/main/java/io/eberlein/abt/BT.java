package io.eberlein.abt;

/*
MIT License

Copyright (c) 2020 Pascal Eberlein

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
** onCreate: BT.create(Context)
 * onDestroy: BT.destroy(Context)
*/
public class BT {
    private static BluetoothManager manager;
    private static BluetoothAdapter adapter;

    public static class ClassicScanner {
        private static final String TAG = "BT.Scanner";

        public interface OnEventListener {
            void onDeviceFound(BluetoothDevice device);
            void onDiscoveryFinished(List<BluetoothDevice> devices);
            void onDiscoveryStarted();
        }

        private static OnEventListener onEventListener;
        private static List<BluetoothDevice> devices = new ArrayList<>();

        private static final BroadcastReceiver deviceFoundReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(BluetoothDevice.ACTION_FOUND.equals(intent.getAction())){
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    Log.d(TAG, device.getAddress() + " : " + device.getName());
                    if(!devices.contains(device)) devices.add(device);
                    onEventListener.onDeviceFound(device);
                }
            }
        };

        private static final BroadcastReceiver discoveryFinishedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())){
                    Log.d(TAG, "discovery finished");
                    Log.d(TAG, "scan yielded " + devices.size() + " devices");
                    onEventListener.onDiscoveryFinished(devices);
                }
            }
        };

        private static final BroadcastReceiver discoveryStartedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(intent.getAction())){
                    Log.d(TAG, "discovery started");
                    onEventListener.onDiscoveryStarted();
                }
            }
        };

        /**
         * initializes the library
         * @param ctx App context
         */
        static void init(Context ctx){
            ctx.registerReceiver(deviceFoundReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            ctx.registerReceiver(discoveryStartedReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
            ctx.registerReceiver(discoveryFinishedReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
        }

        /**
         * @return list of found devices
         */
        public static List<BluetoothDevice> getDevices() {
            return devices;
        }

        /**
         * @param ctx App context
         */
        static void unregisterReceivers(Context ctx){
            ctx.unregisterReceiver(deviceFoundReceiver);
            ctx.unregisterReceiver(discoveryStartedReceiver);
            ctx.unregisterReceiver(discoveryFinishedReceiver);
        }

        /**
         * starts the discovery
         * @param oel your defined on event listener
         * @return True if discovery has successfully started
         */
        public static boolean startDiscovery(OnEventListener oel){
            onEventListener = oel;
            devices = new ArrayList<>();
            return adapter.startDiscovery();
        }

        /**
         * cancels the discovery
         * @return True if discovery has successfully been cancelled
         */
        public static boolean cancelDiscovery(){
            return adapter.cancelDiscovery();
        }
    }

    /**
     * initializes the library
     * @param ctx App context
     */
    public static void create(Context ctx){
        manager = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();
        ClassicScanner.init(ctx);
    }

    /**
     * cleans everything up
     * @param ctx App context
     */
    public static void destroy(Context ctx){
        ClassicScanner.unregisterReceivers(ctx);
    }

    /**
     * enables the bluetooth adapter
     */
    public static void enable(){
        adapter.enable();
    }

    /**
     * disables the bluetooth adapter
     */
    public static void disable(){
        adapter.disable();
    }

    /**
     * checks if bluetooth is supported on this device
     * @return True if bluetooth is supported on this device
     */
    public static boolean supported(){
        return adapter != null;
    }

    /**
     *
     * @return True if adapter is discovering
     */
    public static boolean isDiscovering(){
        return adapter.isDiscovering();
    }

    /**
     *
     * @return visible bluetooth name
     */
    public static String getName(){
        return adapter.getName();
    }

    /**
     *
     * @return bluetooth mac address
     */
    @SuppressLint("HardwareIds")
    public static String getAddress(){
        return adapter.getAddress();
    }

    /**
     * asks the user (or not, looking at huawei) to enable bluetooth discoverability
     * @param ctx App context
     * @param duration seconds
     */
    public static void setDiscoverable(Context ctx, int duration){
        Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration);
        ctx.startActivity(i);
    }

    /**
     * returns if device is in the bonded devices list
     * @param device the bluetooth device to check against
     * @return if is in bounded devices list
     */
    public static boolean isDeviceBonded(BluetoothDevice device){
        return adapter.getBondedDevices().contains(device);
    }

    public interface ConnectionInterface {
        void onConnected();
        void onDisconnected();
    }

    public static class Connector {
        private static ConnectionInterface connectionInterface;
        private static BluetoothSocket socket = null;

        /**
         * registers the Broadcast Receiver and calls functions of the ConnectionInterface
         * @param ctx App context
         * @param ci Instance of ConnectionInterface
         */
        public static void register(Context ctx, ConnectionInterface ci){
            connectionInterface = ci;
            ctx.registerReceiver(connectionReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
            ctx.registerReceiver(connectionReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        }

        static BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String a = intent.getAction();
                if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(a)) connectionInterface.onConnected();
                else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(a)) connectionInterface.onDisconnected();
            }
        };

        /**
         * connects to specific device and uuid
         * @param device Bluetooth device to connect to
         * @param uuid UUID of service
         */
        public static void connect(BluetoothDevice device, UUID uuid){
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
                socket.connect();
            } catch (IOException e){
                e.printStackTrace();
            }
        }

        /**
         * gets the created socket instance
         * @return socket of connected bluetooth device
         */
        public static BluetoothSocket getSocket() {
            return socket;
        }

        /**
         * unregisters bluetooth receiver
         * @param ctx App context
         */
        public static void unregister(Context ctx){
            ctx.unregisterReceiver(connectionReceiver);
        }
    }

    /**
     *
     * @return True if bluetooth is enabled
     */
    public static boolean isEnabled(){
        return adapter.isEnabled();
    }

    public interface ServerInterface {
        void createServerSocket(String name, UUID uuid);
        BluetoothSocket acceptServerSocket();
        void manageSocket(BluetoothSocket socket);
        void closeServerSocket();
        void onServerSocketCreated();
        void onAcceptException(IOException e);
        void onServerSocketCloseException(IOException e);
        void onServerSocketCreateException(IOException e);
        void onClientConnected(BluetoothSocket socket);
    }

    public static abstract class Server extends AsyncTask<Void, Void, Void> implements ServerInterface {
        private static final String TAG = "BT.Server";
        private BluetoothServerSocket serverSocket;

        /**
         * creates a Server instance
         * @param name bluetooth device name
         * @param uuid uuid of service
         */
        public Server(String name, UUID uuid){
            createServerSocket(name, uuid);
        }

        /**
         * creates the actual server socket
         * @param name name
         * @param uuid uuid
         */
        @Override
        public void createServerSocket(String name, UUID uuid) {
            try{
                Log.d(TAG, "creating server socket");
                serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(name, uuid);
                onServerSocketCreated();
            } catch (IOException e){
                onServerSocketCreateException(e);
            }
        }

        @Override
        public void onServerSocketCreated() {
            Log.d(TAG, "server socket created");
        }

        @Override
        public void onServerSocketCreateException(IOException e) {
            e.printStackTrace();
        }

        @Override
        public void onAcceptException(IOException e) {
            e.printStackTrace();
        }

        @Override
        public void onServerSocketCloseException(IOException e) {
            e.printStackTrace();
        }

        /**
         * informs if a client has connected
         * @param socket socket to connected client
         */
        @Override
        public void onClientConnected(BluetoothSocket socket) {
            Log.d(TAG, "client '" + socket.getRemoteDevice().getName() + "; " + socket.getRemoteDevice().getAddress() + "' connected");
        }

        /**
         * accepts new client
         * @return socket to connected client
         */
        @Override
        public BluetoothSocket acceptServerSocket() {
            try {
                Log.d(TAG, "accepting incoming connections");
                BluetoothSocket cs = serverSocket.accept();
                onClientConnected(cs);
                return cs;
            } catch (IOException e){
                onAcceptException(e);
                return null;
            }
        }

        @Override
        public void closeServerSocket() {
            try {
                Log.d(TAG, "closing socket");
                serverSocket.close();
            } catch (IOException e){
                onServerSocketCloseException(e);
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            run();
            return null;
        }

        void run() {
            BluetoothSocket socket = acceptServerSocket();
            if(socket != null) manageSocket(socket);
            closeServerSocket();
        }
    }

    interface ClientInterface {
        void onReady();
        void onExecuted();
        void onFinished();
    }

    interface ReaderInterface {
        void inform(int message);
        void finished();
    }

    interface WriterInterface {
        void inform(int message);
        void finished();
    }

    interface IPCInterface {
        void onMessage(int message);
    }

    public interface OnDataReceivedInterface {
        void onReceived(String data);
    }

    @SuppressLint("StaticFieldLeak")
    public static abstract class Client extends AsyncTask<Void, Void, Void> implements ClientInterface {

        public static class Reader extends AsyncTask<Void, Void, Void> implements IPCInterface {
            private static final String TAG = "BT.Client.Reader";

            static final String DATA_IS_READY = "READY";
            static final int MSG_READER_DEAD = -1;
            static final int MSG_READER_READY = 0;
            static final int MSG_REMOTE_READY = 1;

            private InputStream inputStream;
            private boolean doRun;
            private OnDataReceivedInterface onDataReceivedInterface;

            private ReaderInterface readerInterface;
            private WriterInterface writerInterface;

            /**
             * instantiates a Reader object
             * @param inputStream socket input stream
             * @param onDataReceivedInterface onDataReceivedInterface
             * @param readerInterface readerInterface for ipc
             * @param writerInterface writerInterface for ipc
             */
            Reader(InputStream inputStream, OnDataReceivedInterface onDataReceivedInterface, ReaderInterface readerInterface, WriterInterface writerInterface){
                this.inputStream = inputStream;
                this.onDataReceivedInterface = onDataReceivedInterface;
                this.readerInterface = readerInterface;
                this.writerInterface = writerInterface;
                doRun = true;
            }

            private void run(){
                int bytes;
                byte[] buffer = new byte[1024];
                writerInterface.inform(MSG_READER_READY);
                while (doRun){
                    try {
                        bytes = inputStream.read(buffer);
                        String data = new String(buffer, 0, bytes);
                        for(String s : data.split("\n")) {
                            if (s.equals(DATA_IS_READY))
                                writerInterface.inform(MSG_REMOTE_READY);
                            else onDataReceivedInterface.onReceived(s);
                        }
                    } catch (IOException e){
                        e.printStackTrace();
                        doRun = false;
                        writerInterface.inform(MSG_READER_DEAD);
                    }
                }
                readerInterface.finished();
            }

            @Override
            protected void onCancelled() {
                super.onCancelled();
                doRun = false;
            }

            @Override
            protected Void doInBackground(Void... voids) {
                run();
                return null;
            }

            void stop(){
                doRun = false;
            }

            @Override
            public void onMessage(int message) {

            }
        }

        public static class Writer extends AsyncTask<Void, Void, Void> implements IPCInterface {
            private static final String TAG = "BT.Client.Writer";
            private OutputStream outputStream;
            private boolean doRun;
            private List<String> sendData = new ArrayList<>();
            private WriterInterface writerInterface;

            private boolean readerIsReady = false;
            private boolean remoteReaderIsReady = false;
            private boolean sentIsReady = false;

            /**
             * instantiates a writer object
             * @param outputStream socket outputStream
             * @param writerInterface writerInterface for ipc
             */
            Writer(OutputStream outputStream, WriterInterface writerInterface){
                this.outputStream = outputStream;
                this.writerInterface = writerInterface;
                doRun = true;
            }

            /**
             * writes to the socket and flushes directly afterwards
             * attaches a '\n' to the end of each messages
             * @param data data to be sent
             * @return True if there was no exception
             */
            private boolean writeFlush(String data){
                try {
                    Log.d(TAG, "sending: " + data);
                    outputStream.write(data.getBytes());
                    outputStream.write("\n".getBytes());
                    outputStream.flush();
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }

            private void run(){
                while (doRun){
                    if(!readerIsReady) continue;
                    if(!remoteReaderIsReady) {
                        writeFlush(Reader.DATA_IS_READY);
                        sentIsReady = true;
                        try {
                            Thread.sleep(420);
                        } catch (InterruptedException e){
                            e.printStackTrace();
                        }
                    } else {
                        if(!sentIsReady) {writeFlush(Reader.DATA_IS_READY); sentIsReady = true;}
                        for(String s : sendData) doRun = writeFlush(s);
                        sendData.clear();
                    }
                }
                writerInterface.finished();
            }

            @Override
            protected void onCancelled() {
                super.onCancelled();
                doRun = false;
            }

            @Override
            protected Void doInBackground(Void... voids) {
                run();
                return null;
            }

            void addSendData(String data){
                sendData.add(data);
            }

            void addSendData(List<String> data){
                sendData.addAll(data);
            }

            void stop(){
                doRun = false;
            }

            @Override
            public void onMessage(int message) {
                if(message == Reader.MSG_READER_READY) readerIsReady = true;
                else if(message == Reader.MSG_REMOTE_READY) remoteReaderIsReady = true;
                else if(message == Reader.MSG_READER_DEAD) doRun = false;
            }

            int getSendDataQueueSize(){
                return sendData.size();
            }
        }

        private static final String TAG = "BT.Client";

        private BluetoothSocket socket;

        private OnDataReceivedInterface onDataReceivedInterface;

        private Reader reader = null;
        private Writer writer = null;

        private boolean readerFinished = false;
        private boolean writerFinished = false;

        private ReaderInterface readerInterface = new ReaderInterface() {
            @Override
            public void finished() {
                readerFinished = true;
                if(writerFinished) onFinished();
            }

            @Override
            public void inform(int message) {
                reader.onMessage(message);
            }
        };

        private WriterInterface writerInterface = new WriterInterface() {
            @Override
            public void finished() {
                writerFinished = true;
                if(readerFinished) onFinished();
            }

            @Override
            public void inform(int message) {
                writer.onMessage(message);
            }
        };

        /**
         * instantiates a new client object
         * @param socket the acquired socket
         * @param onDataReceivedInterface onDataReceiveInterface
         */
        public Client(BluetoothSocket socket, OnDataReceivedInterface onDataReceivedInterface){
            this.socket = socket;
            this.onDataReceivedInterface = onDataReceivedInterface;
        }

        /**
         * checks if the socket is not null and connected
         * @return True if everything is fine
         */
        private boolean preCheck(){
            if(socket == null) {
                Log.e(TAG, "socket is null");
                return false;
            }
            if(socket.isConnected()){
                Log.d(TAG, "socket is connected");
            } else {
                Log.e(TAG, "socket is not connected");
                return false;
            }
            return true;
        }

        @Override
        public void onExecuted() {
            Log.d(TAG, "executed reader and writer");
        }

        /**
         * instantiates a reader and writer object and passes input/output stream to them
         */
        void run() {
            if(!preCheck()) return;
            try {
                InputStream is = socket.getInputStream();
                if(is == null) Log.wtf(TAG, "inputstream is null");
                OutputStream os = socket.getOutputStream();
                if(os == null) Log.wtf(TAG, "outputstream is null");
                reader = new Reader(is, onDataReceivedInterface, readerInterface, writerInterface);
                writer = new Writer(os, writerInterface);
                onReady();
                reader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                writer.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                onExecuted();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * stops both reader and writer and closes the socket
         */
        public void stop(){
            if(reader != null) reader.stop();
            if(writer != null) writer.stop();
            try {
                socket.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            run();
            return null;
        }

        /**
         *
         * @return length of the writer to-be-send data queue
         */
        public int getSendDataQueueSize(){return writer.getSendDataQueueSize();}

        /**
         * adds data to the to-be-send ata queue
         * @param data data to add
         */
        protected void addSendData(String data){
            writer.addSendData(data);
        }

        /**
         * adds data to the to-be-send ata queue
         * @param data data to add
         */
        protected void addSendData(List<String> data){
            writer.addSendData(data);
        }
    }
}
