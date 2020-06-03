package cn.haier.bio.medical.constant.temperature;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import java.io.IOException;
import java.lang.ref.WeakReference;

import cn.haier.bio.medical.constant.temperature.tools.ConstantTemperatureTools;
import cn.qd.peiwen.pwlogger.PWLogger;
import cn.qd.peiwen.pwtools.ByteUtils;
import cn.qd.peiwen.pwtools.EmptyUtils;
import cn.qd.peiwen.serialport.PWSerialPortHelper;
import cn.qd.peiwen.serialport.PWSerialPortListener;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ConstantTemperatureSerialPort implements PWSerialPortListener {
    private ByteBuf buffer;
    private HandlerThread thread;
    private ConstantTemperatureHandler handler;
    private PWSerialPortHelper helper;

    private boolean enabled = false;
    private WeakReference<IConstantTemperatureListener> listener;

    public ConstantTemperatureSerialPort() {
    }

    public void init(String path) {
        this.createHandler();
        this.createHelper(path);
        this.createBuffer();
    }

    public void enable() {
        if (this.isInitialized() && !this.enabled) {
            this.enabled = true;
            this.helper.open();
        }
    }

    public void disable() {
        if (this.isInitialized() && this.enabled) {
            this.enabled = false;
            this.helper.close();
        }
    }

    public void release() {
        this.listener = null;
        this.destoryHandler();
        this.destoryHelper();
        this.destoryBuffer();
    }

    public void sendData(byte[] data) {
        if (this.isInitialized() && this.enabled) {
            Message msg = Message.obtain();
            msg.what = 0;
            msg.obj = data;
            this.handler.sendMessage(msg);
        }
    }

    public void changeListener(IConstantTemperatureListener listener) {
        this.listener = new WeakReference<>(listener);
    }

    private boolean isInitialized() {
        if (EmptyUtils.isEmpty(this.handler)) {
            return false;
        }
        if (EmptyUtils.isEmpty(this.helper)) {
            return false;
        }
        if (EmptyUtils.isEmpty(this.buffer)) {
            return false;
        }
        return true;
    }

    private void createHelper(String path) {
        if (EmptyUtils.isEmpty(this.helper)) {
            this.helper = new PWSerialPortHelper("IncubatorSerialPort");
            this.helper.setTimeout(9);
            this.helper.setPath(path);
            this.helper.setBaudrate(9600);
            this.helper.init(this);
        }
    }

    private void destoryHelper() {
        if (EmptyUtils.isNotEmpty(this.helper)) {
            this.helper.release();
            this.helper = null;
        }
    }

    private void createHandler() {
        if (EmptyUtils.isEmpty(this.thread) && EmptyUtils.isEmpty(this.handler)) {
            this.thread = new HandlerThread("ConstantTemperatureSerialPort");
            this.thread.start();
            this.handler = new ConstantTemperatureHandler(this.thread.getLooper());
        }
    }

    private void destoryHandler() {
        if (EmptyUtils.isNotEmpty(this.thread)) {
            this.thread.quitSafely();
            this.thread = null;
            this.handler = null;
        }
    }

    private void createBuffer() {
        if (EmptyUtils.isEmpty(this.buffer)) {
            this.buffer = Unpooled.buffer(4);
        }
    }

    private void destoryBuffer() {
        if (EmptyUtils.isNotEmpty(this.buffer)) {
            this.buffer.release();
            this.buffer = null;
        }
    }

    private void write(byte[] data) {
        PWLogger.d("ConstantTemperature Send:" + ByteUtils.bytes2HexString(data, true, ", "));
        if (this.isInitialized() && this.enabled) {
            this.helper.writeAndFlush(data);
            ConstantTemperatureSerialPort.this.switchReadModel();
        }
    }

    public void switchReadModel() {
        if (EmptyUtils.isNotEmpty(this.listener)) {
            this.listener.get().onConstantTemperatureSwitchReadModel();
        }
    }

    public void switchWriteModel() {
        if (EmptyUtils.isNotEmpty(this.listener)) {
            this.listener.get().onConstantTemperatureSwitchWriteModel();
        }
    }

    private boolean ignorePackage() {
        boolean result = false;
        int index = ConstantTemperatureTools.indexOf(this.buffer, ConstantTemperatureTools.HEADER);
        if (index != -1) {
            result = true;
            byte[] data = new byte[index];
            this.buffer.readBytes(data, 0, data.length);
            this.buffer.discardReadBytes();
            PWLogger.d("指令丢弃:" + ByteUtils.bytes2HexString(data, true, ", "));
        }
        return result;
    }


    @Override
    public void onConnected(PWSerialPortHelper helper) {
        if (!this.isInitialized() || !helper.equals(this.helper)) {
            return;
        }
        this.buffer.clear();
        this.switchWriteModel();
        if (EmptyUtils.isNotEmpty(this.listener)) {
            this.listener.get().onConstantTemperatureConnected();
        }
    }

    @Override
    public void onException(PWSerialPortHelper helper) {
        if (!this.isInitialized() || !helper.equals(this.helper)) {
            return;
        }
        if (EmptyUtils.isNotEmpty(this.listener)) {
            this.listener.get().onConstantTemperatureException();
        }
    }

    @Override
    public void onByteReceived(PWSerialPortHelper helper, byte[] buffer, int length) throws IOException {
        if (!this.isInitialized() || !helper.equals(this.helper)) {
            return;
        }
        this.buffer.writeBytes(buffer, 0, length);

        while (this.buffer.readableBytes() >= 3) {
            byte[] header = new byte[ConstantTemperatureTools.HEADER.length];
            this.buffer.getBytes(0, header);

            if (!ConstantTemperatureTools.checkHeader(header)) {
                if (this.ignorePackage()) {
                    continue;
                } else {
                    break;
                }
            }
            int lenth = 0xFF & this.buffer.getByte(2);

            if (this.buffer.readableBytes() < lenth + 3) {
                break;
            }
            this.buffer.markReaderIndex();

            byte[] data = new byte[lenth + 3];
            this.buffer.readBytes(data, 0, data.length);

            if (!ConstantTemperatureTools.checkFrame(data)) {
                this.buffer.resetReaderIndex();
                //当前包不合法 丢掉正常的包头以免重复判断
                this.buffer.skipBytes(ConstantTemperatureTools.HEADER.length);
                this.buffer.discardReadBytes();
                continue;
            }
            this.buffer.discardReadBytes();
            PWLogger.d("ConstantTemperature Recv:" + ByteUtils.bytes2HexString(data, true, ", "));
            this.switchWriteModel();
            if(EmptyUtils.isNotEmpty(this.listener)){
                this.listener.get().onConstantTemperaturePackageReceived(data);
            }
        }
    }

    private class ConstantTemperatureHandler extends Handler {
        public ConstantTemperatureHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0: {
                    byte[] message = (byte[]) msg.obj;
                    if (EmptyUtils.isNotEmpty(message)) {
                        ConstantTemperatureSerialPort.this.write(message);
                    }
                    break;
                }
                default:
                    break;
            }
        }
    }
}
